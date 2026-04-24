package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.rnapolis.RNApolisGrammarLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnaview.*;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for RNAview parser.
 */
public class RNAviewTest {
    
    @Test
    public void testRNAviewParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/RNAVIREW_1YMO_A.pdb.out").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        RNAviewGrammarLexer lexer = new RNAviewGrammarLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RNAviewGrammarParser parser = new RNAviewGrammarParser(tokens);
        
        RNAviewCustomListener listener = new RNAviewCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.rnaviewFile());
        
        ExtendedRNASecondaryStructure structure = listener.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("RNAview parsed " + structure.getPairs().size() + " pairs");
    }
}
