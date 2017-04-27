package org.ddevec.slowpath.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.objectweb.asm.util.CheckMethodAdapter;

import org.ddevec.slowpath.instr.Instrumentor;
import org.ddevec.slowpath.CloneMethodVisitor;


public class DuplicateClass1 {
  private static void handleError(Exception ex) {
    System.err.println("Error: " + ex);
    ex.printStackTrace();
    System.exit(1);
  }

  private static String outdir = "test_out/DuplicateClass1";

  public static void main(String[] args) {
    String classname = "org.ddevec.slowpath.test.Class1";

    Instrumentor inst = new Instrumentor(outdir) {
        @Override
        public ClassVisitor getClassVisitor(ClassVisitor cv) {
            return new TestClassVisitor(Opcodes.ASM5, cv);
        }
    };

    try {
      inst.instrument(classname);
    } catch (IOException ex) {
      handleError(ex);
    }
  }

  static class TestClassVisitor extends ClassVisitor implements Opcodes {
    public TestClassVisitor(int api, ClassVisitor cv) {
      super(api, cv);
    }

    public MethodVisitor visitMethod(int access, String name, String desc,
        String signature, String[] exceptions) {
      System.out.println("Visiting method: " + name);
      System.out.println("  Desc: " + desc);
      System.out.println("  Sig: " + signature);
      MethodVisitor mv = null;
      if (cv != null) {
        mv = cv.visitMethod(access, name, desc, signature, exceptions);
      } else {
        System.err.println("No MV?");
        return null;
      }

      MethodVisitor mv1 = new MethodVisitor(ASM5, mv) {
        int bci;

        @Override
        public void visitCode() {
          visitFieldInsn(GETSTATIC, "java/lang/System", "err",
              "Ljava/io/PrintStream;");
          visitLdcInsn("In FastPath: " + name + "!");
          visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
              "(Ljava/lang/String;)V", false);

          super.visitCode();
        }

        @Override
        public void visitByteCodeIndex(int bci) {
          this.bci = bci;
          super.visitByteCodeIndex(bci);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack,
            Object[] stack) {
          System.out.println("FP Frame: " + bci);
          super.visitFrame(type, nLocal, local, nStack, stack);
        }

      };

      MethodVisitor mv2 = new MethodVisitor(ASM5, mv) {
        int bci;

        @Override
        public void visitCode() {
          visitFieldInsn(GETSTATIC, "java/lang/System", "err",
              "Ljava/io/PrintStream;");
          visitLdcInsn("In SlowPath: " + name + "!");
          visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
              "(Ljava/lang/String;)V", false);

          super.visitCode();
        }

        @Override
        public void visitByteCodeIndex(int bci) {
          this.bci = bci;
          super.visitByteCodeIndex(bci);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack,
            Object[] stack) {
          System.out.println("SP Frame: " + bci);
          super.visitFrame(type, nLocal, local, nStack, stack);
        }
      };

      mv = new CheckMethodAdapter(mv);

      mv = new CloneMethodVisitor(ASM5, name, mv1, mv2);

      return mv;
    }
  }
}

