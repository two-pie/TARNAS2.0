package it.unicam.cs.bdslab.tarnas.parser.output;

import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;

import java.util.List;

public class RNASecondaryStrucutrePrinter {

    private static String HEADER = "Index\tNucleotide\t" 
        + String.join("\t", BondType.getLeontisWesthofFamily().stream()
            .map(BondType::getInfo)
            .toList()
        );

    public String printExtendedBPSEQ(ExtendedRNASecondaryStructure structure) {
        StringBuilder result = new StringBuilder();
        result.append(HEADER);
        result.append("\n");
        for (int i = 0; i < structure.getSequence().length(); i++) {
            char nucleotide = structure.getSequence().charAt(i);
            result.append(i + 1).append("\t").append(nucleotide).append("\t");
            for (BondType type : BondType.getLeontisWesthofFamily()) {
                int finalI = i;
                List<Integer> matches = structure.getPairs().stream()
                        .filter(pair -> 
                            // Filter pairs that match the current bond type and involve the current nucleotide position
                            pair.getType() == type && 
                                  (pair.getPos1() == finalI  
                                || pair.getPos2() == finalI)
                        )
                        .map(p -> (p.getPos1() == finalI ? p.getPos2() : p.getPos1()) + 1)
                        .toList();
                if (matches.isEmpty()) {
                    result.append("0").append("\t");
                } else {
                    result.append(matches.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0")).append("\t");
                }
            }
            result.append("\n");
        }
        return result.toString();
    }
}
