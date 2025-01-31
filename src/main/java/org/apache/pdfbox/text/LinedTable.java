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

    final String name;
    final Color headingColour;
    final Pattern endTable;
    final int firstPageNo;
    final boolean suppressDuplicateOverlappingText;
    final int extraQuadrantRotation;
    final float tolerance;
    final boolean leadingSpaces;
    final boolean reduceSpaces;
    final boolean removeEmptyRows;
    final String lineEnding;

    /**
     * ArrayList of rows, one for each row in the result table.
     *
     * All rows are the same size. Rows which contain nothing but empty Strings
     * are ignored. Each row consists of an array of cells. A cell is a single
     * text string. Optionally, if there are multiple lines of text in the cell,
     * they may be separated by a newline character.
     */
    public ArrayList<String[]> table;

    /**
     * Constructor.
     *
     * @param name
     * @param pageNo Page to be read -- 1-based.
     * @param headingColour table Heading Color... used to locate the table
     * @param endTable
     * @param data
     */
    public LinedTable(String name, int pageNo, Color headingColour, 
            Pattern endTable, boolean suppressDuplicateOverlappingText, 
            int extraQuadrantRotation, int tolerance, boolean leadingSpaces, 
            boolean reduceSpaces, boolean removeEmptyRows, String lineEnding) {
        this.name = name;
        table = new ArrayList<>(0);
        this.endTable = endTable;
        this.headingColour = headingColour;
        this.firstPageNo = pageNo;
        this.suppressDuplicateOverlappingText = suppressDuplicateOverlappingText;
        this.extraQuadrantRotation = extraQuadrantRotation;
        this.tolerance = tolerance;
        this.leadingSpaces = leadingSpaces;
        this.reduceSpaces = reduceSpaces;
        this.removeEmptyRows = removeEmptyRows;
        this.lineEnding = lineEnding;
    }

    /**
     *
     * @param file
     * @return
     * @throws IOException
     */
    public ArrayList<String[]> extractTable(File file) throws IOException {
        try (org.apache.pdfbox.text.LinedTableStripper pageStripper = new LinedTableStripper(file, extraQuadrantRotation, suppressDuplicateOverlappingText, leadingSpaces, reduceSpaces, removeEmptyRows, tolerance, lineEnding)) {
            table = pageStripper.extractTable(firstPageNo, 1, endTable, headingColour);
        }
        return table;
    }

    @Override
    public String toString() {
        if (table == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        table.stream().map((row) -> {
            for (String c : row) {
                result.append(c).append(", ");
            }
            return row;
        }).forEachOrdered((_item) -> {
            result.append("\n");
        });
        return result.toString();
    }
}
