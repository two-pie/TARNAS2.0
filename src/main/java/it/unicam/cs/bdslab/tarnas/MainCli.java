package it.unicam.cs.bdslab.tarnas;

import it.unicam.cs.bdslab.tarnas.controller.DockerController;
import it.unicam.cs.bdslab.tarnas.controller.ExtendedBPSEQExportController;
import it.unicam.cs.bdslab.tarnas.controller.IOController;
import it.unicam.cs.bdslab.tarnas.models.StructureInfo;
import it.unicam.cs.bdslab.tarnas.parser.output.RNASecondaryStructurePrinter;
import it.unicam.cs.bdslab.tarnas.view.HomeController;
import it.unicam.cs.bdslab.tarnas.view.utils.TOOL;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static it.unicam.cs.bdslab.tarnas.view.utils.TOOL.*;

public class MainCli {
    private static String USAGE = "Usage: ExtrarnasCLI [shared_folder] [csv]";
    private static Logger logger = LoggerFactory.getLogger(MainCli.class);
    private static DockerController dockerController = DockerController.getInstance();
    private static ExtendedBPSEQExportController extendedBPSEQExportController = ExtendedBPSEQExportController.getInstance();
    private static IOController ioController = IOController.getInstance();
    private static Map<TOOL, Runnable> actionsMap = Map.of(
            RNAPOLIS_ANNOTATOR, () -> {
                try {
                    dockerController.rnapolisAnnotator();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            RNAVIEW, () -> {
                try {
                    dockerController.rnaView();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            BARNABA, () -> {
                try {
                    dockerController.baRNAba();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            BPNET, () -> {
                try {
                    dockerController.bpnet();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            FR3D, () -> {
                try {
                    dockerController.fr3d();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            },
            X3DNA, () -> {
                try {
                    dockerController.x3dnaBy(HomeController.dockerX3DNAContainer);
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            },
            MC_ANNOTATE, () -> {
                try {
                    dockerController.mcAnnotate();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

    public static void main(String[] args) {
        Options options = new Options();

        options
                .addOption("h", "help", false, "print this message");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try
        {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h") || cmd.getArgs().length < 2) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(USAGE, options);
                return;
            }

            String sharedDirectory = cmd.getArgList().get(0);
            String csvFilePath = cmd.getArgList().get(1);


            if (sharedDirectory == null || csvFilePath == null) {
                throw new IllegalArgumentException("Both shared_folder and csv options are required.");
            }
            ioController.loadDirectory(Paths.get(sharedDirectory));

            logger.info("building controller");

            dockerController.buildDockerContainerBy(
                    new File(HomeController.dockerfileAllToolsPath),
                    HomeController.dockerAllToolsImage,
                    HomeController.dockerAllToolsImageTag,
                    HomeController.dockerAllToolsContainer,
                    Paths.get(sharedDirectory));

            dockerController.buildxDockerContainerBy(
                    new File(HomeController.dockerfileX3DNAPath),
                    HomeController.dockerX3DNAImage,
                    HomeController.dockerX3DNAImageTag,
                    HomeController.dockerX3DNAContainer);

            List<StructureInfo> result = dockerController.preprocessCsvAndCollectStructures(
                    Paths.get(sharedDirectory),
                    Paths.get(csvFilePath)
            );

            logger.info("Preprocessing completed. Collected {} structures.", result.size());

            executeCommand(new HashSet<>(
                    Arrays.stream(values()).toList()
            ), true);
        }
        catch (ParseException | IllegalArgumentException | IOException | InterruptedException e) {
            System.err.println("Error parsing command line options: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE, options);
        }
    }

    /**
     * This method can be used to execute the pipeline:
     * 1. Execute the selected tools (e.g., RNAPolis Annotator, RNAView...)
     * 2. Generate extended BPSEQ or normal BPSEQ files based on the output of the
     * tools and the user's choice.
     *
     * @param selectedTools       the set of tools selected by the user to run
     * @param outputExtendedBPSEQ if the output format should be extended BPSEQ
     *                            (true) or normal BPSEQ (false)
     */
    private static void executeCommand(Set<TOOL> selectedTools, boolean outputExtendedBPSEQ) throws IOException {

        Map<String, String> supportSequences = Map.of();
        if (selectedTools.stream().anyMatch(Predicate.not(TOOL::giveStructure))) {
            actionsMap.get(RNAPOLIS_ANNOTATOR).run();
            supportSequences = extendedBPSEQExportController.loadStructures(TOOL.RNAPOLIS_ANNOTATOR, ioController.getSharedDirectory())
                    .stream()
                    .map(e -> Map.entry(e.baseName(), e.structure().getSequence()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        for (TOOL tool : selectedTools) {
            Runnable action = actionsMap.get(tool);
            if (action != null) {
                action.run();
            }
            extendedBPSEQExportController.exportForTool(
                    tool,
                    ioController.getSharedDirectory(),
                    RNASecondaryStructurePrinter.OutputFormat.BPSEQ,
                    outputExtendedBPSEQ
                            ? RNASecondaryStructurePrinter.OutputFormat.EXTENDED_BPSEQ
                            : null,
                    supportSequences
            );

        }
    }
}
