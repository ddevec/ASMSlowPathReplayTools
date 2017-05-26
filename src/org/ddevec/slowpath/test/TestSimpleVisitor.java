package org.ddevec.slowpath.test;

import java.util.HashMap;
import java.util.Map;

import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.commons.JSRInlinerAdapter;
import rr.org.objectweb.asm.util.CheckMethodAdapter;

import org.ddevec.slowpath.instr.CloneMethodVisitor;
import org.ddevec.slowpath.instr.MethodDuplicator;
import org.ddevec.slowpath.instr.SlowPathRetarget;

public class TestSimpleVisitor extends ClassVisitor implements Opcodes {
  private static class MethodInfo {
    public int access;
    public String name;
    public String desc;
    public String signature;
    public String[] exceptions;

    public MethodInfo(int access, String name, String desc,
        String signatrue, String[] exceptions) {
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.signature = signature;
      this.exceptions = exceptions;
    }
  }

  private static class PrintVisitor extends MethodVisitor implements Opcodes {
    private int bci;
    private String toPrint;

    public PrintVisitor(int api, MethodVisitor mv,
        String toPrint) {
      super(api, mv);
      this.toPrint = toPrint;
    }

    @Override
    public void visitCode() {

      super.visitCode();

      visitFieldInsn(GETSTATIC, "java/lang/System", "err",
          "Ljava/io/PrintStream;");
      visitLdcInsn(toPrint);
      visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
          "(Ljava/lang/String;)V", false);
    }

    @Override
    public void visitByteCodeIndex(int bci) {
      this.bci = bci;
      super.visitByteCodeIndex(bci);
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack,
        Object[] stack) {
      super.visitFrame(type, nLocal, local, nStack, stack);
    }
  }

  private HashMap<MethodInfo, MethodDuplicator> toDupe;

  public TestSimpleVisitor(int api, ClassVisitor cv) {
    super(api, cv);
    toDupe = new HashMap<MethodInfo, MethodDuplicator>();
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    /*
    System.out.println("Visiting method: " + name);
    System.out.println("  Desc: " + desc);
    System.out.println("  Sig: " + signature);
    */
    MethodVisitor mv = null;
    if (cv != null) {
      mv = cv.visitMethod(access, name, desc, signature, exceptions);
    }

    // FIXME: SCREW INITIALIZERS -- for now
    if (name.equals("<init>") || name.equals("<clinit>")) {
      return mv;
    }

    //mv = new CheckMethodAdapter(mv);

    mv = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);

    mv = new PrintVisitor(ASM5, mv, "In FastPath: " + name + "!");

    return mv;
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }
}

