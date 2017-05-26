package org.ddevec.slowpath.instr;

import rr.org.objectweb.asm.AnnotationVisitor;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Handle;
import rr.org.objectweb.asm.Label;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.TypePath;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Class that duplicates the body of a method immediately following the original
 * body of that method -- calls a specified visitor on that body 
 */

public class CloneMethodVisitor extends MethodVisitor implements Opcodes {
  public static final String misSpecClass =
    "org/ddevec/slowpath/runtime/MisSpecException";

  public static final String flagClassName =
    "org/ddevec/slowpath/runtime/MisSpecException";
  public static final String flagVarName =
    "slowFlag";
  public static final String flagType =
    Type.BOOLEAN_TYPE.getDescriptor();

  private int bci = 0;
  private int retType = 0;
  private boolean foundRet;
  private boolean insAfterSpecial;
  private MethodVisitor mv2;

  private ArrayList<Runnable> visitAtEnd;
  private HashMap<Label, Label> labelRemap;

  private Label fast_end;
  private Label slow_start;
  private Label end;

  private String methodName;

  public CloneMethodVisitor(int api, String methodName,
      MethodVisitor mv1, MethodVisitor mv2) {
    super(api, mv1);
    this.mv2 = mv2;

    this.methodName = methodName;

    labelRemap = new HashMap<Label, Label>();
    visitAtEnd = new ArrayList<Runnable>();
  }

  private Label labelRemap(Label orig) {
    Label ret = labelRemap.get(orig);

    if (ret == null) {
      ret = new Label();
      //System.out.println("LabelRemap: " + orig + " -> " + ret);
      labelRemap.put(orig, ret);
    }

    return ret;
  }

  private void addRunner(Runnable rb) {
    if (!insAfterSpecial) {
      visitAtEnd.add(rb);
    }
  }

  private void doDuplicateMethod() {
    // Ensure this is run exactly once
    assert(foundRet == false);
    foundRet = true;
    super.visitLabel(fast_end);
    //super.visitInsn(opcode);
    mv2.visitLabel(slow_start);

    for (Runnable visit : visitAtEnd) {
      visit.run();
    }

    super.visitLabel(end);
    mv2.visitInsn(retType);
  }

  @Override
  public void visitCode() {
    foundRet = false;

    labelRemap.clear();

    fast_end = new Label();
    slow_start = new Label();
    end = new Label();

    visitAtEnd.add(new Runnable() {
          public void run() {
            mv2.visitCode();
          }
        });

    super.visitCode();

    if (methodName.equals("<init>")) {
      insAfterSpecial = true;
    } else {
      insAfterSpecial = false;
    }
  }

  @Override
  public void visitByteCodeIndex(int bci) {
    this.bci = bci;
    addRunner(new Runnable() {
        public void run() {
          mv2.visitByteCodeIndex(bci);
        }
      });
    super.visitByteCodeIndex(bci);
  }
  
  @Override
  public void visitFrame(int type, int nLocal, Object[] local, int nStack,
      Object[] stack) {
    addRunner(new Runnable() {
          public void run() {
            //System.out.println("  " + bci + ": NewFrame " + type + ": " + nLocal + ", " + nStack);
            mv2.visitFrame(type, nLocal, local, nStack, stack);
          }
        });

    super.visitFrame(type, nLocal, local, nStack, stack);
  }

  @Override
  public void visitLabel(Label lbl) {
    addRunner(new Runnable() {
          public void run() {
            Label remap = labelRemap(lbl);
            //System.out.println("REVisitLabel: " + remap);
            mv2.visitLabel(remap);
          }
        });
    //System.out.println("VisitLabel: " + lbl);
    super.visitLabel(lbl);
  }

