package org.apache.pdfbox.text;

/**
 * Extract data from specified tables in the PDF file.
 *
 * The layout of each table is defined by a PDFTable object.
 *
 * Text in a cell may be wrapped across several lines, so Y-coordinates are not
 * used to identify when a row begins or end. The multiple lines of a wrapped
 * cell are unwrapped and returned as a single String. To accomplish this, a
 * 'full' row is recognized either because the last column entry matches the
 * 'lineEnd' Pattern, or because the 'requiredColumn' column is not empty and
 * the X-coordinate is now to the left of 'returnColumnXCoord'... this may be
 * the right-hand side of the first (or other) column, or the left-hand side of
 * the 'requiredColumn' column.
 *
 * The end of the entire table is recognized by the 'lineEnd' String beginning a
 * line.
 *
 * Multiple tables with different layouts can be specified to be extracted
 * consecutively from a single PDF file.
 *
 * A single table may span multiple pages, or there can be several tables
 * vertically on one page.
 *
 * This is by no means a complete solution to extracting data tables from PDF
 * files.
 *
 * @see PDFTable
 *
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 */
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.SortedSet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Extract data from multiple tables in a PDF document. Any table may be spread
 * across multiple pages. Each table must start on a new page. Each page of a
 * table must have a set of table headings above it. All cells in a column are
 * assumed to be the same width. All cells in a row are assumed to be the same
 * height. Cells are delimited by vertical and horizontal lines.
 *
 * @author frank
 */
public class RegularTable extends LinedTableStripper {

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
    public RegularTable(File file, int extraRotation, boolean suppressDuplicates) throws IOException {
        this(Loader.loadPDF(file), extraRotation, suppressDuplicates);
    }

    /**
     * Constructor.
     *
     * @param document DPDF document to be read.
     * @param firstPage Number of page to read, zero-based
     * @param forceRotation Force page rotation flag
     * @param suppressDuplicates Ignore duplicate text used for bold printing
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    protected RegularTable(PDDocument document, int extraRotation, boolean suppressDuplicates) throws IOException {
        super(document, extraRotation, suppressDuplicates, 3);
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
}
