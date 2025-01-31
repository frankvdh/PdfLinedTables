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
        var lfz = new LinedTable("LFZ", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, false, " ");
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", lfz, 51, "NZL160", "AHURIRI", "[Organisation or Authority:] Central Otago Flying Club, PO Box 159, Alexandra, TEL (03) 448 9050");
}

    @Test
    public void Coords() throws IOException {
        var coords = new LinedTable("Aerodrome Coordinates", 1, Color.BLACK, Pattern.compile("\\*\\*\\*"), true, 0, 1, false, true, false, " ");
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", coords, 215, "ALEXANDRA", "HP", "1735213.00E");
}

    @Test
    public void NZMJ() throws IOException {
        var coords = new LinedTable("NZMJ", 1, Color.BLACK, null, true, 0, 1, false, true, true, "\n");
        strip("AIP/NZMJ.pdf", coords, 3, "RWY", "Gr", "564");
    }
    
    @Test
    public void NZMF() throws IOException {
        var coords = new LinedTable("NZMF", 2, Color.BLACK, null, true, 0, 1, false, true, true, "\n");
        strip("AIP/NZMF_51.1_52.1.pdf", coords, 3, "RWY", "B", "767");
    }
    
    @Test
    public void NZPH() throws IOException {
        var coords = new LinedTable("NZPH", 2, Color.BLACK, null, true, 0, 1, false, true, true, "\n");
        strip("AIP/NZPH_51.1_52.1.pdf", coords, 3, "RWY", "Gr", "860");

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
            int size, String first, String middle, String last) throws IOException {
        LOG.info(filename);
        var resourcePath = Paths.get("src", "test", "resources", filename);
        var absolutePath = resourcePath.toFile().getAbsolutePath();
        var file = new File(absolutePath);
        var table = tab.extractTable(file);
        assertNotNull(table);
        assertEquals(size, table.size());
        assertEquals(first, table.get(0)[0]);
        assertEquals(middle, table.get(size / 2)[1]);
        assertEquals(last, table.getLast()[table.getLast().length- 1]);
    }

}
