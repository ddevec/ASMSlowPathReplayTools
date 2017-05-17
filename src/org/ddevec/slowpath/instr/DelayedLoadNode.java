package org.ddevec.slowpath.instr;

import org.ddevec.utils.ArrayUtils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.InsnList;

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
