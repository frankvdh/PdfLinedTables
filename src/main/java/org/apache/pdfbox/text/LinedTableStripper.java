/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
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
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

/**
 * Extract data from line-delimited tables in a PDF document.
 *
 * Data is extracted as a set of rectangular TableCells on a page, each with a
 * rectangular boundary and the text within that rectangle. Each table must
 * start on a new page, but may span multiple pages.
 *
 * The table may have a row of table headings above it, identified by their fill
 * colour. If no fill colour is specified, it is assumed that the top horizontal
 * line identifies the top of the table.
 *
 * Cells may be arbitrary size and placement, but must be rectangular. A cell
 * may span multiple rows and/or columns. Cells are delimited by vertical and
 * horizontal lines.
 *
 * Please note; it is up to clients of this class to verify that a specific user
 * has the correct permissions to extract text from the PDF document.
 *
 * The normal entry point is a call extractTable() which reads the table via
 * processPage(), finds the start and end of the table start and end of the
 * table on the page, and extracts the cells to an ArrayList. If the end of the
 * table is is process is not found, it continues to the next page.
 *
 * @author @author <a href="mailto:drifter.frank@gmail.com">Frank van der
 * Hulst</a>
 */
public class LinedTableStripper extends PDFGraphicsStreamEngine implements Closeable {

    private static final Logger LOG = LogManager.getLogger(LinedTableStripper.class);
    private static final GlyphList GLYPHLIST;

