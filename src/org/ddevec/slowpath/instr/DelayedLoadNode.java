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

import java.util.Map;
import java.util.function.IntSupplier;

public class DelayedLoadNode extends AbstractInsnNode implements Opcodes {
    IntSupplier idxSupplier;
    Type type;

    public DelayedLoadNode(Type type, IntSupplier idxSupplier) {
        super(AbstractInsnNode.VAR_INSN);
        this.type = type;
        this.idxSupplier = idxSupplier;
    }

    @Override
    public AbstractInsnNode clone(Map<LabelNode, LabelNode> labels) {
        return new DelayedLoadNode(type, idxSupplier);
    }

    @Override
    public void accept(MethodVisitor mv) {
        mv.visitVarInsn(type.getOpcode(ILOAD), idxSupplier.getAsInt());
    }

    @Override
    public int getType() {
        return AbstractInsnNode.VAR_INSN;
    }
}
