package it.unicam.cs.bdslab.tarnas.parser.output;

import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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

    public String printBPSEQ(ExtendedRNASecondaryStructure structure) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < structure.getSequence().length(); i++) {
            int finalI = i;
            char nucleotide = structure.getSequence().charAt(i);
            int pairedIndex = structure.getCanonical().stream()
                    .filter(pair -> pair.getPos1() == finalI || pair.getPos2() == finalI)
                    .map(pair -> (pair.getPos1() == finalI ? pair.getPos2() : pair.getPos1()) + 1)
                    .findFirst()
                    .orElse(0);
            result.append(i + 1).append("\t").append(nucleotide).append("\t").append(pairedIndex).append("\n");
        }
        return result.toString();
    }

    public enum OutputFormat {
        EXTENDED_BPSEQ,
        DBN,
        BPSEQ;

        @Override
        public String toString() {
            return switch (this) {
                case EXTENDED_BPSEQ -> "Extended BPSEQ";
                case BPSEQ -> "BPSEQ";
                case DBN -> "Dot-Bracket Notation";
            };
        }

        public static OutputFormat[] getExtendedFormats() {
            return new OutputFormat[]{EXTENDED_BPSEQ};
        }

        public static OutputFormat[] getNonExtendedFormats() {
            Set<OutputFormat> extendedFormats = Set.of(getExtendedFormats());
            return Arrays.stream(values())
                    .filter(o -> !extendedFormats.contains(o))
                    .toArray(OutputFormat[]::new);
        }
    }
}
