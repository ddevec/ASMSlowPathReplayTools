package org.ddevec.slowpath.instr;

import rr.org.objectweb.asm.AnnotationVisitor;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Handle;
import rr.org.objectweb.asm.Label;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.TypePath;
import rr.org.objectweb.asm.util.Printer;
import rr.org.objectweb.asm.util.Textifier;
import rr.org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.util.HashSet;

public class DebugVisitor extends ClassVisitor {
  String prefix;

  private class DebugMv extends MethodVisitor {
    /*
    HashSet<Label> defined = new HashSet<Label>();
    HashSet<Label> used = new HashSet<Label>();
    */

    public DebugMv(MethodVisitor mv) {
      this(mv, false);
    }

    public DebugMv(MethodVisitor mv, boolean doLabels) {
      super(Opcodes.ASM5, mv);
    }
    
    @Override
    public void visitCode() {
      System.err.println(prefix + ": VisitCode");
      super.visitCode();
    }

    @Override public void visitInsn(int insn) {
      System.err.println(prefix + ": visitInsn: " + insn);
      super.visitInsn(insn);
    }

    /*
    @Override
    public void visitLabel(Label lbl) {
      defined.add(lbl);
      super.visitLabel(lbl);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      used.add(label);
    }
    */

    @Override
    public void visitMaxs(int maxL, int maxS) {
      System.err.println(prefix + ": visitMaxs(" + maxL + ", " + maxS + ")");
      super.visitMaxs(maxL, maxS);
    }

    @Override
    public void visitEnd() {
      System.err.println(prefix + ": VisitEnd");
      super.visitEnd();
    }
  }

  public DebugVisitor(ClassVisitor cv, String prefix) {
    super(Opcodes.ASM5, cv);
    this.prefix = prefix;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    System.err.println(prefix + ": VisitMethod: " + name);
    MethodVisitor ret = cv.visitMethod(access, name, desc, signature, exceptions);
    return new DebugMv(ret);
  }

  @Override
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    System.err.println(prefix + ": VisitClass: " + name);
    super.visit(version, access, name, signature, superName, interfaces);
  }
}

