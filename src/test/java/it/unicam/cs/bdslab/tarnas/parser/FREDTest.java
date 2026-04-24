package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.JSON.JSONParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.Fr3dLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.Fr3dParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.JSONLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fred.JSONFr3dListener;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for FRED JSON parser.
 */
public class FREDTest {
    
    @Test
    public void testFREDParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/FRED_1YMO_A_A_basepair.json").toURI());
        CharStream cs4 = CharStreams.fromFileName(inputFile.getAbsolutePath());
        JSONLexer lexer4 = new JSONLexer(cs4);
        CommonTokenStream tokens4 = new CommonTokenStream(lexer4);
        JSONParser parser4 = new JSONParser(tokens4);
        ParseTree tree4 = parser4.json(); // parse
        JSONFr3dListener listener4 = new JSONFr3dListener();
        ParseTreeWalker.DEFAULT.walk(listener4, tree4);
        ExtendedRNASecondaryStructure structure = listener4.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("FRED parsed " + structure.getPairs().size() + " pairs");
    }
}
