package prev23.phase.seman;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import prev23.common.report.*;
import prev23.data.ast.tree.*;
import prev23.data.ast.tree.decl.*;
import prev23.data.ast.tree.expr.*;
import prev23.data.ast.tree.stmt.*;
import prev23.data.ast.tree.type.*;
import prev23.data.ast.visitor.*;
import prev23.data.typ.*;


public class TypeResolver extends AstFullVisitor<SemType, TypeResolver.Mode> {

	public enum Mode {
		HEAD, BODY, THIRD
	}

	public static final HashMap<SemRec, SymbTable> recDeclares = new HashMap<>();


	public boolean areEqual(SemType first, SemType second) {
		return first.actualType().getClass().equals(second.actualType().getClass());
	}

	public boolean isInt(SemType type) {
		return type.actualType() instanceof SemInt;
	}

	public boolean isChar(SemType type) {
		return type.actualType() instanceof SemChar;
	}

	public boolean isBool(SemType type) {
		return type.actualType() instanceof SemBool;
	}

	public boolean isPtr(SemType type) {
		return type.actualType() instanceof SemPtr;
	}

	public boolean isVoid(SemType type) {
		return type.actualType() instanceof SemVoid;
	}

	// GENERAL PURPOSE

	@Override
	public SemType visit(AstTrees<? extends AstTree> trees, Mode mode) {
		for (AstTree t : trees) {
			if (t != null) {
				t.accept(this, Mode.HEAD);
			}
		}

		for (AstTree t : trees) {
			if (t != null) {
				t.accept(this, Mode.BODY);
			}
		}

		for (AstTree t : trees) {
			if (t != null) {
				t.accept(this, Mode.THIRD);
			}
		}

		return null;
	}


	// TYPES

	// T1
	@Override
	public SemType visit(AstAtomType atomType, Mode mode) {
		SemType ret = switch(atomType.type) {
			case VOID -> new SemVoid();
			case BOOL -> new SemBool();
			case CHAR -> new SemChar();
			case INT -> new SemInt();
		};

		SemAn.isType.put(atomType, ret);
		return ret;
	}

	// T2
	@Override
	public SemType visit(AstArrType arrType, Mode mode) {
		SemType ret = null;

		if(arrType.numElems instanceof AstAtomExpr) {
			AstAtomExpr atomExpr = (AstAtomExpr) arrType.numElems;
			atomExpr.accept(this, mode); // arrType.numElems.accept(this, mode);

			if(atomExpr.type == AstAtomExpr.Type.INT) {
				SemType elemType = arrType.elemType.accept(this, mode);

				// check if elements are not of type void
				if(elemType.actualType() instanceof SemVoid) {
					throw new Report.Error(arrType, "Array elements cannot be of type void.");
				} 

				// check if index is positive and not too big
				try {
					long value = Long.parseLong(atomExpr.value);

					if (value <= 0)
						throw new Report.Error(arrType, "Type error: index value cannot be negative.");

					ret = new SemArr(elemType, value);
					SemAn.isType.put(arrType, ret);
				} catch (NumberFormatException e) {
					throw new Report.Error(arrType, "Type error: index value too big.");
				}
			}
		}

		return ret;
	}

	// T3
	@Override
	public SemType visit(AstRecType recType, Mode mode) {
		recType.comps.accept(this, mode);

		LinkedList<SemType> recs = new LinkedList<>();
		SymbTable table = new SymbTable();

		for(AstCmpDecl cdecl : recType.comps) {
			SemType type = SemAn.isType.get(cdecl.type);

			try {
				table.ins(cdecl.name, cdecl);
			} catch (SymbTable.CannotInsNameException e) {
				throw new Report.Error(recType, "Name error: names cannot match (" + cdecl.name + ").");
			}

			if(type == null)
				throw new Report.Error(recType, "Type error: undeclared type in record type.");
			
			if(isVoid(type))
				throw new Report.Error(recType, "Type error: record type cannot contain void type.");

			recs.add(type);
		}

		SemRec rec = new SemRec(recs);
		SemAn.isType.put(recType, rec);
		recDeclares.put(rec, table);
		return rec;
	}

