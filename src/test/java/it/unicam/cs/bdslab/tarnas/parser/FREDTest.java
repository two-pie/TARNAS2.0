package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.Fr3dLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.Fr3dParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fred.Fr3dParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.McAnnotateParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FRED JSON parser.
 */
public class FREDTest {
    
    @Test
    public void testFREDParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/FRED_1YMO_A_A_basepair.json").toURI());
        CharStream cs4 = CharStreams.fromFileName(inputFile.getAbsolutePath());
        Fr3dLexer lexer4 = new Fr3dLexer(cs4);
        CommonTokenStream tokens4 = new CommonTokenStream(lexer4);
        Fr3dParser parser4 = new Fr3dParser(tokens4);
        ParseTree tree4 = parser4.fr3dFile(); // parse
        Fr3dParserCustomListener listener4 = new Fr3dParserCustomListener();
        ParseTreeWalker.DEFAULT.walk(listener4, tree4);
        ExtendedRNASecondaryStructure structure = listener4.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("FRED parsed " + structure.getPairs().size() + " pairs");
    }
}
