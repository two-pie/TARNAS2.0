package it.unicam.cs.bdslab.tarnas.models;

public enum StructureStatus {
    LOADED,
    PROCESSED,
    ERROR;

    public String translate() {
        return switch (this) {
            case LOADED -> "Loaded";
            case PROCESSED -> "Processed";
            case ERROR -> "Error";
        };
    }
}
