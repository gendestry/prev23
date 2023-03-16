package prev23.phase.seman;

import java.util.Set;

import prev23.common.report.*;
import prev23.data.ast.tree.*;
import prev23.data.ast.tree.decl.*;
import prev23.data.ast.tree.expr.*;
import prev23.data.ast.tree.stmt.AstDeclStmt;
import prev23.data.ast.tree.type.*;
import prev23.data.ast.visitor.*;

/**
 * Name resolver.
 * 
 * Name resolver connects each node of a abstract syntax tree where a name is
 * used with the node where it is declared. The only exceptions are a record
 * field names which are connected with its declarations by type resolver. The
 * results of the name resolver are stored in
 * {@link prev23.phase.seman.SemAn#declaredAt}.
 */

public class NameResolver extends AstFullVisitor<Object, NameResolver.Mode> {
	
	public enum Mode {
		HEAD,
		BODY
	};

	private SymbTable symbTable = new SymbTable();
	
	@Override
	public Object visit(AstTrees<? extends AstTree> trees, Mode mode) {
		if(trees.title.equals("Declarations")) {
			for (AstTree t : trees) {
				t.accept(this, Mode.HEAD);
			}
	
			for (AstTree t : trees) {
				t.accept(this, Mode.BODY);
			}

			return null;
		}

		Set<String> decls = Set.of("TypeDeclarations", "VarDeclarations", "FunDeclarations");
		if(decls.contains(trees.title)) {
			symbTable.newScope();
		}	

		super.visit(trees, mode);
		return null;
	}

	// DECLARATIONS

	@Override
	public Object visit(AstFunDecl funDecl, Mode mode) {
		if(mode == Mode.HEAD) {
			try {
				symbTable.ins(funDecl.name, funDecl);
			}
			catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(funDecl, "Semantic error: redeclaration of function " + funDecl.name);
			}
		}
		else {
			funDecl.type.accept(this, mode);

			symbTable.newScope();

			if(funDecl.pars != null) {
				funDecl.pars.accept(this, Mode.HEAD);
			}

			if(funDecl.stmt != null) {
				funDecl.stmt.accept(this, mode);
			}

			symbTable.oldScope();
		}

		return null;
	}

	@Override
	public Object visit(AstParDecl parDecl, Mode mode) {
		if(mode == Mode.HEAD) {
			try {
				symbTable.ins(parDecl.name, parDecl);
			}
			catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(parDecl, "Semantic error: redeclaration of function parameter " + parDecl.name);
			}
		}
		else if (mode == Mode.BODY) {
			parDecl.type.accept(this, mode);
		}

		return null;
	}

	@Override
	public Object visit(AstTypDecl typDecl, Mode mode) {
		if (mode == Mode.HEAD) {
			try {
				symbTable.ins(typDecl.name, typDecl);
			}
			catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(typDecl, "Semantic error: redeclaration of type " + typDecl.name);
			}
		}
		else if (mode == Mode.BODY) {
			typDecl.type.accept(this, mode);
		}

		return null;
	}

	@Override
	public Object visit(AstVarDecl varDecl, Mode mode) {
		if (mode == Mode.HEAD) {
			try {
				symbTable.ins(varDecl.name, varDecl);
			}
			catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(varDecl, "Semantic error: redeclaration of variable " + varDecl.name);
			}
		}
		else if (mode == Mode.BODY) {
			varDecl.type.accept(this, mode);
		}

		return null;
	}

	// EXPRESSIONS

	@Override
	public Object visit(AstCallExpr callExpr, Mode mode) {
		if(mode == Mode.BODY) {
			try {
				SemAn.declaredAt.put(callExpr, symbTable.fnd(callExpr.name));
			}
			catch (SymbTable.CannotFndNameException e) {
				throw new Report.Error(callExpr, "Semantic error: cannot find name " + callExpr.name);
			}

			if(callExpr.args != null) {
				callExpr.args.accept(this, mode);
			}
		}

		return null;
	}

	@Override
	public Object visit(AstNameExpr nameExpr, Mode mode) {
		if(mode == Mode.BODY) {
			try {
				SemAn.declaredAt.put(nameExpr, symbTable.fnd(nameExpr.name));
			}
			catch (SymbTable.CannotFndNameException e) {
				throw new Report.Error(nameExpr, "Semantic error: cannot find name " + nameExpr.name);
			}
		}

		return null;
	}

	@Override
	public Object visit(AstRecExpr recExpr, Mode mode) {
		if(mode == Mode.BODY) {
			recExpr.rec.accept(this, mode);
		}

		return null;
	}

	// TYPES

	@Override
	public Object visit(AstNameType nameType, Mode mode) {
		if(mode == Mode.BODY) {
			try {
				SemAn.declaredAt.put(nameType, symbTable.fnd(nameType.name));
			}
			catch (SymbTable.CannotFndNameException e) {
				throw new Report.Error(nameType, "Semantic error: cannot find name " + nameType.name);
			}
		}

		return null;
	}

	// STATEMENTS

	@Override
	public Object visit(AstDeclStmt declStmt, Mode mode) {
		if(mode == Mode.BODY) {
			symbTable.newScope();

			declStmt.decls.accept(this, Mode.HEAD);
			declStmt.decls.accept(this, Mode.BODY);

			declStmt.stmt.accept(this, Mode.BODY);

			symbTable.oldScope();
		}

		return null;
	}

}
