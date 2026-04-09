package it.unicam.cs.bdslab.tarnas.view.utils;


public enum TOOL {
    RNAVIEW("RNAVIEW"),
    RNAPOLIS_ANNOTATOR("RNAPOLIS_ANNOTATOR"),
    BARNABA("BARNABA"),
    BPNET("BPNET"),
    FR3D("FR3D"),
    X3DNA("X3DNA"),
    MC_ANNOTATE("MC_ANNOTATE");

    private final String name;

    TOOL(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * Utiltiy method to get the file name to parse based on the selected tool.
     * @param pdbId the PDB ID of the structure
     * @param chainId the chain ID of the structure
     * @return the file name to parse based on the selected tool, e.g., "1ABC_A.out" for RNAVIEW.
     */
    public String getFileNameToParse(String pdbId, String chainId) {
        return String.format("%s_%s.out", pdbId, chainId);
    }

    @Override
    public String toString() {
        return name.replaceAll("_", " ");
    }
}
