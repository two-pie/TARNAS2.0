package it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate;

import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateParserBaseListener;
import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.models.Pair;

import java.util.ArrayList;

/**
 * Custom listener for MC-Annotate parser that builds an ExtendedRNASecondaryStructure
 * from parsed MC-Annotate output files.
 * 
 * Properly extracts Leontis-Westhof base pair classifications from the edge annotations.
 * 
 * @author Federico Di Petta
 */
public class MCAnnotateParserCustomListener extends MCAnnotateParserBaseListener {

    private final ExtendedRNASecondaryStructure.Builder builder;
    private final StringBuilder sequenceBuilder;
    
    // Temporary storage for current bond line parsing
    private String currentEdge1;
    private String currentEdge2;
    private String currentConfiguration; // cis or trans
    private String currentBase1;
    private String currentBase2;
    private int currentPos1;
    private int currentPos2;

    public MCAnnotateParserCustomListener() {
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

    // ------------------------------------------------
    // Sequence parsing
    // ------------------------------------------------
    
    @Override
    public void enterSequenceLine(MCAnnotateParser.SequenceLineContext ctx) {
        String rawValue = null;
        
        if (ctx.nucleotide != null) {
            rawValue = ctx.nucleotide.getText();
        } else if (ctx.nucleotideId != null) {
            // Handle case like "A76 : A23" - extract base from the ID
            rawValue = ctx.nucleotideId.getText();
        } else if (ctx.nucleotideText != null) {
            rawValue = ctx.nucleotideText.getText();
        }
        
        if (rawValue != null) {
            String extractedBase = extractBase(rawValue);
            if (!extractedBase.isEmpty()) {
                sequenceBuilder.append(extractedBase);
            }
        }
    }

    private String extractBase(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }
        
        rawValue = rawValue.trim();
        
        // Single base (A, C, G, U)
        if (rawValue.length() == 1 && "ACGU".contains(rawValue.toUpperCase())) {
            return rawValue.toUpperCase();
        }
        
        // Multi-character (e.g., "GTP", "ATP", "A23") - take first char if it's a valid base
        char firstChar = Character.toUpperCase(rawValue.charAt(0));
        if ("ACGU".indexOf(firstChar) >= 0) {
            return String.valueOf(firstChar);
        }
        
        return "";
    }

    // ------------------------------------------------
    // Stacking parsing
    // ------------------------------------------------
    
    @Override
    public void enterStackingLine(MCAnnotateParser.StackingLineContext ctx) {
        int pos1 = parseResidueId(ctx.id1.getText());
        int pos2 = parseResidueId(ctx.id2.getText());
        
        Pair pair = new Pair(pos1, pos2, BondType.STACKING);
        builder.addPair(pair);
    }

    // ------------------------------------------------
    // Bond/Base-pair parsing
    // ------------------------------------------------
    
    @Override
    public void enterBondLine(MCAnnotateParser.BondLineContext ctx) {
        // Reset temporary state
        currentEdge1 = null;
        currentEdge2 = null;
        currentConfiguration = "cis"; // default
        
        currentPos1 = parseResidueId(ctx.id1.getText());
        currentPos2 = parseResidueId(ctx.id2.getText());
        currentBase1 = ctx.base1.getText();
        currentBase2 = ctx.base2.getText();
    }

    @Override
    public void exitBondLine(MCAnnotateParser.BondLineContext ctx) {
        BondType bondType = determineBondType();
        
        Pair pair = new Pair(currentPos1, currentPos2, currentBase1, currentBase2, bondType);
        builder.addPair(pair);
    }

    @Override
    public void enterEdgeInteraction(MCAnnotateParser.EdgeInteractionContext ctx) {
        currentEdge1 = normalizeEdge(ctx.edge1.getText());
        currentEdge2 = normalizeEdge(ctx.edge2.getText());
    }

    @Override
    public void enterConfiguration(MCAnnotateParser.ConfigurationContext ctx) {
        if (ctx.CIS() != null) {
            currentConfiguration = "cis";
        } else if (ctx.TRANS() != null) {
            currentConfiguration = "trans";
        }
    }

    /**
     * Normalizes edge notation to single uppercase letter.
     * Ww, W -> W (Watson-Crick)
     * Hh, H -> H (Hoogsteen)  
     * Ss, S -> S (Sugar)
     * O2', Bs, etc. -> S (Sugar edge variants)
     */
    private String normalizeEdge(String edge) {
        if (edge == null || edge.isEmpty()) {
            return "?";
        }
        
        String upper = edge.toUpperCase();
        
        // Watson-Crick edge
        if (upper.startsWith("W")) {
            return "W";
        }
        // Hoogsteen edge
        if (upper.startsWith("H")) {
            return "H";
        }
        // Sugar edge (including O2', Bs, etc.)
        if (upper.startsWith("S") || upper.startsWith("O2") || upper.startsWith("B")) {
            return "S";
        }
        
        return "?";
    }

    /**
     * Determines the BondType based on the parsed edge interaction and configuration.
     * Maps to Leontis-Westhof nomenclature: cWW, tWW, cWH, tWH, cWS, tWS, cHH, tHH, cHS, tHS, cSS, tSS
     */
    private BondType determineBondType() {
        if (currentEdge1 == null || currentEdge2 == null) {
            return BondType.UNKNOWN;
        }
        
        // Build LW code: c/t + edge1 + edge2
        String prefix = currentConfiguration.equals("cis") ? "c" : "t";
        String lwCode = prefix + currentEdge1 + currentEdge2;
        
        // Try to match to BondType enum
        BondType type = BondType.fromString(lwCode);
        
        // If not found directly, try canonical/non-canonical fallback
        if (type == BondType.UNKNOWN) {
            // Check if it's a standard canonical pairing
            if (isCanonicalPairing()) {
                return BondType.CANONICAL;
            }
            return BondType.NON_CANONICAL;
        }
        
        return type;
    }

    /**
     * Checks if the current base pair is a canonical Watson-Crick pairing.
     * Canonical pairs: A-U, U-A, G-C, C-G with cis WW edges
     */
    private boolean isCanonicalPairing() {
        if (!"cis".equals(currentConfiguration)) {
            return false;
        }
        if (!"W".equals(currentEdge1) || !"W".equals(currentEdge2)) {
            return false;
        }
        
        String pair = currentBase1.toUpperCase() + "-" + currentBase2.toUpperCase();
        return pair.equals("A-U") || pair.equals("U-A") || 
               pair.equals("G-C") || pair.equals("C-G");
    }

    /**
     * Parses residue ID (e.g., "A12") to position number (1-indexed converted to 0-indexed).
     */
    private int parseResidueId(String text) {
        String number = text.replaceAll("[^0-9]", "");
        return Integer.parseInt(number) - 1; // Convert to 0-indexed
    }
}
