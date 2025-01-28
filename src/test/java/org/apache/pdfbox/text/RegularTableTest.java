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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RegularTableTest {

    public static final Logger LOG = LogManager.getLogger(RegularTable.class.getName());

    @Test
    public void testExtractTable() throws IOException {
        strip("OneRow.pdf", 0, 0, 1, 2, "data1", "data2", "data2");
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
            RegularTable stripper = new RegularTable(doc, extraRotation, true);
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

    /**
     * Test of findTable and extractTable methods, of class SinglePageTable.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testMultiplePages() throws Exception {
        String filename = "AIP/3_06_NZANR_Part_95_Navaids.pdf";
        LOG.info(filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        String[] names = new String[]{"DME-NZZC", "DME-NZZO", "NDB-NZZC", "NDB-NZZO", "VOR-NZZC", "VOR-NZZO"};
        int[] tableSizes = {43, 6, 19, 15, 16, 5};
        int[] rowSizes = {7, 7, 6, 6, 6, 6};
        String[] firsts = {"AUCKLAND", "FALEOLO", "TAUPO", "AITUTAKI", "AUCKLAND", "FALEOLO"};
        String[] lasts = {"WHANGANUI", "FUAAMOTU", "WHANGANUI", "VAVAU", "WHENUAPAI", "PAGO PAGO"};
        Pattern tableEnd = Pattern.compile("\\*\\*\\*");
        RegularTable stripper = new RegularTable(new File(absolutePath), 0, true);

        var pageNum = 0;
        var startY = 0f;
        for (int i = 0; i < names.length; i++) {
            var table = stripper.extractTable(pageNum, Color.BLACK, startY, tableEnd, rowSizes[i]);
            assertEquals(tableSizes[i], table.size());
            LOG.info("Table {}", names[i]);
            for (String[] row : table) {
                assertEquals(rowSizes[i], row.length);
                StringBuilder sb = new StringBuilder(100);
                for (String s : row) {
                    sb.append(s == null ? "" : s.replace('\n', '/')).append(", ");
                }
                LOG.debug(sb.toString());
            }
            assertEquals(firsts[i], table.get(0)[0]);
            assertEquals(lasts[i], table.get(table.size() - 1)[0]);
            pageNum = stripper.getCurrPageNum();   // Continue
            startY = stripper.getTableBottom()+3;
        }
    }
}
