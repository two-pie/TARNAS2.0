package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.fred.FREDParser;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FRED JSON parser.
 */
public class FREDTest {
    
    @Test
    public void testFREDParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/FRED_1YMO_A_A_basepair.json").toURI());
        Path inputPath = inputFile.toPath();
        
        FREDParser parser = new FREDParser();
        ExtendedRNASecondaryStructure structure = parser.parse(inputPath);
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("FRED parsed " + structure.getPairs().size() + " pairs");
    }
}
