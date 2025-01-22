/*
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 * 
 */
package org.apache.pdfbox.text;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;
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
    public IrregularLinedTable(PDDocument document, int firstPage, int extraRotation, boolean suppressDuplicates) throws IOException {
        super(document, firstPage, extraRotation, suppressDuplicates);
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
    public IrregularLinedTable(File file, int firstPage, int extraRotation, boolean suppressDuplicates) throws IOException {
        this(Loader.loadPDF(file), firstPage, extraRotation, suppressDuplicates);
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
    @Override
    public boolean appendToTable(Color headingColour, float startY, float endY, int numColumns, ArrayList<String[]> table) throws IOException {
        TreeSet<TableCell> rects = (TreeSet<TableCell>) extractCells(headingColour, startY, endY);
        if (rects == null || rects.isEmpty()) {
            return false;
        }
        buildRegularTable(rects);
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
    private TreeSet<TableCell> buildRegularTable(TreeSet<TableCell> rects) {
        // Build lists of rows & columns ordered by their X & Y position
        SortedSet<Float> rows = new TreeSet<>();
        SortedSet<Float> colSet = new TreeSet<>();
        float maxX = rects.getFirst().getMaxX();
        for (TableCell c : rects) {
            rows.add(c.getMinY());
            colSet.add(c.getMinX());
            if (c.getMaxX() > maxX) {
                maxX = c.getMaxX();
            }
        }
        rows.add(rects.getLast().getMaxY());
        colSet.add(maxX);

        TreeSet<TableCell> result = new TreeSet<>();
        Float top = rows.removeFirst();
        while (!rows.isEmpty()) {
            SortedSet<Float> cols = new TreeSet<>(colSet);
            float bottom = rows.removeFirst();
            float left = cols.removeFirst();
            while (!cols.isEmpty()) {
                float right = cols.removeFirst();
                TableCell cell = new TableCell(left, top, right, bottom);
                TableCell src = rects.ceiling(cell);

                if (src != null) {
                    cell.setText(src.getText());
                }
                result.add(cell);
                left = right;
            }
        }

        return result;
    }
}
