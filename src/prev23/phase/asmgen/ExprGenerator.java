package prev23.phase.asmgen;

import java.util.*;
import prev23.data.mem.*;
import prev23.phase.regall.RegAll;
import prev23.data.imc.code.expr.*;
import prev23.data.imc.visitor.*;
import prev23.data.asm.*;

/**
 * Machine code generator for expressions.
 */
public class ExprGenerator implements ImcVisitor<MemTemp, Vector<AsmInstr>> {

    @Override
    public MemTemp visit(ImcBINOP binop, Vector<AsmInstr> instrs) {
        Vector<MemTemp> uses = new Vector<>();
        Vector<MemTemp> defs = new Vector<>();
        MemTemp ret = new MemTemp();

        defs.add(ret);
        uses.add(binop.fstExpr.accept(this, instrs));
        uses.add(binop.sndExpr.accept(this, instrs));

        switch (binop.oper) {
            case ADD -> { instrs.add(new AsmOPER("ADD `d0,`s0,`s1", uses, defs, null)); }
            case SUB -> { instrs.add(new AsmOPER("SUB `d0,`s0,`s1", uses, defs, null)); }
            case MUL -> { instrs.add(new AsmOPER("MUL `d0,`s0,`s1", uses, defs, null)); }
            case DIV -> { instrs.add(new AsmOPER("DIV `d0,`s0,`s1", uses, defs, null)); }
            case MOD -> { 
                instrs.add(new AsmOPER("DIV `d0,`s0,`s1", uses, defs, null)); 
                instrs.add(new AsmOPER("GET `d0,rR", null, defs, null));
            }

            case OR -> { instrs.add(new AsmOPER("OR `d0,`s0,`s1", uses, defs, null)); }
            case AND -> { instrs.add(new AsmOPER("AND `d0,`s0,`s1", uses, defs, null)); }

            case EQU -> { 
                instrs.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defs, null)); 
                instrs.add(new AsmOPER("ZSZ `d0,`s0,1", defs, defs, null));
            }
            case NEQ -> { 
                instrs.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defs, null)); 
                instrs.add(new AsmOPER("ZSNZ `d0,`s0,1", defs, defs, null));
            }
            case LTH -> { 
                instrs.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defs, null)); 
                instrs.add(new AsmOPER("ZSN `d0,`s0,1", defs, defs, null));
            }
            case LEQ -> { 
                instrs.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defs, null)); 
                instrs.add(new AsmOPER("ZSNP `d0,`s0,1", defs, defs, null));
            }
            case GTH -> { 
                instrs.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defs, null)); 
                instrs.add(new AsmOPER("ZSP `d0,`s0,1", defs, defs, null));
            }
            case GEQ -> { 
                instrs.add(new AsmOPER("CMP `d0,`s0,`s1", uses, defs, null)); 
                instrs.add(new AsmOPER("ZSNN `d0,`s0,1", defs, defs, null));
            }
        };

        return ret;
    }

    @Override
    public MemTemp visit(ImcCALL call, Vector<AsmInstr> instrs) {
        Vector<MemTemp> uses = new Vector<>();
        Vector<MemTemp> defs = new Vector<>();
        Vector<MemLabel> jumps = new Vector<>();

        for (ImcExpr arg : call.args) {
            uses.add(arg.accept(this, instrs));
        }

        for (int i = 0; i < call.args.size(); i++) {
            Vector<MemTemp> temp = new Vector<>();
            temp.add(uses.get(i));
            instrs.add(new AsmOPER("STO `s0,$254," + call.offs.get(i), temp, null, null));
        }

        jumps.add(call.label);
        instrs.add(new AsmOPER("PUSHJ $" + RegAll.NUM_REGISTERS + "," + call.label.name, uses, null, jumps));

        MemTemp ret = new MemTemp();
        defs.add(ret);
        instrs.add(new AsmOPER("LDO `d0,$254,0", null, defs, null));
        return ret;
    }

    @Override
    public MemTemp visit(ImcCONST constant, Vector<AsmInstr> instrs) {
        Vector<MemTemp> defs = new Vector<>();
        MemTemp ret = new MemTemp();
        long value = Math.abs(constant.value);

        defs.add(ret);

        instrs.add(new AsmOPER(String.format("SETL `d0,%d", value & 0x000000000000FFFFL), null, defs, null));
        if((value & 0x00000000FFFF0000L) > 0)
            instrs.add(new AsmOPER(String.format("INCML `d0,%d", ((value & 0x00000000FFFF0000L) >> 16)), defs, defs, null));
        if((value & 0x0000FFFF00000000L) > 0)
            instrs.add(new AsmOPER(String.format("INCMH `d0,%d", ((value & 0x0000FFFF00000000L) >> 32)), defs, defs, null));
        if((value & 0xFFFF000000000000L) > 0)
            instrs.add(new AsmOPER(String.format("INCH `d0,%d", ((value & 0xFFFF000000000000L) >> 48)), defs, defs, null));

        if (constant.value < 0)
            instrs.add(new AsmOPER("NEG `d0,0,`s0", defs, defs, null));

        return ret;
    }

    @Override
    public MemTemp visit(ImcMEM mem, Vector<AsmInstr> instrs) {
        Vector<MemTemp> uses = new Vector<>();
        Vector<MemTemp> defs = new Vector<>();
        MemTemp ret = new MemTemp();

        defs.add(ret);
        uses.add(mem.addr.accept(this, instrs));

        instrs.add(new AsmOPER("LDO `d0,`s0,0", uses, defs, null));
        return ret;
    }

    @Override
    public MemTemp visit(ImcNAME name, Vector<AsmInstr> instrs) {
        Vector<MemTemp> defs = new Vector<>();
        MemTemp ret = new MemTemp();

        defs.add(ret);
        instrs.add(new AsmOPER("LDA `d0," + name.label.name, null, defs, null));
        return ret;
    }

    @Override
    public MemTemp visit(ImcTEMP temp, Vector<AsmInstr> instrs) {
        return temp.temp;
    }

    @Override
    public MemTemp visit(ImcUNOP unop, Vector<AsmInstr> instrs) {
        Vector<MemTemp> uses = new Vector<>();

        MemTemp ret = unop.subExpr.accept(this, instrs);
        uses.add(ret);

        switch(unop.oper) {
            case NOT -> { instrs.add(new AsmOPER("NEG `d0,0,`s0", uses, uses, null)); }
            case NEG -> { instrs.add(new AsmOPER("NOR `d0,`s0,0", uses, uses, null)); }
        }

        return ret;
    }

}
