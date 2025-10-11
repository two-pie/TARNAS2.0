package it.unicam.cs.bdslab.tarnas.controller;

import org.biojava.nbio.structure.Chain;
import org.biojava.nbio.structure.Structure;
import org.biojava.nbio.structure.StructureImpl;
import org.biojava.nbio.structure.io.PDBFileReader;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

    public List<Structure> readPDBFile(String path, Predicate<Chain> chainFilter) throws IOException {
        Structure structure = reader.getStructure(path);

        List<Structure> singleChainStructures = new ArrayList<>();

        for (Chain chain : structure.getChains()) {
            if (chainFilter.test(chain)) {
                StructureImpl singleChainStructure = new StructureImpl();
                singleChainStructure.addChain(chain);
                singleChainStructures.add(singleChainStructure);
            }
        }

        return singleChainStructures;
    }

    public void writeStructuresWithSingleChain(List<Structure> structures, String baseFilePath) throws IOException {
        for (Structure s : structures) {
            String chainId = s.getChains().get(0).getId();

            String filePath = baseFilePath + "_" + chainId + ".pdb";

            String pdbContent = s.toPDB();
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(pdbContent);
            }
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

    public String downloadPDB(String id) throws IOException {
        return null;
    }

    private Predicate<Chain> getRNAFilter() {
        return chain -> {
            String seq = chain.getAtomSequence().toUpperCase();
            return seq.matches(".*U.*"); // only RNA residues
        };
    }



/*
    public static void main(String[] args) throws IOException {
        var controller = BioJavaController.getInstance();
        var filter = controller.getDefaultChainFilter("A;B");

        var filtered = controller.readPDBFile("/Users/pierohierro/Downloads/6PRV.pdb", filter);
        controller.writePDBFile(filtered, "/Users/pierohierro/Downloads/6PRV_filtered.pdb");
    }
*/

}
