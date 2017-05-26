package org.ddevec.slowpath.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;

import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;

import org.ddevec.slowpath.instr.Instrumentor;


public class CopyClass1 {
  private static void handleError(Exception ex) {
    System.err.println("Error: " + ex);
    ex.printStackTrace();
    System.exit(1);
  }

  private static String outdir = "test_out/CopyClass1";

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
      MethodVisitor mv = null;
      if (cv != null) {
        mv = cv.visitMethod(access, name, desc, signature, exceptions);
      } else {
        System.err.println("No MV?");
        return null;
      }

      mv = new MethodVisitor(ASM5, mv) {
        @Override
        public void visitCode() {
          visitFieldInsn(GETSTATIC, "java/lang/System", "err",
              "Ljava/io/PrintStream;");
          visitLdcInsn("Hello World!");
          visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
              "(Ljava/lang/String;)V", false);

          super.visitCode();
        }
      };
      /*
      mv = new BasicBlockVisitor(ASM5, mv) {
          @Override
          public void visitBasicBlock(int bci) {
              System.out.println("New BB at: " + bci);
              super.visitBasicBlock(bci);
          }
      };
      */

      return mv;
    }
  }

  public static void helloWorld() {
    System.out.println("Hello World?");
  }
}

