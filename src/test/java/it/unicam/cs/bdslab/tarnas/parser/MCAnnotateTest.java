package it.unicam.cs.bdslab.tarnas.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;


import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;

public class MCAnnotateTest {
    @Test
    public void testMCAnnotate() throws FileNotFoundException, IOException, URISyntaxException {
        File inputFile = new File(this.getClass().getResource("/MC_4PLX.txt").toURI());
        CharStream charStream = CharStreams.fromReader(new FileReader(inputFile));
        MCAnnotateLexer lexer = new MCAnnotateLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MCAnnotateParser parser = new MCAnnotateParser(tokens);
        
        MCAnnotateParserCustomListener listener = new MCAnnotateParserCustomListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, parser.mcAnnotateFile());
        ExtendedRNASecondaryStructure structure = listener.getResult();
        assertNotEquals("", structure.getSequence());
        assertNotEquals(0, structure.getPairs().size());   
        
    }
}
