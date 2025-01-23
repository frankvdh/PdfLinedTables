package org.apache.pdfbox.text;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit4TestClass.java to edit this template
 */
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LinedTableStripperTest {

    public static final Logger LOG = LogManager.getLogger(LinedTableStripperTest.class);

    @Test
    public void testExtractTable() throws IOException {
        strip("OneRow.pdf", 0, 0, 1, 2, "data1", "data2", "data2");
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 0, 0, 19, 6, "ALEXANDRA", "[CERTIFICATION]:None", "1753031.00E");
        strip("AIP/1_03_NZANR_Part_71_Low_Flying_Zones_LFZ.pdf", 0, 0,
                18, 5, "NZL160", "500", "[Organisation or Authority:] New Plymouth Aero Club,  New Plymouth Airport, RD3, New Plymouth 4373, TEL (06) 755 0500");
        strip("AIP/1_14_NZANR_Part_71_QNH_Zones.pdf", 0, 0,
                12, 5, "NZQ185", "13000", "");
        strip("AIP/1_04_NZANR_Part_71_Parachute_Landing_Areas_PLA.pdf", 0, 0,
                16, 3, "NZP114", "HAMILTON AERODROME", "[Organisation or Authority:] Using agency: Skydive Queenstown Ltd (trading as Nzone Skydive), PO Box 554, Queenstown 9348, \nTEL (03) 442 2256");
    }

 //   private void strip(String filename, int pageNo, Color headerColour, int size, int cols, String first, String middle, String last) throws IOException {
    private void strip(String filename, int pageNo, int extraRotation,
            int numRows, int numCols, String first, String middle, String last) throws IOException {
        LOG.info("Processing file {}", filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        File file = new File(absolutePath);
        try (PDDocument doc = Loader.loadPDF(file)) {
            LOG.fatal(LOG.getLevel());
            LinedTableStripper stripper = new LinedTableStripper(doc, extraRotation, true);
            PDPage page = doc.getPage(pageNo);
            stripper.processPage(page);
            stripper.findEndTable(0, page.getMediaBox().getUpperRightY(), null);
            ArrayList<String[]> table = new ArrayList<>(numRows);
            stripper.appendToTable(Color.BLACK, 0, numCols, table);
            assertEquals(numRows, table.size());
            assertEquals(numCols, table.get(0).length);
            assertEquals(first, table.get(0)[0]);
            assertEquals(middle, table.get(table.size() / 2)[numCols/2]);
            assertEquals(last, table.get(table.size() - 1)[numCols - 1]);
        }
    }
}
