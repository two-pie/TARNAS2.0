package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet.*;
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
 * Test class for BPNET/BPFIND parser.
 */
public class BPNETTest {
    
    @Test
    public void testBPNETParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/BPNET_1YMO_A.1YMO_A.out").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        BpnetGrammarLexer lexer = new BpnetGrammarLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        BpnetGrammarParser parser = new BpnetGrammarParser(tokens);
        
        BpnetParserCustomListener listener = new BpnetParserCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.bpnetFile());
        
        ExtendedRNASecondaryStructure structure = listener.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("BPNET parsed " + structure.getPairs().size() + " pairs");
        System.out.println("BPNET parsed " + structure.getCanonical().size() + " canonical");
        System.out.println("BPNET sequence: " + structure.getSequence());
        System.out.println("BPNET pairs:");
        structure.getPairs().forEach(pair -> {
            System.out.println(
                "(" + pair.getPos1() + ", " + pair.getPos2() + ") " +
                pair.getNucleotide1() + "-" + pair.getNucleotide2() + " " +
                pair.getType()
            );
        });
    }
}
