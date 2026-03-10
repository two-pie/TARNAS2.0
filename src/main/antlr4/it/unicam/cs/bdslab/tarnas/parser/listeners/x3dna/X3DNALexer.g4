/**
 * ANTLR 4 lexer grammar for 3DNA/X3DNA bp_order.dat output files.
 *
 * 3DNA is a tool for the analysis, rebuilding, and visualization of 
 * three-dimensional nucleic acid structures. This lexer handles the
 * base-pair order output format.
 *
 * @author Federico Di Petta
 * @see X3DNAParser
 */
lexer grammar X3DNALexer;

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

// Comments and headers
COMMENT_LINE: '#' ~[\r\n]* -> skip;
HEADER_LINE: 'Base-pair' ~[\r\n]* -> skip;
END_MARKER: 'End base-pair list' -> skip;
HELIX_HEADER: 'Helix region information' -> skip;
ZDNA_HEADER: 'Z-DNA helical region if any' -> skip;
CONTEXT_HEADER: 'Base-pair context information' -> skip;
END_BASE_PAIR: 'End base-pair list' -> skip;
HELIX_START: 'Helix #' DIGIT+ -> skip;

// Tokens
COLON: ':';
EQUALS: '=';
ARROW: '===>' | '==>' | '=>';
DOT: '.';
UNDERSCORE: '_';
LBRACKET: '[';
RBRACKET: ']';
LPAREN: '(';
RPAREN: ')';
LT: '<';
GT: '>';
DASH: '-';
STAR: '*';
COMMA: ',';

// Chain identifier
CHAIN_ID: LETTER;

// Numbers (including negative and decimal)
NUMBER: '-'? DIGIT+ ('.' DIGIT+)?;

// Base pair symbols
BASE_PAIR_SYMBOL: [ACGU] DASH ('*'+)? DASH? DASH? [ACGUa] ;

// Residue notation like [..G] or [A23]
RESIDUE_NOTATION: '[' '.'* LETTER (DIGIT+)? ']';

// Generic identifier
ID: LETTER (LETTER | DIGIT | '_' | '.')*;

// Catch-all for other text
TEXT: ~[ \t\r\n:=[\]()<>.*, -]+;