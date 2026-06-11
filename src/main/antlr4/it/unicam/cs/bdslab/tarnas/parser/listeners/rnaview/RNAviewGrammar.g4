/**
 * ANTLR 4 grammar for RNAview output files.
 *
 * This parser defines the grammar rules used to parse RNAview base-pair output.
 * The input typically consists of lines representing base pairs with chain
 * identifiers, positions, base pair types, and optional annotations.
 *
 * @author Francesco Palozzi
 */
grammar RNAviewGrammar;

// ------------------------------------
// Parser rules
// ------------------------------------

rnaviewFile: basePairLine* EOF ;                       // Zero or more base pair lines
basePairLine: ASSIGNED_NUMBERS
              CHAIN
              NUMBER
              BASE_PAIR
              NUMBER
              CHAIN
              SYN*
              annotation
              SYN*
              SAENGER?
             ;
annotation: STACKED | EDGE_PAIR ORIENTATION ;          // Either 'stacked' or edge pair + orientation


// ------------------------------------
// Lexer rules
// ------------------------------------

SKIP_START : .*? 'BEGIN_base-pair' -> skip ;          // Skip everything before first section
SKIP_END   : 'END_base-pair' .* -> skip ;             // Skip everything after last section

EDGE_PAIR  : [sSWH+-.?] '/' [sSWH+-.?] ;              // Edge pair notation (e.g., W/W, S/H)
ORIENTATION: 'cis' | 'tran' ;                         // Orientation (cis or trans)
NUMBER     : [0-9]+ ;                                 // Integer (residue number)
ASSIGNED_NUMBERS: NUMBER '_' NUMBER ',';              // Assigned numbers (e.g., 1_2,)
CHAIN      : [A-Za-z0-9] ':';                         // Chain identifier (es. A: oppure 3: per auth numerico)
BASE_PAIR  : IUPAC_BASE '-' IUPAC_BASE ;              // Base pair (e.g., A-U)
STACKED    : 'stacked' ;                              // Stacking annotation
SYN        : 'syn' ;                                  // Sugar-base conformation (syn)

SAENGER: '!' ( '1H' )? '(' [bs] '_' [bs] ')' '.'?
       | 'n/a'
       | [XVI]+ ;

WS : [ \t\r\n]+ -> skip ;                             // Skip whitespace


// ------------------------------------
// Fragments
// ------------------------------------

fragment IUPAC_BASE : [ACGUacguTtRrYysSWwKkMmBbDdHhVvNn] ;  // IUPAC degenerate nucleotide codes
