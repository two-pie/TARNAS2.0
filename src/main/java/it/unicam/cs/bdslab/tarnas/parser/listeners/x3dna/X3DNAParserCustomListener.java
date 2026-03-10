package it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna;

import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNAParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNAParserBaseListener;
import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.models.Pair;

import java.util.ArrayList;

/**
 * Custom listener for X3DNA/3DNA parser that builds an ExtendedRNASecondaryStructure
 * from parsed bp_order.dat files.
 * 
 * Extracts base pair information including positions and bond types.
 * 
 * @author Federico Di Petta
 */
public class X3DNAParserCustomListener extends X3DNAParserBaseListener {

    private final ExtendedRNASecondaryStructure.Builder builder;
    private final StringBuilder sequenceBuilder;

    public X3DNAParserCustomListener() {
        this.builder = new ExtendedRNASecondaryStructure.Builder();
        this.builder.setPairs(new ArrayList<>());
        this.builder.setCanonical(new ArrayList<>());
        this.sequenceBuilder = new StringBuilder();
    }

    /**
     * Returns the ExtendedRNASecondaryStructure built from the parsed file.
     */
    public ExtendedRNASecondaryStructure getResult() {
        this.builder.setSequence(sequenceBuilder.toString());
        return this.builder.build();
    }

    @Override
    public void enterBasePairLine(X3DNAParser.BasePairLineContext ctx) {
        try {
            // Extract positions (convert to 0-indexed)
            int pos1 = Integer.parseInt(ctx.pos1.getText()) - 1;
            int pos2 = Integer.parseInt(ctx.pos2.getText()) - 1;
            
            // Extract base pair symbol (e.g., "G-*---U" or "A-----U")
            String bpSymbol = ctx.basePairSymbol.getText();
            
            // Parse bases from symbol
            String base1 = String.valueOf(bpSymbol.charAt(0));
            String base2 = String.valueOf(bpSymbol.charAt(bpSymbol.length() - 1));
            
            // Determine bond type based on symbol pattern
            BondType bondType = determineBondType(bpSymbol);
            
            Pair pair = new Pair(pos1, pos2, base1, base2, bondType);
            builder.addPair(pair);
            
        } catch (Exception e) {
            // Skip malformed lines
            System.err.println("Warning: Could not parse base pair line: " + ctx.getText());
        }
    }

    /**
     * Determines the bond type from the 3DNA base pair symbol.
     * - "G-----C" = canonical Watson-Crick
     * - "G-*---U" = wobble (G-U)
     * - "G-**--A" = non-canonical
     */
    private BondType determineBondType(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return BondType.UNKNOWN;
        }
        
        // Check for stars indicating non-canonical pairing
        if (symbol.contains("**")) {
            return BondType.NON_CANONICAL;
        }
        
        // Get the bases
        char base1 = Character.toUpperCase(symbol.charAt(0));
        char base2 = Character.toUpperCase(symbol.charAt(symbol.length() - 1));
        
        // G-U wobble pair
        if ((base1 == 'G' && base2 == 'U') || (base1 == 'U' && base2 == 'G')) {
            if (symbol.contains("*")) {
                return BondType.LEONTIS_WESTHOF_cWS; // Wobble is cis Watson-Sugar
            }
        }
        
        // Canonical Watson-Crick pairs
        if (isCanonicalPair(base1, base2) && !symbol.contains("*")) {
            return BondType.LEONTIS_WESTHOF_cWW;
        }
        
        return BondType.NON_CANONICAL;
    }

    private boolean isCanonicalPair(char b1, char b2) {
        return (b1 == 'A' && b2 == 'U') || (b1 == 'U' && b2 == 'A') ||
               (b1 == 'G' && b2 == 'C') || (b1 == 'C' && b2 == 'G');
    }
}
