package org.ddevec.slowpath.analysis;

import java.util.HashSet;
import java.util.Set;

import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.Handle;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;

public class ReachingClassAnalysis extends ClassVisitor {
  Set<String> classes;
  public ReachingClassAnalysis(int api, ClassVisitor cv, Set<String> classes) {
    super(api, cv);
    this.classes = classes;
  }

  @Override
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    // Visit super
    if (superName != null) {
      classes.add(superName);
    }

    for (String iface : interfaces) {
      classes.add(iface);
    }

    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitInnerClass(String name, String outerName, String innerName,
      int access) {
    classes.add(name);
    super.visitInnerClass(name, outerName, innerName, access);
  }


  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    MethodVisitor mv = null;
    if (cv != null) {
      mv = cv.visitMethod(access, name, desc, signature, exceptions);
    }

    mv = new ClassTracker(Opcodes.ASM5, mv, classes);

    return mv;
  }

  private class ClassTracker extends MethodVisitor implements Opcodes {
    private Set<String> classes;
    public ClassTracker(int api, MethodVisitor mv,
        Set<String> classes) {
      super(api, mv);
      this.classes = classes;
    }

    @Override
    public void visitCode() {
      super.visitCode();
    }

    public void visitTypeInsn(int opcode, String type) {
      classes.add(type);
      super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner,
        String name, String desc, boolean itf) {
      //System.out.println("Found call to: " + owner);
      classes.add(owner);
      super.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc,
        Handle bsm, Object... bsmArgs) {
      //System.err.println("WARNING: Unhandled invokeDynamic");
      super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
    }
  }
}

