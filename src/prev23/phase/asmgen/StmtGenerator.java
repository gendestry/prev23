package prev23.phase.asmgen;

import java.util.*;
import prev23.data.imc.code.expr.*;
import prev23.data.imc.code.stmt.*;
import prev23.data.imc.visitor.*;
import prev23.data.mem.*;
import prev23.data.asm.*;

/**
 * Machine code generator for statements.
 */
public class StmtGenerator implements ImcVisitor<Vector<AsmInstr>, Object> {

    @Override
    public Vector<AsmInstr> visit(ImcCJUMP cjump, Object object) {
        Vector<AsmInstr> instrs = new Vector<>();
        Vector<MemLabel> jumps = new Vector<>();
        Vector<MemTemp> uses = new Vector<>();

        MemTemp cond = cjump.cond.accept(new ExprGenerator(), instrs);
        uses.add(cond);

        jumps.add(cjump.posLabel);
        jumps.add(cjump.negLabel);

        instrs.add(new AsmOPER("BP `s0," + cjump.posLabel.name, uses, null, jumps));
        return instrs;
    }

    @Override
    public Vector<AsmInstr> visit(ImcESTMT estmt, Object object) {
        Vector<AsmInstr> instrs = new Vector<>();
        estmt.expr.accept(new ExprGenerator(), instrs);
        return instrs;
    }

    @Override
    public Vector<AsmInstr> visit(ImcJUMP jump, Object object) {
        Vector<AsmInstr> instrs = new Vector<>();
        Vector<MemLabel> jumps = new Vector<>();
        jumps.add(jump.label);

        instrs.add(new AsmOPER("JMP " + jump.label.name, null, null, jumps));
        return instrs;
    }

    @Override
    public Vector<AsmInstr> visit(ImcLABEL label, Object object) {
        Vector<AsmInstr> instrs = new Vector<>();
        instrs.add(new AsmLABEL(label.label));
        return instrs;
    }

    @Override
    public Vector<AsmInstr> visit(ImcMOVE move, Object object) {
        Vector<AsmInstr> instrs = new Vector<>();
        Vector<MemTemp> uses = new Vector<>();
        Vector<MemTemp> defs = new Vector<>();

        MemTemp src = move.src.accept(new ExprGenerator(), instrs);

        if (move.dst instanceof ImcMEM) {
            ImcMEM mem = (ImcMEM) move.dst;
            MemTemp tmp = mem.addr.accept(new ExprGenerator(), instrs);
            uses.add(tmp);
            uses.add(src);
            instrs.add(new AsmOPER("STO `s0,`s1,0", uses, null, null));
            return instrs;
        }

        MemTemp dst = move.dst.accept(new ExprGenerator(), instrs);
        uses.add(src);
        defs.add(dst);
        instrs.add(new AsmOPER("ADD `d0,`s0,0", uses, defs, null));
        return instrs;
    }

}
