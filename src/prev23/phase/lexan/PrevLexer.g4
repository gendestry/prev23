lexer grammar PrevLexer;

@header {
	package prev23.phase.lexan;
	import prev23.common.report.*;
	import prev23.data.sym.*;
}

@members {
    @Override
	public Token nextToken() {
		return (Token) super.nextToken();
	}
}


// Constants
CONST_VOID: 'none';
CONST_BOOL: ('true'|'false');
CONST_INT: ([1-9][0-9]*|'0');
CONST_CHAR: '\''([ -&]|[(-~]|'\\\'')'\'';
CONST_STR: '"'([ -!]|[#-~]|'\\"')*'"';
CONST_PTR: 'nil';

LPAREN: '(';
RPAREN: ')';
LBRACK: '{';
RBRACK: '}';
LBRACE: '[';
RBRACE: ']';

DOT: '.';
COMMA: ',';
COLON: ':';
SEMICOLON: ';';
AMPERSAND: '&';
VERT_LINE: '|';
EXCLAMATION: '!';

EQ: '==';
NEQ: '!=';
LT: '<';
GT: '>';
LEQ: '<=';
GEQ: '>=';

STAR: '*';
SLASH: '/';
PERCENT: '%';
PLUS: '+';
MINUS: '-';
EXP: '^';
EQUALS: '=';

BOOL: 'bool';
CHAR: 'char';
DEL: 'del';
DO: 'do';
ELSE: 'else';
FUN: 'fun';
IF: 'if';
IN: 'in';
INT: 'int';
LET: 'let';
NEW: 'new';
THEN: 'then';
TYP: 'typ';
VAR: 'var';
VOID: 'void';
WHILE: 'while';

IDENTIFIER: [a-zA-Z_]+[a-zA-Z0-9_]*;
COMMENT: '#' ~('\r' | '\n')* -> skip;
WHITESPACE: [ \n\r]+ -> skip;
TAB: '\t' { if (true) { 
	int curr = getCharPositionInLine();
	int off = ((curr / 8) + 1) * 8;
	this.setCharPositionInLine(off); 
}} -> skip;

CONST_INT_ERROR  : ('0')+[0-9]* { if (true) {throw new Report.Error(new Location(_tokenStartLine, _tokenStartCharPositionInLine), "Invalid integer: " + getText()); }};
CONST_CHAR_ERROR : ('\'''\'') { if (true) {throw new Report.Error(new Location(_tokenStartLine, _tokenStartCharPositionInLine), "Empty character: " + getText()); }};
CONST_CHAR_LONG_ERROR : ('\''([ -&]|[(-~])+'\'') { if (true) {throw new Report.Error(new Location(_tokenStartLine, _tokenStartCharPositionInLine), "Invalid character (too long): " + getText()); }};
CONST_CHAR_UNCLOSED_ERROR : ('\'') { if (true) {throw new Report.Error(new Location(_tokenStartLine, _tokenStartCharPositionInLine), "Unclosed character (or character out of range): " + getText()); }};
CONST_STR_ERROR  : ('"') { if (true) {throw new Report.Error(new Location(_tokenStartLine, _tokenStartCharPositionInLine), "Invalid string: " + getText()); }};

ERROR: . { if (true) {throw new Report.Error(new Location(_tokenStartLine, _tokenStartCharPositionInLine), "Error: " + getText()); }};