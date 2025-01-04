/*
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 * 
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Extract irregular tables
 *
 * @author frank
 */
public class IrregularLinedTable extends MultiplePageTable {

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
    public IrregularLinedTable(PDDocument document, int firstPage, boolean forceRotation, boolean suppressDuplicates) throws IOException {
        super(document, firstPage, forceRotation, suppressDuplicates);
    }

    /**
     * Constructor.
     *
     * @param file File to be read.
     * @param firstPage Number of page to read, zero-based
     * @param forceRotation Force page rotation flag
     * @param suppressDuplicates Ignore duplicate text used for bold printing
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public IrregularLinedTable(File file, int firstPage, boolean forceRotation, boolean suppressDuplicates) throws IOException {
        this(Loader.loadPDF(file), firstPage, forceRotation, suppressDuplicates);
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
    
    @Override
    public ArrayList<String[]> extractTable(Color headingColour, Color dataColour, Pattern tableEnd, int numColumns) throws IOException {
        super.extractCells(headingColour, dataColour, tableEnd);
        buildRegularTable();
    }

    /**
     * Append the section of the table matching the criteria on the current page.
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
}
