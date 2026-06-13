package it.unicam.cs.bdslab.tarnas.controller;

import it.unicam.cs.bdslab.tarnas.parser.listeners.JSON.JSONLexer;
import it.unicam.cs.bdslab.tarnas.parser.listeners.JSON.JSONParser;
import it.unicam.cs.bdslab.tarnas.parser.listeners.barnaba.*;
import it.unicam.cs.bdslab.tarnas.parser.listeners.bpnet.*;
import it.unicam.cs.bdslab.tarnas.parser.listeners.fred.JSONFr3dListener;
import it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate.*;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnapolis.*;
import it.unicam.cs.bdslab.tarnas.parser.listeners.rnaview.*;
import it.unicam.cs.bdslab.tarnas.parser.listeners.x3dna.JSONX3dnaListener;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.output.RNASecondaryStructurePrinter;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtendedBPSEQExportController {

    public static final Logger logger = Logger.getLogger(ExtendedBPSEQExportController.class.getName());

    private static final ExtendedBPSEQExportController instance = new ExtendedBPSEQExportController();
    private final RNASecondaryStructurePrinter printer = new RNASecondaryStructurePrinter();

    private ExtendedBPSEQExportController() {
    }

    public static ExtendedBPSEQExportController getInstance() {
        return instance;
    }

    public int exportForTool(TOOL tool, Path sharedDirectory,
        RNASecondaryStructurePrinter.OutputFormat secondaryStrcutureFormat,
        RNASecondaryStructurePrinter.OutputFormat extendendStructureFormat,
        Map<String, String> supportSequences
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
            if (!tool.giveStructure())
                item.structure().setSequence(supportSequences.getOrDefault(sanitize(item.baseName()),
                        "N".repeat(
                                item.structure().getPairs().stream()
                                        .mapToInt(p -> Math.max(p.getPos1(), p.getPos2()))
                                        .max()
                                        .orElse(0)
                        )
                ));

            if (secondaryStrcutureFormat != null) {
                String content = printer.printCanonicalBPSEQ(item.structure());
                String fileName = sanitize(item.baseName()) + item.suffix() + "_" + tool.getName() + ".bpseq.txt";
                Path outputFile = outputDir.resolve(fileName);
                Files.writeString(outputFile, content, StandardCharsets.UTF_8);
            }
            if (extendendStructureFormat != null) {
                String content = printer.printExtendedBPSEQ(item.structure());
                String fileName = sanitize(item.baseName()) + item.suffix() + "_" + tool.getName() + ".bpseqe.txt";
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

    public List<ExportItem> loadStructures(TOOL tool, Path sharedDirectory) throws IOException {
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
            var lexer = new RNAviewGrammarLexer(CharStreams.fromPath(file));
            var parser = new RNAviewGrammarParser(new CommonTokenStream(lexer));
            var listener = new RNAviewCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.rnaviewFile());
            result.add(new ExportItem(baseNameFor(TOOL.RNAVIEW, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseRNApolis(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".3db")) {
            var lexer = new RNApolisGrammarLexer(CharStreams.fromPath(file));
            var parser = new RNApolisGrammarParser(new CommonTokenStream(lexer));
            var listener = new RNApolisCustomListener();
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
            var lexer = new BarnabaGrammarLexer(CharStreams.fromPath(file));
            var parser = new BarnabaGrammarParser(new CommonTokenStream(lexer));
            var listener = new BarnabaCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.barnabaFile());
            result.add(new ExportItem(baseNameFor(TOOL.BARNABA, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseBPNET(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".out")) {
            var lexer = new BpnetGrammarLexer(CharStreams.fromPath(file));
            var parser = new BpnetGrammarParser(new CommonTokenStream(lexer));
            var listener = new BpnetParserCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.bpnetFile());
            result.add(new ExportItem(baseNameFor(TOOL.BPNET, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseFR3D(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".json")) {
            var lexer = new JSONLexer(CharStreams.fromPath(file));
            var parser = new JSONParser(new CommonTokenStream(lexer));
            var listener = new JSONFr3dListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.json());
            result.add(new ExportItem(baseNameFor(TOOL.FR3D, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseX3DNA(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".json")) {
            var lexer = new JSONLexer(CharStreams.fromPath(file));
            var parser = new JSONParser(new CommonTokenStream(lexer));
            var listener = new JSONX3dnaListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.json());
            result.add(new ExportItem(baseNameFor(TOOL.X3DNA, file), "", listener.getStructure()));
        }
        return result;
    }

    private List<ExportItem> parseMCAnnotate(Path folder) throws IOException {
        List<ExportItem> result = new ArrayList<>();
        for (Path file : listFiles(folder, ".txt")) {
            var lexer = new McAnnotateGrammarLexer(CharStreams.fromPath(file));
            var parser = new McAnnotateGrammarParser(new CommonTokenStream(lexer));
            var listener = new McAnnotateCustomListener();
            ParseTreeWalker.DEFAULT.walk(listener, parser.mcAnnotateFile());
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

        return Arrays.stream((switch (tool) {
            case RNAVIEW -> stripSuffixes(name, ".pdb.out", ".out");
            case RNAPOLIS_ANNOTATOR -> stripSuffixes(name, ".3db");
            case BARNABA -> stripSuffixes(name, ".ANNOTATE.pairing.out", ".ANNOTATE.stacking.out", ".out");
            case BPNET -> {
                String cleaned = stripSuffixes(name, ".out");
                int split = cleaned.indexOf('.');
                yield split > 0 ? cleaned.substring(0, split) : cleaned;
            }
            case FR3D -> stripSuffixes(name, "_basepair.json", ".json");
            case X3DNA -> stripSuffixes(name, "_dssr.json", ".json");
            case MC_ANNOTATE -> stripSuffixes(name, ".txt");
        }).split("_"))
                .limit(2)
                .collect(Collectors.joining("_"))
                .toUpperCase(Locale.ROOT);
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

    private String sanitize(String value) {
        String normalized = Objects.requireNonNullElse(value, "unknown").trim();
        if (normalized.isEmpty()) return "unknown";
        return normalized
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .toUpperCase(Locale.ROOT);
    }

    public record ExportItem(String baseName, String suffix, ExtendedRNASecondaryStructure structure) {
    }
}