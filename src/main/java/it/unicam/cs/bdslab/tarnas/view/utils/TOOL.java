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
}
