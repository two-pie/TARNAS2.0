#  EXTRARNAS (Tool for the Analysis of RNA Structures)

EXTRARNAS is a Java application designed to analyze RNA 3D structures and extract their secondary structures and base-pairing interactions. It provides a guided interface to configure and run specialized bioinformatics tools inside isolated environments, generating standardized outputs.

## Features & Workflow

When running the application, you will be guided through a configuration process:

1. **Shared Volume / Workspace Setup:** The software will ask you to specify a local directory to be shared with the Docker container. This is necessary to pass input data to the tools and securely retrieve their outputs.
2. **Tool Selection:** Choose which structural analysis tool you want to launch.
3. **Structure Analysis Level:** 
   * **Secondary Structure:** Analyzes and extracts classical pairings.
   * **Extended Secondary Structure:** Extracts a richer set of interactions, going beyond standard bounds.
4. **Output Formats:**
   * **BPSEQ:** Standard output reporting only canonical base pairs.
   * **Extended BPSEQ:** A modified sequence format that includes all types of limits and non-canonical bonds found by the tool.

### Canonical vs Non-Canonical Base Pairs
In EXTRARNAS, **Canonical base pairs** correspond strictly to standard Watson-Crick pairs (A-U and G-C), trans and cis.

## The Role of Docker

EXTRARNAS leverages **Docker containers** to run its underlying analysis tools. 
Using containers guarantees that:
- You do not need to manually install complex third-party tools, compilers, or specific language versions (such as old Python versions) on your machine.
- Executions are perfectly reproducible and run in an isolated environment.
- *Note:* The `docker/` folder distributed alongside the application contains the Dockerfiles required by the software to build and run the images correctly.

## Prerequisites

- **Java 21** (or higher).
- **Docker** installed and running on your system.

## Build from Source

To compile the application and generate the executable package, run:

```bash
mvn clean package
```
This will place the executable `EXTRARNAS-fat.jar` and its required `docker/` folder in the `target/` directory.

## Usage

To start EXTRARNAS, simply run the jar file via terminal. Make sure you are in the same folder where the `.jar` and the `docker/` directory reside. Make sure to also have installed and running Docker. For start the application run the following command:

```bash
java -jar EXTRARNAS-fat.jar
```

*(You don't need to specify extra complex classpath flags since it's a "fat" jar containing all its dependencies).*

## License

Please refer to the `LICENSE` file in the repository for more details.