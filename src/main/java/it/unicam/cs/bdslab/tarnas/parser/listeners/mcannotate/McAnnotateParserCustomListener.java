package it.unicam.cs.bdslab.tarnas.parser.listeners.mcannotate;

import it.unicam.cs.bdslab.tarnas.parser.models.BondType;
import it.unicam.cs.bdslab.tarnas.parser.models.ExtendedRNASecondaryStructure;
import it.unicam.cs.bdslab.tarnas.parser.models.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Custom listener for MC-Annotate parser that builds an ExtendedRNASecondaryStructure
 * from parsed MC-Annotate output files.
 * 
 * Properly extracts Leontis-Westhof base pair classifications from the edge annotations.
 * 
 * @author Federico Di Petta
 */

public class McAnnotateParserCustomListener implements MCAnnotateParserListener {

    private ExtendedRNASecondaryStructure.Builder structureBuilder;

    private String sequence;
    private final Map<Integer, Integer> positionMap = new HashMap<>();

    public ExtendedRNASecondaryStructure getStructure() {
        return structureBuilder.build();
    }

    @Override
    public void enterMcAannotateFile(MCAnnotateParser.McAannotateFileContext ctx) {
        structureBuilder = new ExtendedRNASecondaryStructure.Builder();
    }

    @Override
    public void exitMcAannotateFile(MCAnnotateParser.McAannotateFileContext ctx) {

    }

    @Override
    public void enterResidueSection(MCAnnotateParser.ResidueSectionContext ctx) {
        sequence = "";
    }

    @Override
    public void exitResidueSection(MCAnnotateParser.ResidueSectionContext ctx) {
        structureBuilder.setSequence(sequence);
    }

    @Override
    public void enterResidueElement(MCAnnotateParser.ResidueElementContext ctx) {
        if(ctx.NUCLEOTIDE() != null) {
            sequence += (ctx.NUCLEOTIDE().getText());
            int position = Integer.parseInt(ctx.STRAND_POSITION().getText().substring(1));
            positionMap.put(position, positionMap.size());
        }
    }

    @Override
    public void exitResidueElement(MCAnnotateParser.ResidueElementContext ctx) {
    }

    @Override
    public void enterAdjStackingSection(MCAnnotateParser.AdjStackingSectionContext ctx) {
    }

    @Override
    public void exitAdjStackingSection(MCAnnotateParser.AdjStackingSectionContext ctx) {
    }

    @Override
    public void enterAdjStackingElement(MCAnnotateParser.AdjStackingElementContext ctx) {
    }

    @Override
    public void exitAdjStackingElement(MCAnnotateParser.AdjStackingElementContext ctx) {
    }

    @Override
    public void enterNonAdjStackingSection(MCAnnotateParser.NonAdjStackingSectionContext ctx) {
    }

    @Override
    public void exitNonAdjStackingSection(MCAnnotateParser.NonAdjStackingSectionContext ctx) {
    }

    @Override
    public void enterNonAdjStackingElement(MCAnnotateParser.NonAdjStackingElementContext ctx) {
        structureBuilder.addPair(buildPair(ctx.NAS_STACK().getText(), BondType.fromString("stacking")));
    }

    @Override
    public void exitNonAdjStackingElement(MCAnnotateParser.NonAdjStackingElementContext ctx) {
    }

    @Override
    public void enterBasePairsSection(MCAnnotateParser.BasePairsSectionContext ctx) {
    }

    @Override
    public void exitBasePairsSection(MCAnnotateParser.BasePairsSectionContext ctx) {
    }

    @Override
    public void enterBasePairsElement(MCAnnotateParser.BasePairsElementContext ctx) {

        structureBuilder
                .addPair(buildPair(ctx.POSITION_PAIR().getText(),
                        getBondType(ctx.ORIENTATION(), ctx.BOND().getFirst().getText()))
                );
    }

    private BondType getBondType(TerminalNode orientation, String bond) {
        if(orientation == null || orientation.getText().isEmpty()) return BondType.UNKNOWN;

        String o = orientation.getText().equals("cis") ? "c" : "t";

        String[] edges = bond.split("/");
        String edge1 = edges[0].substring(0,1);
        String edge2 = edges[1].substring(0,1);

        return BondType.fromString(o+edge1+edge2);
    }

    private Pair buildPair(String pos, BondType bondType) {
        String[] positions = pos.split("-");

        int pos1 = positionMap.get(Integer.parseInt(positions[0].substring(1)));
        int pos2 = positionMap.get(Integer.parseInt(positions[1].substring(1)));
        String nucl1 = String.valueOf(sequence.charAt(pos1));
        String nucl2 = String.valueOf(sequence.charAt(pos2));

        return new Pair(pos1, pos2, nucl1, nucl2, bondType);
    }

    @Override
    public void exitBasePairsElement(MCAnnotateParser.BasePairsElementContext ctx) {
    }

    @Override
    public void visitTerminal(TerminalNode terminalNode) {
    }

    @Override
    public void visitErrorNode(ErrorNode errorNode) {
    }

    @Override
    public void enterEveryRule(ParserRuleContext parserRuleContext) {
    }

    @Override
    public void exitEveryRule(ParserRuleContext parserRuleContext) {
    }
}