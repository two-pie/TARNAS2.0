grammar OutputGrammar;

file
    : line+ EOF
    ;

line
    : sequenceLine
    | stackingLine
    | bondLine
    | headerLine
    | summaryLine
    | ignoredLine
    ;


sequenceLine
    : id=ID COLON val=(BASE | ID | TEXT) rest+=(BASE | ID | TEXT)* NL
    ;

stackingLine
    : id1=ID DASH id2=ID COLON info=TEXT NL
    ;

bondLine
    : id1=ID DASH id2=ID COLON b1=(BASE|TEXT) DASH b2=(BASE|TEXT) info=TEXT NL
    ;

headerLine
    : ( 'Adjacent stackings' | 'Non-Adjacent stackings' | 'Base-pairs' ) DASH* NL
    ;

summaryLine
    : 'Number' TEXT NL
    ;

ignoredLine
    : TEXT? NL
    ;

/* Lexer Rules */
ID      : 'A' [0-9]+ ;
BASE    : [ACGU] ;
COLON   : ':' ;
DASH    : '-' ;
NL      : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;
TEXT    : ~[\r\n:\- ]+ ;