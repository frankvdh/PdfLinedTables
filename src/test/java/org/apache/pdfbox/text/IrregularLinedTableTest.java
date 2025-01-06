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

public class IrregularLinedTableTest {

    public static final Logger log = LogManager.getLogger(IrregularLinedTableTest.class);

    @Test
    public void testExtractTable() throws IOException {
        strip("AIP/GEN_3.7.pdf", 0, new Color(223,223,223),
                Pattern.compile("Table\\s?GEN\\s?3.7-2"), 90, 5,
                69, "ALL AIRCRAFT", "r", "");
    }

    private void strip(String filename, int pageNo, Color hdgColor, Pattern tableEnd, int extraRotation, int numColumns,
            int size, String first, String middle, String last) throws IOException {
        log.info(filename);
        Path resourcePath = Paths.get("src", "test", "resources", filename);
        String absolutePath = resourcePath.toFile().getAbsolutePath();
        File file = new File(absolutePath);
        var stripper = new IrregularLinedTable(file, pageNo, extraRotation, true);
        ArrayList<String[]> table = stripper.extractTable(hdgColor, null, tableEnd, numColumns);
        assertNotNull(table);
        assertEquals(size, table.size());
        assertEquals(first, table.get(0)[0]);
        assertEquals(middle, table.get(size / 2)[1]);
        assertEquals(last, table.get(table.size() - 1)[4]);
    }
}
