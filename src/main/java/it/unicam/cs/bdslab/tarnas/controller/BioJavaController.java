package it.unicam.cs.bdslab.tarnas.controller;

import org.biojava.nbio.structure.Chain;
import org.biojava.nbio.structure.Structure;
import org.biojava.nbio.structure.StructureImpl;
import org.biojava.nbio.structure.io.PDBFileReader;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Predicate;

public class BioJavaController {

    private static final BioJavaController instance = new BioJavaController();
    private final PDBFileReader reader;

    public BioJavaController() {
        reader = new PDBFileReader();
    }

    public static BioJavaController getInstance() {
        return instance;
    }

    public Structure readPDBFile(String path, Predicate<Chain> chainFilter) throws IOException {
        var structure = reader.getStructure(path);

        var filtered = new StructureImpl();


        for (Chain chain : structure.getChains()) {
            if (chainFilter.test(chain)) {
                filtered.addChain(chain);
            }
        }
        return filtered;
    }

    public void writePDBFile(Structure structure, String outputPath) throws IOException {
        String pdbContent = structure.toPDB();
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(pdbContent);
        }
    }

    public Predicate<Chain> getDefaultChainFilter(String allowedIdsStr) {
        Predicate<Chain> idFilter;
        // accept all ids
        if (allowedIdsStr.equals("*")) {
            idFilter = chain -> true; // accept all IDs
        }
        // accept only specified ids
        else {
            var allowedIds = new HashSet<>(Arrays.asList(allowedIdsStr.split(";")));
            idFilter = chain -> allowedIds.contains(chain.getId());
        }
        // combine id filter with rna filter
        return idFilter.and(getRNAFilter());
    }

    private Predicate<Chain> getRNAFilter() {
        return chain -> {
            String seq = chain.getAtomSequence().toUpperCase();
            return seq.matches("[AGCU]+"); // only RNA residues
        };
    }

/* EXAMPLE USAGE
    public static void main(String[] args) throws IOException {
        var controller = BioJavaController.getInstance();
        var filter = controller.getDefaultChainFilter("A;B");

        var filtered = controller.readPDBFile("/to/4D00.pdb", filter);
        controller.writePDBFile(filtered, "/to/4D00_filtered.pdb");
    }

 */

}
