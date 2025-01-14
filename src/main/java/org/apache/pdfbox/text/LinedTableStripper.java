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
import java.util.Collections;
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
     * Map of text by X within Y location for building rectangles in reverse
     * order so that top of page is first
     *
     */
    final protected SortedMap<Float, SortedMap<Float, Character>> textLocationsByYX = new TreeMap<>(Collections.reverseOrder());

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
    }

    private void rotatePage(PDPage page, int rotation) {
        int curr = page.getRotation();
        PDGraphicsState state = getGraphicsState();
        Matrix ctm = state.getCurrentTransformationMatrix();
        LOG.debug("Initial page rotation: {}, BBox {}", curr, mediaBox);
        switch (rotation) {
            case 90 -> {
                page.setRotation((curr + 90) % 360);
                ctm.translate(0, mediaBox.getWidth());
                ctm.rotate(-Math.PI / 2);
            }

            case 180 -> {
                page.setRotation((curr + 180) % 360);
                ctm.translate(mediaBox.getWidth(), mediaBox.getHeight());
                ctm.rotate(Math.PI);
            }
            case 270 -> {
                page.setRotation((curr + 270) % 360);
                ctm.translate(mediaBox.getHeight(), 0);
                ctm.rotate(Math.PI / 2);
            }
            case 0 -> {
            }
            default ->
                throw new RuntimeException("Rotation must be 0, 90, 180, or 270: " + rotation);
        }
        transformRectangle(mediaBox, ctm);
        LOG.traceExit("rotatePage: {}", mediaBox);
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
        rotatePage(page, extraRotation); // Must be done after initPage()
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
        TreeSet<TableCell> rects = (TreeSet<TableCell>) extractCells(headingColour, startY, endY);
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
     * @param startY Y coordinate of top of table relative to top of mediaBox
     * @param endY Y coordinate of bottom of table, as returned by findEndTable
     * @return the bounds of the table.
     *
     * @throws java.io.IOException for file errors May be overridden if there is
     * some other mechanism to identify the top of the table.
     */
    protected SortedSet<TableCell> extractCells(Color headingColour, float startY, float endY) throws IOException {
        assert startY >= 0 : "startY < 0";
        var bounds = new FRectangle(Float.NaN, endY, Float.NaN, mediaBox.getUpperRightY() - startY);
        if (!findTable(headingColour, bounds)) {
            return null;
        }
        // Now have located top & bottom of data in the table, and left & right limits

        // Now have all table limits identified
        // Extract subsets of the lines, rectangles, and text within these limits.
        // Remove horizontal lines above and below the table, trim the remainder to the horizontal limits of the table
        SortedSet<FRectangle> horiz = horizLines.subSet(new FRectangle(0, bounds.getMaxY() + 1), new FRectangle(0, bounds.getMinY() - 1));
        horiz.removeIf((FRectangle h) -> !bounds.intersects(h));
        horiz.forEach((FRectangle h) -> h.trimX(bounds.getMinX(), bounds.getMaxX()));
        horiz.removeIf((FRectangle h) -> h.getWidth() == 0);
        if (horiz.isEmpty()) {
            LOG.error("No horizontal lines in table");
            return null;
        }

        // Remove vertical lines left or right of the table, trim the remainder to the vertical limits of the table
        // Initially  include lines which extend above the top of the table, because they may extend down into the table
        SortedSet<FRectangle> vert = vertLines.subSet(new FRectangle(bounds.getMinX(), bounds.getMaxY()), new FRectangle(mediaBox.getLowerLeftX(), mediaBox.getLowerLeftY()));
        vert.forEach((FRectangle v) -> v.trimY(bounds.getMinY(), bounds.getMaxY()));
        vert.removeIf((FRectangle v) -> v.getHeight() == 0 || !bounds.intersects(v));
        if (vert.isEmpty()) {
            LOG.error("No vertical lines in table");
            return null;
        }

        // Remove rectangles that are below the last horizontal line of the table... this removes any footnotes
        // Initially  include rectangles which extend above the top of the table, because they may extend down into the table
        var rects = rectangles.subSet(new FRectangle(bounds.getMinX(), bounds.getMaxY() + 1), new FRectangle(mediaBox.getLowerLeftX(), mediaBox.getLowerLeftY()));
        rects.removeIf((FRectangle r) -> !r.intersects(bounds));
        rects.forEach((FRectangle r) -> {
            r.intersect(bounds);
        });
        rects.removeIf((FRectangle r) -> r.getHeight() == 0 || r.getWidth() == 0);

        // Remove text that is outside the table
        var textLocs = textLocationsByYX.subMap(bounds.getMaxY(), bounds.getMinY());  // NB reverse order
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
        for (var r : rects) {
            var c = new TableCell(r, getRectangleText(r, textLocs));
            result.add(c);
        }
        result.addAll(buildRectangles(horiz, vert, textLocs));
        return result;
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
    protected SortedSet<TableCell> buildRectangles(SortedSet<FRectangle> horiz, SortedSet<FRectangle> vert, SortedMap<Float, SortedMap<Float, Character>> tableContents) {
        if (horiz.size() < 2 || vert.size() < 2) {
            LOG.warn("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
        }
        var hSet = new ArrayList<Float>();
        var prevH = Float.POSITIVE_INFINITY;
        for (var h : horiz) {
            if (prevH - h.getMaxY() > 3) {
                prevH = h.getMaxY();
                hSet.add(prevH);
            }
        }
        var vSet = new ArrayList<Float>();
        var prevV = Float.NEGATIVE_INFINITY;
        for (var v : vert) {
            if (v.getMaxX() - prevV > 3) {
                prevV = v.getMaxX();
                vSet.add(prevV);
            }
        }

        if (hSet.size() < 2 || vSet.size() < 2) {
            LOG.warn("Unique horizontal lines = {}, vertical lines = {} so no table", hSet.size(), vSet.size());
        }
        // Now have all unique horizontal and vertical lines
        SortedSet<TableCell> rects = new TreeSet<>();
        var v0 = vSet.removeFirst();
        while (!vSet.isEmpty()) {
            final var v1 = vSet.removeFirst();
            var h0 = hSet.getFirst();
            for (var i = 1; i < hSet.size(); i++) {
                final var h1 = hSet.get(i);
                var r = new TableCell(v0, h1, v1, h0);
                r.setText(getRectangleText(r, tableContents));
                rects.add(r);
                h0 = h1;
            }
            v0 = v1;
        }
        LOG.traceExit("buildRectangles: Count = {}", rects.size());
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
        var yRange = tableContents.subMap(rect.getMaxY(), rect.getMinY());
        yRange.values().stream().map(row -> row.subMap(rect.getMinX(), rect.getMaxX())).map(xRange -> {
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
            float tableBottom = horizLines.last().getMinY();
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
            var rects = new TreeSet<>(rectangles);
            rects.removeIf((var r) -> r.getFillColour().getRGB() != headingColour.getRGB());

            // Subsequent rectangles of the same colour provide the left and right boundaries of the table
            FRectangle hdgBounds = new FRectangle(rects.first());
            for (FRectangle r : rects) {
                if (!r.overlapsY(hdgBounds.getMinY(), hdgBounds.getMaxY())) {
                    break;
                }
                hdgBounds.add(r);
            }
            bounds.setMaxY(hdgBounds.getMinY() + 1);
            bounds.setX(hdgBounds.getMinX() - 1, hdgBounds.getMaxX() + 1);
            return true;
        }

        // No heading colour specified
        // The top of the first horizontal line or rectangle is taken
        if (horizLines.isEmpty()) {
            if (rectangles.isEmpty()) {
                return false;
            }
            bounds.setMaxY(rectangles.first().getMaxY() + 1);
        } else {

            if (rectangles.isEmpty()) {
                bounds.setMaxY(horizLines.first().getMaxY() + 1);
            } else {
                bounds.setMaxY(Math.max(horizLines.first().getMaxY(), rectangles.first().getMaxY()) + 1);
            }
        }
        var vert = new TreeSet<>(vertLines);
        vert.removeIf((var v) -> !v.containsY(bounds.getMaxY()));
        if (vert.size() < 2) return false;
            bounds.setMinX(vert.getFirst().getMinX()-1);
            bounds.setMaxX(vert.getLast().getMaxX()+1);
        for (var v: vert) {
            if (v.getMinX() < bounds.getMinX()) bounds.setMinX(v.getMinX()-1);
            if (v.getMaxX() > bounds.getMaxX()) bounds.setMaxX(v.getMaxX()+1);
        }      
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
        LOG.traceEntry("addRectPath: {}", linePath.getBounds());
        Rectangle2D bounds = linePath.getBounds2D();
        // Ignore cells too small for text
        if (bounds.getHeight() < 5 && bounds.getWidth() < 5) {
            linePath.reset();
            return;
        }
        // Coordinates here are already in display space
        FRectangle rect = new FRectangle(fillColour, strokeColour, stroke, (float) bounds.getMinX(), (float) bounds.getMinY(), (float) bounds.getMaxX(), (float) bounds.getMaxY());
        LOG.debug("Rectangle {} -> {}", bounds, rect);
        // Add a rectangle to the appropriate sorted list (horizLines, vertLines, boxes).
        if (bounds.getHeight() < 3 || bounds.getWidth() < 3) {
            // 3 is a kludge -- NZKM.pdf has rectangles 2.96 wide that are not vertical lines
            if (bounds.getHeight() < 3) {
                horizLines.add(rect);
            } else {
                vertLines.add(rect);
            }
            LOG.trace("line added {}", rect);
        } else {
            rectangles.add(rect);
            LOG.trace("rectangle added {}", rect);
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
            // get glyph's horizontal displacement, in text space
            float width = codeLength == 1 && (unicode == 0x20 || unicode == 0xa0) ? wordSpacing
                    : font.getWidth(code) * fontSize / 1000 + charSpacing;

            // get glyph's horizontal displacement, in text space
            float w = width * horizontalScaling;
            // process the decoded char
            Point2D.Float p = new Point2D.Float(textRenderingMatrix.getTranslateX(), textRenderingMatrix.getTranslateY());
            LOG.trace("'{}', {} to {}", unicode, new Point2D.Float(textMatrix.getTranslateX(), textMatrix.getTranslateY()), p);
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
            var yMatches = textLocationsByYX.subMap(p.y + tolerance, p.y - tolerance); // NB reverse order
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
