package org.apache.pdfbox.text;

import java.awt.BasicStroke;
import static java.awt.BasicStroke.CAP_BUTT;
import static java.awt.BasicStroke.JOIN_ROUND;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
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
    final protected SortedSet<FRectangle> horizLines = new TreeSet<>();

    /**
     * Rectangle graphics in the table
     *
     */
    final protected SortedSet<FRectangle> rectangles = new TreeSet<>();

    /**
     * Map of text by location for building rectangles
     *
     */
    final protected SortedMap<Float, SortedMap<Float, Character>> textLocationsByYX = new TreeMap<>();

    /**
     * Size of current page
     *
     */
    final protected PDRectangle mediaBox;

    final private int extraRotation;
    final private boolean suppressDuplicateOverlappingText;
    private final GeneralPath linePath = new GeneralPath();

    /**
     * Constructor.
     *
     * @param page Page to be read.
     * @param extraRotation Force page rotation flag
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public LinedTableStripper(PDPage page, int extraRotation, boolean suppressDuplicates) throws IOException {
        super(page);
        this.extraRotation = extraRotation;
        suppressDuplicateOverlappingText = suppressDuplicates;
        mediaBox = page.getMediaBox();
        PDRectangle cropBox = page.getCropBox();

        // flip y-axis
        flipAT = new Matrix();
        flipAT.translate(0, page.getBBox().getHeight());
        flipAT.scale(1, -1);

        // page may be rotated
        rotateAT = new AffineTransform();
        int rotation = page.getRotation();
        if (rotation != 0) {
            var mediaBox = page.getMediaBox();
            switch (rotation) {
                case 90 ->
                    rotateAT.translate(mediaBox.getHeight(), 0);
                case 270 ->
                    rotateAT.translate(0, mediaBox.getWidth());
                case 180 ->
                    rotateAT.translate(mediaBox.getWidth(), mediaBox.getHeight());
                default -> {
                }
            }
            rotateAT.rotate(Math.toRadians(rotation));
        }

        // cropbox
        transAT = AffineTransform.getTranslateInstance(-cropBox.getLowerLeftX(), cropBox.getLowerLeftY());

    }

    private void rotatePage(PDPage page, int rotation) {
        int curr = page.getRotation();
        PDRectangle cropBox = page.getCropBox();
        PDGraphicsState state = getGraphicsState();
        var ctm = state.getCurrentTransformationMatrix();
        LOG.debug("Initial page rotation: {}, BBox {}, Matrix {}", page.getRotation(), mediaBox, ctm);
        switch (rotation) {
            case 90 -> {
                page.setRotation((curr + 90) % 360);
                ctm.rotate(Math.PI * 3 / 2);
                ctm.translate(cropBox.getHeight(), 0);
            }

            case 180 -> {
                page.setRotation((curr + 180) % 360);
                ctm.rotate(Math.PI);
                ctm.translate(cropBox.getWidth(), cropBox.getHeight());
            }
            case 270 -> {
                page.setRotation((curr + 270) % 360);
                ctm.rotate(Math.PI / 2);
                ctm.translate(0, cropBox.getWidth());
            }
            case 0 -> {
            }
            default ->
                throw new RuntimeException("Rotation must be 0, 90, 180, 270... " + rotation);
        }
        mediaBox.transform(ctm);
        LOG.traceExit("rotatePage: {}", mediaBox);
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
        rotatePage(page, extraRotation); // Must be done after initPage()
        vertLines.clear();
        horizLines.clear();
        rectangles.clear();
        textLocationsByYX.clear();

        if (page.hasContents()) {
            isProcessingPage = true;
            processStream(page);
            isProcessingPage = false;

            // Expand page bounds... some documents have invalid values for right edge of page
            expandMediaBox(vertLines);
            expandMediaBox(horizLines);
            expandMediaBox(rectangles);
            expandMediaBoxByText();
        }
        LOG.traceExit("processPage: {}", mediaBox);
    }

    private void expandMediaBox(SortedSet<FRectangle> set) {
        if (set.isEmpty()) {
            return;
        }
        mediaBox.setLowerLeftY(Math.min(mediaBox.getLowerLeftY(), set.getFirst().getMinY()));
        for (var r : set) {
            if (r.getMinX() < mediaBox.getLowerLeftY()) {
                mediaBox.setLowerLeftX(r.getMinX());
            }
            if (r.getMaxX() > mediaBox.getUpperRightX()) {
                mediaBox.setUpperRightX(r.getMaxX());
            }
            if (r.getMaxY() > mediaBox.getUpperRightY()) {
                mediaBox.setUpperRightY(r.getMaxY());
            }
        }
    }

    private void expandMediaBoxByText() {
        mediaBox.setLowerLeftY(Math.min(mediaBox.getLowerLeftY(), textLocationsByYX.firstKey()));
        mediaBox.setUpperRightY(Math.max(mediaBox.getUpperRightY(), textLocationsByYX.lastKey()));
        for (var it = textLocationsByYX.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (entry.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            var row = entry.getValue();
            if (row.firstKey() < mediaBox.getLowerLeftX()) {
                mediaBox.setLowerLeftX(row.firstKey());
            }
            if (row.lastKey() > mediaBox.getUpperRightX()) {
                mediaBox.setUpperRightX(row.lastKey());
            }
        }
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
    public boolean appendToTable(Color headingColour, Color dataColour, float startY, float endY, int numColumns, ArrayList<String[]> table) throws IOException {
        TreeSet<TableCell> rects = (TreeSet<TableCell>) extractCells(headingColour, dataColour, startY, endY);
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
     * Extract a table matching the criteria on the given page.
     *
     * Finds the table, and removes extraneous lines, rectangles, and text Scans
     * the rectangles sorted collection for the first rectangle of the heading
     * colour. Then expands the X,Y limits until it finds the first data colour
     * rectangle (if any). The bottom of the heading, and therefore the top of
     * the data, is at this Y coordinate.
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
    protected SortedSet<TableCell> extractCells(Color headingColour, Color dataColour, float startY, float endY) throws IOException {
        var bounds = new FRectangle(mediaBox.getLowerLeftX(), Math.max(mediaBox.getLowerLeftY(), startY), mediaBox.getUpperRightX(), endY);
        if (!findTable(headingColour, dataColour, bounds)) {
            return null;
        }
        // Now have located top & bottom of data in the table, and left & right limits

        // Now have all table limits identified
        // Extract subsets of the lines, rectangles, and text within these limits.
        // Remove horizontal lines above and below the table, trim the remainder to the horizontal limits of the table
        SortedSet<FRectangle> horiz = horizLines.subSet(new FRectangle(mediaBox.getLowerLeftX(), bounds.getMinY() + 1), new FRectangle(bounds.getMinX(), bounds.getMaxY() + 1));
        horiz.removeIf((FRectangle h) -> !bounds.intersects(h));
        horiz.forEach((FRectangle h) -> h.trimX(bounds.getMinX(), bounds.getMaxX()));
        horiz.removeIf((FRectangle h) -> h.getWidth() == 0);
        if (horiz.isEmpty()) {
            LOG.error("No horizontal lines in table");
            return null;
        }

        // Remove vertical lines left or right of the table, trim the remainder to the vertical limits of the table
        // Initially  include lines which extend above the top of the table, because they may extend down into the table
        SortedSet<FRectangle> vert = vertLines.subSet(new FRectangle(mediaBox.getLowerLeftX(), mediaBox.getLowerLeftY()), new FRectangle(bounds.getMinX(), bounds.getMaxY()));
        vert.forEach((FRectangle v) -> v.trimY(bounds.getMinY(), bounds.getMaxY()));
        vert.removeIf((FRectangle v) -> v.getHeight() == 0 || !bounds.intersects(v));
        if (vert.isEmpty()) {
            LOG.error("No vertical lines in table");
            return null;
        }

        // Remove rectangles that are below the last horizontal line of the table... this removes any footnotes
        // Initially  include rectangles which extend above the top of the table, because they may extend down into the table
        var rects = rectangles.subSet(new FRectangle(mediaBox.getLowerLeftX(), mediaBox.getLowerLeftY()), new FRectangle(bounds.getMinX(), bounds.getMaxY() + 1));
        rects.removeIf((FRectangle r) -> !r.intersects(bounds));
        rects.forEach((FRectangle r) -> {
            r.intersect(bounds);
        });
        rects.removeIf((FRectangle r) -> r.getHeight() == 0 || r.getWidth() == 0);

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
        var result = new TreeSet<TableCell>();
        rects.forEach(r -> {
            result.add(new TableCell(r, getRectangleText(r, textLocs)));
        });
        result.addAll(buildRectangles(horiz, vert, textLocs));
        return result;
    }

    /**
     * Convert horizontal and vertical lines to rectangles.
     *
     * Assumes that horiz and vert are sorted in Y,X order For each horizontal
     * line, finds two leftmost vertical lines which intersect it and also
     * intersect a lower horizontal line. This rectangle is added to the rects
     * collection
     *
     * @param horiz Set of horizontal lines, sorted by Y,X
     * @param vert Set of vertical lines, sorted by Y,X
     * @param rects Set of rectangles
     */
    protected SortedSet<TableCell> buildRectangles(SortedSet<FRectangle> horiz, SortedSet<FRectangle> vert, SortedMap<Float, SortedMap<Float, Character>> tableContents) {
        if (horiz.isEmpty() || vert.isEmpty()) {
            LOG.warn("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
        }
        SortedSet<TableCell> rects = new TreeSet<>();
        while (!horiz.isEmpty()) {
            for (Iterator<FRectangle> it = vert.iterator(); it.hasNext();) {
                FRectangle v0 = it.next();
                it.remove();
                // Find horizontal line that intersects v0
                float bottom = Float.NaN;
                float top = v0.getMinY();
                FRectangle h1 = null;
                for (Iterator<FRectangle> itH = horiz.iterator(); it.hasNext();) {
                    h1 = itH.next();
                    itH.remove();
                    if (h1.intersects(v0)) {
                        v0.trimY(h1.getMinY(), mediaBox.getUpperRightY());
                        if (v0.getHeight() > 1) {
                            vert.add(v0);
                        }
                        bottom = h1.getMinY();
                        break;
                    }
                    if (h1.getMaxY() > v0.getMaxY()) {
                        break;
                    }
                }
                if (Float.isNaN(bottom)) {
                    // No horizontal lines intersect v0... trim off the
                    //unused bit and try next vertical line
                    if (h1 != null) {
                        horiz.add(h1);
                        v0.trimY(h1.getMinY(), mediaBox.getUpperRightY());
                        if (v0.getHeight() > 1) {
                            vert.add(v0);
                        }
                        continue;
                    }
                }
                // Now have top, bottom, left coordinates of rectangle
                // Find next vertical that intersects h1
                for (FRectangle v1 : vert) {
                    if (!v1.containsY(bottom)) {
                        continue;
                    }
                    if (v1.getMinY() >= bottom - 1) {
                        break;
                    }

                    // Found right edge
                    TableCell r = new TableCell(v0.getMinX(), top, v1.getMinX(), bottom);
                    r.setText(getRectangleText(r, tableContents));
                    rects.add(r);
                    assert (h1 != null);
                    h1.trimX(v1.getMaxX(), mediaBox.getUpperRightX());
                    if (h1.getWidth() > 1) {
                        horiz.add(h1);
                    }
                    break;
                }
            }
        }
        return rects;
    }

    /**
     * Extract the text contained within the specified rectangle
     *
     * @param rect
     * @param tableContents
     * @return
     */
    protected String getRectangleText(FRectangle rect, SortedMap<Float, SortedMap<Float, Character>> tableContents) {
        StringBuilder sb = new StringBuilder(100);
        var yRange = tableContents.tailMap(rect.getMinY()).headMap(rect.getMaxY());
        yRange.values().stream().map(row -> row.tailMap(rect.getMinX()).headMap(rect.getMaxX())).map(xRange -> {
            xRange.values().forEach((Character ch) -> sb.append(ch));
            return xRange;
        }).forEachOrdered(_item -> {
            sb.append('\n');
        });
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
            var rows = textLocationsByYX.subMap(startY, endY);
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
            float tableBottom = horizLines.last().getMaxY();
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
    protected boolean findTable(Color headingColour, Color dataColour, FRectangle bounds) throws IOException {
        LOG.debug("findTable(\"{}\", \"{}\")", headingColour, dataColour);
        boolean headingFound = false;
        if (headingColour == null) {
            // TODO
            if (!horizLines.isEmpty()) {
                bounds.setMinY(horizLines.first().getMinY());
            }
            if (!rectangles.isEmpty()) {
                bounds.setMinY(Math.min(bounds.getMinY(), rectangles.first().getMinY()));
            }
            headingFound = true;
        }

        // Find the location of the table by finding the first rectangle of the right colour
        // If no colour is specified, the first rectangle is taken
        // Subsequent rectangles until a dataColour rectangle provide the left and right boundaries of the table
        for (FRectangle r : rectangles) {
            Color fillColour = r.getFillColour();
            if (headingColour == null || (fillColour != null && fillColour.getRGB() == headingColour.getRGB())) {
                if (r.getMaxX() > bounds.getMaxX() || Float.isNaN(bounds.getMaxX())) {
                    bounds.setMaxX(r.getMaxX() + 1);
                }
                if (r.getMinX() < bounds.getMinX() || Float.isNaN(bounds.getMinX())) {
                    bounds.setMinX(r.getMinX());
                }
                if (r.getMaxY() > bounds.getMinY() || Float.isNaN(bounds.getMinY())) {
                    bounds.setMinY(r.getMaxY());
                }
                headingFound = true;
            } else {
//                if (fillColour == null) {
                // Ignore uncoloured rectangles which overlay the heading
                // These are lines to outline the heading cells
//                    continue;
//                }
                if (headingFound) {
                    if (dataColour == null || (fillColour != null && fillColour.getRGB() == dataColour.getRGB())) {
                        break;
                    }
                }
            }
        }

        if (Float.isNaN(bounds.getMinX())) {
            return false;
        }
        LOG.traceExit("findTable: {}", bounds);
        return true;
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
    private void addRectPath(Color fillColour, Color strokeColour, Stroke stroke) {
        LOG.traceEntry("addRectPath: {}", linePath.getBounds());
        Rectangle2D bounds = linePath.getBounds2D();
        // Ignore cells too small for text
        if (bounds.getHeight() < 5 && bounds.getWidth() < 5) {
            linePath.reset();
            return;
        }
        // Add a rectangle to the appropriate sorted list (horizLines, vertLines, boxes).
        Point2D.Float p0 = new Point2D.Float((float) bounds.getMinX(), (float) bounds.getMinY());
        Point2D.Float p1 = new Point2D.Float((float) bounds.getMaxX(), (float) bounds.getMaxY());
        // Graphics Y coordinates are upside-down wrt Text coordinates
        flipAT.transform(p0);
        flipAT.transform(p1);
        if (bounds.getHeight() < 3 || bounds.getWidth() < 3) {
            FRectangle line = new FRectangle(null, strokeColour, stroke, p0, p1);
            // 3 is a kludge -- NZKM.pdf has rectangles 2.96 wide that are not vertical lines
            if (line.getHeight() < 3) {
                horizLines.add(line);
            } else {
                vertLines.add(line);
            }
            LOG.trace("line added {}", line);
        } else {
            FRectangle rect = new FRectangle(fillColour, strokeColour, stroke, p0, p1);
            LOG.trace("rectangle added {}", rect);
            rectangles.add(rect);
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
            Matrix ctm = state.getCurrentTransformationMatrix();
            Matrix textRenderingMatrix = parameters.multiply(textMatrix).multiply(ctm).multiply(flipAT);

            // use our additional glyph list for Unicode mapping
            String text = font.toUnicode(code, GLYPHLIST);
            // when there is no Unicode mapping available, coerce the character code into Unicode
            char unicode = text == null ? (char) code : text.charAt(0);
            // get glyph's horizontal displacement, in text space
            float width = codeLength == 1 && (unicode == 0x20 || unicode == 0xa0) ? wordSpacing
                    : font.getWidth(code) * fontSize / 1000 + charSpacing;

            // get glyph's horizontal displacement, in text space
            float w = width * horizontalScaling;
            // process the decoded char
            Point2D.Float p = new Point2D.Float(textRenderingMatrix.getTranslateX(), textRenderingMatrix.getTranslateY());
            LOG.trace("Width of '{}' @ {} = {}, {}", unicode, p, width, w);
            ctm.transform(p);
            processTextPosition(unicode, p, w);

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
    protected void processTextPosition(char ch, Point2D.Float p, float charWidth) {
        LOG.traceEntry("processtextPosition {}: p = {}, width {}", ch, p, charWidth);
        SortedMap<Float, Character> sortedRow = textLocationsByYX.computeIfAbsent(p.y, k -> new TreeMap<>());
        if (suppressDuplicateOverlappingText) {
            float tolerance = charWidth / 3.0f;
            var yMatches = textLocationsByYX.subMap(p.y - tolerance, p.y + tolerance);
            for (var yMatch : yMatches.values()) {
                var xMatches = yMatch.subMap(p.x - tolerance, p.x + tolerance).values();
                if (xMatches.contains(ch)) {
                    return;
                }
            }
        }
        sortedRow.put(p.x, ch);
    }
    // NOTE: there are more methods in PDFStreamEngine which can be overridden here too.
    private Matrix flipAT;
    private AffineTransform rotateAT;
    private AffineTransform transAT;

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement) throws IOException {
        super.showGlyph(textRenderingMatrix, font, code, displacement);

        AffineTransform at = textRenderingMatrix.createAffineTransform();
        at.concatenate(font.getFontMatrix().createAffineTransform());

        // draw
        // use our additional glyph list for Unicode mapping
        String text = font.toUnicode(code, GLYPHLIST);
        // when there is no Unicode mapping available, coerce the character code into Unicode
        char unicode = text == null ? (char) code : text.charAt(0);
        // get glyph's horizontal displacement, in text space
        float width = font.getWidth(code) / 1000;
        //codeLength == 1 && (unicode == 0x20 || unicode == 0xa0) ? wordSpacing
        //: font.getWidth(code) / 1000 + charSpacing;

        // get glyph's horizontal displacement, in text space
        float w = width;// * horizontalScaling;
        // process the decoded char
        Point2D.Float p = new Point2D.Float((float) at.getTranslateX(), (float) at.getTranslateY());
        LOG.trace("Width of '{}' @ {} = {}, {}", unicode, p, width, w);
        processTextPosition(unicode, p, w);
    }
}
