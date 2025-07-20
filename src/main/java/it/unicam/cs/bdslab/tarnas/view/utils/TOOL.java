package it.unicam.cs.bdslab.tarnas.view.utils;

import java.util.ArrayList;
import java.util.List;

public enum TOOL {
    RNAVIEW("RNAVIEW"),
    RNAPOLIS_ANNOTATOR("RNAPOLIS_ANNOTATOR"),
    BARNABA("BARNABA"),
    BPNET("BPNET"),
    FR3D("FR3D");

    private final String name;

    TOOL(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
