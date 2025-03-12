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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Wrapper class to contain the definition of a lined table.
 *
 * @author @author <a href="mailto:drifter.frank@gmail.com">Frank van der
 * Hulst</a>
 */
public class LinedTable {

    final private String name;

    /**
     * Get the table's name
     *
     * @return the name of the table
     */
    public String getName() {
        return name;
    }
    final Color[] headingColours;
    final Pattern endTable;
    final boolean suppressDuplicateOverlappingText;
    final int extraQuadrantRotation;
    final float tolerance;
    final boolean leadingSpaces;
    final boolean reduceSpaces;
    final boolean removeEmptyRows;
    final boolean startOnNewPage;
    final String lineEnding;
    final boolean mergeWrappedRows;

    /**
     * Constructor.
     *
     * @param name table name
     * @param headingColour table Heading Color... used to locate the table
     * @param endTable Pattern to identify the end of the table
     * @param suppressDuplicateOverlappingText flag to suppress duplication of
     * text used in PDFs for boldface
     * @param extraQuadrantRotation number of extra 90deg rotations to apply
     * @param tolerance Tolerance to use in deciding if two points are the same
     * or not
     * @param leadingSpaces Flag whether to generate leading spaces to
     * approximate the layout of text within a cell
     * @param reduceSpaces Flag whether to reduce multiple consecutive spaces to
     * a single space
     * @param removeEmptyRows Flag whether to remove rows of empty cells from
     * the table
     * @param startOnNewPage Flag whether this table starts on a new page in the
     * PDF
     * @param mergeWrappedRows Flag whether to merge rows of cells across a page
     * boundary into a single row
     * @param lineEnding String to insert to represent a line ending in a cell;
     * using a space will make all text within a cell into a single line. Using
     * a newline character will keep the line wrapping that is present in the
     * cell
     */
    public LinedTable(String name, Color headingColour,
            Pattern endTable, boolean suppressDuplicateOverlappingText,
            int extraQuadrantRotation, int tolerance, boolean leadingSpaces,
            boolean reduceSpaces, boolean removeEmptyRows, boolean startOnNewPage,
            boolean mergeWrappedRows,
            String lineEnding) {
        this.name = name;
        this.endTable = endTable;
        this.headingColours = headingColour == null ? null : new Color[]{headingColour};
        this.suppressDuplicateOverlappingText = suppressDuplicateOverlappingText;
        this.extraQuadrantRotation = extraQuadrantRotation;
        this.tolerance = tolerance;
        this.leadingSpaces = leadingSpaces;
        this.reduceSpaces = reduceSpaces;
        this.removeEmptyRows = removeEmptyRows;
        this.lineEnding = lineEnding;
        this.startOnNewPage = startOnNewPage;
        this.mergeWrappedRows = mergeWrappedRows;
    }

    /**
     * Constructor.
     *
 * @param name table name
     * @param headingColours table Heading Colours... used to locate the table
     * @param endTable Pattern to identify the end of the table
     * @param suppressDuplicateOverlappingText flag to suppress duplication of
     * text used in PDFs for boldface
     * @param extraQuadrantRotation number of extra 90deg rotations to apply
     * @param tolerance Tolerance to use in deciding if two points are the same
     * or not
     * @param leadingSpaces Flag whether to generate leading spaces to
     * approximate the layout of text within a cell
     * @param reduceSpaces Flag whether to reduce multiple consecutive spaces to
     * a single space
     * @param removeEmptyRows Flag whether to remove rows of empty cells from
     * the table
     * @param startOnNewPage Flag whether this table starts on a new page in the
     * PDF
     * @param mergeWrappedRows Flag whether to merge rows of cells across a page
     * boundary into a single row
     * @param lineEnding String to insert to represent a line ending in a cell;
     * using a space will make all text within a cell into a single line. Using
     * a newline character will keep the line wrapping that is present in the
     * cell
     */
    public LinedTable(String name, Color[] headingColours,
            Pattern endTable, boolean suppressDuplicateOverlappingText,
            int extraQuadrantRotation, int tolerance, boolean leadingSpaces,
            boolean reduceSpaces, boolean removeEmptyRows, boolean startOnNewPage,
            boolean mergeWrappedRows,
            String lineEnding) {
        this.name = name;
        this.endTable = endTable;
        this.headingColours = headingColours;
        this.suppressDuplicateOverlappingText = suppressDuplicateOverlappingText;
        this.extraQuadrantRotation = extraQuadrantRotation;
        this.tolerance = tolerance;
        this.leadingSpaces = leadingSpaces;
        this.reduceSpaces = reduceSpaces;
        this.removeEmptyRows = removeEmptyRows;
        this.lineEnding = lineEnding;
        this.startOnNewPage = startOnNewPage;
        this.mergeWrappedRows = mergeWrappedRows;
    }

    /**
     * Extract a table from the PDF document contained in the file, starting at the give page
     *
     * @param file The file containing the document
     * @param firstPageNo The first page of the table
     * @return A list of String arrays, with on array for each row of the table, and one String for each cell within therow
     * @throws IOException if there is a problem reading the file
     */
    public ArrayList<String[]> extractTable(File file, int firstPageNo) throws IOException {
        try (var stripper = new LinedTableStripper(file)) {
            stripper.setDefinition(this);
            return stripper.extractTable(firstPageNo, 1, endTable, headingColours);
        }
    }
}
