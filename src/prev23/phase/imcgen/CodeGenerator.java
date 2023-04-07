package prev23.phase.imcgen;

import java.util.*;

import prev23.data.ast.tree.*;
import prev23.data.ast.tree.expr.*;
import prev23.data.ast.tree.stmt.*;
import prev23.data.ast.tree.decl.*;


import prev23.common.report.Report;
import prev23.data.ast.visitor.*;
import prev23.data.mem.*;
import prev23.data.typ.*;
import prev23.data.imc.code.expr.*;
import prev23.data.imc.code.stmt.*;
import prev23.phase.memory.*;
import prev23.phase.seman.*;
import prev23.phase.seman.SymbTable.CannotFndNameException;

public class CodeGenerator extends AstNullVisitor<Object, Stack<MemFrame>> {

    @Override
	public SemType visit(AstTrees<? extends AstTree> trees, Stack<MemFrame> stack) {
		if (stack == null)
			stack = new Stack<MemFrame>();

		for (AstTree t : trees) {
			if (t != null) {
				t.accept(this, stack);
			}
		}

		return null;
	}

	// Ex1, Ex2, Ex3
    @Override
	public Object visit(AstAtomExpr atomExpr, Stack<MemFrame> stack) {
		ImcExpr ret = switch(atomExpr.type) {
			case INT ->	new ImcCONST(Integer.parseInt(atomExpr.value));
			case CHAR -> new ImcCONST(atomExpr.value.length() == 3 ? atomExpr.value.charAt(1) : atomExpr.value.charAt(2)); 
			case BOOL -> new ImcCONST(atomExpr.value.equals("true") ? 1 : 0);
			case PTR ->	new ImcCONST(0);
			case VOID -> new ImcCONST(0);
			case STR -> new ImcNAME(Memory.strings.get(atomExpr).label);
		};

		ImcGen.exprImc.put(atomExpr, ret);
		return ret;
	}

	// Ex4
    @Override
	public Object visit(AstPfxExpr pfxExpr, Stack<MemFrame> stack) {
		pfxExpr.expr.accept(this, stack);

		if (pfxExpr.oper == AstPfxExpr.Oper.PTR) {
			ImcMEM mem = (ImcMEM)ImcGen.exprImc.get(pfxExpr.expr);
			ImcGen.exprImc.put(pfxExpr, mem);
			return mem.addr;
		}

		ImcUNOP.Oper oper = switch(pfxExpr.oper) {
			case SUB -> ImcUNOP.Oper.NEG;
			case NOT -> ImcUNOP.Oper.NOT;
			default -> null;
		};

		ImcExpr ret = null;
		if (oper != null)
			ret = new ImcUNOP(oper, ImcGen.exprImc.get(pfxExpr.expr));
		else
			ret = ImcGen.exprImc.get(pfxExpr.expr);

		ImcGen.exprImc.put(pfxExpr, ret);
        return ret;
    }

	// Ex5
	@Override
	public Object visit(AstBinExpr binExpr, Stack<MemFrame> stack) {
		binExpr.fstExpr.accept(this, stack);
		binExpr.sndExpr.accept(this, stack);

		ImcBINOP.Oper oper = switch(binExpr.oper) {
			case OR -> ImcBINOP.Oper.OR;
			case AND -> ImcBINOP.Oper.AND;
			case EQU -> ImcBINOP.Oper.EQU;
			case NEQ -> ImcBINOP.Oper.NEQ;
			case LTH -> ImcBINOP.Oper.LTH;
			case GTH -> ImcBINOP.Oper.GTH;
			case LEQ -> ImcBINOP.Oper.LEQ;
			case GEQ -> ImcBINOP.Oper.GEQ;
			case ADD -> ImcBINOP.Oper.ADD;
			case SUB -> ImcBINOP.Oper.SUB;
			case MUL -> ImcBINOP.Oper.MUL;
			case DIV -> ImcBINOP.Oper.DIV;
			case MOD -> ImcBINOP.Oper.MOD;
			default -> null;
		};

		ImcBINOP ret = new ImcBINOP(oper, ImcGen.exprImc.get(binExpr.fstExpr), ImcGen.exprImc.get(binExpr.sndExpr));
		ImcGen.exprImc.put(binExpr, ret);
		return ret;
	}

