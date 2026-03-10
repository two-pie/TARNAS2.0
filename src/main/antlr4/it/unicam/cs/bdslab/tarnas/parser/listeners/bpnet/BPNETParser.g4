/**
 * ANTLR 4 parser grammar for BPNET/BPFIND output files.
 *
 * This grammar parses base pair information from BPNET/BPFIND tool.
 * Each line contains residue information and base pair annotations
 * with optional triplet information.
 *
 * Format example:
 *      1       1   G ? A       29    29   C ? A    W:WC BP 0.37
 *
 * @author Federico Di Petta
 * @see BPNETLexer
 */
parser grammar BPNETParser;

options {
    tokenVocab = BPNETLexer;
}

// Root rule
bpnetFile
    : residueLine* EOF
    ;

// A residue line can have:
// - Just residue info (unpaired)
// - Residue info + one pair (base pair)
// - Residue info + one pair + additional pair (triplet)
residueLine
    : serialNum=INTEGER pdbNum1=INTEGER base1=BASE insCode1=INS_CODE chain1=CHAIN_ID
      (pairInfo tripletInfo*)?
    ;

// Pair information: paired residue + pair type + BP indicator + e-value
pairInfo
    : pairedSerial=INTEGER pairedPdbNum=INTEGER pairedBase=BASE pairedInsCode=INS_CODE pairedChain=CHAIN_ID
      pairType=PAIR_TYPE indicator=BP_INDICATOR evalue=DECIMAL
    ;

// Triplet information (additional pairing for triplets)
tripletInfo
    : tripletSerial=INTEGER tripletPdbNum=INTEGER tripletBase=BASE tripletInsCode=INS_CODE tripletChain=CHAIN_ID
      tripletPairType=PAIR_TYPE tripletIndicator=BP_INDICATOR tripletEvalue=DECIMAL
    ;
