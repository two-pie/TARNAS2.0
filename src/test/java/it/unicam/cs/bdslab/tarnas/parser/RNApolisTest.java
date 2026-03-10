package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.rnapolis.RNApolisLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnapolis.RNApolisParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.RNApolis.RNApolisParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for RNApolis parser.
 */
public class RNApolisTest {
    
    @Test
    public void testRNApolisParsingFile1() throws Exception {
        File inputFile = new File(this.getClass().getResource("/RNAPOLIS_1YMO_A.3db").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        RNApolisLexer lexer = new RNApolisLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RNApolisParser parser = new RNApolisParser(tokens);
        
        RNApolisParserCustomListener listener = new RNApolisParserCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.rnapolisFile());
        
        List<ExtendedRNASecondaryStructure> structures = listener.getStructures();
        
        // Verify we got some structures
        assertNotNull(structures);
        assertFalse(structures.isEmpty(), "Should have parsed some structures");
        
        System.out.println("RNApolis (1YMO_A) parsed " + structures.size() + " structures");
    }
    
    @Test
    public void testRNApolisParsingFile2() throws Exception {
        File inputFile = new File(this.getClass().getResource("/RNAPOLIS_2K95_A.3db").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        RNApolisLexer lexer = new RNApolisLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RNApolisParser parser = new RNApolisParser(tokens);
        
        RNApolisParserCustomListener listener = new RNApolisParserCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.rnapolisFile());
        
        List<ExtendedRNASecondaryStructure> structures = listener.getStructures();
        
        // Verify we got some structures
        assertNotNull(structures);
        assertFalse(structures.isEmpty(), "Should have parsed some structures");
        
        System.out.println("RNApolis (2K95_A) parsed " + structures.size() + " structures");
    }
}
