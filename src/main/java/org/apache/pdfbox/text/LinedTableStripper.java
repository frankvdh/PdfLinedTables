package org.apache.pdfbox.text;

import java.awt.BasicStroke;
import static java.awt.BasicStroke.CAP_BUTT;
import static java.awt.BasicStroke.JOIN_ROUND;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDTextState;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

/**
 * Extract data from line-delimited tables in a PDF document.
 *
 * Data is extracted as a set of rectangular TableCells on a page, each with a
 * rectangular boundary and the text within that rectangle. Each table must
 * start on a new page. The table may have a row of table headings above it,
 * identified by their fill colour. If no fill Color is specified, it is assumed
 * that the top horizontal line identifies the top of the table. Cells may be
 * arbitrary size and placement. Cells are delimited by vertical and horizontal
 * lines.
 *
 */
public class LinedTableStripper extends PDFGraphicsStreamEngine {

    public static final Logger LOG = LogManager.getLogger(LinedTableStripper.class);
    private static final GlyphList GLYPHLIST;

    static {
        // load additional glyph list for Unicode mapping
        String path = "/org/apache/pdfbox/resources/glyphlist/additional.txt";
        //no need to use a BufferedInputSteam here, as GlyphList uses a BufferedReader
        try (InputStream input = GlyphList.class.getResourceAsStream(path)) {
            GLYPHLIST = new GlyphList(GlyphList.getAdobeGlyphList(), input);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    // Sorted lists of vertical and horizontal lines
    /**
     * Vertical graphic lines in the table
     *
     */
    final protected SortedSet<FRectangle> vertLines = new TreeSet<>();

    /**
     * Horizontal graphic lines in the table
     *
     */
    final protected SortedMap<Float, SortedMap<Float, FRectangle>> horizLines = new TreeMap<>();

    /**
     * Rectangle graphics in the table
     *
     */
    final protected TreeMap<Integer, SortedMap<Float, SortedMap<Float, FRectangle>>> rectangles = new TreeMap<>();

    /**
     * Map of text by X within Y location for building rectangles. In reverse
     * order so that they are in the same order as lines and rectangles... top
     * of page is first
     *
     */
    final protected SortedMap<Float, SortedMap<Float, TextPosition>> textLocationsByYX = new TreeMap<>();

    /**
     * Size of current page
     *
     */
    final protected PDRectangle mediaBox;
    final private boolean suppressDuplicateOverlappingText;
    private final GeneralPath linePath = new GeneralPath();
    private final Matrix postRotate;

    /**
     * Constructor.
     *
     * @param page Page to be read.
     * @param extraRotation Force page rotation flag
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public LinedTableStripper(PDDocument doc, PDPage page, int extraRotation, boolean suppressDuplicates) throws IOException {
        super(page);
        suppressDuplicateOverlappingText = suppressDuplicates;
        mediaBox = page.getMediaBox();
        LOG.debug("Initial page rotation: {}, BBox {}", page.getRotation(), mediaBox);
        // Rotate 180 degrees, so that output uses coordinates increasing down the page
        switch (extraRotation) {
            case 90 -> {
                postRotate = Matrix.getRotateInstance(-Math.PI / 2, 0, mediaBox.getWidth());
            }

            case 180 -> {
                postRotate = Matrix.getRotateInstance(Math.PI, mediaBox.getWidth(), mediaBox.getHeight());
            }
            case 270 -> {
                postRotate = Matrix.getRotateInstance(-Math.PI / 2, mediaBox.getHeight(), 0);
            }
            case 0 -> {
                postRotate = new Matrix();
            }
            default ->
                throw new RuntimeException("Rotation must be 0, 90, 180, or 270: " + extraRotation);
        }
        // Flip, so that output uses coordinates increasing down the page
        postRotate.scale(1, -1);
        postRotate.translate(0, -mediaBox.getUpperRightY());
        transformRectangle(mediaBox, postRotate);
        LOG.traceExit("constructor: {}", mediaBox);
    }

    private Point2D.Float transformPoint(float x, float y, Matrix m) {
        var p = new Point2D.Float(x, y);
        m.transform(p);
        return p;
    }

    private void transformRectangle(PDRectangle r, Matrix m) {
        var p0 = transformPoint(r.getLowerLeftX(), r.getLowerLeftY(), m);
        var p1 = transformPoint(r.getUpperRightX(), r.getUpperRightY(), m);
        LOG.debug("Rectangle transformed from {} to {}, {}", r, p0, p1);
        r.setLowerLeftX(Math.min(p0.x, p1.x));
        r.setLowerLeftY(Math.min(p0.y, p1.y));
        r.setUpperRightX(Math.max(p0.x, p1.x));
        r.setUpperRightY(Math.max(p0.y, p1.y));
    }

    /**
     * Process the specified page... populate the vertLines, horizLines,
     * rectangles, textLocationsByYX structures
     *
     * @param page
     * @throws IOException
     */
    @Override
    final public void processPage(PDPage page) throws IOException {
        super.initPage(page);
        // Some code below depends on the page lower left corner being at (0,0)
        if (mediaBox.getLowerLeftX() != 0 || mediaBox.getLowerLeftY() != 0) {
            LOG.warn("Page is not zero-based: {}", mediaBox);
        }
        LOG.traceEntry("processPage: size {}, rotation {}, matrix {}", mediaBox, page.getRotation(), page.getBBox(), getGraphicsState().getCurrentTransformationMatrix());
//        rotatePage(page, extraRotation); // Must be done after initPage()
        vertLines.clear();
        horizLines.clear();
        rectangles.clear();
        textLocationsByYX.clear();

        if (page.hasContents()) {
            isProcessingPage = true;
            processStream(page);
            isProcessingPage = false;
        }
        LOG.traceExit("processPage: {}", mediaBox);
    }

    protected boolean endTableFound;

    /**
     * Append the section of the table matching the criteria on the current
     * page.
     *
     * Finds the table, and removes extraneous lines, rectangles, and text.
     * Scans the @rectangles sorted collection for the first rectangle of the
     * heading colour. Then expands the X,Y limits until it finds the first data
     * colour rectangle (if any). The bottom of the heading, and therefore the
     * top of the data, is at this Y coordinate.
     *
     * It then extracts text below the heading and between the X,Y bounds, and
     * scans it for the tableEnd pattern.
     *
     * @param headingColour Fill colour of heading, may be null
     * @param dataColour Fill colour of data, may be null
     * @param tableEnd Pattern to identify end of table
     * @return the bounds of the table.
     *
     * @throws java.io.IOException for file errors May be overridden if there is
     * some other mechanism to identify the top of the table.
     */
    public boolean appendToTable(Color headingColour, float startY, float endY, int numColumns, ArrayList<String[]> table) throws IOException {
        SortedSet<TableCell> rects = extractCells(headingColour, startY, endY);
        if (rects == null) {
            return false;
        }
        String[] row = new String[numColumns];
        int colNum = 0;
        for (TableCell r : rects) {
            row[colNum++] = r.getText();
            if (colNum >= numColumns) {
                table.add(row);
                row = new String[numColumns];
                colNum = 0;
            }
        }
        if (colNum > 0) {
            table.add(row);
        }
        return endTableFound;
    }

    /**
     * Extracts the cells of a table from data extracted from a page.
     *
     * Finds the table by scanning the rectangles collection from startY for the
     * row of rectangle of the heading colour. The bottom of the heading is the
     * top of the data. The X bounds of the heading define the X bounds of the
     * table.
     *
     * It then composites text within the table bounds into rectangular cells
     * based on horizontal and vertical lines.
     *
     * @param headingColour Fill colour of heading, may be null
     * @param dataColour Fill colour of data, may be null
     * @param startY Y coordinate of top of table relative to top of mediaBox
     * @param endY Y coordinate of bottom of table, as returned by findEndTable
     * @return the set of rectangles making up the table.
     *
     * @throws java.io.IOException for file errors
     */
    protected SortedSet<TableCell> extractCells(Color headingColour, float startY, float endY) throws IOException {
        assert startY >= 0 : "startY < 0";
        var bounds = new FRectangle(Float.NaN, startY, Float.NaN, endY);
        if (!findTable(headingColour, bounds)) {
            return null;
        }
        // Now have located top & bottom of data in the table, and left & right limits
        // Extract subsets of the lines, rectangles, and text within these limits.

        // Remove rectangles that are below the last horizontal line of the table... this removes any footnotes
        // Initially  include rectangles which extend above the top of the table, because they may extend down into the table
        bounds.expand(1);
        var rects = new TreeMap<>(rectangles.get(headingColour.getRGB()).subMap(bounds.getMinY(), bounds.getMaxY()));
        for (var yIt = rects.entrySet().iterator(); yIt.hasNext();) {
            var yEntry = yIt.next();
            yEntry.getValue().entrySet().removeIf((var xEntry) -> !bounds.containsX(xEntry.getKey()) || xEntry.getValue().getHeight() < 3 || (xEntry.getValue().getWidth() < 3));
            if (yEntry.getValue().isEmpty()) {
                yIt.remove();
            }
        }

        // Remove horizontal lines above and below the table, trim the remainder to the horizontal limits of the table
        var horiz = new TreeMap<>(horizLines.subMap(bounds.getMinY(), bounds.getMaxY()));
        for (var row : horiz.values()) {
            for (var e : row.entrySet()) {
                var h = e.getValue();
                if (!bounds.overlapsX(h.getMinX(), h.getMaxX())) {
                    row.remove(e.getKey());
                    continue;
                }
                h.trimX(bounds.getMinX(), bounds.getMaxX());
                if (h.getMinX() != e.getKey()) {
                    row.remove(e.getKey());
                    if (h.getWidth() >= 3) {
                        row.put(h.getMinX(), h);
                    }
                } else {
                    if (h.getWidth() < 3) {
                        row.remove(e.getKey());
                    }
                }
            }
        }
        if (horiz.isEmpty()) {
            LOG.error("No horizontal lines in table");
            return null;
        }

        // Remove vertical lines left or right of the table, trim the remainder to the vertical limits of the table
        // Initially  include lines which extend above the top of the table, because they may extend down into the table
        SortedSet<FRectangle> vert = new TreeSet<>(vertLines.subSet(new FRectangle(bounds.getMinX(), bounds.getMinY()), new FRectangle(0, bounds.getMaxY())));
        vert.forEach((FRectangle v) -> v.trimY(bounds.getMinY(), bounds.getMaxY()));
        vert.removeIf((FRectangle v) -> v.getHeight() < 3 || !bounds.intersects(v));
        if (vert.isEmpty()) {
            LOG.error("No vertical lines in table");
            return null;
        }

        // Remove text that is outside the table
        var textLocs = textLocationsByYX.subMap(bounds.getMinY(), bounds.getMaxY());
        for (var yIt = textLocs.entrySet().iterator(); yIt.hasNext();) {
            var yEntry = yIt.next();
            yEntry.getValue().entrySet().removeIf((var xEntry) -> !bounds.containsX(xEntry.getKey()));
            if (yEntry.getValue().isEmpty()) {
                yIt.remove();
            }
        }
        if (textLocs.isEmpty()) {
            LOG.error("No text in table");
            return null;
        }
        return buildActualRectangles(horiz, vert, textLocs);
    }

    /**
     * Convert horizontal and vertical lines to rectangles.
     *
     * Assumes that horiz and vert are sorted in descending Y, ascending X order
     * Assumes that the grid is regular... that every horizontal line extends
     * from the left edge to the right, and every vertical line extends from the
     * top to the bottom. Each rectangle so formed is added to the rects
     * collection
     *
     * @param horiz Set of horizontal lines, sorted by Y,X
     * @param vert Set of vertical lines, sorted by Y,X
     * @param rects Set of rectangles
     */
    protected SortedSet<TableCell> buildActualRectangles(SortedMap<Float, SortedMap<Float, FRectangle>> horiz, SortedSet<FRectangle> vert, SortedMap<Float, SortedMap<Float, TextPosition>> tableContents) {
        if (horiz.size() < 2 || vert.size() < 2) {
            LOG.error("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
            return null;
        }

        SortedSet<TableCell> results = new TreeSet<>();
        while (!horiz.isEmpty()) {
            var top = horiz.firstKey();
            var row0 = horiz.remove(top);

            // Trim all vertical lines to the current top, so that lines
            // intersecting h0 are all indexed in left-right order
            while (!vert.isEmpty() && vert.first().getMinY() < top) {
                var v = vert.removeFirst();
                v.trimY(top, v.getMaxY());
                if (v.getHeight() > 3) {
                    vert.add(v);
                }
            }

            while (!row0.isEmpty()) {
                var h0 = row0.remove(row0.firstKey());
                h0.expand(1.5f);
                var vSet = new TreeSet<>(vert.subSet(h0, new FRectangle(h0.getMaxX(), h0.getMaxY())));
                while (vSet.size() > 1) {
                    final var v0 = vSet.removeFirst();
                    if (!v0.intersects(h0)) {
                        // This vertical line doesn't intersect the give horizontal line
                        continue;
                    }
                    final var left = v0.getMinX();
                    FRectangle v1 = null;
                    float right = 0;
                    while (!vSet.isEmpty()) {
                        v1 = vSet.removeFirst();
                        right = v1.getMaxX();
                        if (v1.intersects(h0)) {
                            break;
                        }
                    }
                    if (v1 == null) {
                        break;
                    }
                    var bottom = Math.min(v0.getMaxY(), v1.getMaxY());
                    v0.expand(1f);
                    v1.expand(1f);
                    var hSet = new TreeMap<>(horiz.subMap(top + 1, bottom + 1));
                    while (!hSet.isEmpty()) {
                        bottom = hSet.firstKey();
                        var row1 = new TreeMap<>(hSet.remove(bottom));
                        while (!row1.isEmpty()) {
                            var h1 = row1.remove(row1.firstKey());
                            if (!h1.intersects(v0) || !h1.intersects(v1)) {
                                continue;
                            }
                            var rect = new TableCell(left, bottom, right, top);
                            rect.setText(getRectangleText(rect, tableContents));
                            results.add(rect);
                            LOG.debug("Added \"{}\" {}", rect.getText().replaceAll("\\n", "\\\\\\n"), rect);
                            vert.remove(v0);
                            // Only keep the part of v0 that extends below h1
                            // Do not trim v1 or h1 because they can be the left or top edge of another cell
                            v0.trimY(bottom, v0.getMaxY());
                            if (v0.getHeight() > 3) {
                                vert.add(v0);
                            }
                            h0.trimX(right, h0.getMaxX());
                            if (h0.getWidth() > 3) {
                                row0.put(left, h0);
                            }
                            // Rectangle is complete... start processing next top horizontal line
                            hSet.clear();
                            vSet.clear();
                            break;
                        }
                    }
                }
            }
        }
        LOG.traceExit("buildActualRectangles: Count = {}", results.size());
        return results;
    }

    /**
     * Convert horizontal and vertical lines to rectangles.
     *
     * Assumes that horiz and vert are sorted in descending Y, ascending X order
     * Assumes that the grid is regular... that every horizontal line extends
     * from the left edge to the right, and every vertical line extends from the
     * top to the bottom. Each rectangle so formed is added to the rects
     * collection
     *
     * @param horiz Set of horizontal lines, sorted by Y,X
     * @param vert Set of vertical lines, sorted by Y,X
     * @param rects Set of rectangles
     */
    protected TableCell[][] buildRegularRectangles(SortedSet<FRectangle> horiz, SortedSet<FRectangle> vert, SortedSet<TableCell> tableContents) {
        if (horiz.size() < 2 || vert.size() < 2) {
            LOG.error("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
            return null;
        }
        var hSet = new TreeSet<Float>();
        var prevH = Float.POSITIVE_INFINITY;
        for (var h : horiz) {
            if (prevH - h.getMaxY() > 3) {
                prevH = h.getMaxY();
                hSet.add(prevH);
            }
        }
        var vSet = new TreeSet<Float>();
        for (var v : vert) {
            var exists = !vSet.subSet(v.getMinX() - 3, v.getMaxX() + 3).isEmpty();
            if (!exists) {
                vSet.add(v.getMaxX());
            }
        }

        if (hSet.size() < 2 || vSet.size() < 2) {
            LOG.error("Unique horizontal lines = {}, vertical lines = {} so no table", hSet.size(), vSet.size());
            return null;
        }
        // Now have all unique horizontal and vertical lines
        TableCell[][] results = new TableCell[hSet.size()][vSet.size() - 1];
        var h0 = hSet.removeFirst();
        for (var row = results.length - 1; row >= 0; row--) {
            final var h1 = hSet.removeFirst();
            var v0 = vSet.getFirst();
            float v1;
            for (var col = 0; col < vSet.size() - 1; col++) {
                v1 = vSet.ceiling(v0 + 3);
                var result = new TableCell(v0, h1, v1, h0);
                var srcSet = new TreeSet<>(tableContents);
                srcSet.removeIf((var r) -> !r.intersects(result));
                if (srcSet.isEmpty()) {
                    LOG.error("Rectangle {} has no corresponding source", result);
                    continue;
                }
                if (srcSet.size() > 1) {
                    LOG.error("Rectangle {} has {} sources", result, srcSet.size());
                    continue;
                }
                var src = srcSet.first();
                result.setText(src.getText());
                LOG.debug("Added \"{}\" from rectangles, area = {} to {}", result.getText(), src, result);
                results[row][col] = result;
                v0 = v1;
            }
            h0 = h1;
        }
        LOG.traceExit("buildRectangles: Count = {}", results.length);
        return results;
    }

    /**
     * Extract the text contained within the specified rectangle
     *
     * @param rect
     * @param tableContents
     * @return
     */
    protected String getRectangleText(FRectangle rect, SortedMap<Float, SortedMap<Float, TextPosition>> tableContents) {
        StringBuilder sb = new StringBuilder(100);
        var yRange = tableContents.subMap(rect.getMinY(), rect.getMaxY());
        for (var row : yRange.values()) {
            var xRange = row.subMap(rect.getMinX(), rect.getMaxX());
            var prevX = rect.getMinX();
            for (var tp : xRange.values()) {
            var p = postRotate.transformPoint(tp.getX(), tp.getY());
                while (prevX + tp.getWidthOfSpace() < p.x) {
                    sb.append(' ');
                    prevX += tp.getWidthOfSpace();
                }
                prevX = p.x + tp.getWidth();
                sb.append(tp.getUnicode());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Find the end of the current table
     *
     * @param tableTop
     * @param tableEnd Pattern outside the table to search for. If null, the
     * bottom-most horizontal line on the page is assumed to be the bottom of
     * the table
     * @return Y coordinate of bottom-most line
     */
    protected float findEndTable(float startY, float endY, Pattern tableEnd) {
        LOG.traceEntry("findEndTable \"{}\"", (tableEnd == null ? "null" : tableEnd.toString()));
        // Scan through the text for the endTable delimiter text
        endTableFound = false;
        if (tableEnd != null) {
            var rows = textLocationsByYX.subMap(endY, startY);  // NB Reverse order
            for (var c : rows.entrySet()) {
                float y = c.getKey();
                // Concatenate all of the text onto entire lines
                StringBuilder line = new StringBuilder(c.getValue().values().size());
                c.getValue().values().forEach(text -> {
                    line.append(text);
                });
                if (tableEnd.matcher(line.toString()).find()) {
                    endTableFound = true;
                    LOG.traceExit("findEndTable: {}", y);
                    return y;
                }
            }
            LOG.warn("Table end delimiter \"{}\" not found", tableEnd.toString());
        }
        if (!horizLines.isEmpty()) {
            // Assume that the last horizontal line on the page is the bottom of the table
            float tableBottom = horizLines.lastKey();
            if (tableBottom <= startY) {
                LOG.warn("No Table end delimiter specified and no horizontal line below heading");
            }
            return tableBottom;
        }
        LOG.warn("No Table end delimiter specified and no horizontal line found");
        return Float.NaN;
    }

    /**
     * Find a table matching the criteria on the given page.
     *
     * Scans the rectangles sorted collection for the first rectangle of the
     * heading colour. Then expands the X,Y limits until it finds the first data
     * colour rectangle (if any). The bottom of the heading, and therefore the
     * top of the data, is at this Y coordinate.
     *
     * @param headingColour Fill colour of heading, may be null
     * @param dataColour Fill colour of data, may be null
     * @param bounds Destination for bounds of found table. The top, left, right
     * bounds are populated. The bottom bound maxY is not calculated yet.
     * @return number of columns
     * @throws java.io.IOException for file errors May be overridden if there is
     * some other mechanism to identify the top of the table.
     */
    protected boolean findTable(Color headingColour, FRectangle bounds) throws IOException {
        LOG.debug("findTable(\"{}\")", headingColour);
        if (headingColour != null) {
            // Find the location of the table by finding the first rectangle of the right colour
            var sameColour = rectangles.get(headingColour.getRGB());
            if (sameColour == null || sameColour.isEmpty()) {
                LOG.error("No rectangles found with colour {}", headingColour.toString());
                return false;
            } else {
                FRectangle hdgBounds = new FRectangle(sameColour.firstEntry().getValue().firstEntry().getValue());
                for (var row : sameColour.values()) {
                    // The first rectangle of the specified colour provides the left boundary of the table
                    // The last rectangle of the same colour on the same row as the first provides the right boundary of the table
                    var first = row.firstEntry().getValue();
                    var last = row.lastEntry().getValue();
                    if (!first.intersects(hdgBounds)) {
                        break;
                    }
                    hdgBounds.add(first);
                    hdgBounds.add(last);
                }
                // Top of table = bottom of heading
                bounds.setMinY(hdgBounds.getMaxY());
                bounds.setX(hdgBounds.getMinX(), hdgBounds.getMaxX());
                return true;
            }
        }

        // No heading colour specified
        // The bottom of the first horizontal line or top of first rectangle is taken
        if (!rectangles.isEmpty()) {
            getRectangleHdgRange(bounds);
        }
        if (horizLines.isEmpty()) {
            return bounds.getMinY() == Float.POSITIVE_INFINITY;
        }

        // horizLines is not empty
        bounds.setMinY(Math.min(bounds.getMinY(), horizLines.firstKey()));
        var vert = new TreeSet<>(vertLines);
        vert.removeIf((var v) -> !v.containsY(bounds.getMaxY()));
        if (vert.isEmpty()) {
            var hSet = new TreeMap<>(horizLines.headMap(bounds.getMinY()));
            bounds.setX(horizLines.firstEntry().getValue().firstKey(), hSet.lastEntry().getValue().lastKey());
            return true;
        }
        bounds.setMinX(vert.getFirst().getMinX() - 1);
        bounds.setMaxX(vert.getLast().getMaxX() + 1);
        for (var v : vert) {
            if (v.getMinX() < bounds.getMinX()) {
                bounds.setMinX(v.getMinX() - 1);
            }
            if (v.getMaxX() > bounds.getMaxX()) {
                bounds.setMaxX(v.getMaxX() + 1);
            }
        }
        return true;
    }

    private void getRectangleHdgRange(FRectangle bounds) {
        bounds.setMinY(Float.POSITIVE_INFINITY);
        for (var colour : rectangles.entrySet()) {
            var firstRow = colour.getValue().firstEntry();
            if (firstRow.getKey() < bounds.getMinY()) {
                bounds.setMinY(firstRow.getKey());
                var left = firstRow.getValue().firstKey();
                var rightRect = firstRow.getValue().lastEntry().getValue();
                bounds.setX(left, rightRect.getMaxX());
            }
        }
    }

    /**
     * Append a rectangle to the output
     *
     * @param p0
     * @param p1
     * @param p2
     * @param p3
     */
    @Override
    public void appendRectangle(Point2D p0, Point2D p1,
            Point2D p2, Point2D p3
    ) {
        LOG.traceEntry("appendRectangle {}, {}, {}, {}", p0, p1, p2, p3);
        linePath.moveTo(p0.getX(), p0.getY());
        linePath.lineTo(p1.getX(), p1.getY());
        linePath.lineTo(p2.getX(), p2.getY());
        linePath.lineTo(p3.getX(), p3.getY());
        linePath.closePath();
    }

    /**
     * Add a line/rectangle to the appropriate set -- horizontal or vertical
     * lines, or rectangles
     *
     * Coordinates are in the display coordinate system
     *
     * @param fillColour
     * @param strokeColour
     * @param stroke
     */
    private void addRectPath(Color fillColour, Color strokeColour, Stroke stroke) {
        LOG.traceEntry("addRectPath: {}, {}", linePath.getBounds(), fillColour);
        Rectangle2D bounds = linePath.getBounds2D();
        // Ignore cells too small for text
        if (bounds.getHeight() < 3 && bounds.getWidth() < 3) {
            linePath.reset();
            return;
        }
        var p0 = postRotate.transformPoint((float) bounds.getMinX(), (float) bounds.getMinY());
        var p1 = postRotate.transformPoint((float) bounds.getMaxX(), (float) bounds.getMaxY());
        // Coordinates here are already in display space
        FRectangle rect = new FRectangle(fillColour, strokeColour, stroke, p0, p1);
        LOG.debug("Rectangle {} -> {}", bounds, rect);
        // Add a rectangle to the appropriate sorted list (horizLines, vertLines, boxes).
        // 3 is a kludge -- NZKM.pdf has rectangles 2.96 wide that are not vertical lines
        if (rect.getHeight() <= 3) {
            var row = horizLines.computeIfAbsent(rect.getMinY(), k -> new TreeMap<>());
            row.put(rect.getMinX(), rect);
            LOG.trace("horiz line added {}", rect);
        } else if (rect.getWidth() <= 3) {
            vertLines.add(rect);
            LOG.trace("vertical line added {}", rect);
        } else {
            if (fillColour != null) {
                var sameColour = rectangles.computeIfAbsent(fillColour.getRGB(), k -> new TreeMap<>());
                var sameRow = sameColour.computeIfAbsent(rect.getMinY(), k -> new TreeMap<>());
                sameRow.put(rect.getMinX(), rect);
                LOG.trace("rectangle added {}", rect);
            }
            if (strokeColour != null) {

                // Decompose rectangles into vertical and horizontal lines
                // This may result in duplicate lines
                var row = horizLines.computeIfAbsent(rect.getMinY(), k -> new TreeMap<>());
                if (!row.keySet().contains(rect.getMinX())) {
                    row.put(rect.getMinX(), new FRectangle(rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMinY()));
                }
                row = horizLines.computeIfAbsent(rect.getMaxY(), k -> new TreeMap<>());
                if (!row.keySet().contains(rect.getMinX())) {
                    row.put(rect.getMinX(), new FRectangle(rect.getMinX(), rect.getMaxY(), rect.getMaxX(), rect.getMaxY()));
                }
                var left = new FRectangle(rect.getMinX(), rect.getMinY(), rect.getMinX(), rect.getMaxY());
                if (!vertLines.contains(left)) {
                    vertLines.add(left);
                }
                var right = new FRectangle(rect.getMaxX(), rect.getMinY(), rect.getMaxX(), rect.getMaxY());
                if (!vertLines.contains(right)) {
                    vertLines.add(right);
                }
                LOG.trace("rectangle edges added {}", rect);
            }
        }
        linePath.reset();
    }

    /**
     * Add a stroked path (i.e. a set of lines) to the maps
     *
     * @throws IOException
     */
    @Override
    public void strokePath() throws IOException {
        LOG.traceEntry("strokePath: {}", linePath.getBounds());
        addRectPath(null, getStrokingColor(), getStroke());
    }

    /**
     * Add a filled path (i.e. a shaded rectangle) to the map
     *
     * @throws IOException
     */
    @Override
    public void fillPath(int windingRule) throws IOException {
        LOG.traceEntry("fillPath: {}, {}", linePath.getBounds(), getNonStrokingColor());
        addRectPath(getNonStrokingColor(), null, null);
    }

    /**
     * Fills and then strokes the path.
     *
     * @param windingRule The winding rule this path will use.
     * @throws IOException If there is an IO error while filling the path.
     */
    @Override
    public void fillAndStrokePath(int windingRule) throws IOException {
        LOG.traceEntry("fillAndStrokePath: {}", linePath.getBounds());
        addRectPath(getNonStrokingColor(), getStrokingColor(), getStroke());
    }

    /**
     * Returns the current line path. This is reset to empty after each
     * fill/stroke.
     *
     * @return
     */
    protected GeneralPath getLinePath() {
        return linePath;
    }

    // returns the stroking AWT Paint
    private Color getStrokingColor() throws IOException {
        return getColor(getGraphicsState().getStrokingColor());
    }

    // returns the stroking AWT Paint
    private Color getColor(PDColor colour) throws IOException {
        return new Color(colour.toRGB());
    }

    /**
     * Returns the non-stroking AWT Paint. You may need to call this if you
     * override {@link #showGlyph(Matrix, PDFont, int, Vector) showGlyph()}. See
     * <a href="https://issues.apache.org/jira/browse/PDFBOX-5093">PDFBOX-5093</a>
     * for more.
     *
     * @return The non-stroking AWT Paint.
     * @throws IOException if the non-stroking AWT Paint could not be created
     */
    protected final Color getNonStrokingColor() throws IOException {
        return getColor(getGraphicsState().getNonStrokingColor());
    }

    // create a new stroke based on the current CTM and the current stroke
    private Stroke getStroke() {
        PDGraphicsState state = getGraphicsState();
        Matrix ctm = state.getCurrentTransformationMatrix();
        float x = ctm.getScaleX() + ctm.getShearX();
        float y = ctm.getScaleY() + ctm.getShearY();
        float lineWidth = state.getLineWidth() * (float) Math.sqrt((x * x + y * y) * 0.5);
        return new BasicStroke(lineWidth, CAP_BUTT, JOIN_ROUND, 1);
    }

    @Override
    public void clip(int windingRule) {
    }

    @Override
    public void moveTo(float x, float y) {
        LOG.traceEntry("moveTo {}, {}", x, y);
        linePath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        LOG.traceEntry("lineTo {}, {}", x, y);
        linePath.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        LOG.warn("curveTo ignored: {} {}, {} {}, {} {}", x1, y1, x2, y2, x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return linePath.getCurrentPoint();
    }

    @Override
    public void closePath() {
    }

    @Override
    public void endPath() {
        LOG.traceEntry("endPath: {}", linePath.getBounds());
        linePath.reset();
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        LOG.warn("{} Image {} by {} at {}, {} ignored", pdImage.getSuffix(), pdImage.getWidth(), pdImage.getHeight(), pdImage.getImage().getMinX(), pdImage.getImage().getMinY());
    }

    @Override
    public void showForm(PDFormXObject form) throws IOException {
        LOG.warn("Form ignored {}", form.getBBox());
    }

    @Override
    public void shadingFill(COSName shadingName) throws IOException {
        LOG.warn("shadingFill ignored {}", shadingName);
    }

    @Override
    public void showAnnotation(PDAnnotation annotation) throws IOException {
        LOG.warn("Annotation ignored {}", annotation.getRectangle());
    }

    /**
     * Process text from the PDF Stream. You should override this method if you
     * want to perform an action when encoded text is being processed.
     *
     * @param string the encoded text
     * @throws IOException if there is an error processing the string
     */
    @Override
    protected void showText(byte[] string) throws IOException {
        PDGraphicsState state = getGraphicsState();
        PDTextState textState = state.getTextState();

        // get the current font
        PDFont font = textState.getFont();
        if (font == null) {
            LOG.warn("No current font, will use default");
            font = getDefaultFont();
        }

        float fontSize = textState.getFontSize();
        float horizontalScaling = textState.getHorizontalScaling() / 100f;
        float charSpacing = textState.getCharacterSpacing();
        float wordSpacing = textState.getWordSpacing();

        // put the text state parameters into matrix form
        Matrix parameters = new Matrix(
                fontSize * horizontalScaling, 0, // 0
                0, fontSize, // 0
                0, textState.getRise());         // 1

        Matrix ctm = state.getCurrentTransformationMatrix();
        Matrix textMatrix = getGraphicsState().getTextMatrix();
        // read the stream until it is empty
        InputStream in = new ByteArrayInputStream(string);
        while (in.available() > 0) {
            // decode a character
            int before = in.available();
            int code = font.readCode(in);
            int codeLength = before - in.available();

            // text rendering matrix (text space -> device space)
//            Matrix textRenderingMatrix = textMatrix.multiply(ctm);
            // get glyph's position vector if this is vertical text
            // changes to vertical text should be tested with PDFBOX-2294 and PDFBOX-1422
            if (font.isVertical()) {
                // position vector, in text space
                Vector v = font.getPositionVector(code);

                // apply the position vector to the horizontal origin to get the vertical origin
                textMatrix.translate(v);
            }
            // text rendering matrix (text space -> device space)
            Matrix textRenderingMatrix = parameters.multiply(textMatrix).multiply(ctm);

            // use our additional glyph list for Unicode mapping
            String text = font.toUnicode(code, GLYPHLIST);
            // when there is no Unicode mapping available, coerce the character code into Unicode
            char unicode = text == null ? (char) code : text.charAt(0);
            // get glyph's width, in text space
            float width = codeLength == 1 && (unicode == 0x20 || unicode == 0xa0) ? wordSpacing
                    : font.getWidth(code) * fontSize / 1000 + charSpacing;

            float w = width * horizontalScaling;
            // process the decoded char
            Point2D.Float p = postRotate.transformPoint(textRenderingMatrix.getTranslateX(), textRenderingMatrix.getTranslateY());
            LOG.trace("'{}', {} to {}", text, new Point2D.Float(textMatrix.getTranslateX(), textMatrix.getTranslateY()), p);

        float glyphSpaceToTextSpaceFactor = (font instanceof PDType3Font) ? font.getFontMatrix().getScaleX() : 1 / 1000f;
        float spaceWidthText = 0;
        try
        {
            // to avoid crash as described in PDFBOX-614, see what the space displacement should be
            spaceWidthText = font.getSpaceWidth() * glyphSpaceToTextSpaceFactor;
        }
        catch (Exception exception)
        {
            LOG.warn(exception, exception);
        }
        if (spaceWidthText == 0)
        {
            // the average space width appears to be higher than necessary so make it smaller
            spaceWidthText = font.getAverageFontWidth() * glyphSpaceToTextSpaceFactor *.80f;
        }
        if (spaceWidthText == 0)
        {
            spaceWidthText = 1.0f; // if could not find font, use a generic value
        }
        // the space width has to be transformed into display units
        float spaceWidthDisplay = spaceWidthText * textRenderingMatrix.getScalingFactorX();
        processTextPosition(new TextPosition(0, mediaBox.getWidth(),
                mediaBox.getHeight(), textRenderingMatrix, 0, 0,
                p.y, p.x,
                Math.abs(spaceWidthDisplay), text, new int[] { code } , font, fontSize,
                (int)(fontSize * textMatrix.getScalingFactorX())));

            // update the text matrix
            if (font.isVertical()) {
                textMatrix.translate(0, width);
            } else {
                textMatrix.translate(w, 0);
            }
        }
    }

    /**
     * Add the char to the list of characters on a page. It takes care of
     * overlapping text.
     *
     * @param ch The text to process.
     */
    protected void processTextPosition(TextPosition tp) {
        LOG.traceEntry("processtextPosition {}", tp);
        var sortedRow = textLocationsByYX.computeIfAbsent(tp.getY(), k -> new TreeMap<>());
        if (suppressDuplicateOverlappingText) {
            float tolerance = tp.getWidth() / 3.0f;
            var yMatches = textLocationsByYX.subMap(tp.getY() - tolerance, tp.getY() + tolerance);
            for (var yMatch : yMatches.values()) {
                var xMatches = yMatch.subMap(tp.getX() - tolerance, tp.getX() + tolerance).values();
                if (xMatches.contains(tp)) {
                    return;
                }
            }
        }
        sortedRow.put(tp.getX(), tp);
    }

    // NOTE: there are more methods in PDFStreamEngine which can be overridden here too.
}
