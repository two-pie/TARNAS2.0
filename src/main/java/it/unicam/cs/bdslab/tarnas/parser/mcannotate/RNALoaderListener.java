package it.unicam.cs.bdslab.tarnas.parser.mcannotate;

import it.unicam.cs.bdslab.tarnas.OutputGrammarBaseListener;
import it.unicam.cs.bdslab.tarnas.OutputGrammarParser;
import it.unicam.cs.bdslab.tarnas.parser.models.*; 
import java.util.ArrayList;

import org.antlr.v4.runtime.ParserRuleContext;
public class RNALoaderListener extends OutputGrammarBaseListener {

    private final SecondaryStructure.Builder builder;
    private final StringBuilder sequenceBuilder;

    public RNALoaderListener() {
        // Istanziamo il Builder. 
        // Nota: Assumo che 'Builder' sia 'static' nella classe SecondaryStructure.
        this.builder = new SecondaryStructure.Builder();

        // Inizializziamo le liste vuote nel builder, altrimenti 'builder.addPair' 
        // lancerebbe NullPointerException dato che nel codice fornito non sono istanziate.
        this.builder.setPairs(new ArrayList<>());
        this.builder.setCanonical(new ArrayList<>());

        this.sequenceBuilder = new StringBuilder();
    }

    /**
     * Ritorna l'oggetto SecondaryStructure costruito alla fine del parsing.
     */
    public SecondaryStructure getResult() {
        this.builder.setSequence(sequenceBuilder.toString());
        return this.builder.build();
    }

    @Override
    public void enterSequenceLine(OutputGrammarParser.SequenceLineContext ctx) {
        // Otteniamo il testo grezzo del valore (es. "GTP" o "G" o "G C3p...")
        String rawValue = ctx.val.getText().trim();
        
        String extractedBase = "";

        // CASO 1: È una singola base standard (A, C, G, U)
        if (rawValue.length() == 1 && "ACGU".contains(rawValue)) {
            extractedBase = rawValue;
        } 
        // CASO 2: È una stringa lunga (es. "GTP", "G C3p...", "ATP")
        // Prendiamo il primo carattere se è una base valida
        else if (rawValue.length() > 0) {
            char firstChar = rawValue.charAt(0);
            if ("ACGU".indexOf(firstChar) >= 0) {
                extractedBase = String.valueOf(firstChar);
            }
        }

        // Se abbiamo trovato una base valida, la aggiungiamo
        if (!extractedBase.isEmpty()) {
            sequenceBuilder.append(extractedBase);
        } else {
            // Opzionale: gestire il caso di base non riconosciuta (es. 'X' o parsing fallito)
            // System.err.println("Base non riconosciuta alla riga " + ctx.id.getText() + ": " + rawValue);
        }
    }

    @Override
    public void enterStackingLine(OutputGrammarParser.StackingLineContext ctx) {
        int p1 = parseId(ctx.id1.getText());
        int p2 = parseId(ctx.id2.getText());

        // Creiamo la coppia di tipo STACKING
        Pair pair = createPair(p1, p2, BondType.STACKING);
        builder.addPair(pair);
    }

    @Override
    public void enterBondLine(OutputGrammarParser.BondLineContext ctx) {
        int p1 = parseId(ctx.id1.getText());
        int p2 = parseId(ctx.id2.getText());
        
        String base1 = ctx.b1.getText();
        String base2 = ctx.b2.getText();
        String info = ctx.info.getText(); // Es: "Ww/Ws pairing..."

        BondType type = determineBondType(base1, base2, info);
        
        Pair pair = new Pair(p1, p2, type);
        builder.addPair(pair);
    }


    private int parseId(String text) {
        // Rimuove 'A' o altri caratteri non numerici e parsa l'intero
        // Es: "A12" -> 12
        String number = text.replaceAll("[^0-9]", "");
        return Integer.parseInt(number);
    }

    private Pair createPair(int p1, int p2, BondType type) {
        Pair p = new Pair(p1, p2, type);
        return p;
    }

    private BondType determineBondType(String b1, String b2, String info) {
        // Logica per distinguere Canonico da Non-Canonico
        
        boolean isStandardBases = (b1.equals("G") && b2.equals("C")) ||
                                  (b1.equals("C") && b2.equals("G")) ||
                                  (b1.equals("A") && b2.equals("U")) ||
                                  (b1.equals("U") && b2.equals("A"));

        boolean isCisWatsonCrick = info.contains("Ww/Ww") || 
                                   info.contains("cis XIX") || 
                                   info.contains("cis XX");

        if (isStandardBases && isCisWatsonCrick) {
            return BondType.CANONICAL;
        } else {
            return BondType.NON_CANONICAL;
        }
    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
        // Stampa il nome della classe del contesto attuale
        System.out.println("Entrato in regola: " + ctx.getClass().getSimpleName() + " -> Testo: " + ctx.getText());
    }   
}