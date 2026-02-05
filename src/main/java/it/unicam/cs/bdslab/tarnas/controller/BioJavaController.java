package it.unicam.cs.bdslab.tarnas.controller;

import org.biojava.nbio.structure.*;
import org.biojava.nbio.structure.io.PDBFileReader;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

    public List<Structure> filterById(Path path, String chainId) throws IOException {
        var structure = reader.getStructure(path.toFile());
        var filter = getFilter(chainId);
        var structures = new ArrayList<Structure>();

        for (Chain chain : structure.getChains()) {
            if (filter.test(chain)) {
                StructureImpl singleChainStructure = new StructureImpl();
                singleChainStructure.addChain(chain);
                structures.add(singleChainStructure);
            }
        }
        return structures;
    }

    public List<Structure> filterByStar(Path path) throws IOException {
        var structure = reader.getStructure(path.toFile());
        var filter = getFilter("*");

        var structures = new ArrayList<Structure>();
        for (Chain chain : structure.getChains()) {
            if (filter.test(chain)) {
                StructureImpl singleChainStructure = new StructureImpl();
                singleChainStructure.addChain(chain);
                structures.add(singleChainStructure);
            }
        }
        return structures;
    }

    public void save(Structure structure, Path dst) throws IOException {
        String pdbContent = structure.toPDB();
        String cifContent = structure.toMMCIF().replace(",", ".");
        try (FileWriter writer = new FileWriter(dst.resolveSibling(dst.getFileName()+".pdb").toFile())) {
            writer.write(pdbContent);
        }
        try (FileWriter writer = new FileWriter(dst.resolveSibling(dst.getFileName()+".cif").toFile())) {
            writer.write(cifContent);
        }

    }

    public Path downloadPDB(String pdbId, String outputFolderPath) throws StructureException, IOException {
        pdbId = pdbId.toUpperCase();
        // Fetch the structure from the web using the ID.
        Structure structure = StructureIO.getStructure(pdbId);

        // Check if it is possible to save it as PDB, otherwise save it as CIF
        boolean requiresCif = structure.getChains().stream()
                .flatMap(chain -> chain.getAtomGroups().stream())
                .flatMap(group -> group.getAtoms().stream())
                .anyMatch(atom -> atom.getPDBserial() > 99999);
        String content = requiresCif ? structure.toMMCIF() : structure.toPDB();
        String extension = requiresCif ? ".cif" : ".pdb";

        // Save
        Path outputPath = Paths.get(outputFolderPath, pdbId + extension);
        return Files.write(outputPath, content.getBytes());
    }

    private Predicate<Chain> getFilter(String allowedIds) {
        Predicate<Chain> idFilter;
        // accept all ids
        if (allowedIds.equals("*"))
            idFilter = chain -> true; // accept all IDs
            // accept only specified id
        else {
            Set<String> allowedSet = new HashSet<>(List.of(allowedIds.split(";")));
            idFilter = chain -> allowedSet.contains(chain.getId());
        }
        // combine id filter with rna filter
        return idFilter.and(getRNAFilter());
    }

    private Predicate<Chain> getRNAFilter() {
        return chain -> {
            String seq = chain.getAtomSequence().toUpperCase();
            return seq.matches(".*U.*"); // only RNA residues
        };
    }

}

