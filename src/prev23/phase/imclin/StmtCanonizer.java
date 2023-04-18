package prev23.phase.imclin;

import java.util.*;

import prev23.common.report.*;
import prev23.data.mem.*;
import prev23.data.imc.code.expr.*;
import prev23.data.imc.code.stmt.*;
import prev23.data.imc.visitor.*;

/**
 * Statement canonizer.
 */
public class StmtCanonizer implements ImcVisitor<Vector<ImcStmt>, Object> {

    @Override
    public Vector<ImcStmt> visit(ImcCJUMP cjump, Object arg) {
        Vector<ImcStmt> ret = new Vector<>();
        ImcExpr condition = cjump.cond.accept(new ExprCanonizer(), ret);
        ret.add(new ImcCJUMP(condition, cjump.posLabel, cjump.negLabel));
        return ret;
    }

    @Override
    public Vector<ImcStmt> visit(ImcESTMT eStmt, Object arg) {
        Vector<ImcStmt> ret = new Vector<>();

        if(eStmt.expr instanceof ImcCALL) {
            ImcCALL call = (ImcCALL) eStmt.expr;
            Vector<ImcExpr> args = new Vector<>();

            for(ImcExpr argExpr : call.args) {
                ImcExpr acc = argExpr.accept(new ExprCanonizer(), ret);
                args.add(acc);
            }

            ret.add(new ImcESTMT(new ImcCALL(call.label, call.offs, args)));
        }
        else {
            ImcExpr expr = eStmt.expr.accept(new ExprCanonizer(), ret);
            ret.add(new ImcESTMT(expr));
        }

        return ret;
    }

    @Override
    public Vector<ImcStmt> visit(ImcJUMP jump, Object arg) {
        Vector<ImcStmt> ret = new Vector<>();
        ret.add(new ImcJUMP(jump.label));
        return ret;
    }

    @Override
    public Vector<ImcStmt> visit(ImcLABEL label, Object arg) {
        Vector<ImcStmt> ret = new Vector<>();
        ret.add(new ImcLABEL(label.label));
        return ret;
    }

    @Override
    public Vector<ImcStmt> visit(ImcMOVE move, Object arg) {
        Vector<ImcStmt> ret = new Vector<>();

        if(move.dst instanceof ImcMEM) {
            ImcMEM dstMem = (ImcMEM) move.dst;
            ImcExpr dstExpr = dstMem.addr.accept(new ExprCanonizer(), ret);
            ImcTEMP dstTemp = new ImcTEMP(new MemTemp());
            ret.add(new ImcMOVE(dstTemp, dstExpr));

            ImcExpr srcExpr = move.src.accept(new ExprCanonizer(), ret);
            ImcTEMP srcTemp = new ImcTEMP(new MemTemp());
            ret.add(new ImcMOVE(srcTemp, srcExpr));

            ret.add(new ImcMOVE(new ImcMEM(dstTemp), srcTemp));
        }
        else if(move.dst instanceof ImcTEMP) {
            ImcTEMP dstTemp = (ImcTEMP) move.dst;
            ImcExpr srcExpr = move.src.accept(new ExprCanonizer(), ret);
            ImcTEMP srcTemp = new ImcTEMP(new MemTemp());

            ret.add(new ImcMOVE(srcTemp, srcExpr));
            ret.add(new ImcMOVE(new ImcTEMP(dstTemp.temp), srcTemp));
        }
        else {
            throw new Report.Error(move.toString());
        }

        return ret;
    }

    @Override
    public Vector<ImcStmt> visit(ImcSTMTS stmts, Object arg) {
        Vector<ImcStmt> ret = new Vector<>();
        
        for (ImcStmt stmt : stmts.stmts) {
            ret.addAll(stmt.accept(this, arg));
        }

        return ret;
    }

}
