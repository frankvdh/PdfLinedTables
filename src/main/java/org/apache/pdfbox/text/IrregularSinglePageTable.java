/*
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 * 
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Extract irregular tables
 *
 * @author frank
 */
public class IrregularSinglePageTable extends SinglePageTable {

    /**
     * Constructor for extractor for irregular tables...
     *
     * @param file File to read
     * @param pageNum Page to read, zero-based
     * @param tableBounds Height of top and horizDivider margins
     * @param tableEnd Regex to identify end of table
     * @param table Result table
     * @param wrapChar Char to use when wrapping
     * @param insertSpaces Flag to insert multiple spaces
     * @param monoSpace Monospace
     * @param forceRotation Forge page rotation
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public IrregularSinglePageTable(File file, int pageNum, boolean forceRotation, boolean suppressDuplicates) throws IOException {
        super(file, pageNum, forceRotation, suppressDuplicates);
    }

    /**
     * Extract the defined table
     *
     * @param file PDF file to read from
     * @param firstPageNo First page of table, zero-based
     * @param bounds page bounds to include
     * @param tableStart Regex to identify start of table
     * @param firstData Regex to identify first data in table
     * @param tableEnd Regex to identify end of table
     * @param wrapChar Char to use when wrapping
     * @param insertSpaces Flag to insert multiple spaces
     * @param monoSpace Monospace
     * @param forceRotation Forge page rotation
     * @return table array
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public ArrayList<String[]> extractTable(File file, int pageNo, Color headingColour, Color dataColour, Pattern tableEnd,
            char wrapChar, boolean insertSpaces, boolean monoSpace, boolean forceRotation, boolean suppressDuplicates) throws IOException {
            LOG.debug("extract({}, {})", file.getName(), pageNo);
            SortedSet<TableCell> rects = extractCells(headingColour, dataColour, tableEnd);
            // Find all cell edges... 
            SortedSet<Float> vert = new TreeSet<>();
            SortedSet<Float> horiz = new TreeSet<>();
            for (TableCell r: rects) {
                vert.add(r.minX);
                if (!vert.contains(r.maxX)) vert.add(r.maxX);
                if (!horiz.contains(r.minY)) horiz.add(r.minY);
                if (!horiz.contains(r.maxY)) horiz.add(r.maxY);
            }
            // so the match fails
        return new ArrayList<String[]>();
    }

    /**
     * Extract text divided at cell boundaries, and build the table. Extract
     * text into rectangles in the grid.
     *
     * The rectangles do not have to be the same size, but should in general be
     * multiple rows and/or multiple columns of the underlying grid.
     *
     * A single space is generated when the X coordinates of successive text
     * items warrants.
     *
     * The text for each cell is then inserted into a row, which is in the
     * 'table' table.
     *
     * This requires that the 'textItems', 'horizLines', and 'vertLines' sets
     * all contain data. These have been populated earlier by drawPage().
     *
     * This particular method uses both vertical and horizontal lines to
     * determine the limits of a row and column. I.e. it assumes that columns
     * are separated by vertical lines, and rows are separated by horizontal
     * lines.
     *
     * @param tableTop Y coordinate of the table header.
     * @param removeBelow Flag to remove text below the table
     * @return true on success
     */
    public boolean xextractTable(final FRectangle tableBounds, boolean removeBelow) {
/* TODO        var tableEndFound = prepare(tableBounds, removeBelow, tableEnd);
        if (!tableEndFound) {
            log.info("Table spans multiple pages");
        }
 Does not correctly split text into rectangles
        for (var t : textItems) {
            if (t.str.isBlank()) {
                continue;
            }
            TableCell cell = new TableCell(t.getMinX(), t.getMinY(), t.getWidth(), t.getHeight());
            log.finer("cell {}", cell);
            double left = pageSize.getMinX();
            double right = pageSize.getMaxX();
            for (TableCell v : vertLines) {
                if (v.getMaxY() < cell.getMinY()) {
                    break;
                }
                if (v.getMinY() > cell.getMaxY()) {
                    continue;
                }
                if (v.getMaxX() < cell.getMinX() && left < v.getMaxX()) {
                    log.finest("left {}", v);
                    left = v.getMaxX();
                }
                if (v.getMinX() > cell.getMaxX() && right > v.getMinX()) {
                    log.finest("right {}", v);
                    right = v.getMinX();
                }
            }

            log.finest("left = {}, right = {}", left, right);

            double top = pageSize.getMaxY();
            double bottom = pageSize.getMinY();
            for (TableCell h : horizLines) {
                if (h.getMaxY() < cell.getMinY() && bottom != pageSize.getMinY()) {
                    break;
                }
                if (h.getMinX() >= cell.getMaxX() || h.getMaxX() <= cell.getMinX()) {
                    continue;
                }
                if (h.getMinY() < top && h.getMinY() > cell.getMaxY()) {
                    log.finest("top {}", h);
                    top = h.getMinY();
                }
                if (h.getMaxY() > bottom && h.getMaxY() < cell.getMinY()) {
                    log.finest("bottom {}", h);
                    bottom = h.getMaxY();
                }
            }
            log.finest("bottom = {}, top = {}", bottom, top);

            TableCell rect = new TableCell(left, bottom, right - left, top - bottom);
            log.finer("Cell rectangle: {}", rect);
            if (rectangles.contains(rect)) {
                rect = rectangles.ceiling(rect);
            } else {
                rectangles.add(rect);
            }
            log.finest("Cell rectangle before: {}", rect);
            rect.text.add(t); 
            log.finer("Cell rectangle: {}", rect);
        }
        */
        buildRegularTable();
        return false; // TODO  tableEndFound;
    }

