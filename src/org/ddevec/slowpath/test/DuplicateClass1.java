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

import java.util.HashMap;
import java.util.Map;

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
import org.ddevec.slowpath.SlowPathRetarget;
import org.ddevec.slowpath.MethodDuplicator;


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
}

