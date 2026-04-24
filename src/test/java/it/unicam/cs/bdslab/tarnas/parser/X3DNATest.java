package it.unicam.cs.bdslab.tarnas.parser;

import com.fasterxml.jackson.jaxrs.json.annotation.JSONP;
import it.unicam.cs.bdslab.tarnas.parser.listeners.JSON.JSONParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.JSONLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.JSONX3dnaListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNALexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNAParser;
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
 * Test class for X3DNA/3DNA parser.
 */
public class X3DNATest {
    
    @Test
    public void testX3DNAParsing() throws Exception {
        File inputFile = new File(this.getClass().getResource("/1YMO_A_pair-only.json").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        JSONLexer lexer = new JSONLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JSONParser parser = new JSONParser(tokens);
        
        JSONX3dnaListener listener = new JSONX3dnaListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.json());
        
        ExtendedRNASecondaryStructure structure = listener.getStructure();
        
        // Verify we got some pairs
        assertNotNull(structure);
        assertFalse(structure.getPairs().isEmpty(), "Should have parsed some base pairs");
        
        System.out.println("X3DNA parsed " + structure.getPairs().size() + " pairs");
    }
}
