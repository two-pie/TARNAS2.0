package it.unicam.cs.bdslab.tarnas.controller;

import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.BarnabaLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.BarnabaParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.BarnabaParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet.BPNETLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet.BPNETParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet.BPNETParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fred.Fr3dParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.Fr3dLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fr3d.Fr3dParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.MCAnnotateParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.McAnnotateParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnapolis.RNApolisLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnapolis.RNApolisParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnapolis.RNApolisParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnaview.RNAviewLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnaview.RNAviewParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnaview.RNAviewParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNALexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNAParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.X3DNAParserCustomListener;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.output.RNASecondaryStrucutrePrinter;
import it.unicam.cs.bdslab.tarnas.view.utils.TOOL;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ExtendedBPSEQExportController {

    public static final Logger logger = Logger.getLogger("it.unicam.cs.bdslab.tarnas.controller.ExtendedBPSEQExportController");

    private static final ExtendedBPSEQExportController instance = new ExtendedBPSEQExportController();
    private final RNASecondaryStrucutrePrinter printer = new RNASecondaryStrucutrePrinter();

    private ExtendedBPSEQExportController() {
    }

    public static ExtendedBPSEQExportController getInstance() {
        return instance;
    }

    public int exportForTool(TOOL tool, Path sharedDirectory,
        RNASecondaryStrucutrePrinter.OutputFormat secondaryStrcutureFormat,
        RNASecondaryStrucutrePrinter.OutputFormat extendendStructureFormat
    ) throws IOException {
        if (tool == null || sharedDirectory == null) return 0;

        List<ExportItem> structures = loadStructures(tool, sharedDirectory);
        if (structures.isEmpty()) {
            logger.info("No structures found for tool " + tool.getName() + " in " + sharedDirectory);
            return 0;
        }

        Path outputDir = sharedDirectory.resolve("output");
        Files.createDirectories(outputDir);

        int exported = 0;
        for (ExportItem item : structures) {
            ensureSequence(item.structure());
            if (secondaryStrcutureFormat != null) {
                String content = printer.printExtendedBPSEQ(item.structure());
                String fileName = tool.getName() + "_" + sanitize(item.baseName()) + item.suffix() + ".bpseq";
                Path outputFile = outputDir.resolve(fileName);
                Files.writeString(outputFile, content, StandardCharsets.UTF_8);
            } else if (extendendStructureFormat != null) {
                String content = printer.printBPSEQ(item.structure());
                String fileName = tool.getName() + "_" + sanitize(item.baseName()) + item.suffix() + ".bpseq";
                Path outputFile = outputDir.resolve(fileName);
                Files.writeString(outputFile, content, StandardCharsets.UTF_8);
            }
            exported++;
        }

        return exported;
    }

    public Path getOutputDirectory(Path sharedDirectory) {
        return sharedDirectory.resolve("output");
    }

    private List<ExportItem> loadStructures(TOOL tool, Path sharedDirectory) throws IOException {
        return switch (tool) {
            case RNAVIEW -> parseRNAView(sharedDirectory.resolve("rnaview-output"));
            case RNAPOLIS_ANNOTATOR -> parseRNApolis(sharedDirectory.resolve("rnapolis-output"));
            case BARNABA -> parseBarnaba(sharedDirectory.resolve("barnaba-output"));
            case BPNET -> parseBPNET(sharedDirectory.resolve("bpnet-output"));
            case FR3D -> parseFR3D(sharedDirectory.resolve("fr3d-output"));
            case X3DNA -> parseX3DNA(sharedDirectory.resolve("x3dna-output"));
            case MC_ANNOTATE -> parseMCAnnotate(sharedDirectory.resolve("mc-annotate-output"));
        };
    }

    private List<ExportItem> parseRNAView(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, "pdb.out")) {
            var lexer = new RNAviewLexer(CharStreams.fromPath(file));
            var parser = new RNAviewParser(new CommonTokenStream(lexer));
            var listener = new RNAviewParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.rnaviewFile());
            result.add(new ExportItem(baseNameFor(TOOL.RNAVIEW, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseRNApolis(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".3db")) {
            var lexer = new RNApolisLexer(CharStreams.fromPath(file));
            var parser = new RNApolisParser(new CommonTokenStream(lexer));
            var listener = new RNApolisParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.rnapolisFile());
            List<ExtendedRNASecondaryStructure> structures = listener.getStructures();
            for (int i = 0; i < structures.size(); i++) {
                String suffix = structures.size() > 1 ? "_" + (i + 1) : "";
                result.add(new ExportItem(baseNameFor(TOOL.RNAPOLIS_ANNOTATOR, file), suffix, structures.get(i)));
            }
        }
        return result;
    }

    private List<ExportItem> parseBarnaba(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".ANNOTATE.pairing.out")) {
            var lexer = new BarnabaLexer(CharStreams.fromPath(file));
            var parser = new BarnabaParser(new CommonTokenStream(lexer));
            var listener = new BarnabaParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.barnabaFile());
            result.add(new ExportItem(baseNameFor(TOOL.BARNABA, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseBPNET(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".out")) {
            var lexer = new BPNETLexer(CharStreams.fromPath(file));
            var parser = new BPNETParser(new CommonTokenStream(lexer));
            var listener = new BPNETParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.bpnetFile());
            result.add(new ExportItem(baseNameFor(TOOL.BPNET, file), "", listener.getResult()));
        }
        return result;
    }

    private List<ExportItem> parseFR3D(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".json")) {
            var lexer = new Fr3dLexer(CharStreams.fromPath(file));
            var parser = new Fr3dParser(new CommonTokenStream(lexer));
            var listener = new Fr3dParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.fr3dFile());
            result.add(new ExportItem(baseNameFor(TOOL.FR3D, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseX3DNA(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".json")) {
            var lexer = new X3DNALexer(CharStreams.fromPath(file));
            var parser = new X3DNAParser(new CommonTokenStream(lexer));
            var listener = new X3DNAParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.x3dnaFile());
            result.add(new ExportItem(baseNameFor(TOOL.X3DNA, file), "", listener.getResult()));
        }
        return result;
    }

    private List<ExportItem> parseMCAnnotate(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".txt")) {
            var lexer = new MCAnnotateLexer(CharStreams.fromPath(file));
            var parser = new MCAnnotateParser(new CommonTokenStream(lexer));
            var listener = new McAnnotateParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.mcAannotateFile());
            result.add(new ExportItem(baseNameFor(TOOL.MC_ANNOTATE, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<Path> listFiles(Path folder, String suffix) throws IOException {
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private String baseNameFor(TOOL tool, Path file) {
        String name = file.getFileName().toString();

        return switch (tool) {
            case RNAVIEW -> stripSuffixes(name, ".pdb.out", ".out");
            case RNAPOLIS_ANNOTATOR -> stripSuffixes(name, ".3db");
            case BARNABA -> stripSuffixes(name, ".ANNOTATE.pairing.out", ".ANNOTATE.stacking.out", ".out");
            case BPNET -> {
                String cleaned = stripSuffixes(name, ".out");
                int split = cleaned.indexOf('.');
                yield split > 0 ? cleaned.substring(0, split) : cleaned;
            }
            case FR3D -> stripSuffixes(name, "_basepair.json", ".json");
            case X3DNA -> stripSuffixes(name, "_bp_order.dat", ".dat");
            case MC_ANNOTATE -> stripSuffixes(name, ".txt");
        };
    }

    private static String stripSuffixes(String value, String... suffixes) {
        String result = value;
        for (String suffix : suffixes) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
                break;
            }
        }
        return result;
    }

    private void ensureSequence(ExtendedRNASecondaryStructure structure) {
        if (structure == null) return;

        String sequence = structure.getSequence();
        if (sequence != null && !sequence.isBlank()) return;

        int maxPosition = structure.getPairs().stream()
                .mapToInt(p -> Math.max(p.getPos1(), p.getPos2()))
                .max()
                .orElse(-1);

        if (maxPosition < 0) {
            structure.setSequence("");
            return;
        }

        char[] inferred = new char[maxPosition + 1];
        Arrays.fill(inferred, 'N');

        structure.getPairs().forEach(pair -> {
            if (pair.getPos1() >= 0 && pair.getPos1() < inferred.length) {
                inferred[pair.getPos1()] = toNucleotide(pair.getNucleotide1());
            }
            if (pair.getPos2() >= 0 && pair.getPos2() < inferred.length) {
                inferred[pair.getPos2()] = toNucleotide(pair.getNucleotide2());
            }
        });

        structure.setSequence(new String(inferred));
    }

    private char toNucleotide(String value) {
        if (value == null || value.isBlank()) return 'N';
        return Character.toUpperCase(value.strip().charAt(0));
    }

    private String sanitize(String value) {
        String normalized = Objects.requireNonNullElse(value, "unknown").trim();
        if (normalized.isEmpty()) return "unknown";
        return normalized
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .toUpperCase(Locale.ROOT);
    }

    private record ExportItem(String baseName, String suffix, ExtendedRNASecondaryStructure structure) {
    }
}