package it.unicam.cs.bdslab.tarnas.parser.output;

import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;

import java.util.List;

public class RNASecondartyStrucutrePrinter {

    public String printExtendedBPSEQ(ExtendedRNASecondaryStructure structure) {
        StringBuilder result = new StringBuilder();
        //HEADERS
        result.append(
                "Index" + "\t" + "Nucleotide" + "\t"
        );
        for (BondType type : BondType.values()) {
            result.append(type.getInfo())
                    .append("\t");
        }
        result.append("\n");
        //BODY
        for (int i = 0; i < structure.getSequence().length(); i++) {
            char nucleotide = structure.getSequence().charAt(i);
            result.append(i + 1).append("\t").append(nucleotide).append("\t");
            for (BondType type : BondType.values()) {
                int finalI = i;
                List<Integer> matches = structure.getPairs().stream()
                        .filter(pair -> pair.getType() == type && (pair.getPos1() == finalI + 1 || pair.getPos2() == finalI + 1))
                        .map(p -> p.getPos1() == finalI + 1 ? p.getPos2() : p.getPos1())
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
