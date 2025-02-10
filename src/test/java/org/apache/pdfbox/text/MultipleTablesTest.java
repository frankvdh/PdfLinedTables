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
                new LinedTable("DME-NZZC", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("DME-NZZO", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("NDB-NZZC", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("NDB-NZZO", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("VOR-NZZC", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("VOR-NZZO", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/3_06_NZANR_Part_95_Navaids.pdf", 1, mt,
                new int[]{43, 6, 19, 14, 16, 5},new int[]{7, 7, 6, 6, 6, 6},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "AUCKLAND"),
                        new TestValue(21, 0, "KAITAIA"),
                        new TestValue(21, 1, "KT"),
                        new TestValue(21, 4, "105X"),
                        new TestValue(43 - 1, -1, "MHZ")},
                    new TestValue[]{new TestValue(0, 0, "FALEOLO"),
                        new TestValue(6 / 2, 1, "NU"),
                        new TestValue(6 - 1, -1, "")},
                    new TestValue[]{new TestValue(0, 0, "TAUPO"),
                        new TestValue(19 / 2, 1, "NL"),
                        new TestValue(19 - 1, -1, "KHZ")},
                    new TestValue[]{new TestValue(0, 0, "AITUTAKI"),
                        new TestValue(14 / 2, 1, "NF"),
                        new TestValue(14 - 1, -1, "KHZ")},
                    new TestValue[]{new TestValue(0, 0, "AUCKLAND"),
                        new TestValue(16 / 2, 1, "OH"),
                        new TestValue(16 - 1, -1, "MHZ")},
                    new TestValue[]{new TestValue(0, 0, "FALEOLO"),
                        new TestValue(5 / 2, 1, "RG"),
                        new TestValue(5 - 1, -1, "MHZ")}});
    }

    @Test
    public void CTA() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("CTA zones", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, false, " "),
                new LinedTable("CTA points", new Color[]{Color.BLACK, new Color(0x666666)}, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, false, " "));
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", 1, mt,
                new int[]{125, 1330},new int[]{8, 10},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZA132"),
                        new TestValue(123 / 2, 1, "NAPIER"),
                        new TestValue(123 - 1, -1, "FT")},
                    new TestValue[]{new TestValue(0, 0, "NZA132"),
                        new TestValue(1330 / 2, 5, "GRC"),
                        new TestValue(1330 - 1, -1, ""),
                    new TestValue(22, 0, "NZA137"),
                        new TestValue(22, 1, "6")}}
        );
    }

    @Test
    public void LFZ() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("LFZ zones", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("LFZ points", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, false, " "));
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", 1, mt,
                new int[]{52, 322},new int[]{5, 6},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZL160"),
                        new TestValue(52 / 2, 1, "MOANATUATUA"),
                        new TestValue(52 - 1, -1, "[Organisation or Authority:] Central Otago Flying Club, PO Box 159, Alexandra, TEL (03) 448 9050")},
                    new TestValue[]{new TestValue(0, 0, "NZL160"),
                        new TestValue(322 / 2, 3, "410140.20S"),
                        new TestValue(322 - 1, -1, "FNT")}}
        );
    }

    @Test
    public void GAA() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("GAA zones", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("GAA points", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, true, " "));
        strip("AIP/1_11_NZANR_Part_71_General_Aviation_Areas_GAA.pdf", 1, mt,
                new int[]{45, 311},new int[]{8, 10},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZG152"),
                        new TestValue(45 / 2, 1, "FEILDING"),
                        new TestValue(45 - 1, -1, "[Active:] By ATC approval.")},
                    new TestValue[]{new TestValue(0, 0, "NZG152"),
                        new TestValue(311 / 2, 5, "GRC"),
                        new TestValue(31 - 1, -1, "")}}
        );
    }

    @Test
    public void MBZ() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("MBZ zones", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, " "),
                new LinedTable("MBZ points", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, false, " "));
        strip("AIP/1_12_NZANR_Part_71_Mandatory_Broadcast_Zones_MBZ.pdf", 1, mt,
                new int[]{48, 355},new int[]{8, 10},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZB169"),
                        new TestValue(30, 1, "TAUPO MBZ"),
                        new TestValue(19, 0, "NZB269"),
                        new TestValue(19, -1, "HJ")},
                    new TestValue[]{new TestValue(0, 0, "NZB169_A"),
                        new TestValue(355 / 2, 0, "NZB371"),
                        new TestValue(355 / 2, 1, "12"),
                        new TestValue(355 / 2, 2, "following SH49 from"),
                        new TestValue(4, -1, "NM")}}
        );
    }

    @Test
    public void NZD() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("NZD zones", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, " "),
                new LinedTable("NZD points", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, false, " "));
        strip("AIP/1_10_NZANR_Part_71_Danger_Areas_D.pdf", 1, mt,
                new int[]{58, 163},new int[]{6, 10},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZD120"),
                        new TestValue(30, 1, "MAUI PLATFORM B"),
                        new TestValue(19, 0, "NZD321"),
                        new TestValue(19, 2, "1000")},
                    new TestValue[]{new TestValue(0, 0, "NZD120"),
                        new TestValue(163 / 2, 0, "NZD715"),
                        new TestValue(163 / 2, 1, "1"),
                        new TestValue(163 / 2, 3, "421838.10S"),
                        new TestValue(2, 8, "0.54")}}
        );
    }

    @Test
    public void NZR() throws IOException {
        MultipleTables mt = new MultipleTables(
                    new LinedTable("NZR names", new Color[]{Color.BLACK, new Color(0x666666)}, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, "\n"),
                    new LinedTable("NZR NZZC boundaries", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, true, "\n"),
                    new LinedTable("NZR NZZO boundaries", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, true, "\n"));
        strip("AIP/1_05_NZANR_Part_71_Restricted_Areas_R.pdf", 1, mt,
                new int[]{21, 73, 56},new int[]{5, 10, 10},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZR001"),
                        new TestValue(0, 1, "ANTIPODES ISLANDS"),
                        new TestValue(19, 0, "NZR902"),
                        new TestValue(20, 2, "2000"),
                        new TestValue(20, 0, "NZR905")},
                    new TestValue[]{new TestValue(0, 0, "NZR100"),
                        new TestValue(21, 0, "NZR107"),
                        new TestValue(21, 1, "6"),
                        new TestValue(21, 3, "360837.00S"),
                        new TestValue(73-1, 0, "NZR905")},
                    new TestValue[]{new TestValue(0, 0, "NZR001"),
                        new TestValue(21, 0, "NZR002"),
                        new TestValue(21, 1, "11"),
                        new TestValue(21, 3, "505649.70S"),
                        new TestValue(56-1, 0, "NZR005")}}
        );
    }

    @Test
    public void VHZ() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("zones", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, "\n"),
                new LinedTable("NZZC points", Color.BLACK, Pattern.compile("\\*\\*\\*\\s*OF\\s*NZZC"), true, 0, 1, false, true, true, false, false, " "),
                new LinedTable("NZZO points", Color.BLACK, Pattern.compile("\\*\\*\\*\\s*OF\\s*AIRSPACE"), true, 0, 1, false, true, true, false, false, " "));
        strip("AIP/1_07_NZANR_Part_71_Volcanic_Hazard_Zones_VHZ.pdf", 1, mt,
                new int[]{5, 4, 1},new int[]{5, 6, 6},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZV010"),
                        new TestValue(5 / 2, 2, "9500"),
                        new TestValue(5 - 1, -1, "[Activity or Purpose:]\nWhen standard volcanic activity level = 1\nFor increased volcanic activity the following apply when notified by NOTAM:\nActivity level 2 Radius 8 NM Upper limit FL150\nActivity level 3 Radius 16 NM Upper limit FL330\nActivity level 4 Radius 27 NM Upper limit FL480\nActivity level 5 Radius 50 NM Upper limit FL660")},
                    new TestValue[]{
                        new TestValue(0, 0, "NZV215"),
                        new TestValue(4 / 2, 3, "1753804.20E"),
                        new TestValue(4 - 1, -1, "NM")},
                    new TestValue[]{
                        new TestValue(0, 0, "NZV010"),
                        new TestValue(0, 3, "1775460.00W"),
                        new TestValue(0, -1, "NM")}}
        );
    }

    private void strip(String filename, int firstPageNo, MultipleTables mt,
            int[] size, int[] numCols, TestValue[][] sample) throws IOException {
        LOG.info(filename);
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        var results = mt.extractTables(file, firstPageNo);
        for (var i = 0; i < mt.tables.size(); i++) {
            LOG.info("Table {}", mt.tables.get(i).getName());
            var table = results.get(i);
            assertNotNull(table);
            assertEquals(size[i], table.size());
            assertEquals(numCols[i], table.getFirst().length);
            for (var s : sample[i]) {
                assertEquals(s.getValue(), table.get(s.row)[s.col < 0 ? table.get(s.row).length + s.col : s.col]);
            }
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
