package org.apache.pdfbox.text;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map.Entry;
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
     * Vertical graphic lines on the page Top of page is first
     *
     */
    final protected SortedMap<Float, SortedMap<Float, Float>> vertLines = new TreeMap<>();

    /**
     * Horizontal graphic lines on the page Top of page is first
     *
     */
    final protected SortedMap<Float, SortedMap<Float, Float>> horizLines = new TreeMap<>();

    /**
     * Filled rectangle graphics on the page... used to find table headings.
     * Stroked rectangles are decomposed into vertical and horizontal lines Top
     * of page is first
     *
     */
    final protected SortedMap<Integer, SortedMap<Float, SortedMap<Float, FRectangle>>> rectangles = new TreeMap<>();

    /**
     * Map of text by X within Y location for populating rectangles. Top of page
     * is first
     *
     */
    final protected SortedMap<Float, SortedMap<Float, SimpleTextPosition>> textLocationsByYX = new TreeMap<>();

    /**
     * Size of current page
     *
     */
    protected PDRectangle mediaBox;
    final private boolean suppressDuplicateOverlappingText;
    final private int extraQuadrantRotation;
    private final GeneralPath linePath = new GeneralPath();
    private AffineTransform postRotate;
    protected boolean endTableFound;
    protected float endTablePos;

    /**
     * Constructor.
     *
     * @param page Page to be read.
     * @param extraQuadrantRotation Number of CCW 90deg rotations to apply to
     * page
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public LinedTableStripper(PDDocument doc, int extraQuadrantRotation, boolean suppressDuplicates) throws IOException {
        super(null);
        suppressDuplicateOverlappingText = suppressDuplicates;
        this.extraQuadrantRotation = extraQuadrantRotation;
        // Set up extra rotation that is not specified in the page
        // Some pages e.g. GEN_3.7.pdf are rotated -90 degrees, but
        // page.getRotation() doesn't know it. This extra rotation is performed
        // separately because it can result in objects outside the normal media
        // box, which are then not rendered
        assert (extraQuadrantRotation >= 0 && extraQuadrantRotation <= 3) : "Rotation must be 0-3: " + extraQuadrantRotation;
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
        mediaBox = page.getMediaBox();
        LOG.trace("processPage: size {}, rotation {}", mediaBox, page.getRotation());
        vertLines.clear();
        horizLines.clear();
        rectangles.clear();
        textLocationsByYX.clear();

        postRotate = page.getMatrix().createAffineTransform();
        // Some code below depends on the page lower left corner being at (0,0)
        if (mediaBox.getLowerLeftX() != 0 || mediaBox.getLowerLeftY() != 0) {
            LOG.warn("Page is not zero-based: {}", mediaBox);
            postRotate.translate(-mediaBox.getLowerLeftX(), -mediaBox.getLowerLeftY());
        }
        postRotate.quadrantRotate(extraQuadrantRotation, mediaBox.getWidth() / 2, mediaBox.getWidth() / 2);
        // Also flip the coordinates vertically, so that the coordinates increase
        // down the page, so that rows at the top of the table are output first
        postRotate.scale(1, -1);
        postRotate.translate(0, -mediaBox.getUpperRightY());
        LOG.debug("PostRotate: {}", postRotate);
        transformRectangle(mediaBox, postRotate);
        LOG.debug("Transformed mediaBox: {}", mediaBox);

        if (page.hasContents()) {
            isProcessingPage = true;
            processStream(page);
            isProcessingPage = false;
            //           consolidateRowTable();
        }
    }

    private Point2D.Float transformPoint(float x, float y, AffineTransform at) {
        var p = new Point2D.Float(x, y);
        at.transform(p, p);
        return p;
    }

    private void transformRectangle(PDRectangle r, AffineTransform at) {
        var p0 = transformPoint(r.getLowerLeftX(), r.getLowerLeftY(), at);
        var p1 = transformPoint(r.getUpperRightX(), r.getUpperRightY(), at);
        r.setLowerLeftX(Math.min(p0.x, p1.x));
        r.setLowerLeftY(Math.min(p0.y, p1.y));
        r.setUpperRightX(Math.max(p0.x, p1.x));
        r.setUpperRightY(Math.max(p0.y, p1.y));
    }

    private void consolidateRowTable() {
        if (horizLines.size() < 2) {
            return;
        }
        Entry<Float, SortedMap<Float, Float>> prevRow = null;
        for (var it = horizLines.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            var row = entry.getValue();
            var y = entry.getKey();
            LOG.trace("Row:    {}", row);
            if (row.isEmpty()) {
                it.remove();
                continue;
            }
            if (prevRow == null || y - prevRow.getKey() > 3) {
                prevRow = entry;
            } else {
                mergeRow(prevRow.getValue(), row);
                it.remove();
                LOG.trace("Result: {}", row);
            }
        }
    }

    private void mergeRow(SortedMap<Float, Float> target, SortedMap<Float, Float> src) {
        for (var e : src.entrySet()) {
            var sL = e.getKey();
            var sR = e.getValue();
            var nearby = target.subMap(sL - 3, sR + 3);
            if (nearby.isEmpty()) {
                target.put(sL, sR);
                continue;
            }
            var success = false;
            for (var t : nearby.entrySet()) {
                var tL = t.getKey();
                var tR = t.getValue();
                if (tL <= sL) {
                    // src doesn't overlap target's left edge
                    if (tR >= sL) {
                        // src overlaps tgt, but not on left
                        if (tR <= sR) {
                            // src overlaps tgt to right only. Extend target to right
                            t.setValue(sR);
                        } else {
                            // tgt completely covers src... do nothing
                        }
                        success = true;
                        break;
                    } else {
                        // src is completely right of target, no overlap
                        // keep trying
                    }
                } else if (tR <= sR) {
                    // src contains target
                    target.remove(tL);
                    target.put(sL, sR);
                    success = true;
                    break;
                } else if (sR >= tL) {
                    // src overlaps target, extends left
                    target.remove(tL);
                    target.put(sL, tR);
                    success = true;
                    break;
                } else {
                    // src is completely left of target, no overlap
                    // keep trying
                }
            }
            if (!success) {
                // No overlaps found... add
                target.put(sL, sR);
            }
        }
    }

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
    public void appendToTable(Color headingColour, float startY, int numColumns, ArrayList<String[]> table) throws IOException {
        SortedSet<TableCell> rects = extractCells(headingColour, startY);
        if (rects == null) {
            return;
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
    protected TreeSet<TableCell> extractCells(Color headingColour, float startY) throws IOException {
        assert startY >= 0 : "startY < 0";
        var bounds = new FRectangle(Float.NaN, startY, Float.NaN, endTablePos);
        if (!findTable(headingColour, bounds)) {
            return null;
        }
        // Now have located top & bottom of data in the table, and left & right limits
        // Extract subsets of the lines, rectangles, and text within these limits.

        // Remove rectangles that are below the last horizontal line of the table... this removes any footnotes
        // Initially  include rectangles which extend above the top of the table, because they may extend down into the table
        var rects = new TreeMap<>(rectangles.get(headingColour.getRGB()).subMap(bounds.getMinY(), bounds.getMaxY()));
        for (var yIt = rects.entrySet().iterator(); yIt.hasNext();) {
            var yEntry = yIt.next();
            yEntry.getValue().entrySet().removeIf((var xEntry) -> !bounds.containsX(xEntry.getKey()) || xEntry.getValue().getHeight() < 3 || (xEntry.getValue().getWidth() < 3));
            if (yEntry.getValue().isEmpty()) {
                yIt.remove();
            }
        }

        // Remove horizontal lines above and below the table, trim the remainder to the horizontal limits of the table
        var horiz = new TreeMap<>(horizLines.subMap(bounds.getMinY()-3, bounds.getMaxY()+3));
        for (var row : horiz.values()) {
            for (var it = row.entrySet().iterator(); it.hasNext();) {
                var e = it.next();
                var left = bounds.trimX(e.getKey());
                var right = bounds.trimX(e.getValue());
                if (!bounds.overlapsX(left, right) || right - left < 3) {
                    it.remove();
                }
            }
        }
        horiz.entrySet().removeIf((var e) -> e.getValue().isEmpty());
        if (horiz.isEmpty()) {
            LOG.error("No horizontal lines in table");
            return null;
        }

        // Remove vertical lines left or right of the table, trim the remainder to the vertical limits of the table
        // Initially  include lines which extend above the top of the table, because they may extend down into the table
        // Remove empty rows
        var vert = trimVert(bounds, vertLines);
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

    private SortedMap<Float, SortedMap<Float, Float>> trimVert(FRectangle bounds, SortedMap<Float, SortedMap<Float, Float>> source) {
        var vert = new TreeMap<>(source.headMap(bounds.getMaxY()+3));
        var top = bounds.getMinY();
        var topRow = vert.computeIfAbsent(top, k -> new TreeMap<>());
        for (var itR = vert.entrySet().iterator(); itR.hasNext();) {
            var rowEntry = itR.next();
            var row = rowEntry.getValue();
            var rowY = rowEntry.getKey();

            // Remove lines left or right of the table, or completely above the table, or with top just above the bottom bound
            row.entrySet().removeIf((var e) -> e.getKey() < bounds.getMinX()-3 || e.getKey() > bounds.getMaxX()+3 || e.getValue() < top - 3);
            if (row.isEmpty()) {
                // Remove entry for row
                itR.remove();
                continue;
            }

            // Trim all remaining line bottoms to Y limits of table
            // Already know that the line intersects the table
            for (var e : row.entrySet()) {
                var bottom = e.getValue();
                // Trim bottom
                if (bottom > bounds.getMaxY()) {
                    e.setValue(bounds.getMaxY());
                }
            }
            if (rowY < top) {
                // Changed key == top 
                itR.remove();
                for (var v : row.entrySet()) {
                    if (!topRow.keySet().contains(v.getKey()) || topRow.get(v.getKey()) < v.getValue()) {
                        topRow.put(v.getKey(), v.getValue());
                    }
                }
            }
        }
        return vert;
    }

    /**
     * Trim vertical lines to the table Remove vertical lines whose bottom
     * vertex is above the table Trim vertical lines whose top vertex is above
     * the table
     *
     * @param top Top edge of table
     * @param vert Vertical lines, in X within Y order
     */
    private SortedMap<Float, Float> trimVert(float top, SortedMap<Float, SortedMap<Float, Float>> vert) {
        var topRow = vert.computeIfAbsent(top, k -> new TreeMap<>());
        for (var itR = vert.entrySet().iterator(); itR.hasNext();) {
            var rowEntry = itR.next();
            var rowY = rowEntry.getKey();
            if (rowY > top) {
                break;
            }

            // Remove lines above the table
            var row = rowEntry.getValue();
            row.entrySet().removeIf((var e) -> e.getValue() < top - 3);
            // Changed key == top 
            itR.remove();
                for (var v : row.entrySet()) {
                    if (!topRow.keySet().contains(v.getKey()) || topRow.get(v.getKey()) < v.getValue()) {
                        topRow.put(v.getKey(), v.getValue());
                    }
                }
        }
        vert.entrySet().removeIf((var row) -> row.getValue().isEmpty());
        return topRow;
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
    protected TreeSet<TableCell> buildActualRectangles(
            SortedMap<Float, SortedMap<Float, Float>> horiz,
            SortedMap<Float, SortedMap<Float, Float>> vert,
            SortedMap<Float, SortedMap<Float, SimpleTextPosition>> tableContents) {
        if (horiz.size() < 2 || vert.isEmpty()) {
            LOG.error("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
            return null;
        }

        var results = new TreeSet<TableCell>();
        while (!horiz.isEmpty()) {
            var top = horiz.firstKey();
            var row0 = horiz.remove(top);

            // Trim all vertical lines to the current top, so that lines
            // intersecting h0 are all in a single row, in left-right order
            var topRow = trimVert(top, vert);

            while (!row0.isEmpty()) {
                var h0 = row0.firstEntry();
                var left = h0.getKey();
                var right = h0.getValue();
                row0.remove(left);

                // Current top row has already been removed from horiz, so this
                // searches rows below it (i.e. with higher key values)
                // Find the first horizontal line that lies below the h0 line
                Entry<Float, Float> h1 = null;
                float bottom = Float.NaN;
                for (var rowEntry : horiz.entrySet()) {
                    if (rowEntry.getKey() < top + 3) {
                        // A second line too close below the first... ignore it
                        continue;
                    }
                    var row1 = rowEntry.getValue().subMap(left - 3, right + 3);
                    if (row1.isEmpty()) {
                        continue;
                    }
                    h1 = row1.firstEntry();
                    bottom = rowEntry.getKey();
                    break;
                }
                if (Float.isNaN(bottom)) {
                    continue;
                }

                LOG.trace("{}, {}, {}, {}", left, top, right, bottom);
                final var finalBottom = bottom;
                var vSet = new TreeMap<Float, Float>(topRow.subMap(left - 3, right + 3));
                vSet.entrySet().removeIf((var v) -> v.getValue() < finalBottom - 3);
                while (vSet.size() > 1) {
                    var left1 = vSet.firstEntry();
                    vSet.remove(left1.getKey());
                    var right1 = vSet.ceilingEntry(left + 3);
                    if (right1 == null) {
                        continue;
                    }
                    var rect = new TableCell(left, bottom, right, top);
                    if (rect.getHeight() < 3) {
                        continue;
                    }
                    rect.setText(getRectangleText(rect, tableContents));
                    results.add(rect);
                    LOG.trace("Added \"{}\" {}", rect.getText().replaceAll("\\n", "\\\\\\n"), rect);
                    // Only keep the part of v0 that extends below h1
                    // Do not trim v1 or h1 because they can be the left or top edge of another cell
                    vSet.entrySet().removeIf((var v) -> v.getKey() <= right1.getKey());
                    if (h0.getValue() > right) {
                        row0.put(right, h0.getValue());
                    }
                    // Rectangle is complete... start processing next top horizontal line
                    break;
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
    protected String getRectangleText(FRectangle rect, SortedMap<Float, SortedMap<Float, SimpleTextPosition>> tableContents) {
        StringBuilder sb = new StringBuilder(100);
        var yRange = tableContents.subMap(rect.getMinY(), rect.getMaxY());
        for (var row : yRange.values()) {
            var xRange = row.subMap(rect.getMinX(), rect.getMaxX());
            var prevX = rect.getMinX();
            for (var tp : xRange.values()) {
                var x = tp.getX();
                if (tp.getSpaceWidth() > 0) {
                    while (prevX + tp.getSpaceWidth() < x) {
                        sb.append(' ');
                        prevX += tp.getSpaceWidth();
                    }
                }
                prevX = x + tp.getWidth();
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
    protected void findEndTable(float startY, float endY, Pattern tableEnd) {
        LOG.traceEntry("findEndTable \"{}\"", (tableEnd == null ? "null" : tableEnd.toString()));
        // Scan through the text for the endTable delimiter text
        endTableFound = false;
        if (tableEnd != null) {
            var rows = textLocationsByYX.subMap(startY, endY);
            for (var c : rows.entrySet()) {
                endTablePos = c.getKey();
                // Concatenate all of the text onto entire lines
                StringBuilder line = new StringBuilder(c.getValue().values().size());
                c.getValue().values().forEach(tp -> {
                    line.append(tp.getUnicode());
                });
                if (tableEnd.matcher(line.toString()).find()) {
                    endTableFound = true;
                    LOG.traceExit("findEndTable: {}", endTablePos);
                    return;
                }
            }
            LOG.warn("Table end delimiter \"{}\" not found", tableEnd.toString());
        }
        if (!horizLines.isEmpty()) {
            // Assume that the last horizontal line on the page is the bottom of the table
            endTablePos = horizLines.lastKey();
            if (endTablePos <= startY) {
                LOG.warn("No Table end delimiter specified and no horizontal line below heading");
                endTablePos = Float.NaN;
            }
            return;
        }
        LOG.warn("No Table end delimiter specified and no horizontal line found");
        endTablePos = Float.NaN;
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
        LOG.traceEntry("findTable(\"{}\")", headingColour);
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
                    if (!hdgBounds.overlapsY(first.getMinY(), first.getMaxY())) {
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
        var vert = new TreeMap<>(vertLines.headMap(bounds.getMaxY()));
        var hSet = new TreeMap<>(horizLines.headMap(bounds.getMinY()));
        bounds.setX(horizLines.firstEntry().getValue().firstKey(), hSet.lastEntry().getValue().lastKey());
        if (vert.size() < 2) {
            return true;
        }
        bounds.setX(vert.ceilingEntry(bounds.getMinX() - 3).getKey(), vert.floorKey(bounds.getMaxX() + 3));
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
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
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
    private void addFilledRectangle() throws IOException {
        var fillColour = getNonStrokingColor();
        LOG.traceEntry("addFilledPath: {}, {}", linePath.getBounds(), fillColour);
        Rectangle2D bounds = linePath.getBounds2D();
        // Ignore cells too small for text
        if (bounds.getHeight() < 3 || bounds.getWidth() < 3) {
            LOG.warn("Ignored shaded rectangle too small for text");
            return;
        }

        var p0 = transformPoint((float) bounds.getMinX(), (float) bounds.getMinY(), postRotate);
        var p1 = transformPoint((float) bounds.getMaxX(), (float) bounds.getMaxY(), postRotate);
        // Coordinates here are already in display space
        FRectangle rect = new FRectangle(fillColour, null, null, p0, p1);
        LOG.trace("Rectangle {} -> {}", bounds, rect);

        // Add a rectangle to the appropriate sorted list (horizLines, vertLines, rectangles).
        // 3 is a kludge -- NZKM.pdf has rectangles 2.96 wide that are not vertical lines
        if (rect.getHeight() <= 3) {
            var row = horizLines.computeIfAbsent(rect.getMinY(), k -> new TreeMap<>());
            if (!row.keySet().contains(rect.getMinX()) || row.get(rect.getMinX()) < rect.getMaxX()) {
                row.put(rect.getMinX(), rect.getMaxX());
            }
            LOG.trace("horiz line added {}", rect);
        } else if (rect.getWidth() <= 3) {
            var row = vertLines.computeIfAbsent(rect.getMinY(), k -> new TreeMap<>());
            if (!row.keySet().contains(rect.getMinX()) || row.get(rect.getMinX()) < rect.getMaxY()) {
                row.put(rect.getMinX(), rect.getMaxY());
            }
            LOG.trace("vertical line added {}", rect);
        } else {
            var sameColour = rectangles.computeIfAbsent(fillColour.getRGB(), k -> new TreeMap<>());
            var sameRow = sameColour.computeIfAbsent(rect.getMinY(), k -> new TreeMap<>());
            sameRow.put(rect.getMinX(), rect);
            LOG.trace("rectangle added {}", rect);
        }
    }

    /**
     * Add linePath to the appropriate set -- horizontal or vertical lines
     *
     * Coordinates are in the display coordinate system
     *
     */
    private void addPath() throws IOException {
        var strokeColour = getStrokingColor();
        LOG.traceEntry("addPath: {}, {}", linePath.getBounds(), strokeColour);

        // Coordinates here are already in display space
        // However, there maybe an additional transform done to handle rotated
        // pages without the pageRotation set correctly
        var pi = linePath.getPathIterator(postRotate);
        var numPoints = 0;
        Point2D.Float prev = null;
        while (!pi.isDone()) {
            float coords[] = new float[6];
            pi.currentSegment(coords);
            var p1 = new Point2D.Float(coords[0], coords[1]);
            LOG.trace("coords {} -> {}", coords, p1);
            if (prev != null) {
                // Add a line to the appropriate sorted list (horizLines, vertLines).
                // 3 is a kludge -- NZKM.pdf has rectangles 2.96 wide that are not vertical lines
                if (Math.abs(p1.y - prev.y) <= 3) {
                    var row = horizLines.computeIfAbsent(prev.y, k -> new TreeMap<>());
                    var left = Math.min(prev.x, p1.x);
                    var right = Math.max(prev.x, p1.x);
                    if (!row.keySet().contains(left) || row.get(left) < right) {
                        row.put(left, right);
                    }
                    LOG.trace("horiz line added {} - {}", prev, p1);
                } else if (Math.abs(p1.x - prev.x) <= 3) {
                    var top = Math.min(prev.y, p1.y);
                    var bottom = Math.max(prev.y, p1.y);
                    var row = vertLines.computeIfAbsent(top, k -> new TreeMap<>());
                    if (!row.keySet().contains(prev.x) || row.get(prev.x) < bottom) {
                        row.put(prev.x, bottom);
                    }
                    LOG.trace("vertical line added {} - {}", prev, p1);
                } else {
                    LOG.warn("Diagonal line segment {}, {} ignored", prev, p1);
                }
            }
            prev = p1;
            pi.next();
            numPoints++;
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
        addPath();
    }

    /**
     * Add a filled path (i.e. a shaded rectangle) to the map
     *
     * @throws IOException
     */
    @Override
    public void fillPath(int windingRule) throws IOException {
        LOG.traceEntry("fillPath: {}, {}", linePath.getBounds(), getNonStrokingColor());
        addFilledRectangle();
        linePath.reset();
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
        addFilledRectangle();
        addPath();
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
    private Color getNonStrokingColor() throws IOException {
        return getColor(getGraphicsState().getNonStrokingColor());
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
        LOG.traceEntry("endPath: {}", linePath);
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
            assert font != null;
        }

        float fontSize = textState.getFontSize();
        float horizontalScaling = textState.getHorizontalScaling() / 100f;
        float charSpacing = textState.getCharacterSpacing();
        float wordSpacing = textState.getWordSpacing();

        float glyphSpaceToTextSpaceFactor = (font instanceof PDType3Font) ? font.getFontMatrix().getScaleX() : (1 / 1000f);
        float spaceWidth = 0;
        try {
            // to avoid crash as described in PDFBOX-614, see what the space displacement should be
            spaceWidth = font.getSpaceWidth() * glyphSpaceToTextSpaceFactor;
        } catch (Exception exception) {
            LOG.warn(exception, exception);
        }
        if (spaceWidth == 0) {
            // the average space width appears to be higher than necessary so make it smaller
            spaceWidth = font.getAverageFontWidth() * glyphSpaceToTextSpaceFactor;
        }
        if (spaceWidth == 0) {
            spaceWidth = 1.0f; // if could not find font, use a generic value
        }

        // the space width has to be transformed into display units
        spaceWidth *= fontSize * horizontalScaling;

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
            float width = codeLength == 1 && (unicode == 0x20 || unicode == 0xa0) ? (wordSpacing == 0 ? spaceWidth : wordSpacing)
                    : font.getWidth(code) * fontSize / 1000f + charSpacing;

            float w = width * horizontalScaling;
            // process the decoded char
            Point2D.Float p = transformPoint(textRenderingMatrix.getTranslateX(), textRenderingMatrix.getTranslateY(), postRotate);
            processTextPosition(new SimpleTextPosition(p, w * textRenderingMatrix.getScalingFactorX(), spaceWidth * textRenderingMatrix.getScalingFactorX(), unicode));

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
    protected void processTextPosition(SimpleTextPosition tp) {
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