	// Ex6
	@Override
	public Object visit(AstSfxExpr sfxExpr, Stack<MemFrame> stack) {
		sfxExpr.expr.accept(this, stack);

		if(sfxExpr.oper == AstSfxExpr.Oper.PTR) {
			ImcExpr mem = ImcGen.exprImc.get(sfxExpr.expr);
			ImcMEM ret = new ImcMEM(mem);
			ImcGen.exprImc.put(sfxExpr, ret);
			return ret;
		}

		return null;
	}

	// Ex7
	@Override
	public Object visit(AstNewExpr newExpr, Stack<MemFrame> stack) {
		SemType type = SemAn.isType.get(newExpr.type).actualType();
		ImcExpr size = new ImcCONST(type.size());

		Vector<Long> offs = new Vector<>();
		Vector<ImcExpr> args = new Vector<>();
		offs.add(8L);
		args.add(size);

		ImcCALL call = new ImcCALL(new MemLabel("new"), offs, args);
		ImcGen.exprImc.put(newExpr, call);
		return call;
	}

	// Ex8
	@Override
	public Object visit(AstDelExpr delExpr, Stack<MemFrame> stack) {
		delExpr.expr.accept(this, stack);

		ImcExpr mem = ImcGen.exprImc.get(delExpr.expr);
		Vector<Long> offs = new Vector<>();
		Vector<ImcExpr> args = new Vector<>();
		offs.add(8L);
		args.add(mem);

		ImcCALL call = new ImcCALL(new MemLabel("delete"), offs, args);
		ImcGen.exprImc.put(delExpr, call);
		return call;
	}

	// Ex9
	@Override
	public Object visit(AstNameExpr nameExpr, Stack<MemFrame> stack) {
		AstMemDecl decl = (AstMemDecl)SemAn.declaredAt.get(nameExpr);
		MemAccess mem = Memory.accesses.get(decl);

		ImcExpr ret = null;
		if (mem instanceof MemAbsAccess) {
			MemAbsAccess abs = (MemAbsAccess)mem;
			ret = new ImcNAME(abs.label);
		}
		else if (mem instanceof MemRelAccess) {
			MemRelAccess rel = (MemRelAccess)mem;
			MemFrame frame = stack.peek();
			ImcExpr imcTemp = new ImcTEMP(frame.FP);

			int diff = frame.depth - rel.depth;
			for(int i = 0; i <= diff; i++)
				imcTemp = new ImcMEM(imcTemp);

			ret = new ImcBINOP(ImcBINOP.Oper.ADD, imcTemp, new ImcCONST(rel.offset));
		}

		ret = new ImcMEM(ret);
		ImcGen.exprImc.put(nameExpr, ret);
		return ret;
	}

	// Ex10
	@Override
	public Object visit(AstArrExpr arrExpr, Stack<MemFrame> stack) {
		arrExpr.arr.accept(this, stack);
		arrExpr.idx.accept(this, stack);

		SemType type = ((SemArr)SemAn.ofType.get(arrExpr.arr).actualType()).elemType;
		ImcCONST typeSize = new ImcCONST(type.size());
		ImcBINOP arrOffset = new ImcBINOP(ImcBINOP.Oper.MUL, ImcGen.exprImc.get(arrExpr.idx), typeSize);
		ImcMEM arrMem = (ImcMEM)ImcGen.exprImc.get(arrExpr.arr);
		ImcBINOP arrAddress = new ImcBINOP(ImcBINOP.Oper.ADD, arrMem.addr, arrOffset);

		ImcMEM mem = new ImcMEM(arrAddress);
		ImcGen.exprImc.put(arrExpr, mem);
		return mem;
	}