	// T4
	@Override
	public SemType visit(AstPtrType ptrType, Mode mode) {
		SemType type = ptrType.baseType.accept(this, mode);

		if(type == null) {
			throw new Report.Error(ptrType, "Type error: pointer type cannot be void.");
		}

		SemType ret = new SemPtr(type);
		SemAn.isType.put(ptrType, ret);
		return ret;
	}

	@Override
	public SemType visit(AstNameType nameType, Mode mode) {
		AstNameDecl decl = SemAn.declaredAt.get(nameType);

		if(decl instanceof AstTypDecl) {
			SemName name = SemAn.declaresType.get((AstTypDecl) decl);
			if(name == null) {
				throw new Report.Error(nameType, "Type error: undecleared name - " + nameType.name);
			}

			SemAn.isType.put(nameType, name);
			return name;
		}
		else {
			throw new Report.Error(nameType, "Type error: undecleared type - " + nameType.name);
		}
	}


	// EXPRESSIONS

	// V1, V2
	@Override
	public SemType visit(AstAtomExpr atomExpr, Mode mode) {
		SemType ret = switch(atomExpr.type) {
			case VOID -> new SemVoid();
			case CHAR -> new SemChar();
			case INT -> new SemInt();
			case BOOL -> new SemBool();
			case PTR -> new SemPtr(new SemVoid());
			case STR -> new SemPtr(new SemChar());
		};

		SemAn.ofType.put(atomExpr, ret);
		return ret;
	}

	// V3, V8 (partial)
	@Override
	public SemType visit(AstPfxExpr pfxExpr, Mode mode) {
		SemType exprType = pfxExpr.expr.accept(this, mode);
		SemType ret = null;

		switch(pfxExpr.oper) {
			case ADD, SUB:
				if(exprType.actualType() instanceof SemInt)
					ret = new SemInt();
				else
					throw new Report.Error(pfxExpr, "Type error: +/- can only be used on int type.");
				break;

			case NOT:
				if(exprType.actualType() instanceof SemBool)
					ret = new SemBool();
				else
					throw new Report.Error(pfxExpr, "Type error: ! can only be used on bool type.");
				break;

			case PTR:
				ret = new SemPtr(exprType);
				break;
		};

		SemAn.ofType.put(pfxExpr, ret);
		return ret;
	}

	// V4, V5, V6, V7
	@Override
	public SemType visit(AstBinExpr binExpr, Mode mode) {
		// binExpr.fstExpr.accept(this, mode);
		// binExpr.sndExpr.accept(this, mode);

		SemType first = binExpr.fstExpr.accept(this, mode);
		SemType second = binExpr.sndExpr.accept(this, mode);

		if(first == null || second == null)
			throw new Report.Error(binExpr, "Type error: missing type in binary expression.");

		if(!areEqual(first, second))
			throw new Report.Error(binExpr, "Type error: types in binary expression are not equal.");

		SemType ret = null;

		switch(binExpr.oper) {
			case OR, AND:
				if(first.actualType() instanceof SemBool)
					ret = new SemBool();
				else throw new Report.Error(binExpr, "Type error: or/and can only be used on bool types.");
				break;
			case ADD, SUB, MUL, DIV, MOD:
				if(first.actualType() instanceof SemInt)
					ret = new SemInt();
				else throw new Report.Error(binExpr, "Type error: +, -, *, /, % can only be used on int types.");
				break;
			case EQU, NEQ:
				if(isInt(first) || isBool(first) || isChar(first) || isPtr(first))
					ret = new SemBool();
				else throw new Report.Error(binExpr, "Type error: ==, != can only be used on int, bool, char, ptr types.");
				break;
			case LTH, GTH, LEQ, GEQ:
				if(isInt(first) || isChar(first) || isPtr(first))
					ret = new SemBool();
				else throw new Report.Error(binExpr, "Type error: <, >, <=, >= can only be used on int, char and ptr types.");
				break;
			default:
				throw new Report.Error(binExpr, "Type error: invalid binary operator.");
		}

		SemAn.ofType.put(binExpr, ret);
		return ret;
	}

