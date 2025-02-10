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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Extract data from specified multiple tables in the PDF file.
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
 * Tables may start on a new page, or they may be further down the same page.
 *
 * @see PDFTable
 *
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 */
public class MultipleTables {

    private static final Logger LOG = LogManager.getLogger(MultipleTables.class);
    final ArrayList<LinedTable> tables = new ArrayList<>();

    /**
     *
     * @param marginHeight Height of footer of each page.
     * @param file
     * @param tabs Table layouts
     */
    @SuppressWarnings("unchecked")
    public MultipleTables(LinedTable... tabs) {
        tables.addAll(Arrays.asList(tabs));
    }

    public LinedTable getTable(int n) {
        return tables.get(n);
    }

    public int numTables() {
        return tables.size();
    }

    public void addTable(LinedTable tab) {
        tables.add(tab);
    }
    
    /**
     * Parse all tables in the specified PDF file.
     *
     * @return all tables. Each String[] entry contains each row in the table.
     * Each row is an array of Strings, with one entry for each column in the
     * table.
     * @throws IOException
     */
    public ArrayList<ArrayList<String[]>> extractTables(File file, int firstPageNo) throws IOException {
        var result = new ArrayList<ArrayList<String[]>>(tables.size());
        try (var stripper = new LinedTableStripper(file)) {
            // Page numbers are 1-based here
            var nextPage = firstPageNo + (tables.getFirst().startOnNewPage ? -1 : 0);
            float startY;
            for (var t : tables) {
                LOG.info("Reading " + t.getName());
                stripper.setDefinition(t);
                if (t.startOnNewPage) {
                    startY = 0f;
                    nextPage++;
                } else {
                    startY = stripper.getTableBottom();
                }
                result.add(stripper.extractTable(nextPage, startY, t.endTable, t.headingColours));
                nextPage = stripper.getCurrPageNum();
            }
        }
        return result;
    }
}
