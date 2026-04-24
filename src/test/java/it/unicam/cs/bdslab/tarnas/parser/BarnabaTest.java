package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.*;
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
 * Test class for Barnaba parser.
 */
public class BarnabaTest {
    
    @Test
    public void testBarnabaParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/BARNABA_1YMO_A.pdb.ANNOTATE.pairing.out").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        BarnabaGrammarLexer lexer = new BarnabaGrammarLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        BarnabaGrammarParser parser = new BarnabaGrammarParser(tokens);
        
        BarnabaCustomListener listener = new BarnabaCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.barnabaFile());
        
        ExtendedRNASecondaryStructure structure = listener.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("Barnaba parsed " + structure.getPairs().size() + " pairs");
    }
}
