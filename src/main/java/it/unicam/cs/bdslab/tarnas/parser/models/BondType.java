package it.unicam.cs.bdslab.tarnas.parser.models;

public enum BondType {
    CANONICAL(""),
    LEONTIS_WESTHOF_cWW("cWW"),
    NON_CANONICAL("non-canonical"),
    LEONTIS_WESTHOF_tWW("tWW"),
    // ecc
    STACKING("stacking"),
    UNKNOWN("unknown");
    private final String info;

    BondType(String info) {
        this.info = info;
    }

    public String getInfo() {
        return info;
    }
}