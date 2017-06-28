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
import java.io.IOException;

import java.util.ArrayList;

/**
 * Finds native methods that need to be recorded
 */
public class NativeMethodFinder extends ClassVisitor implements Opcodes {
  private String classname;

  ArrayList<RecordEntry> entries;

  public NativeMethodFinder(ClassVisitor parent, ArrayList<RecordEntry> entries) {
    super(ASM5, parent);
    this.entries = entries;
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

    // System.err.println("VISITING METHOD: " + name);
    if ((access & ACC_NATIVE) != 0) {
      // System.err.println("    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!NATIVE!!!!!!!!!!!!!!!!!!!");
      entries.add(new RecordEntry(classname, name, desc, 
            (access & ACC_STATIC) != 0));
    }

    return ret;
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }

  public static ClassWriter doVisit(ClassReader cr) {
    ClassWriter cw = new ClassWriter(cr,
        ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

    ClassVisitor cv = cw;
    // cv = new NativeMethodFinder(cv);

    cv = new JVMVersionNumberFixer(cv);

    cr.accept(cv, ClassReader.EXPAND_FRAMES);

    return cw;
  }
}

