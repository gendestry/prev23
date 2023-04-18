package prev23.phase.imclin;

import java.util.*;

import prev23.common.report.Report;
import prev23.data.ast.tree.decl.*;
import prev23.data.ast.tree.expr.*;
import prev23.data.ast.visitor.*;
import prev23.data.mem.*;
import prev23.data.imc.code.expr.*;
import prev23.data.imc.code.stmt.*;
import prev23.data.lin.*;
import prev23.phase.imcgen.*;
import prev23.phase.memory.*;

public class ChunkGenerator extends AstFullVisitor<Object, Object> {

    @Override
    public Object visit(AstAtomExpr atomExpr, Object arg) {
        if(atomExpr.type == AstAtomExpr.Type.STR) {
            MemAbsAccess access = Memory.strings.get(atomExpr);
            ImcLin.addDataChunk(new LinDataChunk(access));
        }

        return null;
    }

    @Override
    public Object visit(AstVarDecl varDecl, Object arg) {
        MemAccess access = Memory.accesses.get(varDecl);

        if(access instanceof MemAbsAccess) {
            MemAbsAccess absAccess = (MemAbsAccess) access;
            ImcLin.addDataChunk(new LinDataChunk(absAccess));
        }

        return null;
    }

    @Override
    public Object visit(AstFunDecl funDecl, Object arg) {
        if(funDecl.stmt == null) 
            return null;

        funDecl.stmt.accept(this, arg);

        MemFrame frame = Memory.frames.get(funDecl);
        MemLabel entryLabel = new MemLabel();
        MemLabel exitLabel = new MemLabel();

        Vector<ImcStmt> canonStmts = new Vector<>();
        canonStmts.add(new ImcLABEL(entryLabel));
        
        ImcStmt bodyStmt = ImcGen.stmtImc.get(funDecl.stmt);
        // if(bodyStmt instanceof ImcESTMT) {
        //     ImcExpr bodyExpr = ((ImcESTMT) bodyStmt).expr;
        //     ImcStmt newBodyStmt = new ImcMOVE(new ImcTEMP(frame.RV), bodyExpr);
        //     canonStmts.addAll(newBodyStmt.accept(new StmtCanonizer(), null));
        // }
        // else {
        //     throw new Report.Error(funDecl, "Holy fuck");
        // }
        if (bodyStmt instanceof ImcESTMT) {
            ImcExpr bodyExpr = ((ImcESTMT) bodyStmt).expr;
            ImcStmt newBodyStmt = new ImcMOVE(new ImcTEMP(frame.RV), bodyExpr);
            canonStmts.addAll(newBodyStmt.accept(new StmtCanonizer(), null));
        }
        else if (bodyStmt instanceof ImcSTMTS) {
            System.out.println("Here");
            ImcSTMTS stmts = (ImcSTMTS)bodyStmt;
            ImcStmt last = stmts.stmts.lastElement();

            if(last instanceof ImcESTMT) {
                ImcExpr bodyExpr = ((ImcESTMT)last).expr;
                ImcStmt newBodyStmt = new ImcMOVE(new ImcTEMP(frame.RV), bodyExpr);
                
                canonStmts.addAll(stmts.accept(new StmtCanonizer(), null));
                canonStmts.remove(canonStmts.lastElement());

                canonStmts.addAll(newBodyStmt.accept(new StmtCanonizer(), null));
            }
            else {
                System.out.println("Do nothing");
            }
        }
        else {
            // ImcExpr st = new ImcSEXPR(bodyStmt, new ImcCONST(0));
            canonStmts.addAll(bodyStmt.accept(new StmtCanonizer(), null));
        }

        canonStmts.add(new ImcJUMP(exitLabel));

        Vector<ImcStmt> linearStmts = linearize(canonStmts);
        ImcLin.addCodeChunk(new LinCodeChunk(frame, linearStmts, entryLabel, exitLabel));
        return null;
    }

    private Vector<ImcStmt> linearize(Vector<ImcStmt> stmts) {
        Vector<ImcStmt> linearStmts = new Vector<>();

        for(int s = 0; s < stmts.size(); s++) {
            ImcStmt stmt = stmts.get(s);

            if(stmt instanceof ImcCJUMP) {
                ImcCJUMP cjump = (ImcCJUMP) stmt;
                MemLabel negLabel = new MemLabel();
                linearStmts.add(new ImcCJUMP(cjump.cond, cjump.posLabel, negLabel));
                linearStmts.add(new ImcLABEL(negLabel));
                linearStmts.add(new ImcJUMP(cjump.negLabel));
            }
            else {
                linearStmts.add(stmt);
            }
        }

        return linearStmts;
    }

}
