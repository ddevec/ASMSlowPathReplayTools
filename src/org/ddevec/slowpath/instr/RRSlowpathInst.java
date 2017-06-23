/**
 *  Responsible for doing RoadRunner level instrumentation.
 *
 *  Mostly copies from RR source -- hopefully it works?
 */

package org.ddevec.slowpath.instr;

import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.util.CheckClassAdapter;

import rr.org.objectweb.asm.util.Textifier;
import rr.org.objectweb.asm.util.TraceClassVisitor;

import rr.instrument.classes.AbstractOrphanFixer;
import rr.instrument.classes.ArrayAllocSiteTracker;
import rr.instrument.classes.ClassInitNotifier;
import rr.instrument.classes.CloneFixer;
import rr.instrument.classes.GuardStateInserter;
import rr.instrument.classes.InterfaceThunkInserter;
import rr.instrument.classes.InterruptFixer;
import rr.instrument.classes.JVMVersionNumberFixer;
import rr.instrument.classes.NativeMethodSanityChecker;
import rr.instrument.classes.SyncAndMethodThunkInserter;
import rr.instrument.classes.ThreadDataThunkInserter;
import rr.instrument.classes.ToolSpecificClassVisitorFactory;
import rr.instrument.noinst.NoInstSanityChecker;

import rr.meta.ClassInfo;
import rr.meta.MetaDataInfoMaps;

import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;

import java.io.StringWriter;
import java.io.PrintWriter;

// Okay, lets just give something similar a main and be done with it.
public class RRSlowpathInst {

  public static CommandLineOption<Boolean> clNoSlowPath =
    CommandLine.makeBoolean("noslowpath", false,
      CommandLineOption.Kind.STABLE,
      "Only does standard RR instrumentation -- now slowpath fallthrough");

  public static ClassWriter doVisit(ClassReader cr) {
    /*
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES |
        ClassWriter.COMPUTE_MAXS);
    */
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
    System.err.println("DO VISIT: " + cr.getClassName());

    ClassVisitor cv = cw;
    cv = new DebugVisitor(cv, "DebugInfinity");

    // If the class is abstract...
    if ((cr.getAccess() & Opcodes.ACC_INTERFACE) == 0) {
      String classname = cr.getClassName();
      ClassInfo currentClass = MetaDataInfoMaps.getClass(classname);

      // ClassVisitor cv0 = new CheckClassAdapter(cw);
      ClassVisitor cv0 = cw;
      // ClassVisitor cv0 = cv;
      //ClassVisitor cv1 = cv0;
      ClassVisitor cv1 = new NativeMethodSanityChecker(cv0);
      cv1 = new GuardStateInserter(cv1);
      cv1 = new InterruptFixer(cv1);
      cv1 = new CloneFixer(cv1);
      cv1 = new ClassInitNotifier(currentClass, cv1);
      cv1 = new ArrayAllocSiteTracker(currentClass, cv1);
      cv1 = new AbstractOrphanFixer(cv1);
      //cv1 = new CheckClassAdapter(cv1);
      if (!clNoSlowPath.get()) {
        // Manages merging together stuffs from our two visit paths
        cv1 = new DebugVisitor(cv1, "Debug4");

        cv1 = new ClassVisitorSplitter(cv1, 2);

        cv1 = new DebugVisitor(cv1, "Debug3");

        ClassVisitor cv2 = cv1;
        cv2 = new DebugVisitor(cv2, "Debug2");

        ClassVisitor cv2nt = new ThreadDataThunkInserter(cv1, true, false);
        ClassVisitor cv2forThunks = new ThreadDataThunkInserter(cv1, false,
            false);
        cv2 = new SyncAndMethodThunkInserter(cv2nt, cv2forThunks);

        ClassVisitor cv1Sp = cv1;
        cv1Sp = new DebugVisitor(cv1Sp, "Debug2sp");
        ClassVisitor cvSp = new ThreadDataThunkInserter(cv1Sp, true, true);
        ClassVisitor cv2forThunksSp = new ThreadDataThunkInserter(cv1, false,
            true);
        cvSp = new SyncAndMethodThunkInserter(cvSp, cv2forThunksSp);
        cv2 = new DebugVisitor(cv2, "Debug1");
        cvSp = new DebugVisitor(cvSp, "Debug1sp");
        cv = new SlowPathClassVisitor(cv2, cvSp);
        cv = new DebugVisitor(cv, "Debug0");
      } else {
        ClassVisitor cv2 = new ThreadDataThunkInserter(cv1, true, true);
        ClassVisitor cv2forThunks = new ThreadDataThunkInserter(cv1, false,
            true);
        cv = new SyncAndMethodThunkInserter(cv2, cv2forThunks);
      }
    }

    // FIXME: Tool specific visitors :(
    //cv = insertToolSpecificVisitors(cv);

    cv = new JVMVersionNumberFixer(cv);

    cr.accept(cv, ClassReader.EXPAND_FRAMES);

    ClassReader dumpReader = new ClassReader(cw.toByteArray());
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    TraceClassVisitor tcw = new TraceClassVisitor(pw);
    dumpReader.accept(tcw, 0);

    System.err.println(sw.toString());


    /*
    sw = new StringWriter();
    pw = new PrintWriter(sw);
    CheckClassAdapter.verify(new ClassReader(cw.toByteArray()), false, pw);

    assert sw.toString().length() == 0 : sw.toString();

    */
    return cw;
  }
}

