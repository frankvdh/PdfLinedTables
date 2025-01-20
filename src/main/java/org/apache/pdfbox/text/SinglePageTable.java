package org.apache.pdfbox.text;

/**
 * Scan a single page of a PDF document, and extract a table delimited by lines
 * of text that match the specified delimiters.
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
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Extract data from a single table on a single page in a PDF document.
 * The table must have a set of table headings above it.
 * All cells in a column are assumed to be the same width.
 * All cells in a row are assumed to be the same height.
 * Cells are delimited by vertical and horizontal lines.
 *
 */
public class SinglePageTable extends LinedTableStripper {

    /**
     * Constructor.
     *
     * @param file File to be read.
     * @param pageNum Number of page to read, zero-based
     * @param tableBounds Height of top and horizDivider margins
     * @param tableEnd Regex to find end of table
     * @param table Empty array for result
     * @param wrapChar Char to use for wrapping
     * @param insertSpaces Insert spaces flag
     * @param monoSpace Monospace font flag
     * @param forceRotation Force page rotation flag
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    public SinglePageTable(PDDocument doc, int pageNum, int extraRotation, boolean suppressDuplicates) throws IOException {
        this(doc, doc.getPage(pageNum), extraRotation, suppressDuplicates);
    }

    /**
     * Constructor.
     *
     * @param page Page to be read.
     * @param tableBounds Height of top and horizDivider margins
     * @param tableEnd Regex to find end of table
     * @param table Empty array for result
     * @param wrapChar Char to use for wrapping
     * @param insertSpaces Insert spaces flag
     * @param monoSpace Monospace font flag
     * @param forceRotation Force page rotation flag
     * @throws IOException If there is an error loading properties from the
     * file.
     */
    protected SinglePageTable(PDDocument doc, PDPage page, int extraRotation, boolean suppressDuplicates) throws IOException {
        super(doc, page, extraRotation, suppressDuplicates);
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
    public ArrayList<String[]> extractTable(Color headingColour, float startY, Pattern tableEnd, int numColumns) throws IOException {
        ArrayList<String[]> result = new ArrayList<>();
        processPage(getPage());
        var endY = findEndTable(startY, mediaBox.getUpperRightY(), tableEnd);
        super.appendToTable(headingColour, startY, endY, numColumns, result);
        return result;
    }
 }
