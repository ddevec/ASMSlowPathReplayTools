package org.ddevec.slowpath;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/**
 * Pass that retargets all function calls to their slow-path prefixed variants.
 *
 * Slow-path creation is the responsibility of... slow path creator pass?
 */

public class SlowPathRetarget extends MethodVisitor implements Opcodes {
  public static String SlowPathPrefix = "__ddevecSlowPath__";

  public SlowPathRetarget(int api, MethodVisitor mv) {
    super(api, mv);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner,
      String name, String desc, boolean itf) {
    // Adjust target to -- slowpath
    // EXCEPT: java.lang
    // Ignore <init> -- UGH
    System.out.println("Owner: " + owner);
    if (name.equals("<init>") ||
        name.equals("<clinit>") ||
        owner.startsWith("java/lang") ||
        owner.startsWith("java/io")) {
      super.visitMethodInsn(opcode, owner, name, desc, itf);
    } else {
      name = SlowPathPrefix + name;
      super.visitMethodInsn(opcode, owner, name, desc, itf);
    }
  }
  
}

