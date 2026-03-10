package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.BarnabaLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.BarnabaParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.BarnabaParserCustomListener;
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
 * Test class for Barnaba parser.
 */
public class BarnabaTest {
    
    @Test
    public void testBarnabaParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/BARNABA_1YMO_A.pdb.ANNOTATE.pairing.out").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        BarnabaLexer lexer = new BarnabaLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        BarnabaParser parser = new BarnabaParser(tokens);
        
        BarnabaParserCustomListener listener = new BarnabaParserCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.barnabaFile());
        
        ExtendedRNASecondaryStructure structure = listener.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("Barnaba parsed " + structure.getPairs().size() + " pairs");
    }
}