    /**
     * Build regular table from the irregular rectangular cells.
     *
     * Uses the grid of horizontal and vertical lines to build a regular grid.
     * The text items in the rectangles are then inserted into the regular grid.
     * Where a rectangle overlaps multiple regular cells, all the text is copied
     * into all the cells.
     *
     * Where a cell contains multiple lines of text, they will be separated by
     * 'wrapChar'. Horizontal spacing of the text will be maintained depending
     * on the state of the 'instertSpaces' and 'monoSpace' flags.
     */
    private void buildRegularTable() {
        LOG.debug("buildRegularTable()");
        /* TODO
        // Remove text below the bottom horizontal line -- these are footnotes
        while (textItems.last().getMinY() < horizLines.last().getMaxY()) {
            textItems.remove(textItems.last());
        }
*/
        double minVert = vertLines.first().getMinY();
        for (FRectangle v : vertLines) {
            if (v.getMinY() < minVert) {
                minVert = v.getMinY();
            }
        }

        // Build list of rows indexed by their Y position
        TreeMap<Float, Integer> rows = new TreeMap<>();
        for (FRectangle c : horizLines) {
            rows.put(c.getMinY(), 0);
        }
        int numRow = 0;
        double prev = pageSize.getMaxY();
        for (Iterator<Float> itRow = rows.descendingKeySet().iterator(); itRow.hasNext();) {
            float y = itRow.next();
            if (prev - y < 3) {
                itRow.remove();
            } else {
                rows.put(y, numRow++);
                LOG.debug("horizontal row {} ", y);
                prev = y;
            }
        }

        // Build list of columns indexed by the X position of vertLines. Where two vertLines
        // are close together, only keep the first.
        TreeMap<Float, Integer> columnXs = new TreeMap<>();
        for (FRectangle c : vertLines) {
            LOG.debug("vertLine: {}", c);
            columnXs.put(c.getMinX(), 0);
        }
        int colNum = 0;
        LOG.debug("Column starts:  ");
        double prevCol = -1000;
        TreeMap<Double, Integer> columns = new TreeMap<>();
        for (double d : columnXs.keySet()) {
            if (d < prevCol + 1) {
                continue;
            }
            columns.put(d, colNum++);
            prevCol = d;

            LOG.debug("{} ", d);
        }
        columns.put(columns.lastKey(), -1); // Last vertical line does notr represent a column

        // Put the text from each rectangle into the appropriate column & row in the 
        // output table, based on the X & Y coordinates & width & height of the rectangle
        // Where a rectangle spans multiple rows or columns, it is copied into all the
        // cells it overlaps
        double prevY = pageSize.getMaxY();
        for (double y : rows.descendingKeySet()) {
            // Move text rectangles to this row
            TreeSet<FRectangle> rowCells = new TreeSet<>();
            for (FRectangle r : rectangles) {
                if (r.getMaxY() < y) {
                    continue;
                }
                if (r.getMinY() < prevY - 1) {  // -1 to avoid overlapping lines
                    rowCells.add(r);
                    LOG.debug("In row {}: {}", y, r);
                }
            }
            prevY = y;
            if (rowCells.isEmpty()) {
                continue;
            }

            // For each output column, find text rectangles in this row that overlap it
            TableCell[] row = new TableCell[columns.size() - 1];
            for (double x : columns.keySet()) {
                colNum = columns.get(x);
                if (colNum == -1) {
                    break;
                }
                double maxX = columns.higherKey(x);
                // TODO
//                row[colNum] = new TableCell(x, y, maxX - x, prevY - y);
                // NB rowCells may not be in X order
                for (FRectangle r : rowCells) {
                    if (r.getMaxX() > x + 1 && r.getMinX() < maxX) {
/* TODO                        row[colNum].text.addAll(r.text); */
                    }
                }
                LOG.debug("Column {}: {}", colNum, row[colNum]);
            }

            String[] strRow = new String[row.length];
            for (int i = 0; i < row.length; i++) {
// TODO                strRow[i] = row[i].toText('\n', insertSpaces, monoSpace);
            }
//            table.add(strRow);
        }
    }
}
