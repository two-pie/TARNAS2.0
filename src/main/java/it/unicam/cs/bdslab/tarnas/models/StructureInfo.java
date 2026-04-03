package it.unicam.cs.bdslab.tarnas.models;

/**
 * Represents the structure information of an RNA loaded from a file
 */
public class StructureInfo {
    public String name;
    public String chain;
    public String location;
    public StructureStatus status;

    public StructureInfo(String name, String chain, String location, StructureStatus status) {
        this.name = name;
        this.chain = chain;
        this.location = location;
        this.status = status;
    }

    public StructureInfo(String name, String chain, String location) {
        this(name, chain, location, StructureStatus.LOADED);
    }

    public String getName() {
        return name;
    }

    public String getChain() {
        return chain;
    }

    public String getLocation() {
        return location;
    }

    public StructureStatus getStatus() {
        return status;
    }

    public void setStatus(StructureStatus status) {
        this.status = status;
    }
}
