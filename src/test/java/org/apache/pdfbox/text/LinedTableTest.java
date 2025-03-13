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
public class LinedTableTest {

    private static final Logger LOG = LogManager.getLogger(LinedTableTest.class);

    /**
     * Test reading single one row by 2 column PDF
     *
     * @throws IOException
     */
    @Test
    public void oneRow() throws IOException {
        var def = new LinedTable("one row", Color.BLACK, null, true, 0, 1, false, true, true, true, false, "\n");
        strip("OneRow.pdf", 1, def, 1, 
                new TestValue(0, 0, "data1"), 
                new TestValue(1/2, 1, "data2"), 
                new TestValue(1-1, -1, "data2"));
    }

    /**
     * Test reading LFZ PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void LFZ() throws IOException {
        var lfz = new LinedTable("LFZ", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, false, true, false, " ");
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", 1, lfz, 52, 
                new TestValue(0, 0, "NZL160"),
                new TestValue(52/2, 1, "MOANATUATUA"), 
                new TestValue(52-1, -1, "[Organisation or Authority:] Central Otago Flying Club, PO Box 159, Alexandra, TEL (03) 448 9050"));
}

    /**
     * Test reading runway table from NZMJ PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void NZMJ() throws IOException {
        var coords = new LinedTable("NZMJ", Color.BLACK, null, true, 0, 1, false, true, true, true, false, "\n");
        strip("AIP/NZMJ.pdf", 1, coords, 3, 
                new TestValue(0, 0, "RWY"), 
                new TestValue(3/2, 1, "Gr"), 
                new TestValue(3-1, -1, "564"));
    }
    
    /**
     * Test reading runway table from NZMF PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void NZMF() throws IOException {
        var coords = new LinedTable("NZMF", Color.BLACK, null, true, 0, 1, false, true, true, true, false, "\n");
        strip("AIP/NZMF_51.1_52.1.pdf", 2, coords, 3, 
                new TestValue(0, 0, "RWY"), 
                new TestValue(3/2, 1, "B"), 
                new TestValue(3-1, -1, "767"));
    }
    
    /**
     * Test reading runway table from NZPH PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void NZPH() throws IOException {
        var coords = new LinedTable("NZPH", Color.BLACK, null, true, 0, 1, false, true, true, true, false, "\n");
        strip("AIP/NZPH_51.1_52.1.pdf", 2, coords, 3, 
                new TestValue(0, 0, "RWY"), 
                new TestValue(3/2, 1, "Gr"),                 
                new TestValue(3-1, -1, "860"));
}
   
    /**
     * Test reading runway table from NZKT PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void NZKT() throws IOException {
        var coords = new LinedTable("NZKT", Color.BLACK, null, true, 0, 1, false, true, true, true, false, "\n");
        strip("AIP/NZKT_51.1_52.1.pdf", 2, coords, 4, 
                new TestValue(0, 0, "RWY"), 
                new TestValue(4/2, 1, "B"),                 
                new TestValue(4-1, -1, "1170\n1018"));
}

    /**
     * Test reading first page only of coordinates PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void Coordinates_firstPage() throws IOException {
        var def = new LinedTable("Coordinates 1st page", Color.BLACK, null, true, 0, 1, false, true, true, true, false, "\n");
         strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 1, def, 19, 
                new TestValue(0, 0, "ALEXANDRA"), 
                new TestValue(19/2, 1, "HP"), 
                new TestValue(19-1, -1, "1753031.00E"));
    }

    /**
     * Test reading all pages of coordinates PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void Coordinates() throws IOException {
        var def = new LinedTable("Coordinates", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, false, "\n");
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 1, def, 218, 
                new TestValue(0, 0, "ALEXANDRA"), 
                new TestValue(218/2, 1, "HP"), 
                new TestValue(218-1, -1, "1735213.00E"));
    }

    /**
     * Test reading QNH PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void QNH() throws IOException {
        var def = new LinedTable("QNH", Color.BLACK, null, true, 0, 1, false, true, true, true, false, "\n");
        strip("AIP/1_14_NZANR_Part_71_QNH_Zones.pdf", 1, def,
                12, new TestValue(0, 0, "NZQ185"), 
                new TestValue(12/2, 1, "WEST COAST AREA QNH ZONE"), 
                new TestValue(12-1, -1, ""));
    }

    /**
     * Test reading VRP PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void VRP() throws IOException {
        var def = new LinedTable("VRP", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, " ");
        strip("AIP/1_06_NZANR_Part_71_Visual_Reporting_Points_VRP.pdf", 1, def,
                634, new TestValue(0, 0, "ADA"), 
                new TestValue(634/2, 0, "MISSION BAY"), 
                new TestValue(634-1, -1, "1674858.20E"));
    }

    /**
     * Test reading PLA PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void PLA() throws IOException {
        var def = new LinedTable("PLA", Color.BLACK, null, true, 0, 1, false, true, true, true, false, " ");
        strip("AIP/1_04_NZANR_Part_71_Parachute_Landing_Areas_PLA.pdf", 1, def,
                16, new TestValue(0, 0, "NZP114"), 
                new TestValue(16/2, 1, "HAMILTON AERODROME"), 
                new TestValue(16-1, -1, "[Organisation or Authority:] Using agency: Skydive Queenstown Ltd (trading as Nzone Skydive), PO Box 554, Queenstown 9348, TEL (03) 442 2256"));
    }

    /**
     * Test reading first page only of GEN 3.7 PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void GEN_3_7_firstPage() throws IOException {
        var def = new LinedTable("GEN 3.7 page 1", new Color(223, 223, 223), null, true, 1, 1, false, true, true, true, false, "\n");
        strip("AIP/GEN_3.7.pdf", 1, def, 8,
                new TestValue(0, 0, "ALL AIRCRAFT"), 
                new TestValue(8/2, 1, "FIS"), 
                new TestValue(8-1, -1, "AVBL for IFR ACFT on GND at\nNZAS"));
    }

    /**
     * Test reading all pages of GEN 3.7 PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void GEN_3_7_table() throws IOException {
        var def = new LinedTable("GEN 3.7 table", new Color(223, 223, 223), Pattern.compile("Table\\s?GEN\\s?3.7-2"), true, 1, 1, false, true, true, true, false, "\n");
        strip("AIP/GEN_3.7.pdf", 1, def, 259, 
                new TestValue(0, 0, "ALL AIRCRAFT"), 
                new TestValue(259/2, 1, "FIS"), 
                new TestValue(259-1, -1, "Nominal range at 10,000 ft: 80 NM\nNote: Terrain shielding may reduce\nAVBL range. ELEV 110 ft"));
    }

    /**
     * Test reading zones table from CTA PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void CtaZones() throws IOException {
        var def = new LinedTable("CTA zones", Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, false, " ");
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", 1, def, 125, 
                new TestValue(0, 0, "NZA132"), 
                new TestValue(125/2, 1, "NAPIER"), 
                new TestValue(125-1, -1, "FL"));
    }

    /**
     * Test reading points table from CTA PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void CtaPoints() throws IOException {
        var def = new LinedTable("CTA points", new Color[]{Color.BLACK, new Color(0x66, 0x66, 0x66)}, Pattern.compile("\\*\\*\\*"), true, 0, 2, false, true, true, true, false, " ");
        strip("AIP/1_01_NZANR_Part_71_Controlled_Airspace_CTA.pdf", 7, def, 1330, 
                new TestValue(0, 0, "NZA132"), 
                new TestValue(1330/2, 1, "4"), 
                new TestValue(1330-1, 1, "16"));
    }  

    /**
     * Test reading zones table from CTA PDF, spot-checking a few values
     *
     * @throws IOException
     */
    @Test
    public void NzrNames() throws IOException {
        var def = new LinedTable("NZR zones", new Color[]{Color.BLACK, new Color(0x666666)}, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, true, true, true, "\n");
        strip("AIP/1_05_NZANR_Part_71_Restricted_Areas_R.pdf", 1, def, 21, 
                new TestValue(0, 0, "NZR001"), 
                new TestValue(21/2, 1, "CAPE KIDNAPPERS"), 
                new TestValue(21-1, 1, "TAIAROA HEAD"));
    }  

    private void strip(String filename, int firstPageNo, LinedTable tab,
            int size, TestValue first, TestValue middle, TestValue last) throws IOException {
        LOG.info("Table {}", tab.getName());
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        var table = tab.extractTable(file, firstPageNo);
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
