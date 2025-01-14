package org.apache.pdfbox.text;

/*
 * @author <a href="mailto:drifter.frank@gmail.com">Frank van der Hulst</a>
 * 
 */
import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MultiplePageTableTest {

    public static final Logger LOG = LogManager.getLogger(MultiplePageTable.class.getName());

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
        MultiplePageTable stripper = new MultiplePageTable(new File(absolutePath), 0, 0, true);

        for (int i = 0; i < names.length; i++) {
            ArrayList<String[]> table = stripper.extractTable(Color.BLACK, 0, tableEnd, rowSizes[i]);
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
        }
    }
}