	// Ex11
	@Override
	public Object visit(AstRecExpr recExpr, Stack<MemFrame> stack) {
		recExpr.rec.accept(this, stack);

		SemRec rec = (SemRec)SemAn.ofType.get(recExpr.rec).actualType();
		SymbTable table = TypeResolver.recDeclares.get(rec);

		AstCmpDecl decl = null;
		try {
			decl = (AstCmpDecl)table.fnd(recExpr.comp.name);
		} catch (CannotFndNameException e) {
			throw new Report.Error(recExpr, "Imc error: RecExpr name not found!");
		}

		MemRelAccess mem = (MemRelAccess)Memory.accesses.get(decl);
		ImcCONST compOffset = new ImcCONST(mem.offset);

		ImcMEM recMem = (ImcMEM)ImcGen.exprImc.get(recExpr.rec);
		ImcExpr compAddress = new ImcBINOP(ImcBINOP.Oper.ADD, recMem.addr, compOffset);
		ImcMEM compMem = new ImcMEM(compAddress);

		ImcGen.exprImc.put(recExpr, compMem);
		return compMem;
	}

	// Ex12
	@Override
	public Object visit(AstCallExpr callExpr, Stack<MemFrame> stack) {
		if(callExpr.args != null)
			callExpr.args.accept(this, stack);
		
		AstFunDecl funDecl = (AstFunDecl)SemAn.declaredAt.get(callExpr);
		MemFrame funFrame = Memory.frames.get(funDecl);

		Vector<Long> offs = new Vector<>();
		Vector<ImcExpr> args = new Vector<>();

		long currentDepth = stack.peek().depth;
		ImcExpr memSpam = new ImcTEMP(stack.peek().FP);

		if(currentDepth == 0) {
			memSpam = new ImcMEM(new ImcCONST(0));
		}
		else {
			for(int i = 0; i <= currentDepth - funFrame.depth; i++)
				memSpam = new ImcMEM(memSpam);
		}

		offs.add(0L);
		args.add(memSpam);

		if(callExpr.args != null) {
			long offset = 8;
			for(AstExpr arg : callExpr.args) {
				SemType type = SemAn.ofType.get(arg);
				offs.add(offset);
				args.add(ImcGen.exprImc.get(arg));
				offset += type.size();
			}
		}

		ImcCALL call = new ImcCALL(funFrame.label, offs, args);
		ImcGen.exprImc.put(callExpr, call);
		return call;
	}

	// Ex14, Ex15
	@Override
	public Object visit(AstCastExpr castExpr, Stack<MemFrame> stack) {
		castExpr.type.accept(this, stack);
		castExpr.expr.accept(this, stack);

		SemType type = SemAn.isType.get(castExpr.type).actualType();
		ImcExpr expr = ImcGen.exprImc.get(castExpr.expr);

		if(type instanceof SemChar) {
			expr = new ImcBINOP(ImcBINOP.Oper.MOD, expr, new ImcCONST(256));
		}

		ImcGen.exprImc.put(castExpr, expr);
		return expr;
	}


	// St1
	@Override
	public Object visit(AstExprStmt exprStmt, Stack<MemFrame> stack) {
		exprStmt.expr.accept(this, stack);
		ImcExpr expr = ImcGen.exprImc.get(exprStmt.expr);
		ImcStmt exprImc = new ImcESTMT(expr);
		ImcGen.stmtImc.put(exprStmt, exprImc);
		return exprImc;
	}

	// St2
	@Override
	public Object visit(AstAssignStmt assignStmt, Stack<MemFrame> stack) {
		assignStmt.dst.accept(this, stack);
		assignStmt.src.accept(this, stack);

		ImcExpr dst = ImcGen.exprImc.get(assignStmt.dst);
		ImcExpr src = ImcGen.exprImc.get(assignStmt.src);

		ImcStmt move = new ImcMOVE(dst, src);
		ImcGen.stmtImc.put(assignStmt, move);
		return move;
	}

