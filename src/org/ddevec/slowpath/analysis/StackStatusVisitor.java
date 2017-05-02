package org.ddevec.slowpath.analysis;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;

/**
 * Class tracks what variables are live on each stack frame.
 *
 * This is used to determine what local state needs to be duplciated by the
 * mis-speculation function duplicator
 */
public class StackStatusVisitor extends MethodVisitor implements Opcodes {
  protected ArrayList<Object> stackVals;
  protected ArrayList<Object> localVals;

  public StackStatusVisitor(int api, MethodVisitor mv) {
    super(api, mv);
    stackVals = new ArrayList<Object>();
  }

  @Override
  public void visitCode() {
    

    super.visitCode();
  }

  public void visitFrame(int type, int nLocal, Object[] local, int nStack,
      Object[] stack) {
    switch(type) {
      case F_NEW:
      case F_FULL:
        stackVals.clear();
        localVals.clear();
        for (int i = 0; i < nStack; i++) {
          stackVals.add(stack[i]);
        }
        for (int i = 0; i < nLocal; i++) {
          localVals.add(local[i]);
        }
        break;
      case F_SAME:
        stackVals.clear();
        break;
      case F_SAME1:
        assert(nStack == 1);
        stackVals.add(stack[0]);
        break;
      case F_APPEND:
        assert(nLocal >= 1 && nLocal <= 4);
        for (int i = 0; i < nLocal; i++) {
          localVals.add(local[i]);
        }
        break;
      case F_CHOP:
        assert(nLocal >= 1 && nLocal <= 4);
        for (int i = 0; i < nLocal; i++) {
          localVals.remove(localVals.size() - 1);
        }
        break;
    }
  }
}

