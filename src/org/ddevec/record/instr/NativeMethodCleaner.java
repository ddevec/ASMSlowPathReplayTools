package org.ddevec.record.instr;

import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.Label;
import rr.org.objectweb.asm.util.CheckClassAdapter;

import java.util.Set;

import acme.util.Util;

/**
 * Converts all calls to native methods in our native method set to calls to the
 * appropriate wrapper.
 */

public class NativeMethodCleaner extends ClassVisitor {

  private String classname;
  private Set<RecordEntry> entries;
  private String recordReplayShimFcn;
  private String recordReplayInitFcn;

  private class NativeMethodCleanerMV extends MethodVisitor implements Opcodes {
    public NativeMethodCleanerMV(MethodVisitor mv) {
      super(ASM5, mv);
    }

    // Check calls to methods -- if native, call our wrapper instead
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      // If this is a call to one of our native methods, kill it
      boolean isStatic = opcode == INVOKESTATIC;
      RecordEntry target = new RecordEntry(owner, name, desc, isStatic);
      if (classname.startsWith("TestThree")) {
        System.err.println("VisitMethod: " + owner + "." + name);
        System.err.println("  " + target);
        System.err.println("  " + target.getTuple());
      }
      if (entries.contains(target)) {
        String recordWrapperDesc = ClassRecordLogCreator.getRecordWrapperDesc(target);
        // Call our record wrapper function
        super.visitMethodInsn(INVOKESTATIC,
            target.getLogClassname(),
            recordReplayShimFcn,
            recordWrapperDesc,
            false);
      } else {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
      }
    }
  }

  private class MainShimmer extends MethodVisitor implements Opcodes {
    public MainShimmer(MethodVisitor mv) {
      super(ASM5, mv);
    }

    // Check calls to methods -- if native, call our wrapper instead
    @Override
    public void visitCode() {
      super.visitCode();

      // Load readstring constant
      super.visitMethodInsn(INVOKESTATIC,
          ClassRecordLogCreator.RecordLogClass,
          recordReplayInitFcn,
          Type.getMethodDescriptor(Type.VOID_TYPE), false);
    }
  }

  public NativeMethodCleaner(ClassVisitor cv, Set<RecordEntry> entries,
      String recordReplayShimFcn, String recordReplayInitFcn) {
    super(Opcodes.ASM5, cv);
    this.entries = entries;
    this.recordReplayShimFcn = recordReplayShimFcn;
    this.recordReplayInitFcn = recordReplayInitFcn;
  }

  @Override
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    this.classname = name;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  // We need to tweak all of our methods...
  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    RecordEntry methodRecord = new RecordEntry(classname, name, desc, 
        (access & Opcodes.ACC_STATIC) != 0);
    MethodVisitor mv = null;

    // Assume native?
    if (entries.contains(methodRecord)) {
      // Fix up access
      mv = super.visitMethod((access & ~(Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) | Opcodes.ACC_PUBLIC,
          name, desc, signature, exceptions);
    } else {
      mv = super.visitMethod(access, name, desc, signature, exceptions);
      if (name.equals("main") && (access & Opcodes.ACC_STATIC) != 0) {
        mv = new MainShimmer(mv);
      }
      mv = new NativeMethodCleanerMV(mv);
    }

    return mv;
  }
}

