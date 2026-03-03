package it.unicam.cs.bdslab.tarnas.parser.output;

import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.models.Pair;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RNASecondaryStrcuturePrinterTest {

    @Test
    public void test() {
        List<Pair> pairs = new ArrayList<>();
        pairs.add(new Pair(0, 3, BondType.LEONTIS_WESTHOF_cWH));
        pairs.add(new Pair(1, 3, BondType.LEONTIS_WESTHOF_cHS));
        ExtendedRNASecondaryStructure structure = new ExtendedRNASecondaryStructure("ACGU", pairs);

        RNASecondartyStrucutrePrinter printer = new RNASecondartyStrucutrePrinter();
        System.out.println(printer.printExtendedBPSEQ(structure));
    }
}
