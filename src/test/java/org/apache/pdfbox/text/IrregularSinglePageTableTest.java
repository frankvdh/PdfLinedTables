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
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class IrregularSinglePageTableTest {
    public static final Logger LOG = LogManager.getLogger(IrregularSinglePageTableTest.class);

    /*
     * Test of findTable and extractTable methods, of class SinglePageTable.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testExtractTable() throws IOException {
        strip("AIP/NZPH_51.1_52.1.pdf", 2,
                Color.BLACK, Color.WHITE, Pattern.compile("LIGHTING|FACILITIES|SUPPLEMENTARY|MINIMA|FATO/TLOF|Critical\\s*Obstacles"), 10, 0,
                3, "02\n02", "Gr", "910");
    }

    private void strip(String filename, int pageNo, Color headerColour, Color dataColour, Pattern tableEnd, int cols, int extraRotation, int size, String first, String middle, String last) throws IOException {
        LOG.info(filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        File file = new File(absolutePath);
        try (PDDocument doc = Loader.loadPDF(file)) {
            IrregularSinglePageTable stripper = new IrregularSinglePageTable(doc, pageNo - 1, extraRotation, true);
            // Find the last line of the heading
            ArrayList<String[]> table = stripper.extractTable(headerColour, dataColour, 0, tableEnd, cols);
            
            assert !table.isEmpty() : "Table not found";
            for (String[] row: table) {
                StringBuilder sb = new StringBuilder(100);
                for (String s : row) {
                    if (s != null) {
                        sb.append(s.replace('\n', '/')).append(", ");
                    }
                }
                System.out.println(sb.toString());
            }

            assertEquals(size, table.size());
            String[] firstDataRow = table.get(0);
            assert (firstDataRow.length >= 10);
            assertEquals(first, firstDataRow[0]);
            assertEquals(middle, firstDataRow[1]);
            assertEquals(last, firstDataRow[firstDataRow.length-1]);
        }
    }
}
