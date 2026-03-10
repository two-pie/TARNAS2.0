package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.rnaview.RNAviewLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnaview.RNAviewParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.RNAview.RNAviewParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for RNAview parser.
 */
public class RNAviewTest {
    
    @Test
    public void testRNAviewParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/RNAVIREW_1YMO_A.pdb.out").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        RNAviewLexer lexer = new RNAviewLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RNAviewParser parser = new RNAviewParser(tokens);
        
        RNAviewParserCustomListener listener = new RNAviewParserCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.rnaviewFile());
        
        ExtendedRNASecondaryStructure structure = listener.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("RNAview parsed " + structure.getPairs().size() + " pairs");
    }
}