    static {
        // load additional glyph list for Unicode mapping
        var path = "/org/apache/pdfbox/resources/glyphlist/additional.txt";
        //no need to use a BufferedInputSteam here, as GlyphList uses a BufferedReader
        try (var input = GlyphList.class.getResourceAsStream(path)) {
            GLYPHLIST = new GlyphList(GlyphList.getAdobeGlyphList(), input);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Vertical graphic lines on the page. Sorted by Y within X
     *
     */
    final protected TreeMap<Float, TreeMap<Float, Float>> vertLines = new TreeMap<>();

    /**
     * Horizontal graphic lines on the page. Sorted X within Y
     *
     */
    final protected TreeMap<Float, TreeMap<Float, Float>> horizLines = new TreeMap<>();

    /**
     * Filled rectangle graphics on the page... used to find table headings.
     * First level is colour, then Y, then X of top-left corner.
     * Stroked rectangles are decomposed into vertical and horizontal lines.
     * 
     * Top of page is first
     *
     */
    final protected TreeMap<Integer, TreeMap<Float, TreeMap<Float, FRectangle>>> rectangles = new TreeMap<>();

    /**
     * Map of text for populating rectangles. Sorted by X within Y
     *
     */
    final protected TreeMap<Float, TreeMap<Float, SimpleTextPosition>> textLocationsByYX = new TreeMap<>();

    /**
     * Size of current page
     *
     */
    private PDRectangle mediaBox;
    final private boolean suppressDuplicateOverlappingText;
    final private int extraQuadrantRotation;
    final private float tolerance;
    final private boolean leadingSpaces;
    final private boolean reduceSpaces;
    final private boolean removeEmptyRows;
    final private String lineEnding;
    private final GeneralPath linePath = new GeneralPath();
    private AffineTransform postTransform;
    private boolean endTableFound;
    private float endTablePos;

    final private PDDocument doc;
    private int currPage = -1;

    public int getCurrPageNum() {
        return currPage;
    }

    public float getTableBottom() {
        return endTablePos;
    }

    /**
     * Constructor.
     *
     * @param page Page to be read.
     * @param extraQuadrantRotation Number of CCW 90deg rotations to apply to
     * page
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public LinedTableStripper(File file, int extraQuadrantRotation, boolean suppressDuplicates, boolean leadingSpaces, boolean reduceSpaces, boolean removeEmptyRows, float tolerance, String lineEnding) throws IOException {
        super(null);
        this.doc = Loader.loadPDF(file);
        suppressDuplicateOverlappingText = suppressDuplicates;
        this.extraQuadrantRotation = extraQuadrantRotation;
        this.leadingSpaces = leadingSpaces;
        this.reduceSpaces = reduceSpaces;
        this.removeEmptyRows = removeEmptyRows;
        this.tolerance = tolerance;
        this.lineEnding = lineEnding;
        // Set up extra rotation that is not specified in the page
        // Some pages e.g. GEN_3.7.pdf are rotated -90 degrees, but
        // page.getRotation() doesn't know it. This extra rotation is performed
        // separately because it can result in objects outside the normal media
        // box, which are then not rendered
        assert (extraQuadrantRotation >= 0 && extraQuadrantRotation <= tolerance) : "Rotation must be 0-3: " + extraQuadrantRotation;
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
        mediaBox = page.getMediaBox();
        LOG.trace("processPage: size {}, rotation {}", mediaBox, page.getRotation());
        vertLines.clear();
        horizLines.clear();
        rectangles.clear();
        textLocationsByYX.clear();

        postTransform = page.getMatrix().createAffineTransform();
        // Some code below depends on the page lower left corner being at (0,0)
        if (mediaBox.getLowerLeftX() != 0 || mediaBox.getLowerLeftY() != 0) {
            LOG.warn("Page is not zero-based: {}", mediaBox);
            postTransform.translate(-mediaBox.getLowerLeftX(), -mediaBox.getLowerLeftY());
        }
        var rotCentre = Math.max(mediaBox.getWidth(), mediaBox.getHeight()) / 2;
        postTransform.quadrantRotate(extraQuadrantRotation, rotCentre, rotCentre);
        // Also flip the coordinates vertically, so that the coordinates increase
        // down the page, so that rows at the top of the table are output first
        postTransform.scale(1, -1);
        postTransform.translate(0, -mediaBox.getUpperRightY());
        LOG.trace("PostRotate: {}", postTransform);
        transformRectangle(mediaBox, postTransform);
        LOG.debug("Transformed mediaBox: {}", mediaBox);
        super.processPage(page);
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

    /**
     * Parse a tables in the specified PDF file.
     *
     * @param firstPage Page to start table from.
     * @param headingColour Background colour of heading to search for
     * @param startY Y coordinate on first page to start from
     * @param tableEnd Pattern indicating end of table
     * @param numColumns Number of columns in table
     * @return table. Each String[] entry contains each row in the table. Each
     * row is an array of Strings, with one entry for each column in the table.
     * @throws IOException on file error
     */
    public ArrayList<String[]> extractTable(int firstPage, Color headingColour, float startY, Pattern tableEnd, int numColumns) throws IOException {
        var result = new ArrayList<String[]>();
        do {
            if (currPage != firstPage) {
                processPage(doc.getPage(firstPage));
                currPage = firstPage;
            }
        assert startY >= 0 : "startY < 0";
        var bounds = new FRectangle(Float.NaN, startY, Float.NaN, mediaBox.getUpperRightY());
        if (!findTable(headingColour, bounds)) {
            LOG.error("Table header not found");
            return null;
        }
            LOG.debug("Extracting page {} of {}", currPage + 1, doc.getNumberOfPages());
            endTableFound = findEndTable(bounds, startY, tableEnd, headingColour);
            if (!Float.isNaN(endTablePos)) {
                bounds.setMaxY(endTablePos);
                appendToTable(headingColour, numColumns, bounds, result);
            }
            if (endTableFound) {
                return result;
            }
            startY = 0;
            firstPage = currPage + 1;
        } while (firstPage < doc.getNumberOfPages());
        return result;
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
    public void appendToTable(Color headingColour, int numColumns, FRectangle bounds, ArrayList<String[]> table) throws IOException {
        var rects = extractCells(headingColour, bounds);
        if (rects == null) {
            // No table data... table end pattern is before any table
            return;
        }

        // Build lists of rows & columns ordered by their X & Y position
        var rows = new TreeSet<Float>(rects.keySet());
        var allCols = new TreeSet<Float>();
        rects.entrySet().forEach(row -> {
            allCols.addAll(row.getValue().keySet());
        });
        var bottomRow = rects.lastEntry();
        if (bottomRow == null)
            return;
        var bottomRightCell = bottomRow.getValue().lastEntry().getValue();
        allCols.add(bottomRightCell.getMaxX());
        rows.add(bottomRightCell.getMaxX());

        var top = rows.removeFirst();
        var tableLeft = allCols.removeFirst();
        while (!rows.isEmpty()) {
            var right = tableLeft;
            var bottom = rows.removeFirst();
            var srcRowSet = new TreeMap<>(rects.headMap(bottom));
            var tableRow = new String[numColumns];
            var cols = new TreeSet<>(allCols);
            var colNum = 0;
            while (!cols.isEmpty()) {
                final var left = right;
                right = cols.removeFirst();
                final var finalRight = right;
                var srcRowSubset = new TreeMap<>(srcRowSet);
                for (var it = srcRowSubset.entrySet().iterator(); it.hasNext();) {
                    var srcRow = it.next();
                    var srcRowValue = new TreeMap<>(srcRow.getValue());
                    srcRowValue.entrySet().removeIf((var c) -> c.getKey() >= finalRight || c.getValue().getMaxX() <= left);
                    if (srcRowValue.isEmpty()) {
                        it.remove();
                    }
                }
                var srcRow = srcRowSubset.floorEntry(top).getValue();
                var src = srcRow.floorEntry(left).getValue();
                tableRow[colNum++] = src.getText();
            }
            top = bottom;
            table.add(tableRow);
        }
    }

    /**
     * Extracts the cells of a table from data extracted from a PDF page.
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
     * @param startY Y coordinate of top of table relative to top of page
     * @param endY Y coordinate of bottom of table, as returned by findEndTable
     * @return the set of cells, including their text, making up the table.
     *
     * @throws java.io.IOException for file errors
     */
    public TreeMap<Float, TreeMap<Float, TableCell>> extractCells(Color headingColour, FRectangle bounds) throws IOException {
        // Now have located top & bottom of data in the table, and left & right limits
        // Extract subsets of the lines, rectangles, and text within these limits.

        // Remove rectangles that are below the last horizontal line of the table... this removes any footnotes
        // Initially  include rectangles which extend above the top of the table, because they may extend down into the table
        var rects = new TreeMap<>(rectangles.get(headingColour.getRGB()).subMap(bounds.getMinY(), bounds.getMaxY()));
        for (var yIt = rects.entrySet().iterator(); yIt.hasNext();) {
            var yEntry = yIt.next();
            yEntry.getValue().entrySet().removeIf((var xEntry) -> !bounds.containsX(xEntry.getKey()) || xEntry.getValue().getHeight() < tolerance || (xEntry.getValue().getWidth() < tolerance));
            if (yEntry.getValue().isEmpty()) {
                yIt.remove();
            }
        }

        // Remove horizontal lines above and below the table, trim the remainder to the horizontal limits of the table
        var horiz = new TreeMap<>(horizLines.subMap(bounds.getMinY() - tolerance, bounds.getMaxY() + tolerance));
        horiz.values().forEach(row -> {
            for (var it = row.entrySet().iterator(); it.hasNext();) {
                var e = it.next();
                var left = bounds.trimX(e.getKey());
                var right = bounds.trimX(e.getValue());
                if (!bounds.overlapsX(left, right) || right - left < tolerance) {
                    it.remove();
                }
            }
        });
        horiz.entrySet().removeIf((var e) -> e.getValue().isEmpty());
        if (horiz.isEmpty()) {
            LOG.error("No horizontal lines in table");
            return null;
        }

        // Remove vertical lines left or right of the table, trim the remainder to the vertical limits of the table
        // Initially  include lines which extend above the top of the table, because they may extend down into the table
        // Remove empty rows
        var vert = new TreeMap<>(vertLines.subMap(bounds.getMinX() - tolerance, bounds.getMaxX() + tolerance));
        vert.values().forEach(row -> {
            for (var it = row.entrySet().iterator(); it.hasNext();) {
                var e = it.next();
                var top = bounds.trimY(e.getKey());
                var bottom = bounds.trimY(e.getValue());
                if (!bounds.overlapsX(top, bottom) || bottom - top < tolerance) {
                    it.remove();
                }
            }
        });
        vert.entrySet().removeIf((var e) -> e.getValue().isEmpty());
        if (vert.isEmpty()) {
            LOG.error("No vertical lines in table");
            return null;
        }

        // Remove text that is outside the table
        var textLocs = new TreeMap<>(textLocationsByYX.subMap(bounds.getMinY(), bounds.getMaxY()));
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
        var result = buildActualRectangles(horiz, vert, textLocs);
        if (removeEmptyRows) {
            for (var it = result.entrySet().iterator(); it.hasNext();) {
                var row = it.next();
                var allEmpty = true;
                for (var col : row.getValue().entrySet()) {
                    if (!col.getValue().getText().isBlank()) {
                        allEmpty = false;
                        break;
                    }
                }
                if (allEmpty) {
                    it.remove();
                }
            }
        }
        return result;
    }

    /**
     * Convert maps of horizontal and vertical lines and textPositions to
     * TableCells containing text.
     *
     * Assumes that horiz and tableContents are sorted in ascending Y, ascending
     * X order Assumes that vert is sorted in ascending X, ascending Y order In
     * all cases, ascending Y is moving down the page The grid may be
     * irregular... cells may span multiple rows or multiple columns However,
     * all cells must be enclosed by 2 horizontal and 2 vertical lines
     *
     * Each cell is calculated using the horiz and vert lines and, along with
     * the text it contains, is added to the results map
     *
     * @param horiz Map of horizontal lines, sorted by Y,X
     * @param vert Map of vertical lines, sorted by X,Y
     * @param tableContents Map of textPosiions, sorted by Y,X
     */
    private TreeMap<Float, TreeMap<Float, TableCell>> buildActualRectangles(
            TreeMap<Float, TreeMap<Float, Float>> horiz,
            TreeMap<Float, TreeMap<Float, Float>> vert,
            TreeMap<Float, TreeMap<Float, SimpleTextPosition>> tableContents) {
        if (horiz.size() < 2 || vert.size() < 2) {
            LOG.error("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
            return null;
        }

        var results = new TreeMap<Float, TreeMap<Float, TableCell>>();
        while (!horiz.isEmpty()) {
            var top = horiz.firstKey();
            var hTopRow = horiz.remove(top);

            // Get bottoms of all vertical lines intersecting h0 Y in a single row, in left-right order
            var vTopRow = getTopVert(hTopRow.firstKey(), top, vert);
            if (vTopRow.size() < 2) {
                continue;
            }

            // Merge horizontal lines in hTopRow with vertical lines in vTopRow
            var vLeft = vTopRow.pollFirstEntry();
            var vRight = vTopRow.pollFirstEntry();
            var h0 = hTopRow.pollFirstEntry();
            while (vRight != null && h0 != null) {
                final var left = vLeft.getKey();
                final var right = vRight.getKey();
                if (h0.getKey() > right + tolerance) {
                    vLeft = vRight;
                    vRight = vTopRow.pollFirstEntry();
                    continue;
                }
                if (h0.getValue() < left - tolerance) {
                    h0 = hTopRow.pollFirstEntry();
                    continue;
                }
                // Vertical lines intersect with top horizontal line
                // Find next horizontal line below h0
                var bottom = Math.min(vLeft.getValue(), vRight.getValue());
                var bottomRowSet = new TreeMap<>(horiz.subMap(top + tolerance, bottom + tolerance));
                bottomRowSet.entrySet().removeIf((var h) -> h.getValue().firstKey() > right - tolerance || h.getValue().lastEntry().getValue() < left + tolerance);
                var bottomEntry = bottomRowSet.pollFirstEntry();
                while (bottomEntry != null) {
                    var bottomRow = new TreeMap<>(bottomEntry.getValue());
                    bottomRow.entrySet().removeIf((var h) -> h.getKey() > left + tolerance || h.getValue() < right - tolerance);
                    if (!bottomRow.isEmpty()) {
                        break;
                    }
                    bottomEntry = bottomRowSet.pollFirstEntry();
                }

                if (bottomEntry != null) {
                    if (bottomEntry.getKey() < bottom) {
                        bottom = bottomEntry.getKey();
                    }
                    // Now have the top & bottom lines and X limits
                    LOG.trace("{}, {}, {}, {}", left, top, right, bottom);
                    var rect = getRectangleText(left, top, right, bottom, tableContents);
                    var row = results.computeIfAbsent(top, k -> new TreeMap<>());
                    row.put(left, rect);
                }
                vLeft = vRight;
                vRight = vTopRow.pollFirstEntry();
            }
        }

        LOG.traceExit("buildActualRectangles: Count = {}", results.size());
        return results;
    }

    /**
     * Find vertical lines that intersect the top edge
     *
     * Remove vertical lines whose top vertex is above the table. Lines with
     * bottom vertex above the table are removed entirely Lines whose top vertex
     * is above the table and bottom vertex is in or below the table are
     * returned
     *
     * @param top Top edge of table
     * @param vert Vertical lines, in Y within X order
     * @return Vertical lines extending across or to the top of the table
     */
    private SortedMap<Float, Float> getTopVert(float left, float top, TreeMap<Float, TreeMap<Float, Float>> vert) {
        var topRow = new TreeMap<Float, Float>();
        // Extract lines starting at or above the top of the table
        for (var colEntry : vert.entrySet()) {
            if (colEntry.getKey() < left - tolerance) {
                continue;
            }
            var col = new TreeMap<>(colEntry.getValue());
            // Remove lines whose bottoms are above the table
            col.entrySet().removeIf((var e) -> e.getValue() < top + tolerance || e.getKey() > top + tolerance);
            if (!col.isEmpty()) {
                var x = colEntry.getKey();
                var v = col.firstEntry();
                topRow.put(x, v.getValue());
            }
        }
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
    protected TableCell[][] buildRegularRectangles(SortedSet<FRectangle> horiz, SortedSet<FRectangle> vert, SortedSet<TableCell> tableContents) {
        if (horiz.size() < 2 || vert.size() < 2) {
            LOG.error("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
            return null;
        }
        var hSet = new TreeSet<Float>();
        var prevH = Float.POSITIVE_INFINITY;
        for (var h : horiz) {
            if (prevH - h.getMaxY() > tolerance) {
                prevH = h.getMaxY();
                hSet.add(prevH);
            }
        }
        var vSet = new TreeSet<Float>();
        vert.forEach(v -> {
            if (vSet.subSet(v.getMinX() - tolerance, v.getMaxX() + tolerance).isEmpty()) {
                vSet.add(v.getMaxX());
            }
        });

        if (hSet.size() < 2 || vSet.size() < 2) {
            LOG.error("Unique horizontal lines = {}, vertical lines = {} so no table", hSet.size(), vSet.size());
            return null;
        }
        // Now have all unique horizontal and vertical lines
        var results = new TableCell[hSet.size()][vSet.size() - 1];
        var h0 = hSet.removeFirst();
        for (var row = results.length - 1; row >= 0; row--) {
            final var h1 = hSet.removeFirst();
            var v0 = vSet.getFirst();
            float v1;
            for (var col = 0; col < vSet.size() - 1; col++) {
                v1 = vSet.ceiling(v0 + tolerance);
                var result = new TableCell(v0, h1, v1, h0, "");
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

    private static boolean spacesAllowed;

    /**
     * Extract the text contained within the specified rectangle
     *
     * @param rect
     * @param tableContents
     * @return
     */
    protected TableCell getRectangleText(float x0, float y0, float x1, float y1, TreeMap<Float, TreeMap<Float, SimpleTextPosition>> tableContents) {
        var sb = new StringBuilder(100);
        var yRange = tableContents.subMap(y0, y1);
        yRange.values().stream().map(row -> row.subMap(x0, x1)).filter(xRange -> !(xRange.isEmpty())).map(xRange -> {
            spacesAllowed = leadingSpaces && lineEnding.charAt(lineEnding.length() - 1) != ' ' || !reduceSpaces;
            return xRange;
        }).map(xRange -> {
            var prevX = x0;
            for (var tp : xRange.values()) {
                var x = tp.getX();
                if (spacesAllowed) {
                    var numSpaces = tp.getSpaceWidth() == 0 ? 0 : (int) ((x - prevX) / tp.getSpaceWidth() + 0.5);
                    if (reduceSpaces && numSpaces > 1) {
                        numSpaces = 1;
                    }
                    spacesAllowed = numSpaces > 0;
                    if (spacesAllowed && tp.getSpaceWidth() > 0) {
                        sb.repeat(' ', numSpaces);
                    }
                }
                prevX = x + tp.getWidth();
                sb.append(tp.getUnicode());
                spacesAllowed = true;
            }
            return xRange;
        }).map(_item -> {
            while (sb.charAt(sb.length() - 1) == ' ') {
                sb.deleteCharAt(sb.length() - 1);
            }
            return _item;
        }).forEachOrdered(_item -> {
            sb.append(lineEnding);
        });
        return new TableCell(x0, y0, x1, y1, sb.toString().trim());
    }

    /**
     * Find the end of the current table
     *
     * @param bounds  Table-bounding rectangle, minY = table top
     * @param tableEnd Pattern outside the table to search for. If null, the
     * bottom-most horizontal line on the page is assumed to be the bottom of
     * the table
     * @return Y coordinate of bottom-most line
     */
    protected boolean findEndTable(FRectangle bounds, float startY, Pattern tableEnd, Color headingColor) {
        LOG.traceEntry("findEndTable \"{}\"", (tableEnd == null ? "null" : tableEnd.toString()));
        // Scan through the text for the endTable delimiter text
        if (tableEnd != null) {
            var rows = textLocationsByYX.tailMap(startY);
            for (var c : rows.entrySet()) {
                endTablePos = c.getKey();
                // Concatenate all of the text onto entire lines
                var line = new StringBuilder(c.getValue().values().size());
                c.getValue().values().forEach(tp -> {
                    line.append(tp.getUnicode());
                });
                if (tableEnd.matcher(line.toString()).find()) {
                    LOG.traceExit("findEndTable: {}", endTablePos);
                    return true;
                }
            }
            LOG.warn("Table end delimiter \"{}\" not found", tableEnd.toString());
        }
        if (headingColor != null) {
            // Assume that the next table heading delimits the bottom of the table
            var endPos = rectangles.get(headingColor.getRGB()).ceilingKey(bounds.getMinY()+tolerance);
            if (endPos != null) {
                endTablePos = endPos;
                return true;
            }
            LOG.warn("No other table heading found");
            endTablePos = Float.NaN;
        }
        if (!horizLines.isEmpty()) {
            // Assume that the last horizontal line on the page is the bottom of the table
            endTablePos = horizLines.lastKey();
            if (endTablePos <= bounds.getMinY()) {
                LOG.warn("No Table end delimiter specified and no horizontal line below heading");
                endTablePos = Float.NaN;
            }
            return tableEnd == null;
        }
        LOG.warn("No Table end delimiter specified and no horizontal line found");
        endTablePos = Float.NaN;
        return false;
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
                var hdgBounds = new FRectangle(sameColour.firstEntry().getValue().firstEntry().getValue());
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
        var hTop = horizLines.ceilingEntry(bounds.getMinY());
        bounds.setMinY(Math.max(bounds.getMinY(), hTop.getKey()));
        var minX = hTop.getValue().firstKey();
        var maxX = hTop.getValue().lastEntry().getValue();

        var vLeft = vertLines.ceilingEntry(minX - tolerance);
        if (vLeft != null) {
            bounds.setMinX(vLeft.getKey());
        }
        var vRight = vertLines.floorEntry(maxX + tolerance);
        if (vRight != null) {
            bounds.setMaxX(vRight.getKey());
        }
        return true;
    }

    private void getRectangleHdgRange(FRectangle bounds) {
        bounds.setMinY(Float.POSITIVE_INFINITY);
        rectangles.entrySet().stream().map(colour -> colour.getValue().firstEntry()).filter(firstRow -> (firstRow.getKey() < bounds.getMinY())).map(firstRow -> {
            bounds.setMinY(firstRow.getKey());
            return firstRow;
        }).forEachOrdered(firstRow -> {
            var left = firstRow.getValue().firstKey();
            var rightRect = firstRow.getValue().lastEntry().getValue();
            bounds.setX(left, rightRect.getMaxX());
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
        LOG.traceEntry("appendRectangle {}, {}, {}, {}", p0, p1, p2, p3);
        linePath.moveTo(p0.getX(), p0.getY());
        linePath.lineTo(p1.getX(), p1.getY());
        linePath.lineTo(p2.getX(), p2.getY());
        linePath.lineTo(p3.getX(), p3.getY());
        linePath.closePath();
    }

    /**
     * Add a filled rectangle to the set of rectangles
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
        // Coordinates here are already in display space
        var bounds = linePath.getBounds2D();
        var p0 = transformPoint((float) bounds.getMinX(), (float) bounds.getMinY(), postTransform);
        var p1 = transformPoint((float) bounds.getMaxX(), (float) bounds.getMaxY(), postTransform);
        // Treat short wide rectangles as linesIgnore cells too small for text
        if (bounds.getHeight() < tolerance || bounds.getWidth() < tolerance) {
            // Ignore cells too small for text
            if (bounds.getHeight() < tolerance && bounds.getWidth() < tolerance) {
                LOG.trace("Ignored shaded rectangle {} too small for text", bounds);
                return;
            }
            LOG.trace("Shaded rectangle {} treated as line", bounds);
            addLine(p0, p1);
            return;
        }

        // Add a rectangle to the rectangles sorted list.
        var rect = new FRectangle(fillColour, null, p0.x, p0.y, p1.x, p1.y);
        // p0 & p1 may not be minimum and maximum respectively, so use rect to
        // get left, right, top, bottom instead
        var sameColour = rectangles.computeIfAbsent(fillColour.getRGB(), k -> new TreeMap<>());
        var row = sameColour.computeIfAbsent(rect.getMinY(), k -> new TreeMap<>());
        if (!row.keySet().contains(rect.getMinX())) {
            row.put(rect.getMinX(), rect);
        } else {
            var existing = row.get(rect.getMinX());
            if (rect.getMaxX() >= existing.getMaxX() && rect.getMaxY() >= existing.getMaxY()) {
                row.replace(rect.getMinX(), rect);
            }
        }
        LOG.trace("rectangle added {}", rect);
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
        var pathIt = linePath.getPathIterator(postTransform);
        var numPoints = 0;
        Point2D.Float prev = null;
        while (!pathIt.isDone()) {
            var coords = new float[6];
            pathIt.currentSegment(coords);
            var p1 = new Point2D.Float(coords[0], coords[1]);
            LOG.trace("coords {} -> {}", coords, p1);
            if (prev != null) {
                // Add a line to the appropriate sorted list (horizLines, vertLines).
                addLine(prev, p1);
            }
            prev = p1;
            pathIt.next();
            numPoints++;
        }
        linePath.reset();
    }

    /**
     * Add line segment to the appropriate set -- horizontal or vertical lines
     *
     * Coordinates are in the display coordinate system
     *
     */
    private void addLine(Point2D.Float p0, Point2D.Float p1) throws IOException {
        LOG.traceEntry("addPath: {} {}", p0, p1);

        // Coordinates here are already in display space
        // Add a line to the appropriate sorted list (horizLines, vertLines).
        if (Math.abs(p1.y - p0.y) <= tolerance) {
            var y = (p1.y + p0.y) / 2;
            var left = Math.min(p0.x, p1.x);
            var right = Math.max(p0.x, p1.x);
            var nearby = horizLines.subMap(y - tolerance, y + tolerance);
            if (nearby.isEmpty()) {
                var row = new TreeMap<Float, Float>();
                row.put(left, right);
                horizLines.put(y, row);
                LOG.trace("new horiz line added y = {}, {} - {}", y, left, right);
            } else {
                // A row has been found within tolerance... insert this segment 
                // into the nearest row
                var nextRow = horizLines.ceilingEntry(y);
                var prevRow = horizLines.floorEntry(y);
                var row = prevRow == null ? nextRow : nextRow == null ? prevRow : y - prevRow.getKey() < nextRow.getKey() - y ? prevRow : nextRow;
                insertIntoRow(left, right, row.getValue());
                LOG.trace("added to horiz line y = {}, {} - {}", y, left, right);
            }
        } else if (Math.abs(p1.x - p0.x) <= tolerance) {
            var x = (p0.x + p1.x) / 2;
            var top = Math.min(p0.y, p1.y);
            var bottom = Math.max(p0.y, p1.y);
            var nearby = vertLines.subMap(x - tolerance, x + tolerance);
            if (nearby.isEmpty()) {
                var row = new TreeMap<Float, Float>();
                row.put(top, bottom);
                vertLines.put(x, row);
                LOG.trace("new vert line added x = {}, {} - {}", x, top, bottom);
            } else {
                // A row has been found within tolerance... insert this segment 
                // into the nearest row
                var nextRow = vertLines.ceilingEntry(x);
                var prevRow = vertLines.floorEntry(x);
                var row = prevRow == null ? nextRow : nextRow == null ? prevRow : x - prevRow.getKey() < nextRow.getKey() - x ? prevRow : nextRow;
                insertIntoRow(top, bottom, row.getValue());
                LOG.trace("added to vert line x = {}, {} - {}", x, top, bottom);
            }
        } else {
            LOG.warn("Diagonal line segment {}, {} ignored", p0, p1);
        }
    }

    private void insertIntoRow(float min, float max, TreeMap<Float, Float> row) {
        var prev = row.floorEntry(min);
        if (prev == null || prev.getValue() < min - tolerance) {
            // No intersection with prev, check next
            var next = row.ceilingEntry(min);
            if (next == null || next.getKey() > max + tolerance) {
                // No intersection with prev or next, insert new
                row.put(min, max);
                return;
            }
            // No intersection with prev, intersects next -> prepend to next
            row.remove(next.getKey());
            row.put(max, next.getValue());
            return;
        }

        if (prev.getValue() < max) {
            // Overlaps prev
            var next = row.ceilingEntry(min);
            if (next == null || next.getKey() > max + tolerance) {
                // No intersection with next, append to prev
                row.put(prev.getKey(), max);
                return;
            }
            // Intersection with both prev and next -> append to prev
            row.put(prev.getKey(), next.getValue());
        } else {
            // Completely covered by prev... ignore
        }
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
     * Process a glyph from the PDF Stream.
     *
     * Checks whether to ignore it if it is a duplicate... sometimes boldface is
     * represented by the same character output twice with a small offset.
     * Otherwise adds the character to a table of locations.
     *
     * @param string the encoded text
     * @throws IOException if there is an error processing the string
     */
    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement) throws IOException {
        var p = transformPoint(textRenderingMatrix.getTranslateX(), textRenderingMatrix.getTranslateY(), postTransform);
        var dispX = textRenderingMatrix.getScalingFactorX() * displacement.getX();
        // use our additional glyph list for Unicode mapping
        var text = font.toUnicode(code, GLYPHLIST);
        // when there is no Unicode mapping available, coerce the character code into Unicode
        var unicode = text == null ? (char) code : text.charAt(0);

        var glyphSpaceToTextSpaceFactor = (font instanceof PDType3Font) ? font.getFontMatrix().getScaleX() : (1 / 1000f);
        var spaceWidth = 0f;
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
            spaceWidth = 1f; // if could not find font, use a generic value
        }

        // Transform space width into display units. The divide by 2 is a kludge...
        // sometimes spaces in the PDF are narrower than specified by the font
        // The actual value is not critical... anywhere between 0.3 and 0.6 seems to work
        spaceWidth *= getGraphicsState().getTextState().getFontSize() * textRenderingMatrix.getScalingFactorX() * 0.6f;
        var tp = new SimpleTextPosition(p, dispX, spaceWidth, unicode);

        // Add the char to the list of characters on a page. Take care of overlapping text.
        LOG.trace("textPosition {}", tp);
        var sortedRow = textLocationsByYX.computeIfAbsent(tp.getY(), k -> new TreeMap<>());
        if (suppressDuplicateOverlappingText) {
            var overlapTolerance = tp.getWidth() / 3;
            var yMatches = textLocationsByYX.subMap(tp.getY() - overlapTolerance, tp.getY() + overlapTolerance);
            for (var yMatch : yMatches.values()) {
                var xMatches = new TreeMap<>(yMatch.subMap(tp.getX() - overlapTolerance, tp.getX() + overlapTolerance)).values();
                xMatches.removeIf((var m) -> m.getUnicode() != tp.getUnicode());
                if (!xMatches.isEmpty()) {
                    LOG.trace("Dup char {}", tp);
                    return;
                }
            }
        }
        sortedRow.put(tp.getX(), tp);
    }

    // NOTE: there are more methods in PDFStreamEngine which can be overridden here too.
    @Override
    public void close() throws IOException {
        doc.close();
    }
}
