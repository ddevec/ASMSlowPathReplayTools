package org.ddevec.slowpath.instr;

import org.ddevec.asm.tools.AsmInstrumentation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.lang.instrument.IllegalClassFormatException;

import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.commons.JSRInlinerAdapter;
import rr.org.objectweb.asm.util.CheckClassAdapter;

import rr.meta.MetaDataInfoMaps;

import org.ddevec.slowpath.instr.ClassVisitorSplitter;
import org.ddevec.slowpath.instr.SlowPathClassVisitor;

import acme.util.Util;
import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;

public class SlowPathInstrumentation extends AsmInstrumentation {

  // Load up our NativeMethodSet...
  public SlowPathInstrumentation(CommandLine cl) {
    super(cl);
  }

  @Override
  protected void preload(Set<String> classes) {
    classes.add("org/ddevec/slowpath/runtime/MisSpecException");
    super.preload(classes);
  }


  @Override
  protected void runPostPreload(Set<String> classes) {
  }

  @Override
  protected ClassVisitor getClassVisitor(ClassReader cr, ClassVisitor cv) {
    System.err.println("Get cv for: " + cr.getClassName());

    // If the class is abstract...
    if ((cr.getAccess() & Opcodes.ACC_INTERFACE) == 0) {
      ClassVisitor cv1 = cv;

      // Manages merging together stuffs from our two visit paths
      cv1 = new ClassVisitorSplitter(cv1, 2);

      ClassVisitor cv2 = cv1;

      cv = new SlowPathClassVisitor(cv1, cv2);
    }

    // Cleanup JSR nonsense
    cv = new ClassVisitor(Opcodes.ASM5, cv) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        if ((access & Opcodes.ACC_INTERFACE) == 0 && (access & Opcodes.ACC_ABSTRACT) == 0) {
          mv = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
        }

        return mv;
      }
    };

    cv = new rr.instrument.classes.JVMVersionNumberFixer(cv);

    return cv;
  }

  @Override
  protected void postInstrumentation() {
    MetaDataInfoMaps.dump(outdir.getAbsolutePath() + "/rr.meta");
  }
}

