package it.unicam.cs.bdslab.tarnas.parser.output;

import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.models.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class RNASecondaryStrcuturePrinterTest {

    @Test
    public void test() {
        List<Pair> pairs = new ArrayList<>();
        pairs.add(new Pair(0, 3, BondType.LEONTIS_WESTHOF_cWH));
        pairs.add(new Pair(1, 3, BondType.LEONTIS_WESTHOF_cHS));
        
        ExtendedRNASecondaryStructure structure = new ExtendedRNASecondaryStructure("ACGU", pairs); 
        RNASecondaryStrucutrePrinter printer = new RNASecondaryStrucutrePrinter();
        String result = printer.printExtendedBPSEQ(structure);
        System.out.println(result);
        assertNotEquals("", result.trim(), "Output should not be empty");
        String[] lines = result.lines().toArray(String[]::new);

        for (int i = 1; i < lines.length; i++) {
            ensureEachNonHeaderLine(lines[i], i, pairs, String.valueOf(structure.getSequence().charAt(i - 1)));
        }
    }

    /**
     * Ensures that each non-header line in the output has at least one column and no empty columns.
     * @param line the line to check
     * @param index the line index 
     * @param pairs the list of pairs
     * @param nucleotide the expected nucleotide at this line
     */
    private void ensureEachNonHeaderLine(String line, int index, List<Pair> pairs, String nucleotide) {
        String[] parts = line.split("\t");
        assertNotEquals(0, parts.length, "Line " + index + " should have at least one column");
        assertEquals(String.valueOf(index), parts[0], "Line " + index + " should have the correct index in the first column (expected: " + index + ", found: '" + parts[0] + "')");
        assertEquals(nucleotide, parts[1], "Line " + index + " should have the correct nucleotide in the second column (expected: " + nucleotide + ", found: '" + parts[1] + "')");
        for (String string : parts) {
            assertEquals(string, string.trim(), "Line " + index + " Should not contain leading or trailing whitespace in columns (found in line: '" + line + "')");
            assertNotEquals("", string, "Line " + index + " should not have empty columns" + " (found empty column in line: '" + line + "')");
        }

        // 2 case: 
        // 1- if for this row there are no pairs, all the columns should be 0
        // 2- if for this row there are pairs, the corresponding columns should not be 0 but the right index
        if (pairs.stream().noneMatch(pair -> pair.getPos1() == index - 1 || pair.getPos2() == index - 1)) {
            // case 1
            IntStream.range(2, parts.length).forEach(i -> {
                assertEquals("0", parts[i], "Line " + index + " should have '0' in column " + i + " if there are no pairs for this nucleotide (found: '" + parts[i].trim() + "')");
            });
        } else {
            Pair pair = pairs.stream().filter(p -> p.getPos1() == index - 1 || p.getPos2() == index - 1).findFirst().orElseThrow();
            int expectedIndex = (pair.getPos1() == index - 1 ? pair.getPos2() : pair.getPos1()) + 1; // +1 because the output is 1-based
            int columnIndex = 2 + BondType.getLeontisWesthofFamily().indexOf(pair.getType());
            assertNotEquals("0", parts[columnIndex], "Line " + index + " should have the index of the paired nucleotide in column " + columnIndex + " for bond type "
                + pair.getType().getInfo() + " (expected index: " + expectedIndex + ", found: '" + parts[columnIndex].trim() + "')");
                assertEquals(String.valueOf(expectedIndex), parts[columnIndex], "Line " + index + " should have the correct index in column " + columnIndex + " (expected: " + expectedIndex + ", found: '" + parts[columnIndex].trim() + "')"   );
        }
    }
}
/**
 * Index	Nucleotide	cWW	tWW	cWH	tWH	cWS	tWS	cHH	tHH	cHS	tHS	cSS	tSS
 *  1	A	0   	0	0	0	0	0	0	0	4	0	0	0
 * 	2	C	0	0	0	0	0	0	0	0	0	0	0	0
 * 	3	G	0	0	0	0	0	0	0	0	1	0	0	0
 * 	4	U	0	0	0	0	0	0	0	0	0	0	0	0	
 * 
 */