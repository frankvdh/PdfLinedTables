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
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Extract data from multiple tables in a PDF document.
 * Any table may be spread across multiple pages.
 * Each table must start on a new page.
 * Each page of a table must have a set of table headings above it.
 * All cells in a column are assumed to be the same width.
 * All cells in a row are assumed to be the same height.
 * Cells are delimited by vertical and horizontal lines.
 *
 * @author frank
 */
public class MultiplePageTable extends LinedTableStripper {

    final private PDDocument doc;
    private int currPage;

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
    public MultiplePageTable(File file, int firstPage, boolean forceRotation, boolean suppressDuplicates) throws IOException {
        this(Loader.loadPDF(file), firstPage, forceRotation, suppressDuplicates);
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
    protected MultiplePageTable(PDDocument document, int firstPage, boolean forceRotation, boolean suppressDuplicates) throws IOException {
        super(document.getPage(firstPage), forceRotation, suppressDuplicates);
        currPage = firstPage;
        doc = document;
    }

    /**
     * Parse all tables in the specified PDF file.
     *
     * @return all tables. Each String[] entry contains each row in the table.
     * Each row is an array of Strings, with one entry for each column in the
     * table.
     * @throws IOException on file error
     */
    public ArrayList<String[]> extractTable(Color headingColour, Color dataColour, Pattern tableEnd, int numColumns) throws IOException {
        ArrayList<String[]> result = new ArrayList<>();
        do {
            super.processPage(doc.getPage(currPage++));
        } while (currPage < doc.getNumberOfPages() && !super.appendToTable(headingColour, dataColour, tableEnd, numColumns, result));
        return result;
    }
}
