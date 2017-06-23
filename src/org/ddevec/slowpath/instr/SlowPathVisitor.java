package org.ddevec.slowpath.instr;

import java.util.HashMap;
import java.util.Map;

import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.util.CheckMethodAdapter;
import rr.org.objectweb.asm.commons.JSRInlinerAdapter;

public class SlowPathVisitor extends ClassVisitor implements Opcodes {
  int version;

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

  private String curClassname;

  public SlowPathVisitor(int api, ClassVisitor cv) {
    super(api, cv);
    toDupe = new HashMap<MethodInfo, MethodDuplicator>();
  }
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    curClassname = name;
    this.version = version;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    System.out.println("  Visiting method: " + name);
    //System.out.println("  Desc: " + desc);
    //System.out.println("  Sig: " + signature);
    MethodVisitor mv = null;
    if (cv != null) {
      mv = cv.visitMethod(access, name, desc, signature, exceptions);
    } else {
      System.err.println("No MV?");
      return null;
    }

    //mv = new CheckMethodAdapter(mv, version);
    //mv = new CheckMethodAdapter(mv);
    // FIXME: INITIALIZERS ARE EVIL -- for now
    if (name.equals("<init>") || name.equals("<clinit>")) {
      mv = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
      return mv;
    }

    // If this is a slowpath method, only do slowpath code
    if (name.startsWith(SlowPathRetarget.SlowPathPrefix)) {
      mv = new PrintVisitor(ASM5, mv,
          "In SlowPath: " + name + "!");

      mv = new SlowPathRetarget(ASM5, mv);

    // If it is a fastpath then add fastpath + xfer code
    } else {
      MethodVisitor mv2 = new SlowPathRetarget(ASM5, mv);

      mv = new CloneMethodVisitor(ASM5, name, mv, mv2);

      /*
      mv = new CallArgMover(ASM5, curClassname,
          access, name, desc, signature, exceptions, mv);
      */

      MethodDuplicator dmv = new MethodDuplicator(ASM5, mv);
      toDupe.put(new MethodInfo(access, name, desc, signature, exceptions),
          dmv);

      mv = dmv;
    }

    mv = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);

    return mv;
  }

  @Override
  public void visitEnd() {
    for (Map.Entry<MethodInfo, MethodDuplicator> entry : toDupe.entrySet()) {
      MethodInfo info = entry.getKey();
      MethodDuplicator cloner = entry.getValue();

      String name = SlowPathRetarget.SlowPathPrefix + info.name;

      MethodVisitor mv = visitMethod(info.access, name, info.desc, info.signature,
          info.exceptions);
      cloner.doVisits(mv);
      mv.visitEnd();
    }
  }
}

