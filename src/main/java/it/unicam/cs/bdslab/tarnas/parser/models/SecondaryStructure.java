package it.unicam.cs.bdslab.tarnas.parser.models;

import java.util.List;

public class SecondaryStructure {
    private String sequence;
    private List<Pair> pairs;
    private List<Pair> canonical;
    /**
     * Base construcutor, monstly for testing purposes.
     */
    public SecondaryStructure(String sequence, List<Pair> pairs) {
        this.sequence = sequence;
        this.pairs = pairs;
    }

    private SecondaryStructure(Builder builder) {
        this.sequence = builder.sequence;
        this.pairs = builder.pairs;
        this.canonical = builder.canonical;
    }

    public String getSequence() {
        return sequence;
    }

    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    public List<Pair> getPairs() {
        return pairs;
    }

    public List<Pair> getCanonical() {
        return canonical;
    }

    public static class Builder {
        private String sequence;
        private List<Pair> pairs;
        private List<Pair> canonical;

        public Builder setSequence(String sequence) {
            this.sequence = sequence;
            return this;
        }

        public Builder setPairs(List<Pair> pairs) {
            this.pairs = pairs;
            return this;
        }

        public Builder setCanonical(List<Pair> canonical) {
            this.canonical = canonical;
            return this;
        }

        public Builder addPair(Pair pair) {
            this.pairs.add(pair);
            if (pair.getType() == BondType.CANONICAL) {
                this.canonical.add(pair);
            }
            return this;
        }

        public SecondaryStructure build() {
            return new SecondaryStructure(this);
        }
    }
}
