package prev23.phase.seman;

import prev23.common.report.*;
import prev23.data.ast.tree.decl.*;
import prev23.data.ast.tree.expr.*;
import prev23.data.ast.tree.stmt.*;
import prev23.data.ast.visitor.*;
import prev23.data.typ.*;

public class AddrResolver extends AstFullVisitor<Object, Object> {
	
	@Override
    public Object visit(AstAssignStmt assStmt, Object mode) {
		assStmt.dst.accept(this, mode);
		assStmt.src.accept(this, mode);

		Boolean ok = SemAn.isAddr.get(assStmt.dst);

		if(ok == null || !ok) {
			throw new Report.Error(assStmt, "Address error: Lvalue expected!");
		}

		return null;
	}

	@Override
    public Object visit(AstNameExpr nameExpr, Object mode) {
		AstNameDecl decl = SemAn.declaredAt.get(nameExpr);

		if (decl instanceof AstVarDecl || decl instanceof AstParDecl)
			SemAn.isAddr.put(nameExpr, true);

		return null;
	}

	@Override
    public Object visit(AstSfxExpr sfxExpr, Object mode) {
		sfxExpr.expr.accept(this, mode);
		SemType type = SemAn.ofType.get(sfxExpr.expr).actualType();
		
		if (type instanceof SemPtr)
			SemAn.isAddr.put(sfxExpr, true);

		return null;
	}

	@Override
    public Object visit(AstArrExpr arrExpr, Object mode) {
		arrExpr.arr.accept(this, mode);
		arrExpr.idx.accept(this, mode);

		Boolean ok = SemAn.isAddr.get(arrExpr.arr);
		
		if (ok != null && ok)
			SemAn.isAddr.put(arrExpr, true);
		
        return null;
	}

	@Override
    public Object visit(AstRecExpr recExpr, Object mode) {
		recExpr.rec.accept(this, mode);
		Boolean ok = SemAn.isAddr.get(recExpr.rec);

		if (ok != null && ok)
			SemAn.isAddr.put(recExpr, true);

		return null;
	}
}