	// St3, St4
	@Override
	public Object visit(AstIfStmt ifStmt, Stack<MemFrame> stack) {
		ifStmt.cond.accept(this, stack);
		ifStmt.thenStmt.accept(this, stack);

		if(ifStmt.elseStmt != null)
			ifStmt.elseStmt.accept(this, stack);

		Vector<ImcStmt> stmts = new Vector<>();

		MemLabel tru = new MemLabel();
		MemLabel fls = new MemLabel();
		MemLabel end = new MemLabel();

		stmts.add(new ImcCJUMP(ImcGen.exprImc.get(ifStmt.cond), tru, fls));
		stmts.add(new ImcLABEL(tru));
		stmts.add(ImcGen.stmtImc.get(ifStmt.thenStmt));
		stmts.add(new ImcJUMP(end));

		stmts.add(new ImcLABEL(fls));
		if(ifStmt.elseStmt != null)
			stmts.add(ImcGen.stmtImc.get(ifStmt.elseStmt));
		stmts.add(new ImcLABEL(end));

		ImcSTMTS imcStmt = new ImcSTMTS(stmts);
		ImcGen.stmtImc.put(ifStmt, imcStmt);
		return imcStmt;
	}

	// St5, St6
	@Override
	public Object visit(AstWhileStmt whileStmt, Stack<MemFrame> stack) {
		whileStmt.cond.accept(this, stack);
		whileStmt.bodyStmt.accept(this, stack);

		Vector<ImcStmt> stmts = new Vector<>();

		MemLabel beg = new MemLabel();
		MemLabel tru = new MemLabel();
		MemLabel end = new MemLabel();

		stmts.add(new ImcLABEL(beg));
		stmts.add(new ImcCJUMP(ImcGen.exprImc.get(whileStmt.cond), tru, end));
		stmts.add(new ImcLABEL(tru));
		stmts.add(ImcGen.stmtImc.get(whileStmt.bodyStmt));
		stmts.add(new ImcJUMP(beg));
		stmts.add(new ImcLABEL(end));

		ImcSTMTS imcStmt = new ImcSTMTS(stmts);
		ImcGen.stmtImc.put(whileStmt, imcStmt);
		return imcStmt;
	}

	// St7
	@Override
	public Object visit(AstDeclStmt declStmt, Stack<MemFrame> stack) {
		declStmt.stmt.accept(this, stack);
		declStmt.decls.accept(this, stack);

		ImcStmt stmt = ImcGen.stmtImc.get(declStmt.stmt);
		ImcGen.stmtImc.put(declStmt, stmt);
		return stmt;
	}

	// St8
	@Override
	public Object visit(AstStmts stmts, Stack<MemFrame> stack) {
		stmts.stmts.accept(this, stack);

		AstStmt last = stmts.stmts.get(stmts.stmts.size() - 1);
		Vector<ImcStmt> imcStmts = new Vector<>();
		for(AstStmt stmt : stmts.stmts) {
			imcStmts.add(ImcGen.stmtImc.get(stmt));
		}

		ImcSEXPR sexpr = null;
		if(last instanceof AstExprStmt) {
			imcStmts.remove(imcStmts.size() - 1);
			ImcExpr ex = ImcGen.exprImc.get(((AstExprStmt) last).expr);
			sexpr = new ImcSEXPR(new ImcSTMTS(imcStmts), ex);
		}
		else {
			sexpr = new ImcSEXPR(new ImcSTMTS(imcStmts), new ImcCONST(0L));
		}

		ImcGen.stmtImc.put(stmts, new ImcESTMT(sexpr));
		return sexpr;
	}


	public Object visit(AstFunDecl funDecl, Stack<MemFrame> stack) {
		stack.push(Memory.frames.get(funDecl));

		if(funDecl.stmt != null)
			funDecl.stmt.accept(this, stack);
		
		stack.pop();
		return null;
	}

}
