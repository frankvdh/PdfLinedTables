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
        strip("OneRow.pdf", 0, 0,
                1, 2, "data1", "", "data2");
        strip("AIP/NZANR-Aerodrome_Coordinates.pdf", 0, 0,
                19, 6, "ALEXANDRA", "2", "1753031.00E");
    }

    private void strip(String filename, int pageNo, int extraRotation,
            int numRows, int numCols, String first, String middle, String last) throws IOException {
        LOG.info("Processing file {}", filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        File file = new File(absolutePath);
        try (PDDocument doc = Loader.loadPDF(file)) {
            LOG.fatal(LOG.getLevel());
            PDPage page = doc.getPage(pageNo);
            LinedTableStripper stripper = new LinedTableStripper(page, extraRotation, true);
            stripper.processPage(page);
            var endY = stripper.findEndTable(0, page.getMediaBox().getUpperRightY(), null);
            ArrayList<String[]> table = new ArrayList<>(numRows);
            stripper.appendToTable(Color.BLACK, Color.WHITE, 0, endY, numCols, table);
            assertEquals(numRows, table.size());
            assertEquals(numCols, table.get(0).length);
            assertEquals(first, table.get(0)[0]);
//            assertEquals(middle, table.get(size / 2)[1]);
            assertEquals(last, table.get(table.size() - 1)[numCols - 1]);
        }
    }
}
