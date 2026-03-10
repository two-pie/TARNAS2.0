/**
 * ANTLR 4 lexer grammar for MC-Annotate output files.
 *
 * This lexer defines the tokens used by the MCAnnotateParser to parse output
 * files from the MC-Annotate tool for RNA structure annotation.
 * The output format consists of:
 * - Sequence lines: residue definitions (A1 : G, A2 : G C3p_endo anti, etc.)
 * - Header lines: section headers (Adjacent stackings, Base-pairs, etc.)
 * - Stacking lines: stacking interactions (A2-A3 : adjacent_5p upward)
 * - Bond lines: base pair interactions with Leontis-Westhof annotation
 * - Summary lines: statistics (Number of stackings = 60)
 *
 * @author Francesco Palozzi
 * @see MCAnnotateParser
 */
lexer grammar MCAnnotateLexer;

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

// Section headers - switch to HEADER mode to consume dashes
ADJACENT_STACKINGS: 'Adjacent stackings' -> pushMode(HEADER_MODE);
NON_ADJACENT_STACKINGS: 'Non-Adjacent stackings' -> pushMode(HEADER_MODE);
BASE_PAIRS: 'Base-pairs' -> pushMode(HEADER_MODE);

// Summary line start
NUMBER_OF: 'Number of' -> pushMode(SUMMARY_MODE);

// Residue ID (e.g. A1, A10, B5)
RESIDUE_ID: LETTER DIGIT+;

// Punctuation
COLON: ':';
DASH: '-';

// Bases
BASE: [ACGU];

// Switch to appropriate mode based on context
// When we see RESIDUE_ID DASH RESIDUE_ID COLON, it's either stacking or bond line
// We'll handle this in the parser

// Edge annotation for Leontis-Westhof (e.g., Ww, Hh, Ss, O2', Bs)
// Sugar edge variants: O2', O2P, Bs
SUGAR_EDGE: 'O2\'' | 'O2P' | 'Bs';
EDGE: [WHSwhs][whs]?;

// Slash separator for edge pairs
SLASH: '/';

// Orientation keywords
PAIRING: 'pairing';
PARALLEL: 'parallel';
ANTIPARALLEL: 'antiparallel';
CIS: 'cis';
TRANS: 'trans';

// Stacking types
STACKING_TYPE: 'adjacent_5p' | 'adjacent_3p' | 'upward' | 'downward' | 'outward' | 'inward';

// One hydrogen bond annotation
ONE_HBOND: 'one_hbond';

// Saenger classification (Roman numerals or numbers)
SAENGER: [IVX]+ | DIGIT+;

// Generic text fallback for unrecognized content
TEXT: ~[ \t\r\n:/-]+;

// ------------------------------------------------
// Header mode - consume dashes after section headers
// ------------------------------------------------
mode HEADER_MODE;

H_DASH: '-'+ -> skip;
H_NEWLINE: NEWLINE_CHAR -> skip, popMode;
H_WS: WS_CHAR+ -> skip;

// ------------------------------------------------
// Summary mode - for Number of ... lines
// ------------------------------------------------
mode SUMMARY_MODE;

S_WS: WS_CHAR+ -> skip;
S_NEWLINE: NEWLINE_CHAR -> skip, popMode;
S_EQUALS: '=';
S_NUMBER: DIGIT+;
S_TEXT: ~[ \t\r\n=]+;