  @Override
  public void visitInsn(int opcode) {
    if (opcode == RETURN ||
        opcode == IRETURN ||
        opcode == LRETURN ||
        opcode == FRETURN ||
        opcode == DRETURN ||
        opcode == ARETURN) {
      // Instead add goto end
      retType = opcode;
      super.visitJumpInsn(GOTO, end);
      addRunner(new Runnable() {
            public void run() {
              mv2.visitJumpInsn(GOTO, end);
            }
          });
    } else {
      addRunner(new Runnable() {
            public void run() {
              mv2.visitInsn(opcode);
            }
          });
      super.visitInsn(opcode);
    }
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitIntInsn(opcode, operand);
          }
        });
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitVarInsn(opcode, var);
          }
        });
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitTypeInsn(opcode, type);
          }
        });
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner,
      String name, String desc) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitFieldInsn(opcode, owner, name, desc);
          }
        });
    super.visitFieldInsn(opcode, owner, name, desc);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner,
      String name, String desc, boolean itf) {
    Label slow = new Label();
    addRunner(new Runnable() {
          public void run() {
            mv2.visitMethodInsn(opcode, owner, name, desc, itf);

            // Add slowpath label
            mv2.visitLabel(slow);
            // Do I need to add branch?
          }
        });
    super.visitMethodInsn(opcode, owner, name, desc, itf);
    if (!insAfterSpecial) {
      super.visitFieldInsn(GETSTATIC, flagClassName, flagVarName, flagType);
      // branch to slow if flag is not zero
      super.visitJumpInsn(IFNE, slow);
    }
    if (insAfterSpecial && opcode == INVOKESPECIAL) {
      insAfterSpecial = false;
    }
  }

  @Override
  public void visitInvokeDynamicInsn(String name, String desc,
      Handle bsm, Object... bsmArgs) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
          }
        });
    super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    addRunner(new Runnable() {
          public void run() {
            Label remap = labelRemap(label);
            mv2.visitJumpInsn(opcode, remap);
          }
        });
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLdcInsn(Object cst) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitLdcInsn(cst);
          }
        });
    super.visitLdcInsn(cst);
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitIincInsn(var, increment);
          }
        });
    super.visitIincInsn(var, increment);
  }

  @Override
  public void visitTableSwitchInsn(int min, int max,
      Label dflt, Label... labels) {
    addRunner(new Runnable() {
          public void run() {
            Label remap = labelRemap(dflt);
            Label[] lblRemaps = new Label[labels.length];
            for (int i = 0; i < lblRemaps.length; i++) {
              lblRemaps[i] = labelRemap(labels[i]);
            }
            mv2.visitTableSwitchInsn(min, max, remap, lblRemaps);
          }
        });
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt,
      int[] keys, Label... labels) {
    addRunner(new Runnable() {
          public void run() {
            Label remap = labelRemap(dflt);
            Label[] lblRemaps = new Label[labels.length];
            for (int i = 0; i < lblRemaps.length; i++) {
              lblRemaps[i] = labelRemap(labels[i]);
            }
            mv2.visitLookupSwitchInsn(remap, keys, lblRemaps);
          }
        });
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(String desc, int dims) {
    addRunner(new Runnable() {
          public void run() {
            mv2.visitMultiANewArrayInsn(desc, dims);
          }
        });
    super.visitMultiANewArrayInsn(desc, dims);
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end,
      Label handler, String type) {
    addRunner(new Runnable() {
          public void run() {
            Label startRemap = labelRemap(start);
            Label endRemap = labelRemap(end);
            Label handlerRemap = labelRemap(handler);
            mv2.visitTryCatchBlock(startRemap, endRemap, handlerRemap, type);
          }
        });
    super.visitTryCatchBlock(start, end, handler, type);
  }

  @Override
  public void visitLocalVariable(String name, String desc,
      String signature, Label start, Label end, int index) {
    addRunner(new Runnable() {
          public void run() {
            Label startRemap = labelRemap(start);
            Label endRemap = labelRemap(end);
            //System.out.println("!--! startRemap: " + startRemap);
            mv2.visitLocalVariable(name, desc, signature, startRemap, endRemap, index);
          }
        });
    super.visitLocalVariable(name, desc, signature, start, end, index);
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    addRunner(new Runnable() {
          public void run() {
            Label startRemap = labelRemap(start);
            mv2.visitLineNumber(line, startRemap);
          }
        });
    super.visitLineNumber(line, start);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    doDuplicateMethod();

    super.visitMaxs(maxStack, maxLocals);
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }
}
