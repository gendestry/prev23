package prev23.phase.imclin;

import java.util.*;
import prev23.data.mem.*;
import prev23.data.imc.code.expr.*;
import prev23.data.imc.code.stmt.*;
import prev23.data.imc.visitor.*;

/**
 * Expression canonizer.
 */
public class ExprCanonizer implements ImcVisitor<ImcExpr, Vector<ImcStmt>> {

    @Override
	public ImcExpr visit(ImcBINOP binOp, Vector<ImcStmt> stmts) {
        ImcExpr first = binOp.fstExpr.accept(this, stmts);
        ImcTEMP firstTemp = new ImcTEMP(new MemTemp());
        stmts.add(new ImcMOVE(firstTemp, first));

        ImcExpr second = binOp.sndExpr.accept(this, stmts);
        ImcTEMP secondTemp = new ImcTEMP(new MemTemp());
        stmts.add(new ImcMOVE(secondTemp, second));

        return new ImcBINOP(binOp.oper, firstTemp, secondTemp);
    }

    @Override
	public ImcExpr visit(ImcCALL call, Vector<ImcStmt> stmts) {
        Vector<ImcExpr> args = new Vector<ImcExpr>();

        for (ImcExpr arg : call.args) {
            ImcExpr acc = arg.accept(this, stmts);
            ImcTEMP temp = new ImcTEMP(new MemTemp());

            stmts.add(new ImcMOVE(temp, acc));
            args.add(temp);
        }

        ImcTEMP ret = new ImcTEMP(new MemTemp());
        stmts.add(new ImcMOVE(ret, new ImcCALL(call.label, call.offs, args)));
        return ret;
    }

    @Override
	public ImcExpr visit(ImcCONST imcConst, Vector<ImcStmt> stmts) {
		return imcConst;
	}

	@Override
	public ImcExpr visit(ImcMEM mem, Vector<ImcStmt> stmts) {
		ImcExpr expr = mem.addr.accept(this, stmts);
		return new ImcMEM(expr);
	}

	@Override
	public ImcExpr visit(ImcNAME name, Vector<ImcStmt> stmts) {
		return name;
	}

	@Override
	public ImcExpr visit(ImcSEXPR sExpr, Vector<ImcStmt> stmts) {
		stmts.addAll(sExpr.stmt.accept(new StmtCanonizer(), stmts));
		return sExpr.expr.accept(this, stmts);
	}

	@Override
	public ImcExpr visit(ImcTEMP temp, Vector<ImcStmt> stmts) {
		return temp;
	}

	@Override
	public ImcExpr visit(ImcUNOP unOp, Vector<ImcStmt> stmts) {
		ImcExpr acc = unOp.subExpr.accept(this, stmts);
		return new ImcUNOP(unOp.oper, acc);
	}
}
