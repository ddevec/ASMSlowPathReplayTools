package org.ddevec.slowpath.instr;

import java.util.HashMap;
import java.util.Map;

import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.util.CheckMethodAdapter;
import rr.org.objectweb.asm.commons.JSRInlinerAdapter;

import rr.instrument.Instrumentor;
import rr.instrument.MethodContext;
import rr.meta.MethodInfo;
import rr.meta.ClassInfo;
import rr.meta.MetaDataInfoMaps;

import rr.instrument.classes.RRClassAdapter;

import tools.fasttrack.FastTrackTool;

public class SlowPathClassVisitor extends RRClassAdapter implements Opcodes {
  int version;

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

  private class MethodDuplicatorInfo {
    public MethodDuplicator dupe;
    public int access;
    public String signature;
    public String[] exceptions;

    public MethodDuplicatorInfo(MethodDuplicator dupe, int access,
        String signature, String[] exceptions) {
      this.dupe = dupe;
      this.access = access;
      this.signature = signature;
      this.exceptions = exceptions;
    }
  }

  private HashMap<MethodInfo, MethodDuplicatorInfo> toDupe;

  private String curClassname;

  private ClassVisitor slowpathCv;

  public SlowPathClassVisitor(ClassVisitor fastpathCv, ClassVisitor slowpathCv) {
    super(fastpathCv);
    this.slowpathCv = slowpathCv;
    toDupe = new HashMap<MethodInfo, MethodDuplicatorInfo>();
  }

  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    curClassname = name;
    this.version = version;
    super.visit(version, access, name, signature, superName, interfaces);
    slowpathCv.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    System.out.println("  Visiting method: " + name);
    System.out.println("  Desc: " + desc);
    System.out.println("  Sig: " + signature);
    System.out.println("  access: " + access);

    if ((access & Opcodes.ACC_ABSTRACT) != 0) {
      return super.visitMethod(access, name, desc, signature, exceptions);
    }
    System.err.println("    NOT ABSTRACT -- go for it");

    // mv = new CheckMethodAdapter(mv);
    // FIXME: INITIALIZERS ARE EVIL -- for now
    if (name.equals("<init>") || name.equals("<clinit>")) {
      //mv = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
      //
      // We do slowpath here to be conservative...
      MethodVisitor fastpathMv = this.cv.visitMethod(access, name, desc, signature, exceptions);
      fastpathMv.visitCode();
      fastpathMv.visitMaxs(3, 3);
      fastpathMv.visitEnd();

      return slowpathCv.visitMethod(access, name, desc, signature, exceptions);
    }

    MethodVisitor mv;
    // If this is a slowpath method, only do slowpath code
    if (name.startsWith(SlowPathRetarget.SlowPathPrefix)) {
      System.err.println("Creating SP-only fcn");

      // Make dummy fp visitor, to satisfy assumption of 2x visits
      MethodVisitor fastpathMv = this.cv.visitMethod(access, name, desc, signature, exceptions);
      fastpathMv.visitCode();
      fastpathMv.visitMaxs(3, 3);
      fastpathMv.visitEnd();

      mv = slowpathCv.visitMethod(access, name, desc, signature, exceptions);
      mv = new PrintVisitor(ASM5, mv,
          "In SlowPath: " + name + "!");

      mv = new SlowPathRetarget(ASM5, mv);

      // If it is a fastpath then add fastpath + xfer code
    } else {
      System.err.println("Creating FP-SP fcn");
      MethodVisitor mv2 = slowpathCv.visitMethod(access, name, desc, signature,
          exceptions);

      mv2 = new SlowPathRetarget(ASM5, mv2);

      mv = this.cv.visitMethod(access, name, desc, signature, exceptions);
      mv = new CloneMethodVisitor(ASM5, name, mv, mv2);

      MethodDuplicatorInfo dmv = new MethodDuplicatorInfo(
          new MethodDuplicator(ASM5, mv),
          access, signature, exceptions);
      ClassInfo owner = context.getRRClass();
      MethodInfo myInfo = MetaDataInfoMaps.getMethod(owner, name, desc);
      toDupe.put(myInfo, dmv);

      mv = dmv.dupe;
    }

    mv = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);

    return mv;
  }

  @Override
  public void visitEnd() {
    for (Map.Entry<MethodInfo, MethodDuplicatorInfo> entry : toDupe.entrySet()) {
      MethodInfo info = entry.getKey();
      MethodDuplicatorInfo dupeInfo = entry.getValue();
      MethodDuplicator cloner = dupeInfo.dupe;
      int access = dupeInfo.access;
      String signature = dupeInfo.signature;
      String[] exceptions = dupeInfo.exceptions;

      String name = SlowPathRetarget.SlowPathPrefix + info.getName();

      // Need to initialize the new method... somehow
      MethodInfo newMethod = MetaDataInfoMaps.getMethod(info.getOwner(),
          name, info.getDescriptor());
      newMethod.setFlags(info);

      MethodContext oldContext = Instrumentor.methodContext.get(info);
      MethodContext context = Instrumentor.methodContext.get(newMethod);
      context.setFirstFreeVar(oldContext.getNextFreeVar(0));

      System.err.println("Visiting SP-only function");
      MethodVisitor mv = visitMethod(access, name,
          info.getDescriptor(),
          signature, exceptions);
      cloner.doVisits(mv);
      System.err.println("Ending on SP function");
      mv.visitEnd();
    }

    super.visitEnd();
    slowpathCv.visitEnd();
  }
}

