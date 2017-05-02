package org.ddevec.slowpath.analysis;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class BasicBlockVisitor extends MethodVisitor implements Opcodes {
  int bci;
  private boolean checkNext;
  private Label labelNext;

  private HashMap<Label, Integer> labels;
  private HashSet<Label> toVisitLabels;
  private HashSet<Integer> visitedBcis;

  public BasicBlockVisitor(int api, MethodVisitor mv) {
    super(api, mv);
    labels = new HashMap<Label, Integer>();
    toVisitLabels = new HashSet<Label>();
    visitedBcis = new HashSet<Integer>();
  }

  @Override
  public void visitCode() {
    // Always check bci 0
    checkNext = true;
    labelNext = null;
    super.visitCode();
    labels.clear();
    toVisitLabels.clear();
    visitedBcis.clear();
  }

  @Override
  public void visitByteCodeIndex(int bci) {
    this.bci = bci;
    super.visitByteCodeIndex(bci);

    if (checkNext) {
      //System.out.println("CheckNextVisit");
      doVisitBasicBlock(bci);
      checkNext = false;
    }

    if (labelNext != null) {
      //System.out.println(bci + ": Label Check: " + labelNext);
      labels.put(labelNext, bci);

      if (toVisitLabels.contains(labelNext)) {
        //System.out.println("  " + bci + ": To Visit Label: " + labelNext);
        doVisitBasicBlock(bci);
      }
      labelNext = null;
    }
  }

  private void doVisitBasicBlock(int bci) {
    // If this bci has not been visited
    if (!visitedBcis.contains(bci)) {
      visitedBcis.add(bci);

      visitBasicBlock(bci);
    }
  }

  public void visitBasicBlock(int bci) { }

  @Override
  public void visitLabel(Label lbl) {
    //System.out.println(bci + ": Visit Label: " + lbl);
    labelNext = lbl;
    super.visitLabel(lbl);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    super.visitJumpInsn(opcode, label);

    checkNext = true;

    checkLabel(label);
  }

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler,
      String type) {
    checkLabel(handler);
  }

  private void checkLabel(Label label) {
    Integer lblBci = labels.get(label);
    if (lblBci == null) {
      //System.out.println("  " + bci + ": Do visit: " + label);
      toVisitLabels.add(label);
    } else {
      //System.out.println(bci + ": lblBci visit: " + lblBci);
      doVisitBasicBlock(lblBci);
    }
  }
}

