package it.unicam.cs.bdslab.tarnas.parser.models;

public enum BondType {
    CANONICAL(""),
    NON_CANONICAL("non-canonical"),
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