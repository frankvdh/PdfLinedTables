/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
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
 *
 * @author frank
 */
public class MultipleTablesTest {

    private static final Logger LOG = LogManager.getLogger(MultipleTablesTest.class);

    @Test
    public void IfrNavaids() throws IOException {
        LOG.debug("MultipleTables test");
        MultipleTables mt = new MultipleTables(
                new LinedTable("DME-NZZC", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("DME-NZZO", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("NDB-NZZC", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("NDB-NZZO", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("VOR-NZZC", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("VOR-NZZO", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/3_06_NZANR_Part_95_Navaids.pdf", mt,
                new int[]{43, 6, 19, 15, 16, 5},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "AUCKLAND"),
                        new TestValue(43 / 2, 1, "1731428.20E"),
                        new TestValue(43 - 1, -1, "MHZ")},
                    new TestValue[]{new TestValue(0, 0, "FALEOLO"),
                        new TestValue(6 / 2, 1, "1695457.24W"),
                        new TestValue(6 - 1, -1, "")},
                    new TestValue[]{new TestValue(0, 0, "TAUPO"),
                        new TestValue(19 / 2, 1, "1744941.50E"),
                        new TestValue(19 - 1, -1, "KHZ")},
                    new TestValue[]{new TestValue(0, 0, "AITUTAKI"),
                        new TestValue(15 / 2, 1, "1675515.00E"),
                        new TestValue(15 - 1, -1, "KHZ")},
                    new TestValue[]{new TestValue(0, 0, "AUCKLAND"),
                        new TestValue(16 / 2, 1, "1752331.05E"),
                        new TestValue(16 - 1, -1, "MHZ")},
                    new TestValue[]{new TestValue(0, 0, "FALEOLO"),
                        new TestValue(5 / 2, 1, "1594852.00W"),
                        new TestValue(5 - 1, -1, "MHZ")}});
    }

    @Test
    public void CTA() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", mt,
                new int[]{123, 1260},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZA132"),
                        new TestValue(123 / 2, 1, "9500"),
                        new TestValue(123 - 1, -1, "FL")},
                    new TestValue[]{new TestValue(0, 0, "NZA132"),
                        new TestValue(1260 / 2, 1, "GRC"),
                        new TestValue(1260 - 1, -1, "")}}
        );
    }

    @Test
    public void LFZ() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", mt,
                new int[]{51, 307},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZL160"),
                        new TestValue(51 / 2, 1, "500"),
                        new TestValue(51 - 1, -1, "[Organisation or Authority:] Central Otago Flying Club, PO Box 159, Alexandra, TEL (03) 448 9050")},
                    new TestValue[]{new TestValue(0, 0, "NZL160"),
                        new TestValue(307 / 2, 1, "384516.40S"),
                        new TestValue(307 - 1, -1, "FNT")}}
        );
    }

    @Test
    public void GAA() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true,false,  " "),
                new LinedTable("points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_11_NZANR_Part_71_General_Aviation_Areas_GAA.pdf", mt,
                new int[]{45, 311},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZG152"),
                        new TestValue(45 / 2, 1, ""),
                        new TestValue(45 - 1, -1, "[Active:] By ATC approval.")},
                    new TestValue[]{new TestValue(0, 0, "NZG152"),
                        new TestValue(311 / 2, 1, "GRC"),
                        new TestValue(311 - 1, -1, "")}}
        );
    }

    @Test
    public void VHZ() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, "\n"),
                new LinedTable("NZZC points", -1, Color.BLACK, Pattern.compile("\\*\\*\\*\\s*OF\\s*NZZC"), true, 0, 1, false, true, true, false, false, " "),
                new LinedTable("NZZO points", -1, Color.BLACK, Pattern.compile("\\*\\*\\*\\s*OF\\s*AIRSPACE"), true, 0, 1, false, true, true, false, false, " "));
        strip("AIP/1_07_NZANR_Part_71_Volcanic_Hazard_Zones_VHZ.pdf", mt,
                new int[]{5, 4, 1},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZV010"),
                        new TestValue(5 / 2, 1, "9500"),
                        new TestValue(5 - 1, -1, "[Activity or Purpose:]\nWhen standard volcanic activity level = 1\nFor increased volcanic activity the following apply when notified by NOTAM:\nActivity level 2 Radius 8 NM Upper limit FL150\nActivity level 3 Radius 16 NM Upper limit FL330\nActivity level 4 Radius 27 NM Upper limit FL480\nActivity level 5 Radius 50 NM Upper limit FL660")},
                    new TestValue[]{
                        new TestValue(0, 0, "NZV215"),
                        new TestValue(4 / 2, 1, "1753804.20E"),
                        new TestValue(4 - 1, -1, "NM")},
                    new TestValue[]{
                        new TestValue(0, 0, "NZV010"),
                        new TestValue(0, 1, "1775460.00W"),
                        new TestValue(0, -1, "NM")}}
        );
    }

    private void strip(String filename, MultipleTables mt,
            int[] size, TestValue[][] sample) throws IOException {
        LOG.info(filename);
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        mt.extractTables(file);
        for (var i = 0; i < mt.tables.length; i++) {
            var table = mt.tables[i].table;
            assertNotNull(table);
            assertEquals(size[i], table.size());
            assertEquals(sample[i][0].getValue(), table.get(sample[i][0].row)[sample[i][0].col]);
            assertEquals(sample[i][1].getValue(), table.get(sample[i][1].row)[table.get(sample[i][1].row).length / 2]);
            assertEquals(sample[i][2].getValue(), table.get(sample[i][2].row)[sample[i][2].col < 0 ? table.get(sample[i][2].row).length + sample[i][2].col : sample[i][2].col]);
        }
    }

    private class TestValue {

        final int row, col;
        final String value;

        public TestValue(int row, int col, String value) {
            this.row = row;
            this.col = col;
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