	// V8 (partial)
	@Override
	public SemType visit(AstSfxExpr sfxExpr, Mode mode) {
		SemType exprType = sfxExpr.expr.accept(this, mode);

		if(exprType == null) 
			throw new Report.Error(sfxExpr, "Type error: missing type in suffix expression.");

		SemType ret = null;
		if(sfxExpr.oper == AstSfxExpr.Oper.PTR) {
			if(exprType.actualType() instanceof SemPtr)
				ret = ((SemPtr)exprType.actualType()).baseType;
			else
				throw new Report.Error(sfxExpr, "Type error: expression must be of pointer type.");

		}
		else throw new Report.Error(sfxExpr, "Type error: invalid suffix type.");
		
		SemAn.ofType.put(sfxExpr, ret);
		return ret;
	}

	// V9 (partial)
	@Override
	public SemType visit(AstNewExpr newExpr, Mode mode) {
		SemType type = newExpr.type.accept(this, mode);
		SemType ret = new SemPtr(type);
		SemAn.ofType.put(newExpr, ret);
		return ret;
	}
	
	// V9 (partial)
	@Override
	public SemType visit(AstDelExpr delExpr, Mode mode) {
		SemType exprType = delExpr.expr.accept(this, mode);
		SemType ret = null;

		if(exprType.actualType() instanceof SemPtr) {
			ret = new SemVoid();
		}
		else {
			throw new Report.Error(delExpr, "Type error: del expression must be of pointer type.");
		}

		SemAn.ofType.put(delExpr, ret);
		return ret;
	}

	// V10
	@Override
	public SemType visit(AstArrExpr arrExpr, Mode mode) {
		SemType arrType = arrExpr.arr.accept(this, mode);
		SemType indexType = arrExpr.idx.accept(this, mode);

		if(arrType == null || indexType == null) 
			throw new Report.Error(arrExpr, "Type error: missing type in array expression.");

		if(!(indexType instanceof SemInt))
			throw new Report.Error(arrExpr, "Type error: array index must be of int type.");

		if(!(arrType instanceof SemArr))
			throw new Report.Error(arrExpr, "Type error: expression not of array type.");

		SemType ret = ((SemArr)arrType).elemType;
		SemAn.ofType.put(arrExpr, ret);
		return ret;
	}

	// V11
	@Override
	public SemType visit(AstRecExpr recExpr, Mode mode) {
		SemType recType = recExpr.rec.accept(this, mode).actualType();

		if(recType instanceof SemRec) {
			SymbTable table = recDeclares.get(recType);

			try {
				AstNameDecl decl = table.fnd(recExpr.comp.name);
				if(decl instanceof AstCmpDecl) {
					AstCmpDecl cmpDecl = (AstCmpDecl) decl;
					SemType type = SemAn.isType.get(cmpDecl.type);
					SemAn.ofType.put(recExpr, type);
					return type;
				}
				else throw new Report.Error(recExpr, "Type error: record expression invalid type.");
			}
			catch (SymbTable.CannotFndNameException e) {
				throw new Report.Error(recExpr, "Type error: component not found.");
			}
		}
		else throw new Report.Error(recExpr, "Type error: expression not of record type.");
	}

