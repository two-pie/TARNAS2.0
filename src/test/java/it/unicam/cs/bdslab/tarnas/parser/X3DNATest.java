package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNALexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNAParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNAParserCustomListener;
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
 * Test class for X3DNA/3DNA parser.
 */
public class X3DNATest {
    
    @Test
    public void testX3DNAParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/3XDNA_4PLX_A_bp_order.dat").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        X3DNALexer lexer = new X3DNALexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        X3DNAParser parser = new X3DNAParser(tokens);
        
        X3DNAParserCustomListener listener = new X3DNAParserCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.x3dnaFile());
        
        ExtendedRNASecondaryStructure structure = listener.getResult();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("X3DNA parsed " + structure.getPairs().size() + " pairs");
    }
}
