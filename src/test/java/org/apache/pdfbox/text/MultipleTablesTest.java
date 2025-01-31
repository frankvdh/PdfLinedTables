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
                new LinedTable("DME-NZZC", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "),
                new LinedTable("DME-NZZO", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "),
                new LinedTable("NDB-NZZC", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "),
                new LinedTable("NDB-NZZO", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "),
                new LinedTable("VOR-NZZC", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "),
                new LinedTable("VOR-NZZO", -1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "));
        strip("AIP/3_06_NZANR_Part_95_Navaids.pdf", mt,
                new int[]{43, 6, 19, 15, 16, 5},
                new String[][]{
                    new String[]{"AUCKLAND", "1731428.20E", "MHZ"},
                    new String[]{"FALEOLO", "1695457.24W", ""},
                    new String[]{"TAUPO", "1744941.50E", "KHZ"},
                    new String[]{"AITUTAKI", "1675515.00E", "KHZ"},
                    new String[]{"AUCKLAND", "1752331.05E", "MHZ"},
                    new String[]{"FALEOLO", "1594852.00W", "MHZ"},});
    }

    @Test
    public void CTA() throws IOException {
       MultipleTables mt = new MultipleTables(
                new LinedTable("zones", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "),
                new LinedTable("points", -1, new Color(0x66, 0x66, 0x66), Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, " "));
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", mt,
                new int[]{123, 1260},
                new String[][]{
                new String[]{"NZA132", "9500", "FL"},
                new String[]{"NZA132", "GRC", ""}}
                );
    }

    private void strip(String filename, MultipleTables mt,
            int[] size, String[][] sample) throws IOException {
        LOG.info(filename);
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        var results = mt.extractTables(file);
        for (var i = 0; i < mt.tables.length; i++) {
            var table = mt.tables[i].table;
            assertNotNull(table);
            assertEquals(size[i], table.size());
            assertEquals(sample[i][0], table.get(0)[0]);
            assertEquals(sample[i][1], table.get(size[i] / 2)[table.get(size[i]/2).length / 2]);
            assertEquals(sample[i][2], table.getLast()[table.getLast().length - 1]);
        }
    }
}
