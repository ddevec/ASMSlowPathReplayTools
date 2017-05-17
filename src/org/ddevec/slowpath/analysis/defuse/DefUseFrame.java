/**
 * asm-defuse: asm powered by definitions/uses analysis
 * Copyright (c) 2013, 2017 Roberto Araujo (roberto.andrioli@gmail.com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.ddevec.slowpath.analysis.defuse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;

public class DefUseFrame extends Frame<Value> {

    public static final DefUseFrame NONE = new DefUseFrame(0, 0);

    public final boolean predicate;

    private Set<Value> defs = Collections.emptySet();

    private Set<Value> uses = Collections.emptySet();

    public DefUseFrame(final int nLocals, final int nStack) {
        super(nLocals, nStack);
        predicate = false;
    }

    public DefUseFrame(final Frame<? extends Value> src) {
        super(src);
        this.predicate = false;
    }

    public DefUseFrame(final Frame<Value> src, final boolean predicate) {
        super(src);
        this.predicate = predicate;
    }

    public Set<Value> getDefinitions() {
        return defs;
    }

    public Set<Value> getUses() {
        return uses;
    }

    @Override
    public void execute(final AbstractInsnNode insn, final Interpreter<Value> interpreter)
            throws AnalyzerException {

        Value value1, value2, value3;
        List<Value> values;
        Value variable;
        int var;

        if (insn instanceof MethodInsnNode) {
            MethodInsnNode min = (MethodInsnNode)insn;
            System.err.println("  Processing call: " + min.owner + "." + min.name);
        } else {
            System.err.println("  Processing: " + insn);
        }
        System.err.print("    init-Stack: ");
        for (int i = 0; i < getStackSize(); i++) {
            Value v = getStack(i);
            System.err.print(" " + v);
        }
        System.err.println();
        switch (insn.getOpcode()) {
        case Opcodes.NOP:
            break;
        case Opcodes.ACONST_NULL:
        case Opcodes.ICONST_M1:
        case Opcodes.ICONST_0:
        case Opcodes.ICONST_1:
        case Opcodes.ICONST_2:
        case Opcodes.ICONST_3:
        case Opcodes.ICONST_4:
        case Opcodes.ICONST_5:
        case Opcodes.LCONST_0:
        case Opcodes.LCONST_1:
        case Opcodes.FCONST_0:
        case Opcodes.FCONST_1:
        case Opcodes.FCONST_2:
        case Opcodes.DCONST_0:
        case Opcodes.DCONST_1:
        case Opcodes.BIPUSH:
        case Opcodes.SIPUSH:
        case Opcodes.LDC:
            handleNew(insn, interpreter);
            break;
        case Opcodes.ILOAD:
        case Opcodes.LLOAD:
        case Opcodes.FLOAD:
        case Opcodes.DLOAD:
        case Opcodes.ALOAD:
            handleLoad(insn, interpreter);
            break;
        case Opcodes.IALOAD:
        case Opcodes.LALOAD:
        case Opcodes.FALOAD:
        case Opcodes.DALOAD:
        case Opcodes.AALOAD:
        case Opcodes.BALOAD:
        case Opcodes.CALOAD:
        case Opcodes.SALOAD:
            handleBinary(insn, interpreter);
            break;
        case Opcodes.ISTORE:
        case Opcodes.LSTORE:
        case Opcodes.FSTORE:
        case Opcodes.DSTORE:
        case Opcodes.ASTORE:
            handleStore(insn, interpreter);
            break;
        case Opcodes.IASTORE:
        case Opcodes.LASTORE:
        case Opcodes.FASTORE:
        case Opcodes.DASTORE:
        case Opcodes.AASTORE:
        case Opcodes.BASTORE:
        case Opcodes.CASTORE:
        case Opcodes.SASTORE:
            value3 = pop();
            value2 = pop();
            value1 = pop();
            uses = new LinkedHashSet<Value>();
            uses.add(value3);
            uses.add(value2);
            uses.add(value1);
            uses = Collections.unmodifiableSet(uses);
            break;
        case Opcodes.POP:
            value1 = pop();
            uses = Collections.singleton(value1);
            break;
        case Opcodes.POP2:
            if (getStackSize() == 1) {
                value1 = pop();
                uses = Collections.singleton(value1);
            } else {
                value1 = pop();
                value2 = pop();
                uses = new LinkedHashSet<Value>();
                uses.add(value1);
                uses.add(value2);
                uses = Collections.unmodifiableSet(uses);
            }
            break;
        case Opcodes.DUP:
        case Opcodes.DUP_X1:
        case Opcodes.DUP_X2:
        case Opcodes.DUP2:
        case Opcodes.DUP2_X1:
        case Opcodes.DUP2_X2:
        case Opcodes.SWAP:
            handleCopy(insn, interpreter);
            break;
        case Opcodes.IADD:
        case Opcodes.LADD:
        case Opcodes.FADD:
        case Opcodes.DADD:
        case Opcodes.ISUB:
        case Opcodes.LSUB:
        case Opcodes.FSUB:
        case Opcodes.DSUB:
        case Opcodes.IMUL:
        case Opcodes.LMUL:
        case Opcodes.FMUL:
        case Opcodes.DMUL:
        case Opcodes.IDIV:
        case Opcodes.LDIV:
        case Opcodes.FDIV:
        case Opcodes.DDIV:
        case Opcodes.IREM:
        case Opcodes.LREM:
        case Opcodes.FREM:
        case Opcodes.DREM:
        case Opcodes.ISHL:
        case Opcodes.LSHL:
        case Opcodes.ISHR:
        case Opcodes.LSHR:
        case Opcodes.IUSHR:
        case Opcodes.LUSHR:
        case Opcodes.IAND:
        case Opcodes.LAND:
        case Opcodes.IOR:
        case Opcodes.LOR:
        case Opcodes.IXOR:
        case Opcodes.LXOR:
            handleBinary(insn, interpreter);
            break;
        case Opcodes.INEG:
        case Opcodes.LNEG:
        case Opcodes.FNEG:
        case Opcodes.DNEG:
            handleUnary(insn, interpreter);
            break;
        case Opcodes.IINC:
            {
            var = ((IincInsnNode) insn).var;
            value1 = getLocal(var);
            Value def1 = value1.with(insn);

            setLocal(var, def1);
            defs = Collections.singleton(def1);
            uses = Collections.singleton(value1);
            }
            break;
        case Opcodes.I2L:
        case Opcodes.I2F:
        case Opcodes.I2D:
        case Opcodes.L2I:
        case Opcodes.L2F:
        case Opcodes.L2D:
        case Opcodes.F2I:
        case Opcodes.F2L:
        case Opcodes.F2D:
        case Opcodes.D2I:
        case Opcodes.D2L:
        case Opcodes.D2F:
        case Opcodes.I2B:
        case Opcodes.I2C:
        case Opcodes.I2S:
            handleUnary(insn, interpreter);
            break;
        case Opcodes.LCMP:
        case Opcodes.FCMPL:
        case Opcodes.FCMPG:
        case Opcodes.DCMPL:
        case Opcodes.DCMPG:
            handleBinary(insn, interpreter);
            break;
        case Opcodes.IFEQ:
        case Opcodes.IFNE:
        case Opcodes.IFLT:
        case Opcodes.IFGE:
        case Opcodes.IFGT:
        case Opcodes.IFLE:
            uses = Collections.singleton(pop());
            break;
        case Opcodes.IF_ICMPEQ:
        case Opcodes.IF_ICMPNE:
        case Opcodes.IF_ICMPLT:
        case Opcodes.IF_ICMPGE:
        case Opcodes.IF_ICMPGT:
        case Opcodes.IF_ICMPLE:
        case Opcodes.IF_ACMPEQ:
        case Opcodes.IF_ACMPNE:
            uses = new LinkedHashSet<Value>();
            uses.add(pop());
            uses.add(pop());
            break;
        case Opcodes.GOTO:
            break;
        case Opcodes.JSR:
            assert false : "JSR Unsupported in DefuseFrame";
            super.execute(insn, interpreter);
            break;
        case Opcodes.RET:
            break;
        case Opcodes.TABLESWITCH:
        case Opcodes.LOOKUPSWITCH:
        case Opcodes.IRETURN:
        case Opcodes.LRETURN:
        case Opcodes.FRETURN:
        case Opcodes.DRETURN:
        case Opcodes.ARETURN:
            handleUnary(insn, interpreter);
            break;
        case Opcodes.RETURN:
            // Does nothing
            super.execute(insn, interpreter);
            defs = Collections.emptySet();
            break;
        case Opcodes.GETSTATIC:
            handleNew(insn, interpreter);
            break;
        case Opcodes.PUTSTATIC: {
            handleUnary(insn, interpreter);
            break;
        }
        case Opcodes.GETFIELD:
            // DEFs: the stack variable of the field
            // USEs: the stack object variable
            handleUnary(insn, interpreter);
            //super.execute(insn, interpreter);
            break;
        case Opcodes.PUTFIELD: {
            // DEFs: None -- requires ptsto...
            // USEs: the stack object variable, the stack object value
            final FieldInsnNode f = (FieldInsnNode) insn;
            value2 = pop();
            value1 = pop();
            variable = new ObjectField(f.owner, f.name, f.desc, value1);
            defs = Collections.singleton(variable);
            uses = new LinkedHashSet<Value>();
            uses.add(value2);
            uses.add(value1);
            uses = Collections.unmodifiableSet(uses);
            break;
        }
        case Opcodes.INVOKEVIRTUAL:
        case Opcodes.INVOKESPECIAL:
        case Opcodes.INVOKESTATIC:
        case Opcodes.INVOKEINTERFACE: {
            values = new ArrayList<Value>();
            final String desc = ((MethodInsnNode) insn).desc;
            uses = new LinkedHashSet<Value>();
            for (int i = Type.getArgumentTypes(desc).length; i > 0; --i) {
                Value v = pop();
                uses.add(v);
                values.add(0, v);
            }
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                Value v = pop();
                uses.add(v);
                values.add(0, v);
            }

            uses = Collections.unmodifiableSet(uses);
            if (Type.getReturnType(desc) != Type.VOID_TYPE) {
                Value def = interpreter.naryOperation(insn, values);
                System.err.println("  CALL SIZE: " + Type.getReturnType(desc).getSize());
                assert Type.getReturnType(desc).getSize() == 1 : "Unhandled call returning double";
                defs = Collections.singleton(def);
                push(def);
            }
            break;
        }
        case Opcodes.INVOKEDYNAMIC: {
            values = new ArrayList<Value>();
            uses = new LinkedHashSet<Value>();

            final String desc = ((InvokeDynamicInsnNode) insn).desc;
            for (int i = Type.getArgumentTypes(desc).length; i > 0; --i) {
                Value v = pop();
                uses.add(v);
                values.add(0, v);
            }

            uses = Collections.unmodifiableSet(uses);
            if (Type.getReturnType(desc) != Type.VOID_TYPE) {
                Value def = interpreter.naryOperation(insn, values);
                assert Type.getReturnType(desc).getSize() == 1 : "Unhandled dynamic call returning double";
                defs = Collections.singleton(def);
                push(def);
            }
            break;
        }
        case Opcodes.NEW:
             handleNew(insn, interpreter);
             break;
        case Opcodes.NEWARRAY:
        case Opcodes.ANEWARRAY:
        case Opcodes.ARRAYLENGTH:
            handleUnary(insn, interpreter);
            break;
        case Opcodes.ATHROW:
            handleUnary(insn, interpreter);
            break;
        case Opcodes.CHECKCAST:
        case Opcodes.INSTANCEOF:
            handleUnary(insn, interpreter);
            break;
        case Opcodes.MONITORENTER:
        case Opcodes.MONITOREXIT:
            handleUnary(insn, interpreter);
            break;
        case Opcodes.MULTIANEWARRAY:
            {
                MultiANewArrayInsnNode mnai = (MultiANewArrayInsnNode)insn;
                values = new ArrayList<Value>();

                for (int i = 0; i < mnai.dims; i++) {
                    Value v = pop();
                    uses.add(v);
                    values.add(0, v);
                }

                uses = Collections.unmodifiableSet(uses);

                Value def = interpreter.naryOperation(insn, values);
                defs = Collections.singleton(def);
                push(def);
            }
            // naryOperator
            // Uses: lots of stuff
            // Defs: top of stack
            super.execute(insn, interpreter);
            break;
        case Opcodes.IFNULL:
        case Opcodes.IFNONNULL:
            handleUnary(insn, interpreter);
            break;
        default:
            throw new IllegalStateException("Illegal opcode " + insn.getOpcode());
        }

        System.err.print("    end-Stack: ");
        for (int i = 0; i < getStackSize(); i++) {
            Value v = getStack(i);
            System.err.print(" " + v);
        }
        System.err.println();
    }

    public void addDef(final Value var) {
        final Set<Value> newDefs = new LinkedHashSet<Value>();
        newDefs.addAll(defs);
        newDefs.add(var);
        defs = Collections.unmodifiableSet(newDefs);
    }

    private void handleUnary(final AbstractInsnNode insn, final Interpreter<Value> interpreter) throws AnalyzerException {
        // Pop the top stack value
        Value value = pop();

        // That is our use...
        uses = Collections.singleton(value);

        // Add a our new value to the stack
        Value toPush = interpreter.unaryOperation(insn, value);
        if (toPush != null) {
            push(toPush);
            defs = Collections.singleton(toPush);
        } else {
            defs = Collections.emptySet();
        }
    }

    private void handleBinary(final AbstractInsnNode insn, final Interpreter<Value> interpreter) throws AnalyzerException {
        // Pop the top stack value
        Value value2 = pop();
        Value value1 = pop();

        uses = new LinkedHashSet<Value>();
        uses.add(value1);
        uses.add(value2);
        uses = Collections.unmodifiableSet(uses);

        // Add a our new value to the stack
        Value toPush = interpreter.binaryOperation(insn, value1, value2);
        if (toPush != null) {
            push(toPush);
            defs = Collections.singleton(toPush);
        } else {
            defs = Collections.emptySet();
        }
    }

    private void handleLoad(final AbstractInsnNode insn, final Interpreter<Value> interpreter) throws AnalyzerException {
        Value value2;

        final VarInsnNode v = (VarInsnNode) insn;
        Value local = getLocal(v.var);
        Value localDef = interpreter.copyOperation(insn,
                local);
        // def1 goes on the stack
        push(localDef);

        defs = new LinkedHashSet<Value>();
        uses = new LinkedHashSet<Value>();
        uses.add(local);
        if (local.getSize() > 1) {
            System.err.println("!~~ Load SIze 2? ~~!");
            Value local2 = getLocal(v.var + 1);
            uses.add(local2);
            defs.add(interpreter.copyOperation(insn, local2));
            push(local2);
        }

        defs = Collections.singleton(localDef);
        uses = Collections.unmodifiableSet(uses);
    }

    private void handleStore(final AbstractInsnNode insn, final Interpreter<Value> interpreter) throws AnalyzerException {
        int var = ((VarInsnNode) insn).var;

        // Value to be stored
        Value use = pop();

        defs = new LinkedHashSet<Value>();
        uses = new LinkedHashSet<Value>();
        Value local = interpreter.copyOperation(insn,
                use);
        setLocal(var, local);
        defs.add(local);
        uses.add(use);

        if (local.getSize() > 1) {
            Value use2 = pop();
            Value local2 = interpreter.copyOperation(insn, use2);
            setLocal(var + 1, local2);
            defs.add(local2);
            uses.add(use2);
        }

        uses = Collections.unmodifiableSet(uses);
        defs = Collections.unmodifiableSet(defs);

        /* FIXME: -- Don't understand this -- appears to clear local behind us if we're size 2
        if (var > 0) {
            final Value local = getLocal(var - 1);
            if (local != null && local.getSize() == 2) {
                setLocal(var - 1, interpreter.newValue(null));
            }
        }
        */
    }

    private void handleCopy(final AbstractInsnNode insn, final Interpreter<Value> interpreter) throws AnalyzerException {
        Value value1;
        Value value2;
        Value value3;
        Value value4;

        Value def1;
        Value def2;
        Value def3;
        Value def4;
        Value def5;
        Value def6;

        switch (insn.getOpcode()) {
            case Opcodes.DUP:
                // duplicate the top element of the stack
                value1 = pop();
                def1 = value1.with(insn);
                def2 = value1.with(insn);

                push(def1);
                push(def2);

                uses = Collections.singleton(value1);
                defs = new LinkedHashSet<Value>();
                defs.add(def1);
                defs.add(def2);
                defs = Collections.unmodifiableSet(defs);

                break;
            case Opcodes.DUP_X1:
                value1 = pop();
                value2 = pop();

                def1 = value1.with(insn);
                def2 = value2.with(insn);
                def3 = value1.with(insn);

                push(def1);
                push(def2);
                push(def3);

                defs = new LinkedHashSet<Value>();
                defs.add(def1);
                defs.add(def2);
                defs.add(def3);
                defs = Collections.unmodifiableSet(defs);

                uses = new LinkedHashSet<Value>();
                uses.add(value1);
                uses.add(value2);
                uses = Collections.unmodifiableSet(uses);

                break;
            case Opcodes.DUP_X2:
                value1 = pop();
                value2 = pop();
                value3 = pop();

                def1 = value1.with(insn);
                def2 = value3.with(insn);
                def3 = value2.with(insn);
                def4 = value1.with(insn);

                push(def1);
                push(def2);
                push(def3);
                push(def4);

                defs = new LinkedHashSet<Value>();
                defs.add(def1);
                defs.add(def2);
                defs.add(def3);
                defs.add(def4);
                defs = Collections.unmodifiableSet(defs);

                uses = new LinkedHashSet<Value>();
                uses.add(value1);
                uses.add(value2);
                uses.add(value3);
                uses = Collections.unmodifiableSet(uses);

                break;
            case Opcodes.DUP2:
                value1 = pop();
                value2 = pop();

                def1 = value2.with(insn);
                def2 = value1.with(insn);
                def3 = value2.with(insn);
                def4 = value1.with(insn);

                push(def1);
                push(def2);
                push(def3);
                push(def4);

                defs = new LinkedHashSet<Value>();
                defs.add(def1);
                defs.add(def2);
                defs.add(def3);
                defs.add(def4);
                defs = Collections.unmodifiableSet(defs);

                uses = new LinkedHashSet<Value>();
                uses.add(value1);
                uses.add(value2);
                uses = Collections.unmodifiableSet(uses);

                break;
            case Opcodes.DUP2_X1:
                value1 = pop();
                value2 = pop();
                value3 = pop();

                def1 = value2.with(insn);
                def2 = value1.with(insn);
                def3 = value3.with(insn);
                def4 = value2.with(insn);
                def5 = value1.with(insn);

                push(def1);
                push(def2);
                push(def3);
                push(def4);
                push(def5);

                defs = new LinkedHashSet<Value>();
                defs.add(def1);
                defs.add(def2);
                defs.add(def3);
                defs.add(def4);
                defs.add(def5);
                defs = Collections.unmodifiableSet(defs);

                uses = new LinkedHashSet<Value>();
                uses.add(value1);
                uses.add(value2);
                uses.add(value3);
                uses = Collections.unmodifiableSet(uses);

                break;
            case Opcodes.DUP2_X2:
                value1 = pop();
                value2 = pop();
                value3 = pop();
                value4 = pop();

                def1 = value2.with(insn);
                def2 = value1.with(insn);
                def3 = value4.with(insn);
                def4 = value3.with(insn);
                def5 = value2.with(insn);
                def6 = value1.with(insn);

                push(def1);
                push(def2);
                push(def3);
                push(def4);
                push(def5);
                push(def6);

                defs = new LinkedHashSet<Value>();
                defs.add(def1);
                defs.add(def2);
                defs.add(def3);
                defs.add(def4);
                defs.add(def5);
                defs.add(def6);
                defs = Collections.unmodifiableSet(defs);

                uses = new LinkedHashSet<Value>();
                uses.add(value1);
                uses.add(value2);
                uses.add(value3);
                uses.add(value4);
                uses = Collections.unmodifiableSet(uses);

                break;
            case Opcodes.SWAP:
                value1 = pop();
                value2 = pop();

                def1 = value1.with(insn);
                def2 = value2.with(insn);

                push(def1);
                push(def2);

                defs = new LinkedHashSet<Value>();
                defs.add(def1);
                defs.add(def2);
                defs = Collections.unmodifiableSet(defs);

                uses = new LinkedHashSet<Value>();
                uses.add(value1);
                uses.add(value2);
                uses = Collections.unmodifiableSet(uses);

                break;
            default:
                throw new IllegalStateException("Illegal opcode " + insn.getOpcode());
        }
    }

    private void handleNew(final AbstractInsnNode insn, final Interpreter<Value> interpreter) throws AnalyzerException {
        // Add a our new value to the stack
        Value toPush = interpreter.newOperation(insn);
        if (toPush != null) {
            push(toPush);
            if (toPush.getSize() > 1) {
                push(toPush);
            }
            defs = Collections.singleton(toPush);
        } else {
            defs = Collections.emptySet();
        }
    }
}
