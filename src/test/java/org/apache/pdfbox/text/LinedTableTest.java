package org.apache.pdfbox.text;

/*
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 * 
 */
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.apache.pdfbox.Loader.loadPDF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class LinedTableTest {

    public static final Logger log = LogManager.getLogger(LinedTableTest.class);

    @Test
    public void oneRow() throws IOException {
        strip("OneRow.pdf", 0, Color.BLACK, null, 0, true, true, "\n", 2, 1, "data1", "data2", "data2");
    }

    @Test
    public void Coordinates_firstPage() throws IOException {
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 0, Color.BLACK, null, 0, false, true, "\n", 6, 19, "ALEXANDRA", "HP", "1753031.00E");
    }

    @Test
    public void Coordinates() throws IOException {
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 0, Color.BLACK, Pattern.compile("\\*\\*\\*"), 0, false, true, "\n", 6, 215, "ALEXANDRA", "HP", "1735213.00E");
    }

    @Test
    public void LFZ() throws IOException {
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", 0, Color.BLACK, null, 0, false, true, "\n",
                5, 18, "NZL160", "THAMES", "[Organisation or Authority:] New Plymouth Aero Club,  New Plymouth Airport, RD3, New Plymouth 4373, TEL (06) 755 0500");
    }

    @Test
    public void QNH() throws IOException {
        strip("AIP/1_14_NZANR_Part_71_QNH_Zones.pdf", 0, Color.BLACK, null, 0, false, true, "\n",
                5, 12, "NZQ185", "WEST COAST AREA QNH ZONE", "");
    }

    @Test
    public void PLA() throws IOException {
        strip("AIP/1_04_NZANR_Part_71_Parachute_Landing_Areas_PLA.pdf", 0, Color.BLACK, null, 0, false, true, " ",
                3, 16, "NZP114", "HAMILTON AERODROME", "[Organisation or Authority:] Using agency: Skydive Queenstown Ltd (trading as Nzone Skydive), PO Box 554, Queenstown 9348, TEL (03) 442 2256");
    }

    @Test
    public void GEN_3_7_firstPage() throws IOException {
        strip("AIP/GEN_3.7.pdf", 0, new Color(223, 223, 223),
                null, 1, false, true, "\n", 8,
                8, "ALL AIRCRAFT", "FIS", "AVBL for IFR ACFT on GND at\nNZAS");
    }

    @Test
    public void GEN_3_7_table1() throws IOException {
        strip("AIP/GEN_3.7.pdf", 0, new Color(223, 223, 223),
                Pattern.compile("Table\\s?GEN\\s?3.7-2"), 1, false, true, "\n", 8,
                259, "ALL AIRCRAFT", "FIS", "Nominal range at 10,000 ft: 80 NM\nNote: Terrain shielding may reduce\nAVBL range. ELEV 110 ft");
    }

    private void strip(String filename, int pageNo, Color hdgColor, Pattern tableEnd, int extraRotation, boolean leadingSpaces, boolean reduceSpaces, String lineEnding,
            int numColumns,
            int size, String first, String middle, String last) throws IOException {
        log.info(filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        File file = new File(absolutePath);
        var stripper = new LinedTableStripper(loadPDF(file), extraRotation, true, leadingSpaces, reduceSpaces, 3, lineEnding);
        ArrayList<String[]> table = stripper.extractTable(pageNo, hdgColor, extraRotation, tableEnd, numColumns);
        assertNotNull(table);
        assertEquals(size, table.size());
        assertEquals(first, table.get(0)[0]);
        assertEquals(middle, table.get(size / 2)[1]);
        assertEquals(last, table.get(table.size() - 1)[numColumns - 1]);
    }
}
