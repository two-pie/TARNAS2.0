package it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet;

import it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet.BPNETParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet.BPNETParserBaseListener;
import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.models.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom listener for BPNET/BPFIND parser that builds an ExtendedRNASecondaryStructure
 * from parsed .out files.
 * 
 * Extracts base pair and triplet information with Leontis-Westhof classifications.
 * 
 * BPNET pair types are formatted as Edge1:Edge2+Orientation, e.g.:
 * - W:WC = Watson-Crick/Watson-Crick cis
 * - W:HC = Watson-Crick/Hoogsteen cis
 * - S:HT = Sugar/Hoogsteen trans
 * 
 * @author Federico Di Petta
 */
public class BPNETParserCustomListener extends BPNETParserBaseListener {

    private final ExtendedRNASecondaryStructure.Builder builder;
    private final StringBuilder sequenceBuilder;
    private final Map<Integer, String> sequenceMap; // Track sequence by position

    public BPNETParserCustomListener() {
        this.builder = new ExtendedRNASecondaryStructure.Builder();
        this.builder.setPairs(new ArrayList<>());
        this.builder.setCanonical(new ArrayList<>());
        this.sequenceBuilder = new StringBuilder();
        this.sequenceMap = new HashMap<>();
    }

    /**
     * Returns the ExtendedRNASecondaryStructure built from the parsed file.
     */
    public ExtendedRNASecondaryStructure getResult() {
        // Build sequence from map
        if (!sequenceMap.isEmpty()) {
            int maxPos = sequenceMap.keySet().stream().max(Integer::compareTo).orElse(0);
            StringBuilder seq = new StringBuilder();
            for (int i = 1; i <= maxPos; i++) {
                seq.append(sequenceMap.getOrDefault(i, "N"));
            }
            this.builder.setSequence(seq.toString());
        }
        return this.builder.build();
    }

    @Override
    public void enterResidueLine(BPNETParser.ResidueLineContext ctx) {
        try {
            // Extract residue information
            int serialNum = Integer.parseInt(ctx.serialNum.getText());
            int pdbNum = Integer.parseInt(ctx.pdbNum1.getText());
            String base = ctx.base1.getText();
            
            // Store in sequence map (using pdbNum as position)
            sequenceMap.put(pdbNum, base);
            
            // If there's pair info, process it
            if (ctx.pairInfo() != null) {
                processPairInfo(ctx.pairInfo(), pdbNum, base);
            }
            
            // Process any triplet information
            for (BPNETParser.TripletInfoContext tripletCtx : ctx.tripletInfo()) {
                processTripletInfo(tripletCtx, pdbNum, base);
            }
            
        } catch (Exception e) {
            System.err.println("Warning: Could not parse residue line: " + ctx.getText());
        }
    }

    private void processPairInfo(BPNETParser.PairInfoContext ctx, int pos1, String base1) {
        try {
            int pos2 = Integer.parseInt(ctx.pairedPdbNum.getText());
            String base2 = ctx.pairedBase.getText();
            String pairType = ctx.pairType.getText();
            
            // Store paired base in sequence map
            sequenceMap.put(pos2, base2);
            
            // Only add pair if pos1 < pos2 to avoid duplicates
            if (pos1 < pos2) {
                BondType bondType = parseBPNETPairType(pairType);
                Pair pair = new Pair(pos1 - 1, pos2 - 1, base1, base2, bondType);
                builder.addPair(pair);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not parse pair info: " + ctx.getText());
        }
    }

    private void processTripletInfo(BPNETParser.TripletInfoContext ctx, int pos1, String base1) {
        try {
            int pos2 = Integer.parseInt(ctx.tripletPdbNum.getText());
            String base2 = ctx.tripletBase.getText();
            String pairType = ctx.tripletPairType.getText();
            
            // Store triplet base in sequence map
            sequenceMap.put(pos2, base2);
            
            // Add triplet pair (mark as non-canonical since triplets are special)
            if (pos1 < pos2) {
                BondType bondType = parseBPNETPairType(pairType);
                Pair pair = new Pair(pos1 - 1, pos2 - 1, base1, base2, bondType);
                builder.addPair(pair);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not parse triplet info: " + ctx.getText());
        }
    }

    /**
     * Parses BPNET pair type notation to BondType.
     * BPNET format: Edge1:Edge2+Orientation
     * Examples: W:WC (Watson-Crick/Watson-Crick cis), W:HT (Watson-Crick/Hoogsteen trans)
     */
    private BondType parseBPNETPairType(String pairType) {
        if (pairType == null || pairType.length() < 4) {
            return BondType.UNKNOWN;
        }
        
        // Parse format: X:YZ where X=edge1, Y=edge2, Z=cis/trans
        char edge1 = pairType.charAt(0);
        char edge2 = pairType.charAt(2);
        char orientation = pairType.charAt(3);
        
        // Build Leontis-Westhof code
        String prefix = (orientation == 'C') ? "c" : "t";
        String lwCode = prefix + edge1 + edge2;
        
        BondType type = BondType.fromString(lwCode);
        
        // If it's cWW, it's canonical, otherwise it's the LW type or non-canonical
        if (type != BondType.UNKNOWN) {
            return type;
        }
        
        // Fallback
        if ("W:WC".equals(pairType)) {
            return BondType.LEONTIS_WESTHOF_cWW;
        }
        
        return BondType.NON_CANONICAL;
    }
}
