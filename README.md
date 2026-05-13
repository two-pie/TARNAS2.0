# EXTRARNAS (Tool for the Analysis of RNA Structures)

EXTRARNAS is a Java application designed to analyze RNA 3D structures and extract their secondary structures and base-pairing interactions. It provides a guided interface to configure and run specialized bioinformatics tools inside isolated environments, generating standardized outputs.

## Prerequisites

- **Java 21** (or higher) `jre` installed on your system. See, e.g., [Java Downloads](https://www.oracle.com/it/java/technologies/downloads/)
- **Docker Desktop** installed and running on your system. See [Docker.com](https://www.docker.com/products/docker-desktop/)
- **JAVAFX 21** (optional). See [JavaFX Download](https://www.oracle.com/java/technologies/downloads/javafx/)

## Features & Workflow

When running the application, you will be guided through a configuration process:

1. **Shared Volume / Workspace Setup:** The software will ask you to specify a local directory to be shared with the Docker container. This is necessary to pass input data to the extraction tools and to securely retrieve their outputs.

2. **Add CSV molecules list:** Use this button to select a CSV file from your system and load the molecules to be processed. The file must contain two columns: `id` and `chain`, where `id` is the PDB code (4 letters or digits) and `chain` is the chain identifier in the PDB file (one or more letters, or `*` to process all chains in the structure). Example:

| id   | chain |
|------|-------|
| 4PLX | A     |
| 6QNR | A     |
| 2KOC | *     |

*Note: `*` means that all chains in the structure will be processed.*

3. **Tool Selection:** Choose which extraction tool you want to run.

4. **Structure Analysis Level:**
   * **Secondary Structure:** Analyzes and extracts canonical cis Watson–Crick base pairs.
   * **Extended Secondary Structure:** Extracts all Leontis–Westhof base-pair types.

5. **Output Formats:**
   * **BPSEQ:** Standard output reporting only canonical base pairs.
   * **Extended BPSEQ:** An extended BPSEQ format in which the third column corresponds to canonical cis Watson–Crick pairs (as in BPSEQ), while the remaining columns encode the other base-pair types according to the Leontis–Westhof nomenclature.

   The extended format includes the following columns:

   | Index | Nucleotide | cWW | tWW | cWH | tWH | cWS | tWS | cHH | tHH | cHS | tHS | cSS | tSS |
      |-------|------------|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|

### Canonical vs Non-Canonical Base Pairs
In EXTRARNAS, **Canonical base pairs** correspond strictly to standard cis Watson-Crick pairs (A-U, G-C, G-U).

## The Role of Docker

EXTRARNAS leverages **Docker containers** to run its underlying analysis tools.
Using containers guarantees that:
- You do not need to manually install complex third-party tools, compilers, or specific language versions (such as old Python versions) on your machine.
- Executions are perfectly reproducible and run in an isolated environment.
- *Note:* The `docker/` folder distributed alongside the application contains the Dockerfiles required by the software to build and run the images correctly.

## Build from Source

To compile the application and generate the executable package, run:

```bash
mvn clean package
```
This will place the executable `EXTRARNAS-fat.jar` and its required `docker/` folder in the `target/` directory.

## Usage

To start EXTRARNAS, simply run the jar file via terminal. Make sure you are in the same folder where the `.jar` and the `docker/` directory reside. Make sure to also have installed and running Docker. For the application we provide three main bundle (win, mac, linux), chose the specific jar for your OS. If your OS is not listed or the application is not starting up see the next section (JAVAFX Startup problem).

Run the following command on your specific jar:

```bash
java -jar EXTRARNAS-specificBundle-fat.jar
```

### JAVFX Startup problem
If you have problem at the startup it's due to a javafx startup error. You have to download **JAVAFX 21** and run the following command

```bash
java --module-path "path\to\javafx\lib" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.graphics,javafx.media -jar EXTRARNAS-fat.jar
```

### Molecules loading error

If you receive an error when loading the CSV, try to add this option:
```bash
java -Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize=true ...
```

*(You don't need to specify extra complex classpath flags since it's a "fat" jar containing all its dependencies).*

## License

Please refer to the `LICENSE` file in the repository for more details.
