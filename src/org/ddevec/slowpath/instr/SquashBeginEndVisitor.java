/**
 *  Stops visitEnd and VisitCode() from being called for any methodVisitors
 *  chained after this.  Intended for use with ClassVisitorSplitter, to stop one
 *  of the visitors from duplicating begins and ends unnecessarily
 */

package org.ddevec.slowpath.instr;

import rr.org.objectweb.asm.AnnotationVisitor;
import rr.org.objectweb.asm.Attribute;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.FieldVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.TypePath;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

// Okay, lets just give something similar a main and be done with it.
public class SquashBeginEndVisitor extends ClassVisitor {
  private class SquashBeginEndMv extends MethodVisitor {
    public SquashBeginEndMv(int api, MethodVisitor mv) {
      super(api, mv);
    }

    @Override
    public void visitCode() {
      System.err.println("Squash visitCode()!");
    }

    @Override
    public void visitEnd() {
      System.err.println("Squash visitEnd()!");
    }
  }

  public SquashBeginEndVisitor(ClassVisitor cv) {
    super(Opcodes.ASM5, cv);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);

    mv = new SquashBeginEndMv(Opcodes.ASM5, mv);

    return mv;
  }
}