	// V12
	@Override
	public SemType visit(AstCallExpr callExpr, Mode mode) {
		AstFunDecl decl = (AstFunDecl) SemAn.declaredAt.get(callExpr);
		decl.type.accept(this, mode);
		// SemType retType = decl.type.accept(this, mode);

		int argSize = callExpr.args != null ? callExpr.args.size() : 0;
		int paramSize = decl.pars != null ? decl.pars.size() : 0;

		if(argSize != paramSize)
			throw new Report.Error(callExpr, "Type error: number of arguments does not match number of parameters.");

		if(argSize > 0) {
			Iterator<AstParDecl> params = decl.pars.iterator();
			Iterator<AstExpr> args = callExpr.args.iterator();

			while(params.hasNext()) {
				SemType pType = SemAn.isType.get(params.next().type);
				AstExpr expr = args.next();
				SemType aType = expr.accept(this, mode);

				if(!areEqual(pType, aType))
					throw new Report.Error(callExpr, "Type error: argument type does not match parameter type.");
			}
		}

		SemType ret = SemAn.isType.get(decl.type);
		SemAn.ofType.put(callExpr, ret);
		return ret;
	}

	// V13
	@Override
	public SemType visit(AstCastExpr castExpr, Mode mode) {
		SemType ogType = castExpr.expr.accept(this, mode);
		SemType castType = castExpr.type.accept(this, mode);

		if(!isChar(ogType) || !isInt(ogType) || !isBool(ogType))
			throw new Report.Error(castExpr, "Type error: expression must be of char, int or bool type.");

		if(!isChar(castType) || !isInt(castType) || !isBool(castType))
			throw new Report.Error(castExpr, "Type error: cast type must be of char, int or bool type.");

		SemAn.ofType.put(castExpr, castType);
		return castType;
	}

	@Override
	public SemType visit(AstNameExpr nameExpr, Mode mode) {
		AstNameDecl decl = SemAn.declaredAt.get(nameExpr);
		SemType ret = null;

		if(decl instanceof AstFunDecl) {
			AstFunDecl funDecl = (AstFunDecl) decl;
			if (funDecl.pars != null) {
				throw new Report.Error(nameExpr, "Provide arguments for a function call");
			}
			ret = SemAn.isType.get(funDecl.type);
		}
		else if(decl instanceof AstVarDecl) {
			AstVarDecl varDecl = (AstVarDecl) decl;
			ret = SemAn.isType.get(varDecl.type);
		}
		else if(decl instanceof AstParDecl) {
			AstParDecl parDecl = (AstParDecl) decl;
			ret = SemAn.isType.get(parDecl.type);
		}

		SemAn.ofType.put(nameExpr, ret);
		return ret;
	}


	// STATEMENTS

	// S1
	@Override
	public SemType visit(AstAssignStmt assignStmt, Mode mode) {
		SemType eType1 = assignStmt.dst.accept(this, mode);
		SemType eType2 = assignStmt.src.accept(this, mode);

		if(eType1 == null || eType2 == null)
			throw new Report.Error(assignStmt, "Type error: missing type in assignment statement.");

		if(!areEqual(eType1, eType2))
			throw new Report.Error(assignStmt, "Type error: types in assignment statement do not match.");

		if(!(isBool(eType1) || isInt(eType1) || isChar(eType1) || isPtr(eType1)))
			throw new Report.Error(assignStmt, "Type error: types in assignment statement must be of bool, int, char or pointer type.");

		SemType ret = new SemVoid();
		SemAn.ofType.put(assignStmt, ret);
		return ret;
	}

	// S2, S3
	@Override
	public SemType visit(AstIfStmt ifStmt, Mode mode) {
		SemType condType = ifStmt.cond.accept(this, mode);
		ifStmt.thenStmt.accept(this, mode);
		if(ifStmt.elseStmt != null)
			ifStmt.elseStmt.accept(this, mode);

		if(!isBool(condType))
			throw new Report.Error(ifStmt, "Type error: condition in if statement must be of bool type.");
			
		SemType ret = new SemVoid();
		SemAn.ofType.put(ifStmt, ret);
		return ret;
	}

	// S4
	@Override
	public SemType visit(AstWhileStmt whileStmt, Mode mode) {
		SemType condType = whileStmt.cond.accept(this, mode);
		whileStmt.bodyStmt.accept(this, mode);

		if(!isBool(condType))
			throw new Report.Error(whileStmt, "Type error: condition in while statement must be of bool type.");
		
		SemType ret = new SemVoid();
		SemAn.ofType.put(whileStmt, ret);
		return ret;
	}

