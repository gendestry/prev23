parser grammar PrevParser;

@header {

	package prev23.phase.synan;
	
	import java.util.*;
	
	import prev23.common.report.*;
	import prev23.phase.lexan.*;
	
}

options{
    tokenVocab=PrevLexer;
}


source
  : declarations EOF
  ;

declarations
	: (typedecls | fundecls | vardecls)+
	;

typedecls
	: TYP IDENTIFIER EQUALS type (COMMA IDENTIFIER EQUALS type)* SEMICOLON
	;

fundecls
	: FUN IDENTIFIER LPAREN (IDENTIFIER COLON type (COMMA IDENTIFIER COLON type)*)? RPAREN COLON type (EQUALS stmt)? 
	(COMMA IDENTIFIER LPAREN (IDENTIFIER COLON type (COMMA IDENTIFIER COLON type)*)? RPAREN COLON type (EQUALS stmt)? )* SEMICOLON
	;

vardecls
	: VAR IDENTIFIER COLON type (COMMA IDENTIFIER COLON type)* SEMICOLON
	;

type
	: VOID | CHAR | INT | BOOL | IDENTIFIER
	| LBRACE expr RBRACE type
	| EXP type
	| LBRACK IDENTIFIER COLON type (COMMA IDENTIFIER COLON type)* RBRACK
	| LPAREN type RPAREN
	;

expr
	: CONST_VOID | CONST_BOOL | CONST_INT | CONST_CHAR | CONST_STR | CONST_PTR
	| IDENTIFIER (LPAREN (expr (COMMA expr)*)? RPAREN)?
	| NEW LPAREN type RPAREN
	| DEL LPAREN expr RPAREN
	| LPAREN expr (COLON type)? RPAREN
	| expr ((LBRACE expr RBRACE) | EXP | (DOT IDENTIFIER))
	| (EXCLAMATION | PLUS | MINUS | EXP) expr
	| expr (STAR | SLASH | PERCENT) expr
	| expr (PLUS | MINUS) expr
	| expr (EQ | NEQ | LT | GT | LEQ | GEQ) expr
	| expr AMPERSAND expr
	| expr VERT_LINE expr
	;

stmt
	: expr (EQUALS expr)?
	| IF expr THEN stmt (ELSE stmt)?
	| WHILE expr DO stmt
	| LET declarations IN stmt
	| LBRACK stmt (SEMICOLON stmt)* RBRACK
	;

