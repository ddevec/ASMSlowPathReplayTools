package org.ddevec.record.instr;

import rr.org.objectweb.asm.AnnotationVisitor;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Handle;
import rr.org.objectweb.asm.Label;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.TypePath;

import rr.instrument.classes.JVMVersionNumberFixer;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * Finds native methods that need to be recorded
 */
public class NativeMethodFinder extends ClassVisitor implements Opcodes {
  private String classname;

  public NativeMethodFinder(ClassVisitor parent) {
    super(ASM5, parent);
  }

  @Override
  public void visit(int version, int access, String name,
      String signature, String superName,
      String[] interfaces) {
    classname = name;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature,
      String[] exceptions) {
    MethodVisitor ret = cv.visitMethod(access, name, desc, signature,
        exceptions);

    if ((access & ACC_NATIVE) != 0) {
      /*
      System.err.println("  Have native method: " + name);
      System.err.println("    Desc: " + desc);
      if (signature != null) {
        System.err.println("    Signature: " + signature);
      }
      if (exceptions != null) {
        for (int i = 0; i < exceptions.length; i++) {
          System.err.println("      ex: " + exceptions[i]);
        }
      }
      */
      System.err.println(classname + "," + name + "," + desc);
    }

    return ret;
  }

  public static ClassWriter doVisit(ClassReader cr) {
    ClassWriter cw = new ClassWriter(cr,
        ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

    ClassVisitor cv = cw;
    cv = new NativeMethodFinder(cv);

    cv = new JVMVersionNumberFixer(cv);

    cr.accept(cv, ClassReader.EXPAND_FRAMES);

    return cw;
  }
}

