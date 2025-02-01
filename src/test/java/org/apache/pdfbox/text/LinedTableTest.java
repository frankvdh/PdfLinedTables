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

    @Test
    public void LFZ() throws IOException {
        var lfz = new LinedTable("LFZ", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, false, true, " ");
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", lfz, 51, 
                new TestValue(0, 0, "NZL160"),
                new TestValue(51/2, 1, "AHURIRI"), 
                new TestValue(51-1, -1, "[Organisation or Authority:] Central Otago Flying Club, PO Box 159, Alexandra, TEL (03) 448 9050"));
}

    @Test
    public void Coords() throws IOException {
        var coords = new LinedTable("Aerodrome Coordinates", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, false, true, " ");
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", coords, 215, 
                new TestValue(0, 0, "ALEXANDRA"), 
                new TestValue(215/2, 1, "HP"), 
                new TestValue(215-1, -1, "1735213.00E"));
}

    @Test
    public void NZMJ() throws IOException {
        var coords = new LinedTable("NZMJ", 1, Color.BLACK, null, true, 0, 1, false, true, true, true, "\n");
        strip("AIP/NZMJ.pdf", coords, 3, 
                new TestValue(0, 0, "RWY"), 
                new TestValue(3/2, 1, "Gr"), 
                new TestValue(3-1, -1, "564"));
    }
    
    @Test
    public void NZMF() throws IOException {
        var coords = new LinedTable("NZMF", 2, Color.BLACK, null, true, 0, 1, false, true, true, true, "\n");
        strip("AIP/NZMF_51.1_52.1.pdf", coords, 3, 
                new TestValue(0, 0, "RWY"), 
                new TestValue(3/2, 1, "B"), 
                new TestValue(3-1, -1, "767"));
    }
    
    @Test
    public void NZPH() throws IOException {
        var coords = new LinedTable("NZPH", 2, Color.BLACK, null, true, 0, 1, false, true, true, true, "\n");
        strip("AIP/NZPH_51.1_52.1.pdf", coords, 3, 
                new TestValue(0, 0, "RWY"), 
                new TestValue(3/2, 1, "Gr"),                 
                new TestValue(3-1, -1, "860"));

//  LinedTable stripper = new LinedTable("NZKT_51.1_52.1", 2, Pattern.compile("1\\:[234]0.*1\\:[2345][05]"), Pattern.compile("\\d\\d"),
//    Pattern.compile("LIGHTING|FACILITIES|SUPPLEMENTARY|MINIMA|FATO/TLOF"), '\n', false);
//
//  stripper.extractTable("/home/frank/Mapping/AIP/NZKT_51.1_52.1.pdf", 40);
//  LinedTable stripper
//    = new LinedTable("MBZ boundaries", 5, Pattern.compile("(?:Identifier.*Sequence.*)"), Pattern.compile("(?:NZB\\d\\d\\d)|(?:\\s*[A-Za-z]{4,})"), Pattern.compile("\\*\\*\\*"));
        //= new LinedTable("MBZ names", 1, Pattern.compile("(?:Identifier.*Name.*)"), Pattern.compile("NZB\\d\\d\\d.*"), Pattern.compile("\\*\\*\\*"));
//new LinedTable("VRP", 0, Pattern.compile("\\*\\*\\*"), Pattern.compile("Name.*Latitude.*Longitude"));
        //    new LinedTable("DME-NZZC", 1, null, Pattern.compile("[A-Z]+.*?[A-Z]+.*?\\d+\\.\\d+[NS].*"), Pattern.compile("\\*\\*\\*"));
        // new LinedTable("NZD boundaries", 17, Pattern.compile("Identifier.*Sequence.*"), Pattern.compile("(?:NZD\\d\\d\\d)|\\w{4,}"), Pattern.compile("\\*\\*\\*"), ' ');
//    new LinedTable("VHZ descriptions", 1, null, Pattern.compile("Activity.*or.*Purpose.*"), Pattern.compile("\\*\\*\\*"));
    }

    private void strip(String filename, LinedTable tab,
            int size, TestValue first, TestValue middle, TestValue last) throws IOException {
        LOG.info(filename);
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        var table = tab.extractTable(file);
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
