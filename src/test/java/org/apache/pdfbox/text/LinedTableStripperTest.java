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
import java.nio.file.Paths;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 * Tests for LinedTableStripper class.
 *
 * PDF documents in the AIP folder are copyright by NZ Civil Aviation Authority
 * so can't be distributed. They can be downloaded from https://www.aip.net.nz/.
 * These documents are updated approximately monthly.
 * .
 * @author @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 */
public class LinedTableStripperTest {

    private static final Logger LOG = LogManager.getLogger(LinedTableStripperTest.class);

    @Test
    public void oneRow() throws IOException {
        strip("OneRow.pdf", 1, Color.BLACK, null, 0, true, true, "\n", 1, 
                new TestValue(0, 0, "data1"), 
                new TestValue(1/2, 1, "data2"), 
                new TestValue(1-1, -1, "data2"));
    }

    @Test
    public void Coordinates_firstPage() throws IOException {
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 1, Color.BLACK, null, 0, false, true, "\n", 19, 
                new TestValue(0, 0, "ALEXANDRA"), 
                new TestValue(19/2, 1, "HP"), 
                new TestValue(19-1, -1, "1753031.00E"));
    }

    @Test
    public void Coordinates() throws IOException {
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), 0, false, true, "\n", 215, 
                new TestValue(0, 0, "ALEXANDRA"), 
                new TestValue(215/2, 1, "HP"), 
                new TestValue(215-1, -1, "1735213.00E"));
    }

    @Test
    public void LFZ() throws IOException {
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", 1, Color.BLACK, null, 0, false, true, "\n",
                18, new TestValue(0, 0, "NZL160"), 
                new TestValue(18/2, 1, "THAMES"), 
                new TestValue(18-1, -1, "[Organisation or Authority:] New Plymouth Aero Club,  New Plymouth Airport, RD3, New Plymouth 4373, TEL (06) 755 0500"));
    }

    @Test
    public void QNH() throws IOException {
        strip("AIP/1_14_NZANR_Part_71_QNH_Zones.pdf", 1, Color.BLACK, null, 0, false, true, "\n",
                12, new TestValue(0, 0, "NZQ185"), 
                new TestValue(12/2, 1, "WEST COAST AREA QNH ZONE"), 
                new TestValue(12-1, -1, ""));
    }

    @Test
    public void PLA() throws IOException {
        strip("AIP/1_04_NZANR_Part_71_Parachute_Landing_Areas_PLA.pdf", 1, Color.BLACK, null, 0, false, true, " ",
                16, new TestValue(0, 0, "NZP114"), 
                new TestValue(16/2, 1, "HAMILTON AERODROME"), 
                new TestValue(16-1, -1, "[Organisation or Authority:] Using agency: Skydive Queenstown Ltd (trading as Nzone Skydive), PO Box 554, Queenstown 9348, TEL (03) 442 2256"));
    }

    @Test
    public void GEN_3_7_firstPage() throws IOException {
        strip("AIP/GEN_3.7.pdf", 1, new Color(223, 223, 223),
                null, 1, false, true, "\n", 8,
                new TestValue(0, 0, "ALL AIRCRAFT"), 
                new TestValue(8/2, 1, "FIS"), 
                new TestValue(8-1, -1, "AVBL for IFR ACFT on GND at\nNZAS"));
    }

    @Test
    public void GEN_3_7_table1() throws IOException {
        strip("AIP/GEN_3.7.pdf", 1, new Color(223, 223, 223),
                Pattern.compile("Table\\s?GEN\\s?3.7-2"), 1, false, true, "\n",
                259, new TestValue(0, 0, "ALL AIRCRAFT"), 
                new TestValue(259/2, 1, "FIS"), 
                new TestValue(259-1, -1, "Nominal range at 10,000 ft: 80 NM\nNote: Terrain shielding may reduce\nAVBL range. ELEV 110 ft"));
    }

    @Test
    public void CtaZones() throws IOException {
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", 1, Color.BLACK,
                Pattern.compile("\\*\\*\\*"), 0, false, true, " ",
                123, new TestValue(0, 0, "NZA132"), 
                new TestValue(123/2, 1, "NAPIER"), 
                new TestValue(123-1, -1, "FL"));
    }

    @Test
    public void CtaPoints() throws IOException {
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", 6, new Color(0x66, 0x66, 0x66),
                Pattern.compile("\\*\\*\\*"), 0, false, true, " ",
                1260, 
                new TestValue(0, 0, "NZA132"), 
                new TestValue(1260/2, 1, "1"), 
                new TestValue(1260-1, -1, ""));
    }

    private void strip(String filename, int pageNo, Color hdgColor, Pattern tableEnd, int extraRotation, boolean leadingSpaces, boolean reduceSpaces, String lineEnding,
            int size, TestValue first, TestValue middle, TestValue last) throws IOException {
        LOG.info(filename);
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        var stripper = new LinedTableStripper(file, extraRotation, true, leadingSpaces, reduceSpaces, false, 1, lineEnding);
        var table = stripper.extractTable(pageNo, extraRotation, tableEnd, hdgColor);
        assertNotNull(table);
        assertEquals(size, table.size());
        assertEquals(first.getValue(), table.get(first.row)[first.col]);
        assertEquals(middle.getValue(), table.get(middle.row)[middle.col]);
        assertEquals(last.getValue(), table.get(last.row)[last.col < 0 ? table.get(last.row).length + last.col: last.col]);
    }
    

    private class TestValue {
        final int row, col;
        final String value;
        
    public    TestValue(int row, int col, String value) {
            this.row = row;
            this.col = col;
            this.value = value;
        }

public String getValue() {
        return value;
    }
    }
}
