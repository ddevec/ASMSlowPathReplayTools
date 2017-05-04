package org.ddevec.slowpath.instr;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class that creates a series of a operations which will duplciate a method
 * when called on a specified MethodVisitor.
 */

public class MethodDuplicator extends MethodVisitor implements Opcodes {
  private abstract class MVRunner {
    abstract void run(MethodVisitor mv);
  };

  private ArrayList<MVRunner> visits;
  private HashMap<Label, Label> labelRemap;

  public MethodDuplicator(int api, MethodVisitor mv) {
    super(api, mv);

    labelRemap = new HashMap<Label, Label>();
    visits = new ArrayList<MVRunner>();
  }

  private final void addRunner(MVRunner rb) {
    visits.add(rb);
  }

  public void doVisits(MethodVisitor mv) {
    for (MVRunner runner : visits) {
      runner.run(mv);
    }
  }

  private Label labelRemap(Label orig) {
    Label ret = labelRemap.get(orig);

    if (ret == null) {
      ret = new Label();
      labelRemap.put(orig, ret);
    }

    return ret;
  }

  @Override
  public void visitCode() {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitCode();
          }
        });

    super.visitCode();
  }

  @Override
  public void visitByteCodeIndex(int bci) {
    addRunner(new MVRunner() {
        public void run(MethodVisitor mv) {
          mv.visitByteCodeIndex(bci);
        }
      });

    super.visitByteCodeIndex(bci);
  }
  
  @Override
  public void visitFrame(int type, int nLocal, Object[] local, int nStack,
      Object[] stack) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitFrame(type, nLocal, local, nStack, stack);
          }
        });

    super.visitFrame(type, nLocal, local, nStack, stack);
  }

  @Override
  public void visitLabel(Label lbl) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            Label remap = labelRemap(lbl);
            mv.visitLabel(remap);
          }
        });
    super.visitLabel(lbl);
  }

  @Override
  public void visitInsn(int opcode) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitInsn(opcode);
          }
        });
    super.visitInsn(opcode);
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitIntInsn(opcode, operand);
          }
        });
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitVarInsn(opcode, var);
          }
        });
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitTypeInsn(opcode, type);
          }
        });
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner,
      String name, String desc) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitFieldInsn(opcode, owner, name, desc);
          }
        });
    super.visitFieldInsn(opcode, owner, name, desc);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner,
      String name, String desc, boolean itf) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
          }
        });
    super.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String desc,
      Handle bsm, Object... bsmArgs) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
          }
        });
    super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            Label remap = labelRemap(label);
            mv.visitJumpInsn(opcode, remap);
          }
        });
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLdcInsn(Object cst) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitLdcInsn(cst);
          }
        });
    super.visitLdcInsn(cst);
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitIincInsn(var, increment);
          }
        });
    super.visitIincInsn(var, increment);
  }

  @Override
  public void visitTableSwitchInsn(int min, int max,
      Label dflt, Label... labels) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            Label remap = labelRemap(dflt);
            Label[] lblRemaps = new Label[labels.length];
            for (int i = 0; i < lblRemaps.length; i++) {
              lblRemaps[i] = labelRemap(labels[i]);
            }
            mv.visitTableSwitchInsn(min, max, remap, lblRemaps);
          }
        });
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt,
      int[] keys, Label... labels) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            Label remap = labelRemap(dflt);
            Label[] lblRemaps = new Label[labels.length];
            for (int i = 0; i < lblRemaps.length; i++) {
              lblRemaps[i] = labelRemap(labels[i]);
            }
            mv.visitLookupSwitchInsn(remap, keys, lblRemaps);
          }
        });
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(String desc, int dims) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitMultiANewArrayInsn(desc, dims);
          }
        });
    super.visitMultiANewArrayInsn(desc, dims);
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end,
      Label handler, String type) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            Label startRemap = labelRemap(start);
            Label endRemap = labelRemap(end);
            Label handlerRemap = labelRemap(handler);
            mv.visitTryCatchBlock(startRemap, endRemap, handlerRemap, type);
          }
        });
    super.visitTryCatchBlock(start, end, handler, type);
  }

  @Override
  public void visitLocalVariable(String name, String desc,
      String signature, Label start, Label end, int index) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            Label startRemap = labelRemap(start);
            Label endRemap = labelRemap(end);
            mv.visitLocalVariable(name, desc, signature, startRemap, endRemap, index);
          }
        });
    super.visitLocalVariable(name, desc, signature, start, end, index);
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            Label startRemap = labelRemap(start);
            mv.visitLineNumber(line, startRemap);
          }
        });
    super.visitLineNumber(line, start);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    addRunner(new MVRunner() {
          public void run(MethodVisitor mv) {
            mv.visitMaxs(maxStack, maxLocals);
          }
        });
    super.visitMaxs(maxStack, maxLocals);
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }
}
