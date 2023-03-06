parser grammar PrevParser;

@header {

	package prev23.phase.synan;
	
	import java.util.*;
	
	import prev23.common.report.*;
	import prev23.phase.lexan.*;

	import prev23.data.ast.tree.*;
	import prev23.data.ast.tree.decl.*;
	import prev23.data.ast.tree.expr.*;
	import prev23.data.ast.tree.stmt.*;
	import prev23.data.ast.tree.type.*;

}

@members {
	private Location loc(Token tok) { return new Location((prev23.data.sym.Token) tok); }
	private Location loc(Locatable loc) { return new Location(loc); }
	private Location loc(Token tok1, Token tok2) { return new Location((prev23.data.sym.Token) tok1, (prev23.data.sym.Token) tok2); }
	private Location loc(Token tok1, Locatable loc2) { return new Location((prev23.data.sym.Token) tok1, loc2); }
	private Location loc(Locatable loc1, Token tok2) { return new Location(loc1, (prev23.data.sym.Token) tok2); }
	private Location loc(Locatable loc1, Locatable loc2) { return new Location(loc1, loc2); }
}

options{
    tokenVocab=PrevLexer;
}


source returns [AstTrees<AstTrees<AstDecl>> ast]
  : declarations { $ast = $declarations.ast; } EOF
  ;

declarations returns [AstTrees<AstTrees<AstDecl>> ast]
	: { Vector<AstTrees<AstDecl>> trees = new Vector<>(); }
		(typedecls { trees.add($typedecls.ast); } | fundecls { trees.add($fundecls.ast); } | vardecls { trees.add($vardecls.ast); })+
		{ $ast = new AstTrees<AstTrees<AstDecl>>("Declarations", trees); }
	;

