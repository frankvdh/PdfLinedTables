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
     * @param name
     * @param pageNo Page to be read -- 1-based.
     * @param headingColour table Heading Color... used to locate the table
     * @param endTable
     * @param data
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
     * @param name
     * @param pageNo Page to be read -- 1-based.
     * @param headingColour table Heading Color... used to locate the table
     * @param endTable
     * @param data
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
     *
     * @param file
     * @return
     * @throws IOException
     */
    public ArrayList<String[]> extractTable(File file, int firstPageNo) throws IOException {
        try (LinedTableStripper stripper = new LinedTableStripper(file)) {
            stripper.setDefinition(this);
            var table = stripper.extractTable(firstPageNo, 1, endTable, headingColours);
        return table;
        }
    }
}
