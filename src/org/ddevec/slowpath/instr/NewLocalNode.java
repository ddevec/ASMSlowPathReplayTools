package org.ddevec.slowpath.instr;

import org.ddevec.utils.ArrayUtils;

import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Handle;
import rr.org.objectweb.asm.Label;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.TypePath;
import rr.org.objectweb.asm.tree.AbstractInsnNode;
import rr.org.objectweb.asm.tree.LabelNode;
import rr.org.objectweb.asm.tree.InsnList;

import rr.org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Map;


public class NewLocalNode extends AbstractInsnNode {
    public static final int NEW_LOCAL = 16;

    int localIdx;
    Type type;

    public NewLocalNode(Type type) {
        super(NEW_LOCAL);
        this.type = type;
        localIdx = -1;
    }

    public int getIndex() {
        assert localIdx >= 0 : "getIndex called before index set";
        return localIdx;
    }

    @Override
    public AbstractInsnNode clone(Map<LabelNode, LabelNode> labels) {
        return new NewLocalNode(type);
    }

    @Override
    public void accept(MethodVisitor mv) {
        if (mv instanceof LocalVariablesSorter) {
            localIdx = ((LocalVariablesSorter)mv).newLocal(type);
        } else {
            System.err.println("WARNING: localNode called on non-local sorter mv");
            assert false;
        }
    }

    @Override
    public int getType() {
        return NEW_LOCAL;
    }
}