typedecls returns [AstTrees<AstDecl> ast]
	: { Vector<AstDecl> decls = new Vector<>(); }
		TYP IDENTIFIER EQUALS type { decls.add(new AstTypDecl(loc($TYP, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		(COMMA IDENTIFIER EQUALS type { decls.add(new AstTypDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); })* SEMICOLON
		{ $ast = new AstTrees<AstDecl>("TypeDeclarations", decls); }
	;

fundecls returns [AstTrees<AstDecl> ast]
	: { Vector<AstDecl> decls = new Vector<>(); }
		FUN IDENTIFIER LPAREN 
		{ Vector<AstParDecl> params = new Vector<>(); }
		{ String name = $IDENTIFIER.getText(); }
		(IDENTIFIER COLON type { params.add(new AstParDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		(COMMA IDENTIFIER COLON type
		{ params.add(new AstParDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		)*)? RPAREN COLON type 
		{ AstTrees<AstParDecl> treeParams = params.size() > 0 ? new AstTrees<AstParDecl>("ParDeclarations", params) : null; }
		{ Location location = loc($FUN, $type.ast); }
		{ AstStmt body = null; }
		(EQUALS stmt { body = $stmt.ast; location = loc($FUN, $stmt.ast); } )?
		{ decls.add(new AstFunDecl(location, name, treeParams, $type.ast, body)); }

		(c1 = COMMA IDENTIFIER LPAREN 
		{ Vector<AstParDecl> paramsAdd = new Vector<>(); }
		{ String nameAdd = $IDENTIFIER.getText(); }
		(IDENTIFIER COLON type { paramsAdd.add(new AstParDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		(COMMA IDENTIFIER COLON type
		{ paramsAdd.add(new AstParDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		)*)? RPAREN COLON type 
		{ AstTrees<AstParDecl> treeParamsAdd = paramsAdd.size() > 0 ? new AstTrees<AstParDecl>("ParDeclarations", paramsAdd) : null; }
		{ Location locationAdd = loc($c1, $type.ast); }
		{ AstStmt bodyAdd = null; }
		(EQUALS stmt { bodyAdd = $stmt.ast; locationAdd = loc($c1, $stmt.ast); })? 
		{ decls.add(new AstFunDecl(locationAdd, nameAdd, treeParamsAdd, $type.ast, bodyAdd)); }
		)* SEMICOLON
		{ $ast = new AstTrees<AstDecl>("FunDeclarations", decls); }
	;

vardecls returns [AstTrees<AstDecl> ast]
	: { Vector<AstDecl> decls = new Vector<>(); }
		VAR IDENTIFIER COLON type { decls.add(new AstVarDecl(loc($VAR, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		(COMMA IDENTIFIER COLON type { decls.add(new AstVarDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); })* SEMICOLON
		{ $ast = new AstTrees<AstDecl>("VarDeclarations", decls); }
	;

type returns [AstType ast]
	: VOID { $ast = new AstAtomType(loc($VOID), AstAtomType.Type.VOID); }
	| CHAR { $ast = new AstAtomType(loc($CHAR), AstAtomType.Type.CHAR); }
	| INT  { $ast = new AstAtomType(loc($INT), AstAtomType.Type.INT); }
	| BOOL { $ast = new AstAtomType(loc($BOOL), AstAtomType.Type.BOOL); }

	| IDENTIFIER { $ast = new AstNameType(loc($IDENTIFIER), $IDENTIFIER.getText()); }
	| LBRACE expr RBRACE type { $ast = new AstArrType(loc($LBRACE, $type.ast), $type.ast, $expr.ast); }
	| EXP type { $ast = new AstPtrType(loc($EXP, $type.ast), $type.ast); }

	// RecType
	| LBRACK IDENTIFIER COLON type 
		{ Vector<AstCmpDecl> comps = new Vector<>(); }
		{ comps.add(new AstCmpDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		(COMMA IDENTIFIER COLON type { comps.add(new AstCmpDecl(loc($IDENTIFIER, $type.ast), $IDENTIFIER.getText(), $type.ast)); }
		)* RBRACK
		{ AstTrees<AstCmpDecl> tree = comps.size() > 0 ? new AstTrees<AstCmpDecl>("CompDeclarations", comps) : null; }
		{ $ast = new AstRecType(loc($LBRACK, $RBRACK), tree); }

	// (type)
	| LPAREN type RPAREN
		{ $ast = $type.ast; }
		{ $ast.relocate(loc($LPAREN, $RPAREN)); }
	;

expr returns [AstExpr ast]
	// AtomExpr
	: CONST_VOID { $ast = new AstAtomExpr(loc($CONST_VOID), AstAtomExpr.Type.VOID, $CONST_VOID.getText()); } 
	| CONST_BOOL { $ast = new AstAtomExpr(loc($CONST_BOOL), AstAtomExpr.Type.BOOL, $CONST_BOOL.getText()); }
	| CONST_INT  { $ast = new AstAtomExpr(loc($CONST_INT), AstAtomExpr.Type.INT, $CONST_INT.getText()); }
	| CONST_CHAR { $ast = new AstAtomExpr(loc($CONST_CHAR), AstAtomExpr.Type.CHAR, $CONST_CHAR.getText()); }
	| CONST_STR  { $ast = new AstAtomExpr(loc($CONST_STR), AstAtomExpr.Type.STR, $CONST_STR.getText()); }
	| CONST_PTR  { $ast = new AstAtomExpr(loc($CONST_PTR), AstAtomExpr.Type.PTR, $CONST_PTR.getText()); }

	// NameExpr, CallExpr
	| { boolean isFunCall = false; Vector<AstExpr> exprs = new Vector<>(); }
		IDENTIFIER (LPAREN (expr { exprs.add($expr.ast); } (COMMA expr { exprs.add($expr.ast); })*)? RPAREN { isFunCall = true; })?
		{ 	if(isFunCall) {
				AstTrees<AstExpr> exprTree = exprs.size() > 0 ? new AstTrees<AstExpr>("CallParameters", exprs) : null;
				$ast = new AstCallExpr(loc($IDENTIFIER, $RPAREN), $IDENTIFIER.getText(), exprTree);
			}
			else {
				$ast = new AstNameExpr(loc($IDENTIFIER), $IDENTIFIER.getText());
			}
		}

	// NewExpr, DelExpr
	| NEW LPAREN type RPAREN { $ast = new AstNewExpr(loc($NEW, $RPAREN), $type.ast); }
	| DEL LPAREN expr RPAREN { $ast = new AstDelExpr(loc($DEL, $RPAREN), $expr.ast); }

	// CastExpr, (expr)
	| { boolean isTypeCast = false; AstType castType = null; }
		LPAREN expr (COLON type { isTypeCast = true; castType = $type.ast; })? RPAREN
		{ 	if(isTypeCast) {
				$ast = new AstCastExpr(loc($LPAREN, $RPAREN), $expr.ast, castType);
			}
			else {
				$ast = $expr.ast;
				$ast.relocate(loc($LPAREN, $RPAREN));
			}
		}

	// AstArrExpr
	| e1 = expr LBRACE e2 = expr RBRACE { $ast = new AstArrExpr(loc($e1.ast, $RBRACE), $e1.ast, $e2.ast); }
	
	// AstSfxExpr
	| e1 = expr EXP { $ast = new AstSfxExpr(loc($e1.ast, $EXP), AstSfxExpr.Oper.PTR, $e1.ast); }
	
	// AstRecExpr
	| e1 = expr DOT IDENTIFIER { $ast = new AstRecExpr(loc($e1.ast, $IDENTIFIER), $e1.ast, new AstNameExpr(loc($IDENTIFIER), $IDENTIFIER.getText())); }

	// Prefix Expr
	| { AstPfxExpr.Oper oper = null; Location location = null; } 
	(	EXCLAMATION { oper = AstPfxExpr.Oper.NOT; location = loc($EXCLAMATION); } | 
		PLUS { oper = AstPfxExpr.Oper.ADD; location = loc($PLUS); } | 
		MINUS { oper = AstPfxExpr.Oper.SUB; location = loc($MINUS); } | 
		EXP { oper = AstPfxExpr.Oper.PTR; location = loc($EXP); }
	) expr
	{ $ast = new AstPfxExpr(new Location(location, $expr.ast.location()), oper, $expr.ast); }

	// Multiplication BinExpr
	| e1 = expr { AstBinExpr.Oper oper = null; }
	(	STAR { oper = AstBinExpr.Oper.MUL; } |
		SLASH { oper = AstBinExpr.Oper.DIV; }|
		PERCENT { oper = AstBinExpr.Oper.MOD; }
	) e2 = expr
	{ $ast = new AstBinExpr(loc($e1.ast, $e2.ast), oper, $e1.ast, $e2.ast); }

	// Addition BinExpr
	| e1 = expr { AstBinExpr.Oper oper = null; }
	(	PLUS { oper = AstBinExpr.Oper.ADD; } |
		MINUS { oper = AstBinExpr.Oper.SUB; }
	) e2 = expr
	{ $ast = new AstBinExpr(loc($e1.ast, $e2.ast), oper, $e1.ast, $e2.ast); }
	
	// Compare operators BinExpr
	| e1 = expr { AstBinExpr.Oper oper = null; } 
	(	EQ { oper = AstBinExpr.Oper.EQU; } |
		NEQ { oper = AstBinExpr.Oper.NEQ; } |
		LT { oper = AstBinExpr.Oper.LTH; } |
		GT { oper = AstBinExpr.Oper.GTH; } |
		LEQ { oper = AstBinExpr.Oper.LEQ; } |
		GEQ { oper = AstBinExpr.Oper.GEQ; }
	) e2 = expr
	{ $ast = new AstBinExpr(loc($e1.ast, $e2.ast), oper, $e1.ast, $e2.ast); }

	// And, Or BinExpr
	| e1 = expr AMPERSAND e2 = expr { $ast = new AstBinExpr(loc($e1.ast, $e2.ast), AstBinExpr.Oper.AND, $e1.ast, $e2.ast); }
	| e1 = expr VERT_LINE e2 = expr { $ast = new AstBinExpr(loc($e1.ast, $e2.ast), AstBinExpr.Oper.OR, $e1.ast, $e2.ast); }
	;

stmt returns [AstStmt ast]
	// AssignStmt, ExprStmt
	: { boolean assign = false; AstExpr assignExpr = null; } 
		e1 = expr (EQUALS e2 = expr { assign = true; assignExpr = $e2.ast; })? 
		{ 	if(assign)
				$ast = new AstAssignStmt(loc($e1.ast, assignExpr), $e1.ast, assignExpr);
			else
				$ast = new AstExprStmt(loc($e1.ast), $e1.ast); 
		}

	// IfStmt
	| { AstStmt elseStmt = null; } 
		IF expr THEN s1 = stmt { Location location = loc($IF, $s1.ast); } (ELSE s2 = stmt { elseStmt = $s2.ast; location = loc($IF, $s2.ast); })?
		{ $ast = new AstIfStmt(location, $expr.ast, $s1.ast, elseStmt); }

	// WhileStmt
	| WHILE expr DO stmt
		{ $ast = new AstWhileStmt(loc($WHILE, $stmt.ast), $expr.ast, $stmt.ast); }

	// DeclStmt
	| LET declarations IN stmt
		{ $ast = new AstDeclStmt(loc($LET, $stmt.ast), $declarations.ast , $stmt.ast); }

	// Stmts
	| { Vector<AstStmt> stmts = new Vector<>(); }
		LBRACK stmt { stmts.add($stmt.ast); } (SEMICOLON stmt { stmts.add($stmt.ast); })* RBRACK
		{ $ast = new AstStmts(loc($LBRACK, $RBRACK), new AstTrees<AstStmt>("Statements", stmts)); }
	;
