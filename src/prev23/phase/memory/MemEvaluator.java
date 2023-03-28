package prev23.phase.memory;

import prev23.data.ast.tree.*;
import prev23.data.ast.tree.decl.*;
import prev23.data.ast.tree.expr.*;
import prev23.data.ast.tree.type.*;
import prev23.data.ast.visitor.*;
import prev23.data.typ.*;
import prev23.data.mem.*;
import prev23.phase.seman.*;

/**
 * Computing memory layout: frames and accesses.
 */
public class MemEvaluator extends AstFullVisitor<Object, MemEvaluator.Context> {

    public class Context {
        int depth = 0;
        int offset = 0;
        int locsSize = 0;
        int argsSize = 0;
        boolean inFunction = false;
    };

    @Override
	public Object visit(AstTrees<? extends AstTree> trees, Context ctx) {
		if(ctx == null)
			ctx = new Context();
		
		for (AstTree t : trees)
			if (t != null)
				t.accept(this, ctx);
		return null;
	}

    // Declarations

    @Override
    public Object visit(AstFunDecl funDecl, Context ctx) {
        Context ctx2 = new Context();
        ctx2.inFunction = true;
        ctx2.depth = ctx.depth + 1;

        if (funDecl.pars != null)
            funDecl.pars.accept(this, ctx2);

        funDecl.type.accept(this, ctx2);
        ctx2.offset = 0;

        if (funDecl.stmt != null)
            funDecl.stmt.accept(this, ctx2);
        
        MemLabel label = ctx.depth == 0 ? new MemLabel(funDecl.name) : new MemLabel();
        MemFrame frame = new MemFrame(label, ctx.depth, ctx2.locsSize, ctx2.argsSize);
        Memory.frames.put(funDecl, frame);
        return null;
    }

    @Override
	public Object visit(AstParDecl parDecl, Context ctx) {
        parDecl.type.accept(this, ctx);
        SemType type = SemAn.isType.get(parDecl.type);
        ctx.offset += type.size();

        MemAccess access = new MemRelAccess(type.size(), ctx.offset, ctx.depth);
        Memory.accesses.put(parDecl, access);
        return null;
    }

    @Override
    public Object visit(AstVarDecl varDecl, Context ctx) {
        varDecl.type.accept(this, ctx);

        SemType type = SemAn.isType.get(varDecl.type);
        MemAccess access = null;

        if (ctx.inFunction) {
            ctx.offset -= type.size();
            ctx.locsSize += type.size();
            access = new MemRelAccess(type.size(), ctx.offset, ctx.depth);
        }
        else {
            access = new MemAbsAccess(type.size(), new MemLabel(varDecl.name));
        }

        Memory.accesses.put(varDecl, access);
        return null;
    }

    @Override
	public Object visit(AstTypDecl typDecl, Context ctx) {
		typDecl.type.accept(this, ctx);
		return null;
	}

    // Expressions

    @Override
    public Object visit(AstAtomExpr atomExpr, Context ctx) {
        if (atomExpr.type != AstAtomExpr.Type.STR)
            return null;

        String temp = new String(atomExpr.value);
        temp = temp.substring(1, atomExpr.value.length() - 1); // remove quotes
        temp = temp.replace("\\\"", "\""); // replace escaped quotes

        long size = 8 * (temp.length() + 1);

        MemAbsAccess access = new MemAbsAccess(size, new MemLabel(), temp);
        Memory.strings.put(atomExpr, access);
        return null;
    }

    @Override
    public Object visit(AstCallExpr callExpr, Context ctx) {
        int size = 8;

        if(callExpr.args != null && callExpr.args.size() > 0) {
            callExpr.args.accept(this, ctx);

            for(AstExpr expr : callExpr.args) {
                SemType type = SemAn.ofType.get(expr);
                size += type.size();
            }
        }

        ctx.argsSize = Math.max(size, ctx.argsSize);
        return null;
    }

    // Types

    @Override
    public Object visit(AstRecType recType, Context ctx) {
        long offset = 0;

        for(AstCmpDecl decl : recType.comps) {
            decl.type.accept(this, ctx);

            SemType type = SemAn.isType.get(decl.type);
            MemAccess access = new MemRelAccess(type.size(), offset, 0);
            offset += type.size();
            
            Memory.accesses.put(decl, access);
        }

        return null;
    }
}
