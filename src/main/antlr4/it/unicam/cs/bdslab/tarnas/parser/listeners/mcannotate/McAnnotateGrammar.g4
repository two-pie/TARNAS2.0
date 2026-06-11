/*
 * Combined grammar for mc-annotate output files.
 *
 * Parses sections: Residue conformations, Adjacent stackings,
 * Non-Adjacent stackings, and Base-pairs, including summary counts.
 *
 * @author Francesco Palozzi
 */
grammar McAnnotateGrammar;

// ------------------------------------------------
// Parser rules
// ------------------------------------------------

mcAnnotateFile
    : residueSection
      adjacentSection
      nonAdjacentSection
      countSection
      basePairsSection
      EOF
    ;                                         // File structure: all sections in order

residueSection
    : RESIDUE_HEADER residueLine*
    ;                                     // Header followed by zero or more residue lines

adjacentSection
    : ADJACENT_HEADER adjacentLine*
    ;                                         // Adjacent stacking section

nonAdjacentSection
    : NON_ADJ_HEADER nonAdjacentLine*
    ;                                         // Non-adjacent stacking section

countSection
    : countLine+
    ;                                         // One or more count summary lines

basePairsSection
    : BASE_PAIRS_HEADER basePairLine*
    ;                                         // Base-pairs section

residueLine
    : IDENTIFIER COLON IDENTIFIER SUGAR? ANTI_SYN?
    ;                               // es. C1 : A C3p_endo anti   /   '3'1 : U C2p_endo anti

adjacentLine
    : PAIR_ID COLON ADJACENT_5P DIRECTION PAIRING?
    ;                                         // es. C1-C2 : adjacent_5p upward

nonAdjacentLine
    : PAIR_ID COLON DIRECTION PAIRING?
    ;                                         // es. C37-C104 : inward   /   '3'24-'3'56 : inward pairing

basePairLine
    : PAIR_ID COLON NUCLEOTIDE_PAIR BOND+
      ( ADJACENT_5P | DIRECTION | PAIRING | ORIENTATION | ADDITIONAL | SAENGER )*
    ;                                       // es. '3'2-'3'118 : G-C Ww/Ws pairing antiparallel cis one_hbond 130


countLine
    : COUNT_STACKINGS
    | COUNT_ADJ
    | COUNT_NON_ADJ
    ;                                         // One of three count types


// ------------------------------------------------
// Lexer rules
// ------------------------------------------------

WS          : [ \t\r\n]+ -> skip ;            // Skip whitespace

// Section headers (dashed lines)
fragment DASH : '-' ;
RESIDUE_HEADER    : 'Residue conformations' WS* DASH+ ;
ADJACENT_HEADER   : 'Adjacent stackings' WS* DASH+ ;
NON_ADJ_HEADER    : 'Non-Adjacent stackings' WS* DASH+ ;
BASE_PAIRS_HEADER : 'Base-pairs' WS* DASH+ ;


// Count summary lines
COUNT_STACKINGS : 'Number of stackings =' WS* INT ;
COUNT_ADJ       : 'Number of adjacent stackings =' WS* INT ;
COUNT_NON_ADJ   : 'Number of non adjacent stackings =' WS* INT ;

// token keyword atomici (dichiarati PRIMA di IDENTIFIER/ADDITIONAL per vincere sui pareggi)
ADJACENT_5P : 'adjacent_5p' ;
DIRECTION   : 'outward' | 'downward' | 'inward' | 'upward' ;
PAIRING     : 'pairing' ;
ORIENTATION : 'antiparallel' | 'parallel' | 'cis' | 'trans' ;
ANTI_SYN    : 'anti' | 'syn' ;
SAENGER: [XVI]+;                              // Saenger classification (e.g., XI, VI)
SUGAR       : [a-zA-Z0-9_]+? ( 'endo' | 'exo' ) ;  // Sugar pucker (e.g., C3p_endo)
PAIR_ID : RESREF '-' RESREF ;                 // C36-C104  oppure  '3'1-'3'120
fragment RESREF
    : '\'' [A-Za-z0-9]+ '\'' [0-9]+           // chain con apici:  '3'1
    | [A-Za-z] [0-9]+                          // chain a lettera:  C1
    ;

IDENTIFIER
    : '\'' [A-Za-z0-9]+ '\'' [0-9]+           // residue id, chain con apici:  '3'1
    | [A-Za-z] [A-Za-z0-9]*                    // residue id (C1) oppure nucleotide (A,U,G,C)
    ;

COLON           : ':' ;
NUCLEOTIDE_PAIR : [ACGU] '-' [ACGU] ;
BOND            : [a-zA-Z0-9'/]+ '/' [a-zA-Z0-9'/]+ ;  // W/W, O2'/Ww, Hh/O2', ...
ADDITIONAL      : [a-zA-Z0-9_]+ ;             // antiparallel, parallel, one_hbond, 130, ...
INT             : [0-9]+ ;