	// S5
	@Override
	public SemType visit(AstDeclStmt declStmt, Mode mode) {
		declStmt.decls.accept(this, mode);

		SemType ret = declStmt.stmt.accept(this, mode);
		SemAn.ofType.put(declStmt, ret);
		return ret;
	}

	@Override
	public SemType visit(AstExprStmt exprStmt, Mode mode) {
		SemType ret = exprStmt.expr.accept(this, mode);
		SemAn.ofType.put(exprStmt, ret);
		return ret;
	}

	// S6
	@Override
	public SemType visit(AstStmts stmts, Mode mode) {
		stmts.stmts.accept(this, mode);
		int lastIndex = stmts.stmts.size() - 1;

        SemType type = SemAn.ofType.get(stmts.stmts.get(lastIndex));
        SemAn.ofType.put(stmts, type);
        return type;
	}

	// DECLARATIONS

	// D1
	@Override
	public SemType visit(AstTypDecl typDecl, Mode mode) {
		if(mode == Mode.HEAD) {
			SemAn.declaresType.put(typDecl, new SemName(typDecl.name));
		}
		else if(mode == Mode.BODY) {
			SemType typ = typDecl.type.accept(this, mode);
			SemAn.declaresType.get(typDecl).define(typ);
		}
		else if(mode == Mode.THIRD) {
			typDecl.type.accept(this, mode);
		}

		return SemAn.declaresType.get(typDecl);
	}

	// D2
	@Override
	public SemType visit(AstVarDecl varDecl, Mode mode) {
		SemType ret = varDecl.type.accept(this, mode);

		if(mode == Mode.THIRD) {
			if(ret == null) {
				Report.warning(varDecl, "Type error: SemType is null");
				return null;
			}

			if(isVoid(ret))
				throw new Report.Error(varDecl, "Type error: variable cannot be of void type.");
		}

		return ret;
	}

	// D3, D4
	@Override
	public SemType visit(AstFunDecl funDecl, Mode mode) {
		SemType ret = null;

		if(mode == Mode.BODY) {
			if(funDecl.pars != null)
				funDecl.pars.accept(this, mode);
		}
		else if(mode == Mode.THIRD) {
			ret = funDecl.type.accept(this, mode);

			if(funDecl.pars != null)
				funDecl.pars.accept(this, mode);

			if(ret.actualType() instanceof SemArr || ret.actualType() instanceof SemRec)
				throw new Report.Error(funDecl, "Type error: invalid function return type.");

			if(funDecl.stmt != null) {
				SemType other = funDecl.stmt.accept(this, mode);

				if(!areEqual(ret, other))
					throw new Report.Error(funDecl, "Type error: function return type does not match statement's return type.");
			}
			
			SemAn.isType.put(funDecl.type, ret);
		}

		return ret;
	}

	@Override
	public SemType visit(AstCmpDecl cmpDecl, Mode mode) {
		SemType ret = cmpDecl.type.accept(this, mode);

		if(mode == Mode.BODY) {
			SemAn.isType.put(cmpDecl.type, ret);
		}
		else if(mode == Mode.THIRD && isVoid(ret)) {
			throw new Report.Error(cmpDecl, "Type error: component cannot be of void type.");
		}

		return ret;
	}

	@Override
	public SemType visit(AstParDecl parDecl, Mode mode) {
		SemType ret = SemAn.isType.get(parDecl.type);

		if(mode == Mode.BODY) {
			ret = parDecl.type.accept(this, mode);
			SemAn.isType.put(parDecl.type, ret);
		}
		else if(mode == Mode.THIRD) {
			if(ret.actualType() instanceof SemRec || ret.actualType() instanceof SemVoid || ret.actualType() instanceof SemArr)
				throw new Report.Error(parDecl, "Type error: invalid parameter type.");
		}

		return ret;
	}

}