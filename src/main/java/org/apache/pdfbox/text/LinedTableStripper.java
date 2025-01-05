package org.apache.pdfbox.text;

/**
 * Scan a PDF document page by page, and extract a table delimited by lines of
 * text that match the specified delimiters.
 *
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 * @version $Revision: 1.00 $
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
import java.awt.BasicStroke;
import static java.awt.BasicStroke.CAP_BUTT;
import static java.awt.BasicStroke.JOIN_ROUND;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.util.BoundingBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDCIDFont;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

/**
 * Extract data from a line-delimited tables in a PDF document. Data is
 * extracted as a set of rectangular TableCells on a page, each with a
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
    private Matrix translateMatrix;
    private static final GlyphList GLYPHLIST;
    private final Map<COSDictionary, Float> fontHeightMap = new WeakHashMap<>();

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
    final protected SortedSet<TableCell> rectangles = new TreeSet<>();

    /**
     * Map of text by character to identify duplicates
     *
     */
    final private TreeMap<String, TreeMap<Float, TreeSet<Float>>> textLocationsByChar = new TreeMap<>();

    /**
     * Map of text by location for building rectangles
     *
     */
    final protected SortedMap<Float, SortedMap<Float, String>> textLocationsByYX = new TreeMap<>();

    /**
     * Size of current page
     *
     */
    final protected FRectangle pageSize = new FRectangle();
    final private boolean isRotated;
    final private boolean suppressDuplicateOverlappingText;
    private final GeneralPath linePath = new GeneralPath();

    /**
     * Constructor.
     *
     * @param page Page to be read.
     * @param forceRotation Force page rotation flag
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public LinedTableStripper(PDPage page, boolean forceRotation, boolean suppressDuplicates) throws IOException {
        super(page);
        isRotated = forceRotation || page.getRotation() == 90;
        suppressDuplicateOverlappingText = suppressDuplicates;
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
        if (isRotated) {
            page.setRotation(90);
        }
        LOG.debug("Rotated: {}", page.getRotation());
        pageSize.setMinMax((int) page.getCropBox().getLowerLeftX(), (int) page.getCropBox().getLowerLeftY(),
                (int) page.getCropBox().getUpperRightX() + 1, (int) page.getCropBox().getUpperRightY() + 1);
        // Some code below depends on the page lower left corner being at (0,0)
        if (pageSize.getMinX() != 0 || pageSize.getMinY() != 0) {
            LOG.warn("Page is not zero-based: {}", pageSize);
        }
        LOG.debug("Page size: {}", pageSize);
        vertLines.clear();
        horizLines.clear();
        rectangles.clear();
        textLocationsByChar.clear();
        textLocationsByYX.clear();

        super.processPage(page);
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
    public boolean appendToTable(Color headingColour, Color dataColour, Pattern tableEnd, int numColumns, ArrayList<String[]> table) throws IOException {
        TreeSet<TableCell> rects = (TreeSet<TableCell>) extractCells(headingColour, dataColour, tableEnd);
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
    protected SortedSet<TableCell> extractCells(Color headingColour, Color dataColour, Pattern tableEnd) throws IOException {
        FRectangle bounds = findTable(headingColour, dataColour);
        LOG.debug("Table bounds: {}", bounds);
        if (bounds == null) {
            return null;
        }
        // Now have located top of data in the table, and left & right limits

        // Copy text items that are inside the boundary, down to the end of 
        // the page, so that the endTable pattern can be searched for
        buildCharMap(bounds);

        // Find the end of the table
        bounds.setMaxY(findEndTable(bounds.getMinY(), tableEnd));

        // Now have all table limits identified
        // Extract subsets of the lines, rectangles, and text within these limits.
        // Keep horizontal lines that extend left of the table
        SortedSet<FRectangle> horiz = horizLines.subSet(new FRectangle(pageSize.getMinX(), bounds.getMinY() + 1), new FRectangle(pageSize.getMaxX(), pageSize.getMaxY()));
        horiz.removeIf((FRectangle h) -> !bounds.intersects(h));
        if (horiz.isEmpty()) {
            LOG.error("No horizontal lines in table");
            return null;
        }
        horiz.forEach((FRectangle h) -> h.trimX(bounds.getMinX(), bounds.getMaxX()));
        // Remove rectangles and text items that are below the last horizontal line of the table... this removes any footnotes
        bounds.setMaxY(horiz.getLast().getMaxY());
        // Initially  include vertical lines which extend above the top of the table
        SortedSet<FRectangle> vert = vertLines.subSet(new FRectangle(bounds.getMinX() - 1, pageSize.getMinY()), new FRectangle(bounds.getMaxX(), bounds.getMaxY()));
        vert.forEach((FRectangle v) -> v.trimY(bounds.getMinY(), bounds.getMaxY()));
        vert.removeIf((FRectangle v) -> v.getHeight() == 0 || !bounds.intersects(v));
        if (vert.isEmpty()) {
            LOG.error("No vertical lines in table");
            return null;
        }

        // Initially  include rectangles which extend above the top of the table
        SortedSet<TableCell> rects = rectangles.subSet(new TableCell(bounds.getMinX() - 1, pageSize.getMinY()), new TableCell(bounds.getMaxX(), bounds.getMaxY()));
        rects.forEach((FRectangle r) -> {
            r.intersect(bounds);
        });
        rects.removeIf((FRectangle r) -> r.getHeight() == 0 || r.getWidth() == 0);

        // Remove text that is outside the table
        textLocationsByYX.entrySet().removeIf((Map.Entry<Float, SortedMap<Float, String>> yEntry) -> !bounds.containsY(yEntry.getKey()));
        for (Iterator<Map.Entry<Float, SortedMap<Float, String>>> yIt = textLocationsByYX.entrySet().iterator(); yIt.hasNext();) {
            Map.Entry<Float, SortedMap<Float, String>> yEntry = yIt.next();
            yEntry.getValue().entrySet().removeIf((Map.Entry<Float, String> xEntry) -> !bounds.containsX(xEntry.getKey()));
            if (yEntry.getValue().isEmpty()) {
                yIt.remove();
            }
        }
        if (textLocationsByYX.isEmpty()) {
            LOG.error("No text in table");
            return null;
        }

        rects.forEach(r -> {
            r.setText(getRectangleText(r, textLocationsByYX));
        });
        rects.addAll(buildRectangles(horiz, vert, textLocationsByYX));
        return rects;
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
    protected SortedSet<TableCell> buildRectangles(SortedSet<FRectangle> horiz, SortedSet<FRectangle> vert, SortedMap<Float, SortedMap<Float, String>> tableContents) {
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
                        v0.trimY(h1.getMinY(), pageSize.getMaxY());
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
                        v0.trimY(h1.getMinY(), pageSize.getMaxY());
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
                    LOG.trace("Cell: ({}, {}), ({}, {})", v0.getMinX(), top, v1.getMinX(), bottom);
                    TableCell r = new TableCell(v0.getMinX(), top, v1.getMinX(), bottom);
                    r.setText(getRectangleText(r, tableContents));
                    rects.add(r);
                    assert (h1 != null);
                    h1.trimX(v1.getMaxX(), pageSize.getMaxX());
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
    protected String getRectangleText(FRectangle rect, SortedMap<Float, SortedMap<Float, String>> tableContents) {
        StringBuilder sb = new StringBuilder(100);
        SortedMap<Float, SortedMap<Float, String>> yRange = tableContents.tailMap(rect.getMinY()).headMap(rect.getMaxY());
        yRange.values().stream().map(row -> row.tailMap(rect.getMinX()).headMap(rect.getMaxX())).map(xRange -> {
            xRange.values().forEach((String s) -> sb.append(s));
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
    protected float findEndTable(float tableTop, Pattern tableEnd) {
        // Scan through the text for the endTable delimiter text
        endTableFound = false;
        if (tableEnd != null) {
            for (Entry<Float, SortedMap<Float, String>> r : textLocationsByYX.entrySet()) {
                float y = r.getKey();
                if (y < tableTop) {
                    continue;
                }
                // Concatenate all of the text onto entire lines
                StringBuilder line = new StringBuilder(r.getValue().values().size());
                r.getValue().values().forEach(text -> {
                    line.append(text);
                });
                if (tableEnd.matcher(line).find()) {
                    endTableFound = true;
                    // end of table marker may be the first line of the table on a given page
                    LOG.trace("End of Table \"{}\" found at {}", tableEnd.toString(), y);
                    return y;
                }
            }

            LOG.warn("Table end delimiter \"{}\" not found", tableEnd.toString());
            return pageSize.getMaxY();
        }
        if (!horizLines.isEmpty()) {
            // Assume that the last horizontal line on the page is the bottom of the table
            float tableBottom = horizLines.last().getMaxY();
            if (tableBottom <= tableTop) {
                LOG.warn("No Table end delimiter specified and no horizontal line below heading");
            }
            return tableBottom;
        }
        LOG.warn("No Table end delimiter specified and no horizontal line found");
        return pageSize.getMaxY();
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
    protected FRectangle findTable(Color headingColour, Color dataColour) throws IOException {
        LOG.debug("findTable(\"{}\", \"{}\")", headingColour, dataColour);
        FRectangle bounds = new FRectangle(-1, pageSize.getMinY(), -1, pageSize.getMaxY());
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
                if (r.getMaxX() > bounds.getMaxX() || bounds.getMaxX() == -1) {
                    bounds.setMaxX(r.getMaxX() + 1);
                }
                if (r.getMinX() < bounds.getMinX() || bounds.getMinX() == -1) {
                    bounds.setMinX(r.getMinX());
                }
                if (r.getMaxY() > bounds.getMinY()) {
                    bounds.setMinY(r.getMaxY());
                }
                headingFound = true;
            } else {
                if (r.getMaxY() <= bounds.getMaxY()) {
                    // Ignore uncoloured rectangles which overlay the heading
                    // These are lines to delineate the heading cells
                    continue;
                }
                if (headingFound) {
                    if (dataColour == null || (fillColour != null && fillColour.getRGB() == dataColour.getRGB())) {
                        break;
                    }
                }
            }
        }

        if (bounds.getMinX() == pageSize.getMaxX()) {
            return null;
        }
        return bounds;
    }

    /**
     * Build the X within Y map from the textLocationsByChar map
     *
     * @param bounds
     */
    protected void buildCharMap(FRectangle bounds) {
        // TODO Handle page rotation
        textLocationsByChar.entrySet().forEach(charEntry -> {
            String ch = charEntry.getKey();
            SortedMap<Float, TreeSet<Float>> yMap = charEntry.getValue().subMap(bounds.getMinY(), bounds.getMaxY());
            yMap.entrySet().forEach(yEntry -> {
                SortedSet<Float> xMap = yEntry.getValue().subSet(bounds.getMinX(), bounds.getMaxX());
                if (!(xMap.isEmpty())) {
                    SortedMap<Float, String> sortedRow = textLocationsByYX.computeIfAbsent(yEntry.getKey(), k -> new TreeMap<>());
                    xMap.forEach(x -> {
                        sortedRow.put(x, ch);
                    });
                }
            });
        });
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
        LOG.trace("appendRectangle {} {}, {} {}, {} {}, {} {}",
                p0.getX(), p0.getY(), p1.getX(), p1.getY(),
                p2.getX(), p2.getY(), p3.getX(), p3.getY());
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
     * NB: This inverts the Y coordinate so that it is the same as the text
     * coordinate system
     *
     * @param fillColour
     * @param strokeColour
     * @param stroke
     */
    private void addRectPath(Color fillColour, Color strokeColour, Stroke stroke) {
        LOG.trace("addRectPath: {}", linePath.getBounds());
        Rectangle2D bounds = linePath.getBounds2D();
        // Ignore cells too small for text
        if (bounds.getHeight() < 5 && bounds.getWidth() < 5) {
            linePath.reset();
            return;
        }
        if (isRotated) {
            bounds.setRect(bounds.getMinY(), bounds.getMinX(), bounds.getHeight(), bounds.getWidth());
        }
        if (bounds.getHeight() < 3 || bounds.getWidth() < 3) {
            FRectangle line = new FRectangle(null, strokeColour, stroke, (float) bounds.getMinX(), pageSize.getMaxY() - (float) bounds.getMaxY(), (float) bounds.getMaxX(), pageSize.getMaxY() - (float) bounds.getMinY());
            LOG.debug("line added {}", line);

            // Add a rectangle to the appropriate sorted list (horizLines, vertLines, boxes).
            // 2.5 is a kludge -- NZKM.pdf has rectangles 2.96 wide that are not vertical lines
            if (bounds.getHeight() < 3) {
                horizLines.add(line);
            } else {
                vertLines.add(line);
            }
        } else {
            TableCell rect = new TableCell(fillColour, strokeColour, stroke, (float) bounds.getMinX(), pageSize.getMaxY() - (float) bounds.getMaxY(), (float) bounds.getMaxX(), pageSize.getMaxY() - (float) bounds.getMinY());
            LOG.debug("rectangle added {}", rect);
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
        LOG.debug("strokePath: {}", linePath.getBounds());
        addRectPath(null, getStrokingColor(), getStroke());
    }

    /**
     * Add a filled path (i.e. a shaded rectangle) to the map
     *
     * @throws IOException
     */
    @Override
    public void fillPath(int windingRule) throws IOException {
        LOG.debug("fillPath: {}, {}", linePath.getBounds(), getNonStrokingColor());
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
        LOG.debug("fillAndStrokePath: {}", linePath.getBounds());
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
        float lineWidth = transformWidth(state.getLineWidth());
        return new BasicStroke(lineWidth, CAP_BUTT, JOIN_ROUND, 1);
    }

    @Override
    public void clip(int windingRule) {
    }

    @Override
    public void moveTo(float x, float y) {
        LOG.trace("moveTo {}, {}", x, y);
        linePath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        LOG.trace("lineTo {}, {}", x, y);
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
        LOG.trace("endPath: {}", linePath.getBounds());
        linePath.reset();
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        LOG.warn("Image ignored {} by {} ", pdImage.getWidth(), pdImage.getHeight());
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
     * Build textPosition Overridden from PDFStreamEngine.
     */
    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement)
            throws IOException {
        LOG.trace("showGlyph {}: disp = {}, trans = {}, {}", font.toUnicode(code), displacement.toString(), textRenderingMatrix.getTranslateX(), textRenderingMatrix.getTranslateY());
        /**
         * Glyph bounding boxes.
         */
        // draw glyph
        super.showGlyph(textRenderingMatrix, font, code, displacement);

        //
        // legacy calculations from PDFTextStripper which were previously in PDFStreamEngine
        //
        //  DO NOT USE THIS CODE UNLESS YOU ARE WORKING WITH PDFTextStripper.
        //  THIS CODE IS DELIBERATELY INCORRECT
        //
        PDGraphicsState state = getGraphicsState();
        Matrix ctm = state.getCurrentTransformationMatrix();
        float fontSize = state.getTextState().getFontSize();
        float horizontalScaling = state.getTextState().getHorizontalScaling() / 100f;
        Matrix textMatrix = getTextMatrix();

        float displacementX = displacement.getX();
        // the sorting algorithm is based on the width of the character. As the displacement
        // for vertical characters doesn't provide any suitable value for it, we have to 
        // calculate our own
        if (font.isVertical()) {
            displacementX = font.getWidth(code) / 1000;
            // there may be an additional scaling factor for true type fonts
            TrueTypeFont ttf = null;
            if (font instanceof PDTrueTypeFont) {
                ttf = ((PDTrueTypeFont) font).getTrueTypeFont();
            } else if (font instanceof PDType0Font) {
                PDCIDFont cidFont = ((PDType0Font) font).getDescendantFont();
                if (cidFont instanceof PDCIDFontType2) {
                    ttf = ((PDCIDFontType2) cidFont).getTrueTypeFont();
                }
            }

            if (ttf != null && ttf.getUnitsPerEm() != 1000) {
                displacementX *= 1000f / ttf.getUnitsPerEm();
            }
        }

        //
        // legacy calculations which were previously in PDFStreamEngine
        //
        //  DO NOT USE THIS CODE UNLESS YOU ARE WORKING WITH PDFTextStripper.
        //  THIS CODE IS DELIBERATELY INCORRECT
        //
        // (modified) combined displacement, this is calculated *without* taking the character
        // spacing and word spacing into account, due to legacy code in TextStripper
        float tx = displacementX * fontSize * horizontalScaling;
        float ty = displacement.getY() * fontSize;

        // (modified) combined displacement matrix
        Matrix td = Matrix.getTranslateInstance(tx, ty);

        // (modified) text rendering matrix
        Matrix nextTextRenderingMatrix = td.multiply(textMatrix).multiply(ctm); // text space -> device space
        float nextX = nextTextRenderingMatrix.getTranslateX();
        float nextY = nextTextRenderingMatrix.getTranslateY();

        // (modified) width and height calculations
        float dxDisplay = nextX - textRenderingMatrix.getTranslateX();
        Float fontHeight = fontHeightMap.get(font.getCOSObject());
        if (fontHeight == null) {
            fontHeight = computeFontHeight(font);
            fontHeightMap.put(font.getCOSObject(), fontHeight);
        }
        float dyDisplay = fontHeight * textRenderingMatrix.getScalingFactorY();

        //
        // start of the original method
        //
        // Note on variable names. There are three different units being used in this code.
        // Character sizes are given in glyph units, text locations are initially given in text
        // units, and we want to save the data in display units. The variable names should end with
        // Text or Disp to represent if the values are in text or disp units (no glyph units are
        // saved).
        float glyphSpaceToTextSpaceFactor = 1 / 1000f;
        if (font instanceof PDType3Font) {
            glyphSpaceToTextSpaceFactor = font.getFontMatrix().getScaleX();
        }

        float spaceWidthText = 0;
        try {
            // to avoid crash as described in PDFBOX-614, see what the space displacement should be
            spaceWidthText = font.getSpaceWidth() * glyphSpaceToTextSpaceFactor;
        } catch (Exception exception) {
            LOG.warn(exception, exception);
        }

        if (Float.compare(spaceWidthText, 0) == 0) {
            spaceWidthText = font.getAverageFontWidth() * glyphSpaceToTextSpaceFactor;
            // the average space width appears to be higher than necessary so make it smaller
            spaceWidthText *= .80f;
        }
        if (Float.compare(spaceWidthText, 0) == 0) {
            spaceWidthText = 1.0f; // if could not find font, use a generic value
        }

        // the space width has to be transformed into display units
        float spaceWidthDisplay = spaceWidthText * textRenderingMatrix.getScalingFactorX();

        // use our additional glyph list for Unicode mapping
        String unicode = font.toUnicode(code, GLYPHLIST);

        // when there is no Unicode mapping available, Acrobat simply coerces the character code
        // into Unicode, so we do the same. Subclasses of PDFStreamEngine don't necessarily want
        // this, which is why we leave it until this point in PDFTextStreamEngine.
        if (unicode == null) {
            if (font instanceof PDSimpleFont) {
                char c = (char) code;
                unicode = String.valueOf(c);
            } else {
                // Acrobat doesn't seem to coerce composite font's character codes, instead it
                // skips them. See the "allah2.pdf" TestTextStripper file.
                return;
            }
        }

        // adjust for cropbox if needed
        Matrix translatedTextRenderingMatrix;
        if (translateMatrix == null) {
            translatedTextRenderingMatrix = textRenderingMatrix;
        } else {
            translatedTextRenderingMatrix = Matrix.concatenate(translateMatrix, textRenderingMatrix);
            nextX -= pageSize.getMinX();
            nextY -= pageSize.getMinY();
        }

        processTextPosition(new TextPosition(0, pageSize.getWidth(),
                pageSize.getHeight(), translatedTextRenderingMatrix, nextX, nextY,
                Math.abs(dyDisplay), dxDisplay,
                Math.abs(spaceWidthDisplay), unicode, new int[]{code}, font, fontSize,
                (int) (fontSize * textMatrix.getScalingFactorX())));
    }

    /**
     * Overridden from PDFStreamEngine.
     */
    @Override
    protected void showFontGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement)
            throws IOException {
    }

    /**
     * Compute the font height. Override this if you want to use own
     * calculations.
     *
     * @param font the font.
     * @return the font height.
     *
     * @throws IOException if there is an error while getting the font bounding
     * box.
     */
    private float computeFontHeight(PDFont font) throws IOException {
        BoundingBox bbox = font.getBoundingBox();
        if (bbox.getLowerLeftY() < Short.MIN_VALUE) {
            // PDFBOX-2158 and PDFBOX-3130
            // files by Salmat eSolutions / ClibPDF Library
            bbox.setLowerLeftY(-(bbox.getLowerLeftY() + 65536));
        }
        // 1/2 the bbox is used as the height todo: why?
        float glyphHeight = bbox.getHeight() / 2;

        // sometimes the bbox has very high values, but CapHeight is OK
        PDFontDescriptor fontDescriptor = font.getFontDescriptor();
        if (fontDescriptor != null) {
            float capHeight = fontDescriptor.getCapHeight();
            if (Float.compare(capHeight, 0) != 0
                    && (capHeight < glyphHeight || Float.compare(glyphHeight, 0) == 0)) {
                glyphHeight = capHeight;
            }
            // PDFBOX-3464, PDFBOX-4480, PDFBOX-4553:
            // sometimes even CapHeight has very high value, but Ascent and Descent are ok
            float ascent = fontDescriptor.getAscent();
            float descent = fontDescriptor.getDescent();
            if (capHeight > ascent && ascent > 0 && descent < 0
                    && ((ascent - descent) / 2 < glyphHeight || Float.compare(glyphHeight, 0) == 0)) {
                glyphHeight = (ascent - descent) / 2;
            }
        }

        // transformPoint from glyph space -> text space
        float height;
        if (font instanceof PDType3Font) {
            height = font.getFontMatrix().transformPoint(0, glyphHeight).y;
        } else {
            height = glyphHeight / 1000;
        }

        return height;
    }

    /**
     * This will process a TextPosition object and add the text to the list of
     * characters on a page. It takes care of overlapping text.
     *
     * @param text The text to process.
     */
    protected void processTextPosition(TextPosition text) {
        boolean showChar = true;
        String ch = text.getUnicode();
        float x, y;
        if (isRotated) {
            y = (int) text.getX();
            x = (int) (text.getY() - text.getHeight());
        } else {
            x = (int) text.getX();
            y = (int) (text.getY() - text.getHeight());
        }
        TreeMap<Float, TreeSet<Float>> sameTextChars = textLocationsByChar.computeIfAbsent(ch, k -> new TreeMap<>());
        if (suppressDuplicateOverlappingText) {
            // RDD - Here we compute the value that represents the end of the rendered
            // text. This value is used to determine whether subsequent text rendered
            // on the same line overwrites the current text.
            //
            // We subtract any positive padding to handle cases where extreme amounts
            // of padding are applied, then backed off (not sure why this is done, but there
            // are cases where the padding is on the order of 10x the character width, and
            // the TJ just backs up to compensate after each character). Also, we subtract
            // an amount to allow for kerning (a percentage of the width of the last
            // character).
            float tolerance = text.getWidth() / ch.length() / 3.0f;

            SortedMap<Float, TreeSet<Float>> yMatches = sameTextChars.subMap(y - tolerance, y + tolerance);
            for (TreeSet<Float> yMatch : yMatches.values()) {
                SortedSet<Float> xMatches = yMatch.subSet(x - tolerance, x + tolerance);
                if (!xMatches.isEmpty()) {
                    showChar = false;
                    break;
                }
            }
        }
        if (showChar) {
            TreeSet<Float> xSet = sameTextChars.computeIfAbsent(y, k -> new TreeSet<>());
            xSet.add(x);
        }
    }
    // NOTE: there are more methods in PDFStreamEngine which can be overridden here too.
}
