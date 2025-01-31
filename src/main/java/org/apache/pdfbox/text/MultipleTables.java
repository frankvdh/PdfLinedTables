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

/**
 * Extract data from specified tables in the PDF file.
 *
 * The layout of each table is defined by a LinedTable object.
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MultipleTables {

    private static final Logger LOG = LogManager.getLogger(MultipleTables.class);
    final LinedTable[] tables;

    /**
     *
     * @param marginHeight Height of footer of each page.
     * @param file
     * @param tabs Table layouts
     */
    @SuppressWarnings("unchecked")
    public MultipleTables(LinedTable... tabs) {
        tables = tabs;
    }

    /**
     * Parse all tables in the specified PDF file.
     *
     * @return all tables. Each String[] entry contains each row in the table.
     * Each row is an array of Strings, with one entry for each column in the
     * table.
     * @throws IOException
     */
    public ArrayList<ArrayList<String[]>> extractTables(File file) throws IOException {
        var results = new ArrayList<ArrayList<String[]>>(tables.length);
        try (var stripper = new LinedTableStripper(file, tables[0].extraQuadrantRotation, tables[0].suppressDuplicateOverlappingText, tables[0].leadingSpaces, tables[0].reduceSpaces, tables[0].removeEmptyRows, tables[0].tolerance, tables[0].lineEnding)) {
            var nextPage = tables[0].firstPageNo;
            var startY = 0f;
            for (var t : tables) {
                LOG.debug("Reading " + t.name);
                t.table = stripper.extractTable(t.firstPageNo < 1 ? nextPage : t.firstPageNo, startY, t.endTable, t.headingColour);
                if (t.table == null || t.table.isEmpty()) {
                    LOG.error("No table for {}, page {}", t.name, nextPage);
                    results.add(null);
                } else {
                    results.add(t.table);
                    assert t.table.get(0).length > 2 : "Columns not found for PDF table " + t.name + ", page " + nextPage;
                }
                startY = 0f;
                nextPage = stripper.getCurrPageNum() + 1;
            }
        }
        return results;
    }
}
