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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Wrapper class to contain the definition of a lined table.
 *
 * @author @author <a href="mailto:drifter.frank@gmail.com">Frank van der
 * Hulst</a>
 */
public class LinedTable {

    private final static Logger LOG = LogManager.getLogger(LinedTable.class.getSimpleName());

    final String name;
    private LinedTableStripper pageStripper;
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
    final int numColumns;

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
     * @param heading table Heading Pattern... used to locate the second table
     * when two tables are on the same page
     * @param pageNo Page to be read -- 1-based.
     * @param endTable
     * @param data
     */
    public LinedTable(String name, int pageNo, Color headingColour, Pattern endTable, boolean suppressDuplicateOverlappingText, int extraQuadrantRotation, int tolerance, boolean leadingSpaces, boolean reduceSpaces, boolean removeEmptyRows, String lineEnding, int numColumns) {
        this.name = name;
        table = new ArrayList<>(0);
        this.endTable = endTable;
        this.headingColour = headingColour;
        this.firstPageNo = pageNo - 1;
        this.suppressDuplicateOverlappingText = suppressDuplicateOverlappingText;
        this.extraQuadrantRotation = extraQuadrantRotation;
        this.tolerance = tolerance;
        this.leadingSpaces = leadingSpaces;
        this.reduceSpaces = reduceSpaces;
        this.removeEmptyRows = removeEmptyRows;
        this.lineEnding = lineEnding;
        this.numColumns = numColumns;
    }

    public ArrayList<String[]> extractTable(File file) throws IOException {
        pageStripper = new LinedTableStripper(file, extraQuadrantRotation, suppressDuplicateOverlappingText, leadingSpaces, reduceSpaces, removeEmptyRows, tolerance, lineEnding);
        pageStripper.extractTable(firstPageNo, headingColour, 0, endTable, numColumns);
        assert table.get(0).length > 2 : "Columns not found for PDF table " + name + ", page " + firstPageNo;
        pageStripper.close();
        pageStripper = null;
        return table;
    }

    public void close() {
        if (pageStripper != null) {
            try {
                pageStripper.close();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
            pageStripper = null;
        }
        table = null;
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
