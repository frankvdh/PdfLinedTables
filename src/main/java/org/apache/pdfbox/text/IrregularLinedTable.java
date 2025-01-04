/*
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 * 
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Extract irregular tables
 *
 * @author frank
 */
public class IrregularLinedTable extends LinedTableStripper {

    /**
     * Constructor for extractor for irregular tables...
     *
     * @param name Name of table to read
     * @param pageNo Page to read, one-based
     * @param heading
     * @param data
     * @param tableEnd Regex to identify end of table
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public IrregularLinedTable(PDPage page, boolean forceRotation, boolean suppressDuplicates) throws IOException {
        super(page, forceRotation, suppressDuplicates);
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
    public static ArrayList<String[]> extract(File file, int firstPageNo, Color heading, Color firstData, Pattern tableEnd,
            boolean forceRotation) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            for (int pageNo = firstPageNo;; pageNo++) {
                IrregularLinedTable stripper = new IrregularLinedTable(doc.getPage(pageNo), forceRotation, true);
//               FRectangle tableBounds = stripper.findTable(heading, firstData, tableEnd);
                LOG.error("extract({}, Page {})", file.getName(), pageNo);
//                var result = stripper.extractTable(tableBounds.getMaxY(), true);
            }
        }
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
    
    public boolean extractTable(final double tableTop, boolean removeBelow
    ) {
        /*
        var tableEndFound = prepare(tableTop, removeBelow);
        if (!tableEndFound) {
            log.info("Table spans multiple pages");
        }
// TODO Does not correctly split text into rectangles
        for (var t : textItems) {
            if (t.str.isBlank()) {
                continue;
            }
            TableCell text = new TableCell(t.x, t.y, t.width, t.height);
            log.finer("text {}", text);
            double left = pageSize.getMinX();
            double right = pageSize.getMaxX();
            for (TableCell v : vertLines) {
                if (v.getMaxY() < text.getMinY()) {
                    break;
                }
                if (v.getMinY() > text.getMaxY()) {
                    continue;
                }
                if (v.getMaxX() < text.getMinX() && left < v.getMaxX()) {
                    log.finest("left {}", v);
                    left = v.getMaxX();
                }
                if (v.getMinX() > text.getMaxX() && right > v.getMinX()) {
                    log.finest("right {}", v);
                    right = v.getMinX();
                }
            }

            log.finest("left = {}, right = {}", left, right);

            double top = pageSize.getMaxY();
            double bottom = pageSize.getMinY();
            for (TableCell h : horizLines) {
                if (h.getMaxY() < text.getMinY() && bottom != pageSize.getMinY()) {
                    break;
                }
                if (h.getMinX() >= text.getMaxX() || h.getMaxX() <= text.getMinX()) {
                    continue;
                }
                if (h.getMinY() < top && h.getMinY() > text.getMaxY()) {
                    log.finest("top {}", h);
                    top = h.getMinY();
                }
                if (h.getMaxY() > bottom && h.getMaxY() < text.getMinY()) {
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
        buildRegularTable();
        return tableEndFound;
        */
        return false;
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
    /*
    private void buildRegularTable() {
//        System.out.printf("\n\nHoriz lines: ");
        // Remove text below the bottom horizontal line -- these are footnotes
        while (textItems.last().y < horizLines.last().getMaxY()) {
            textItems.remove(textItems.last());
        }

        double minVert = vertLines.first().y;
        for (TableCell v : vertLines) {
            if (v.y < minVert) {
                minVert = v.y;
            }
        }

        // Build list of rows indexed by their Y position
        TreeMap<Double, Integer> rows = new TreeMap<>();
        for (TableCell c : horizLines) {
            rows.put(c.y, 0);
        }
        int numRow = 0;
        double prev = pageSize.getMaxY();
        for (Iterator<Double> itRow = rows.descendingKeySet().iterator(); itRow.hasNext();) {
            double y = itRow.next();
            if (prev - y < 3) {
                itRow.remove();
            } else {
                rows.put(y, numRow++);
//                System.out.printf("%8.2f ", f);
                prev = y;
            }
        }
//        System.out.printf("\n\n");

        // Build list of columns indexed by the X position of vertLines. Where two vertLines
        // are close together, only keep the first.
        TreeMap<Double, Integer> columnXs = new TreeMap<>();
        for (TableCell c : vertLines) {
            log.finer("vertLine: {}", c);
            columnXs.put(c.x, 0);
        }
        int colNum = 0;
        log.finer("Column starts:  ");
        double prevCol = -1000;
        TreeMap<Double, Integer> columns = new TreeMap<>();
        for (double d : columnXs.keySet()) {
            if (d < prevCol + 1) {
                continue;
            }
            columns.put(d, colNum++);
            prevCol = d;

            log.finer("{} ", d);
        }
        columns.put(columns.lastKey(), -1); // Last vertical line does notr represent a column

        // Put the text from each rectangle into the appropriate column & row in the 
        // output table, based on the X & Y coordinates & width & height of the rectangle
        // Where a rectangle spans multiple rows or columns, it is copied into all the
        // cells it overlaps
        double prevY = pageSize.getMaxY();
        for (double y : rows.descendingKeySet()) {
            // Move text rectangles to this row
            TreeSet<TableCell> rowCells = new TreeSet<>();
            for (TableCell r : rectangles) {
                if (r.getMaxY() < y) {
                    continue;
                }
                if (r.getMinY() < prevY) {
                    rowCells.add(r);
                    log.finer("In row {}: {}", y, r);
                }
            }
            prevY = y;

            // For each output column, find text rectangles in this row that overlap it
            TableCell[] row = new TableCell[columns.size() - 1];
            for (double x : columns.keySet()) {
                colNum = columns.get(x);
                if (colNum == -1) {
                    break;
                }
                double maxX = columns.higherKey(x);
                row[colNum] = new TableCell(x, y, maxX - x, prevY - y);
                // NB rowCells may not be in X order
                for (TableCell r : rowCells) {
                    if (r.getMaxX() > x + 1 && r.getMinX() < maxX) {
                        row[colNum].text.addAll(r.text);
                    }
                }
                log.finer("Column {}: {}", colNum, row[colNum]);
            }

            String[] strRow = new String[row.length];
            for (int i = 0; i < row.length; i++) {
                strRow[i] = row[i].toText('\n');
            }
            table.add(strRow);
        }
    }
*/
}
