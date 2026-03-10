package it.unicam.cs.bdslab.tarnas.parser.listeners.fred;

import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.models.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Parser for FRED (FR3D-like) JSON output files.
 * 
 * FRED outputs base pair annotations in JSON format with fields:
 * - pdb_id: PDB identifier
 * - chain_id: Chain identifier
 * - annotations: Array of base pair annotations
 *   - seq_id1, seq_id2: Sequence positions
 *   - nt1, nt2: Nucleotide types
 *   - bp: Base pair type (Leontis-Westhof notation like cWW, cWH, tSH)
 *   - crossing: Crossing number for pseudoknots
 * 
 * @author Federico Di Petta
 */
public class FREDParser {

    /**
     * Parses a FRED JSON file and returns an ExtendedRNASecondaryStructure.
     * 
     * @param filePath Path to the JSON file
     * @return ExtendedRNASecondaryStructure containing parsed pairs
     * @throws IOException if file cannot be read
     */
    public ExtendedRNASecondaryStructure parse(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return parseJson(content);
    }

    /**
     * Parses a FRED JSON string and returns an ExtendedRNASecondaryStructure.
     * 
     * @param jsonContent JSON string content
     * @return ExtendedRNASecondaryStructure containing parsed pairs
     */
    public ExtendedRNASecondaryStructure parseJson(String jsonContent) {
        ExtendedRNASecondaryStructure.Builder builder = new ExtendedRNASecondaryStructure.Builder();
        builder.setPairs(new ArrayList<>());
        builder.setCanonical(new ArrayList<>());
        
        JSONObject root = new JSONObject(jsonContent);
        
        // Extract header info
        if (root.has("pdb_id")) {
            builder.addHeaderInfo("pdb_id", root.getString("pdb_id"));
        }
        if (root.has("chain_id")) {
            builder.addHeaderInfo("chain_id", root.getString("chain_id"));
        }
        
        // Build sequence from annotations
        Map<Integer, String> sequenceMap = new HashMap<>();
        
        // Parse annotations
        if (root.has("annotations")) {
            JSONArray annotations = root.getJSONArray("annotations");
            
            for (int i = 0; i < annotations.length(); i++) {
                JSONObject annot = annotations.getJSONObject(i);
                
                int seqId1 = annot.getInt("seq_id1");
                int seqId2 = annot.getInt("seq_id2");
                String nt1 = annot.getString("nt1");
                String nt2 = annot.getString("nt2");
                String bp = annot.getString("bp");
                
                // Store nucleotides in sequence map
                sequenceMap.put(seqId1, nt1);
                sequenceMap.put(seqId2, nt2);
                
                // Convert to 0-indexed positions
                int pos1 = seqId1 - 1;
                int pos2 = seqId2 - 1;
                
                // Parse bond type from Leontis-Westhof notation
                BondType bondType = parseFREDBondType(bp);
                
                // Only add if pos1 < pos2 to avoid duplicates
                if (pos1 < pos2) {
                    Pair pair = new Pair(pos1, pos2, nt1, nt2, bondType);
                    builder.addPair(pair);
                }
            }
        }
        
        // Build sequence string
        if (!sequenceMap.isEmpty()) {
            int maxPos = sequenceMap.keySet().stream().max(Integer::compareTo).orElse(0);
            StringBuilder seq = new StringBuilder();
            for (int i = 1; i <= maxPos; i++) {
                seq.append(sequenceMap.getOrDefault(i, "N"));
            }
            builder.setSequence(seq.toString());
        }
        
        return builder.build();
    }

    /**
     * Parses FRED bond type notation to BondType.
     * FRED uses standard Leontis-Westhof notation: cWW, cWH, tSH, etc.
     */
    private BondType parseFREDBondType(String bp) {
        if (bp == null || bp.isEmpty()) {
            return BondType.UNKNOWN;
        }
        
        // Direct match with BondType enum
        BondType type = BondType.fromString(bp);
        if (type != BondType.UNKNOWN) {
            return type;
        }
        
        // Handle common variations
        bp = bp.toUpperCase();
        if (bp.startsWith("C")) {
            bp = "c" + bp.substring(1);
        } else if (bp.startsWith("T")) {
            bp = "t" + bp.substring(1);
        }
        
        return BondType.fromString(bp);
    }
}
