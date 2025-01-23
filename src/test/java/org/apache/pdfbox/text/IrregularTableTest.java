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
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class IrregularTableTest {

    public static final Logger log = LogManager.getLogger(IrregularTableTest.class);

    @Test
    public void testExtractTable() throws IOException {
//        strip("AIP/GEN_3.7.pdf", 0, new Color(223,223,223),
//                Pattern.compile("Table\\s?GEN\\s?3.7-2"), 1, 4,
//                69, "ALL AIRCRAFT", "r", "");
        strip("AIP/GEN_3.7.pdf", 0, new Color(223,223,223),
                Pattern.compile("Table\\s?GEN\\s?3.7-2"), 1, 8,
                207, "ALL AIRCRAFT", "123.7", "Nominal range at 10,000 ft: 80 NM    Note: Terrain shielding may reduce            AVBL range. ELEV 110 ft");
    }

    private void strip(String filename, int pageNo, Color hdgColor, Pattern tableEnd, int extraRotation, int numColumns,
            int size, String first, String middle, String last) throws IOException {
        log.info(filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        File file = new File(absolutePath);
        var stripper = new IrregularTable(file, extraRotation, true);
        ArrayList<String[]> table = stripper.extractTable(pageNo, hdgColor, extraRotation, tableEnd, numColumns);
        assertNotNull(table);
        assertEquals(size, table.size());
        assertEquals(first, table.get(0)[0]);
        assertEquals(middle, table.get(size / 2)[1]);
        assertEquals(last, table.get(table.size() - 1)[numColumns-1]);
    }
}
