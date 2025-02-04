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
                new int[]{43, 6, 19, 14, 16, 5},
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
                new LinedTable("CTA zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("CTA points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", mt,
                new int[]{125, 1330},
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
                new LinedTable("LFZ zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("LFZ points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", mt,
                new int[]{52, 322},
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
                new LinedTable("GAA zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "),
                new LinedTable("GAA points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_11_NZANR_Part_71_General_Aviation_Areas_GAA.pdf", mt,
                new int[]{45, 311},
                new TestValue[][]{
                    new TestValue[]{new TestValue(0, 0, "NZG152"),
                        new TestValue(45 / 2, 1, "FEILDING"),
                        new TestValue(45 - 1, -1, "[Active:] By ATC approval.")},
                    new TestValue[]{new TestValue(0, 0, "NZG152"),
                        new TestValue(311 / 2, 5, "GRC"),
                        new TestValue(311 - 1, -1, "")}}
        );
    }

    @Test
    public void MBZ() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("MBZ zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, " "),
                new LinedTable("MBZ points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_12_NZANR_Part_71_Mandatory_Broadcast_Zones_MBZ.pdf", mt,
                new int[]{48, 355},
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
                new LinedTable("NZD zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, " "),
                new LinedTable("NZD points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, " "));
        strip("AIP/1_10_NZANR_Part_71_Danger_Areas_D.pdf", mt,
                new int[]{58, 163},
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
    public void VHZ() throws IOException {
        MultipleTables mt = new MultipleTables(
                new LinedTable("zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, "\n"),
                new LinedTable("NZZC points", -1, Color.BLACK, Pattern.compile("\\*\\*\\*\\s*OF\\s*NZZC"), true, 0, 1, false, true, true, false, false, " "),
                new LinedTable("NZZO points", -1, Color.BLACK, Pattern.compile("\\*\\*\\*\\s*OF\\s*AIRSPACE"), true, 0, 1, false, true, true, false, false, " "));
        strip("AIP/1_07_NZANR_Part_71_Volcanic_Hazard_Zones_VHZ.pdf", mt,
                new int[]{5, 4, 1},
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

    private void strip(String filename, MultipleTables mt,
            int[] size, TestValue[][] sample) throws IOException {
        LOG.info(filename);
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        mt.extractTables(file);
        for (var i = 0; i < mt.tables.length; i++) {
            LOG.info("Table {}", mt.tables[i].name);
            var table = mt.tables[i].table;
            assertNotNull(table);
            assertEquals(size[i], table.size());
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
