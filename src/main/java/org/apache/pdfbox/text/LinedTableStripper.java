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
    final protected TreeMap<Float, TreeMap<Float, Float>> vertLines = new TreeMap<>();

    /**
     * Horizontal graphic lines on the page Top of page is first
     *
     */
    final protected TreeMap<Float, TreeMap<Float, Float>> horizLines = new TreeMap<>();

    /**
     * Filled rectangle graphics on the page... used to find table headings.
     * Stroked rectangles are decomposed into vertical and horizontal lines Top
     * of page is first
     *
     */
    final protected TreeMap<Integer, TreeMap<Float, TreeMap<Float, FRectangle>>> rectangles = new TreeMap<>();

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
    final private float tolerance;
    private final GeneralPath linePath = new GeneralPath();
    private AffineTransform postRotate;
    protected boolean endTableFound;
    protected float endTablePos;

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
    public LinedTableStripper(PDDocument doc, int extraQuadrantRotation, boolean suppressDuplicates, float tolerance) throws IOException {
        super(null);
        suppressDuplicateOverlappingText = suppressDuplicates;
        this.extraQuadrantRotation = extraQuadrantRotation;
        this.tolerance = tolerance;
        this.doc = doc;
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
        var rotCentre = Math.max(mediaBox.getWidth(), mediaBox.getHeight()) / 2;
        postRotate.quadrantRotate(extraQuadrantRotation, rotCentre, rotCentre);
        // Also flip the coordinates vertically, so that the coordinates increase
        // down the page, so that rows at the top of the table are output first
        postRotate.scale(1, -1);
        postRotate.translate(0, -mediaBox.getUpperRightY());
        LOG.trace("PostRotate: {}", postRotate);
        transformRectangle(mediaBox, postRotate);
        LOG.debug("Transformed mediaBox: {}", mediaBox);

        if (page.hasContents()) {
            isProcessingPage = true;
            processStream(page);
            isProcessingPage = false;
            consolidateVertRowTable();
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

    private void consolidateVertRowTable() {
        if (vertLines.size() < 2) {
            return;
        }
        Entry<Float, TreeMap<Float, Float>> prevRow = null;
        for (var it = vertLines.sequencedEntrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            var row = entry.getValue();
            var y = entry.getKey();
            it.forEachRemaining((var r) -> {
            });
            LOG.debug("Row: {} ->   {}", y, row);
            if (row.isEmpty()) {
                it.remove();
                continue;
            }
            if (prevRow == null || y - prevRow.getKey() > tolerance) {
                prevRow = entry;
            } else {
                mergeVertRow(prevRow.getKey(), prevRow.getValue(), y, row);
                it.remove();
                LOG.debug("merged: {}", row);
            }
            simplifyVertRow(prevRow.getValue());
            LOG.debug("result: {}", prevRow);
        }
    }

    private void simplifyVertRow(TreeMap<Float, Float> row) {
        if (row.size() < 2) {
            return;
        }
        var it = row.entrySet().iterator();
        var prev = it.next();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey() <= prev.getValue() + tolerance) {
                prev.setValue(e.getValue());
                it.remove();
            } else {
                prev = e;
            }
        }
    }

    private void mergeVertRow(float tT, SortedMap<Float, Float> target, float sT, SortedMap<Float, Float> src) {
        for (var e : src.entrySet()) {
            var sX = e.getKey();
            var sB = e.getValue();
            var nearby = target.subMap(sX - tolerance, sX + tolerance);
            if (nearby.isEmpty()) {
                target.put(sX, sB);
                continue;
            }
            var success = false;
            for (var t : nearby.entrySet()) {
                var tX = t.getKey();
                var tB = t.getValue();
                if (sT > tT) {
                    // src doesn't overlap target's top edge
                    if (sT < tB) {
                        // src overlaps tgt, but not at top
                        if (sB > tB) {
                            // src overlaps tgt at bottom only. Extend target down
                            t.setValue(sB);
                        } else {
                            // tgt completely covers src... do nothing
                        }
                        success = true;
                        break;
                    } else {
                        // src is completely right of target, no overlap
                        // keep trying
                    }
                } else if (sB > tB) {
                    // src contains target
                    target.remove(tX);
                    target.put(tX, sB);
                    success = true;
                    break;
                } else if (sB >= tX) {
                    // src overlaps target, extends above
                    target.remove(tX);
                    target.put(sX, tB);
                    success = true;
                    break;
                } else {
                    // src is completely left of target, no overlap
                    // keep trying
                }
            }
            if (!success) {
                // No overlaps found... add
                target.put(sX, sB);
            }
        }
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
        ArrayList<String[]> result = new ArrayList<>();
        do {
            if (currPage != firstPage) {
                processPage(doc.getPage(firstPage));
                currPage = firstPage;
            }
            LOG.debug("Extracting page {} of {}", currPage + 1, doc.getNumberOfPages());
            findEndTable(startY, mediaBox.getUpperRightY(), tableEnd);
            if (!Float.isNaN(endTablePos)) {
                appendToTable(headingColour, startY, numColumns, result);
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
    public void appendToTable(Color headingColour, float startY, int numColumns, ArrayList<String[]> table) throws IOException {
        throw new RuntimeException("Not implemented");
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
            LOG.error("Table header not found");
            return null;
        }
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
        for (var row : horiz.values()) {
            for (var it = row.entrySet().iterator(); it.hasNext();) {
                var e = it.next();
                var left = bounds.trimX(e.getKey());
                var right = bounds.trimX(e.getValue());
                if (!bounds.overlapsX(left, right) || right - left < tolerance) {
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

    private TreeMap<Float, TreeMap<Float, Float>> trimVert(FRectangle bounds, TreeMap<Float, TreeMap<Float, Float>> source) {
        var vert = new TreeMap<>(source.headMap(bounds.getMaxY() + tolerance));
        var top = bounds.getMinY();
        var topRow = vert.computeIfAbsent(top, k -> new TreeMap<>());
        for (var itR = vert.entrySet().iterator(); itR.hasNext();) {
            var rowEntry = itR.next();
            var row = rowEntry.getValue();
            var rowY = rowEntry.getKey();

            // Remove lines left or right of the table, or completely above the table, or with top just above the bottom bound
            row.entrySet().removeIf((var e) -> e.getKey() < bounds.getMinX() - tolerance || e.getKey() > bounds.getMaxX() + tolerance || e.getValue() < top - tolerance);
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

    private TreeMap<Float, Float> getRow(float y, TreeMap<Float, TreeMap<Float, Float>> map) {
        var r = map.subMap(y - tolerance, y + tolerance);
        return (r.isEmpty())
                ? map.computeIfAbsent(y, k -> new TreeMap<>())
                : map.floorEntry(y).getValue();

    }

    /**
     * Trim vertical lines to the table R
     *
     * Remove vertical lines whose top vertex is above the table. Lines with
     * bottom vertex above the table are removed entirely Lines whose top vertex
     * is above the table and bottom vertex is in or below the table are
     * returned
     *
     * @param top Top edge of table
     * @param vert Vertical lines, in X within Y order
     * @return Vertical lines extending across or to the top of the table
     */
    private SortedMap<Float, Float> mergeTopRow(float top, TreeMap<Float, TreeMap<Float, Float>> vert) {
        var topRow = new TreeMap<Float, Float>();
        // Process lines starting above the top of the table
        for (var itR = vert.entrySet().iterator(); itR.hasNext();) {
            var rowEntry = itR.next();
            var rowY = rowEntry.getKey();
            if (rowY > top + tolerance) {
                break;
            }

            var row = rowEntry.getValue();
            // Remove the entire row from the table
            itR.remove();
            // Remove lines whose bottom is above the table
            row.entrySet().removeIf((var e) -> e.getValue() < top + tolerance);
            // Remaining entries have bottoms extending into or below the table
            // Add them to the new top row
            for (var v : row.entrySet()) {
                var vSet = topRow.subMap(v.getKey() - tolerance, v.getKey() + tolerance);
                if (vSet.isEmpty() || vSet.firstEntry().getValue() < v.getValue()) {
                    topRow.put(v.getKey(), v.getValue());
                }
            }
        }
        vert.put(top, topRow);
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
            TreeMap<Float, TreeMap<Float, Float>> horiz,
            TreeMap<Float, TreeMap<Float, Float>> vert,
            SortedMap<Float, SortedMap<Float, SimpleTextPosition>> tableContents) {
        if (horiz.size() < 2 || vert.isEmpty()) {
            LOG.error("Horizontal lines = {}, Vertical lines = {} so no table", horiz.size(), vert.size());
            return null;
        }

        var results = new TreeSet<TableCell>();
        while (!horiz.isEmpty()) {
            var top = horiz.firstKey();
            var hRow0 = horiz.remove(top);

            // Get all vertical lines intersecting h0 Y in a single row, in left-right order
            var vTopRow = mergeTopRow(top, vert);

            while (true) {
                var h0 = hRow0.pollFirstEntry();
                if (h0 == null) {
                    break;
                }
                var left = h0.getKey();
                var rightH0 = h0.getValue();
                var bottomRowSet = new TreeMap<>(horiz.tailMap(top + tolerance));
                var leftFinal = left;
                bottomRowSet.entrySet().removeIf((var h) -> h.getValue().firstKey() - leftFinal > tolerance);
                if (bottomRowSet.isEmpty()) {
                    continue;
                }
                var rightFinal = rightH0;
                var bottomEntry = bottomRowSet.firstEntry();
                var bottomRow = new TreeMap<>(bottomEntry.getValue());
                bottomRow.entrySet().removeIf((var h) -> h.getKey() > rightFinal - tolerance);
                if (bottomRow.isEmpty()) {
                    continue;
                }
                var bottom = bottomEntry.getKey();
                // Horizontal lines do not overlap with each other
                if (bottomRow.firstKey() > left + tolerance) {
                    left = bottomRow.firstKey();
                }
                if (bottomRow.lastEntry().getValue() < rightH0 - tolerance) {
                    rightH0 = bottomRow.lastEntry().getValue();
                }

                // Now have the top & bottom lines and X limits
                LOG.debug("{}, {}, {}, {}", left, top, rightH0, bottom);

                var leftFinal2 = left;
                vTopRow.entrySet().removeIf((var v) -> v.getKey() < leftFinal2 - tolerance);
                var vSet = new TreeMap<>(vTopRow.headMap(rightH0 + tolerance));
                var bottomFinal = bottom;
                vSet.entrySet().removeIf((var v) -> v.getValue() < bottomFinal - tolerance);
                left = vSet.pollFirstEntry().getKey();
                var right = vSet.firstKey();
                var rect = new TableCell(left, bottom, right, top);
                rect.setText(getRectangleText(rect, tableContents));
                results.add(rect);
                LOG.trace("Added \"{}\" {}", rect.getText().replaceAll("\\n", "\\\\\\n"), rect);
                vTopRow.entrySet().removeIf((var v) -> v.getKey() < right - tolerance);
                if (h0.getValue() > right + tolerance) {
                    hRow0.put(right, h0.getValue());
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
            if (prevH - h.getMaxY() > tolerance) {
                prevH = h.getMaxY();
                hSet.add(prevH);
            }
        }
        var vSet = new TreeSet<Float>();
        for (var v : vert) {
            var exists = !vSet.subSet(v.getMinX() - tolerance, v.getMaxX() + tolerance).isEmpty();
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
                v1 = vSet.ceiling(v0 + tolerance);
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
            endTableFound = tableEnd == null;
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
        bounds.setX(vert.ceilingEntry(bounds.getMinX() - tolerance).getKey(), vert.floorKey(bounds.getMaxX() + tolerance));
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
        Rectangle2D bounds = linePath.getBounds2D();
        // Ignore cells too small for text
        if (bounds.getHeight() < tolerance || bounds.getWidth() < tolerance) {
            LOG.warn("Ignored shaded rectangle {} too small for text", bounds);
            return;
        }

        var p0 = transformPoint((float) bounds.getMinX(), (float) bounds.getMinY(), postRotate);
        var p1 = transformPoint((float) bounds.getMaxX(), (float) bounds.getMaxY(), postRotate);
        // Coordinates here are already in display space

        // Add a rectangle to the appropriate sorted list (horizLines, vertLines, rectangles).
        FRectangle rect = new FRectangle(fillColour, null, null, p0, p1);
        var sameColour = rectangles.computeIfAbsent(fillColour.getRGB(), k -> new TreeMap<>());
        var row = sameColour.computeIfAbsent(p0.y, k -> new TreeMap<>());
        if (!row.keySet().contains(p0.x)) {
            row.put(p0.x, rect);
        } else {
            var existing = row.get(p0.x);
            if (p1.x >= existing.getMaxX() && p1.y >= existing.getMaxY()) {
                row.replace(p0.x, rect);
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
        var pathIt = linePath.getPathIterator(postRotate);
        var numPoints = 0;
        Point2D.Float prev = null;
        while (!pathIt.isDone()) {
            float coords[] = new float[6];
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
                LOG.debug("new horiz line added y = {}, {} - {}", y, left, right);
            } else {
                // A row has been found within tolerance... insert this segment 
                // into the nearest row
                var nextRow = horizLines.ceilingEntry(y);
                var prevRow = horizLines.floorEntry(y);
                var row = prevRow == null ? nextRow : nextRow == null ? prevRow : y - prevRow.getKey() < nextRow.getKey() - y ? prevRow : nextRow;
                insertHorizRow(left, right, row.getValue());
                LOG.debug("added to horiz line y = {}, {} - {}", y, left, right);
            }
        } else if (Math.abs(p1.x - p0.x) <= tolerance) {
            var top = Math.min(p0.y, p1.y);
            var bottom = Math.max(p0.y, p1.y);
            var x = (p0.x + p1.x) / 2;
            var nearby = new TreeMap<>(horizLines.headMap(bottom + tolerance));
            for (var it = nearby.entrySet().iterator(); it.hasNext();) {
                var rowEntry = it.next();
                var row = rowEntry.getValue().entrySet();
                var rowY = rowEntry.getValue();
                row.removeIf((var v) -> Math.abs(v.getKey() - x) > tolerance);
                if (row.isEmpty()) {
                    it.remove();
                }
            }
            if (nearby.isEmpty()) {
                var row = new TreeMap<Float, Float>();
                row.put(x, bottom);
                vertLines.put(top, row);
                LOG.debug("new vert line added x = {}, {} - {}", x, top, bottom);
            } else {
                // A row has been found within tolerance... insert this segment 
                // into the nearest row
                var nextRow = vertLines.ceilingEntry(bottom);
                var prevRow = vertLines.floorEntry(top);
                var row = prevRow == null ? nextRow : nextRow == null ? prevRow : top - prevRow.getKey() < nextRow.getKey() - top ? prevRow : nextRow;
                insertVertRow(top, bottom,);
                LOG.debug("added to vert line x = {}, {} - {}", x, top, bottom);
            }
            if (!row.keySet().contains(x) || row.get(x) < bottom) {
                row.put(x, bottom);
            }
            LOG.trace("vertical line added x = {}, {} - {}", x, top, row.get(x));
        } else {
            LOG.warn("Diagonal line segment {}, {} ignored", p0, p1);
        }
    }

    private void insertHorizRow(float left, float right, TreeMap<Float, Float> row) {
        var prev = row.floorEntry(left);
        if (prev == null || prev.getValue() < left - tolerance) {
            // No intersection with prev, check next
            var next = row.ceilingEntry(left);
            if (next == null || next.getKey() > right + tolerance) {
                // No intersection with prev or next, insert new
                row.put(left, right);
                return;
            }
            // No intersection with prev, intersects next -> prepend to next
            row.remove(next.getKey());
            row.put(right, next.getValue());
            return;
        }

        if (prev.getValue() < right) {
            // Overlaps prev
            var next = row.ceilingEntry(left);
            if (next == null || next.getKey() > right + tolerance) {
                // No intersection with next, append to prev
                row.put(prev.getKey(), right);
                return;
            }
            // Intersection with both prev and next -> append to prev
            // No intersection with next, append to prev
            row.put(prev.getKey(), next.getValue());
            return;

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
            float overlapTolerance = tp.getWidth() / tolerance;
            var yMatches = textLocationsByYX.subMap(tp.getY() - overlapTolerance, tp.getY() + overlapTolerance);
            for (var yMatch : yMatches.values()) {
                var xMatches = yMatch.subMap(tp.getX() - overlapTolerance, tp.getX() + overlapTolerance).values();
                if (xMatches.contains(tp)) {
                    return;
                }
            }
        }
        sortedRow.put(tp.getX(), tp);
    }

    // NOTE: there are more methods in PDFStreamEngine which can be overridden here too.
}
