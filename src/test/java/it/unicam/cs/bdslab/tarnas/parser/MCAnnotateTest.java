package it.unicam.cs.bdslab.tarnas.parser;

import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.*;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class MCAnnotateTest {
    @Test
    public void testMCAnnotate() throws FileNotFoundException, IOException, URISyntaxException {
        File inputFile = new File(this.getClass().getResource("/MC_4PLX.txt").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        McAnnotateGrammarLexer lexer = new McAnnotateGrammarLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        McAnnotateGrammarParser parser = new McAnnotateGrammarParser(tokens);
        
        McAnnotateCustomListener listener = new McAnnotateCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.mcAnnotateFile());
        ExtendedRNASecondaryStructure structure = listener.getStructure();
        assertNotEquals("", structure.getSequence());
        assertNotEquals(0, structure.getPairs().size());   
    }
}
