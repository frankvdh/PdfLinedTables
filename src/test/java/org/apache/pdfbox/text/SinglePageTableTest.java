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
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SinglePageTableTest {

    private static final double MARGIN_HEIGHT = 57.0;
    public static final Logger LOG = LogManager.getLogger(SinglePageTable.class);

    /**
     * Test of findTable and extractTable methods, of class SinglePageTable.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testExtractTable() throws IOException {
        strip("AIP/1_14_NZANR_Part_71_QNH_Zones.pdf", 1, Color.BLACK, Color.WHITE,
                12, 5, "NZQ185", "WEST COAST AREA QNH ZONE", "");
        strip("OneRow.pdf", 1, Color.BLACK, Color.WHITE,
                1, 2, "data1", null, "data2");
        strip("AIP/1_04_NZANR_Part_71_Parachute_Landing_Areas_PLA.pdf", 1, Color.BLACK, Color.WHITE,
                16, 3, "NZP114", "HAMILTON AERODROME", "[Organisation or Authority:] Using agency: Skydive Queenstown Ltd (trading as Nzone Skydive), PO Box 554, Queenstown 9348, \nTEL (03) 442 2256");
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", 1, Color.BLACK, Color.WHITE,
                18, 5, "NZL160", "THAMES", "[Organisation or Authority:] New Plymouth Aero Club,  New Plymouth Airport, RD3, New Plymouth 4373, TEL (06) 755 0500");
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 1, Color.BLACK, Color.WHITE,
                19, 6, "ALEXANDRA", "HP", "1753031.00E");
    }

    private void strip(String filename, int pageNo, Color headerColour, Color dataColour, int size, int cols, String first, String middle, String last) throws IOException {
        LOG.info(filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        SinglePageTable stripper = new SinglePageTable(new File(absolutePath), pageNo - 1, false, true);
        ArrayList<String[]> table = stripper.extractTable(headerColour, dataColour, Pattern.compile("\\*\\*\\*"), cols);
        assertEquals(cols, table.get(0).length);
        assertEquals(size, table.size());
        assertEquals(first, table.get(0)[0]);
        if (middle != null) {
            assertEquals(middle, table.get(size / 2)[1]);
        }
        assertEquals(last, table.get(table.size()-1)[cols - 1]);
    }
}
