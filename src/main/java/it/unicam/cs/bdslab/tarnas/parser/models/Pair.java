package it.unicam.cs.bdslab.tarnas.parser.models;

public class Pair {
    
    private int pos1;
    private int pos2;
    private BondType type;

    public Pair(int pos1, int pos2, BondType type) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.type = type;
    }

    public int getPos1() {
        return pos1;
    }

    public int getPos2() {
        return pos2;
    }

    public BondType getType() {
        return type;
    }
}
