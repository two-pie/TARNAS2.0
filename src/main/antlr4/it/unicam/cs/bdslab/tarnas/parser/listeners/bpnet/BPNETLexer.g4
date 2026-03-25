/**
 * ANTLR 4 lexer grammar for BPNET/BPFIND output files.
 *
 * BPNET/BPFIND is a tool for identifying base pairs and triplets in RNA structures.
 * This lexer handles the output format with base pair annotations.
 *
 * @author Federico Di Petta
 * @see BPNETParser
 */
lexer grammar BPNETLexer;

// ------------------------------------------------
// Fragments
// ------------------------------------------------
fragment WS_CHAR: [ \t];
fragment NEWLINE_CHAR: '\r'? '\n';
fragment DIGIT: [0-9];
fragment LETTER: [a-zA-Z];

// ----------------------------------------------------------------
// Default mode
// ----------------------------------------------------------------
WS: WS_CHAR+ -> skip;
NEWLINE: NEWLINE_CHAR -> skip;

// Header/Comment lines (start with #)
HEADER_LINE: '#' ~[\r\n]* -> skip;
SEPARATOR_LINE: '=====' '='* -> skip;

// Base pair type: W:WC, W:HC, S:HC, S:WT, H:WC, H:SC, etc.
PAIR_TYPE: [WHS] ':' [WHS] [CT];

// BP/TP indicator (Base Pair or Triple Pair or Bifurcate pair)
BP_INDICATOR: 'BP' | 'TP' | 'BF';

// Base name
BASE: [ACGU];

// Chain ID
CHAIN_ID: [A-Z];

// PDB insertion code (usually ?)
INS_CODE: '?';

// Numbers (integers and decimals)
DECIMAL: DIGIT+ '.' DIGIT+;
INTEGER: DIGIT+;
