package org.ddevec.record.instr;

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
import java.lang.instrument.IllegalClassFormatException;

import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.util.CheckClassAdapter;

import rr.loader.LoaderContext;
import rr.loader.MetaDataBuilder;
import rr.meta.ClassInfo;
import rr.meta.InstrumentationFilter;
import rr.meta.MetaDataInfoMaps;

import org.ddevec.record.instr.ClassRecordLogCreator;
import org.ddevec.record.instr.NativeMethodFinder;
import org.ddevec.record.instr.NativeMethodSet;
import org.ddevec.record.instr.NativeMethodCleaner;
import org.ddevec.record.instr.ThreadInstrumentor;
import org.ddevec.record.instr.RecordEntry;

import org.ddevec.slowpath.instr.Analysis;
import org.ddevec.slowpath.analysis.ReachingClassAnalysis;

import acme.util.Util;
import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;


public class RecordInstrumentation extends AsmInstrumentation {
  private static final CommandLineOption<String> clNativeMethodFile =
    CommandLine.makeString("native-method-file", "native_methods.csv",
        CommandLineOption.Kind.STABLE,
        "File to read which Native methods should be analyzed for record/replay");

  private static CommandLineOption<Boolean> clFindNative = CommandLine.makeBoolean("findnative", false,
      CommandLineOption.Kind.STABLE, "Prints the names of all observed native methods ");

  NativeMethodSet nms;
  // Load up our NativeMethodSet...
  public RecordInstrumentation(CommandLine cl) {
    super(cl);
  }

  @Override
  protected void setupCl(CommandLine cl) {
    cl.addGroup("RecordInstrumentation");
    cl.add(clNativeMethodFile);
    cl.add(clFindNative);
    cl.add(rr.instrument.classes.ClassInitNotifier.saveClassInitMapping);
  }

  @Override
  public void instrument() {
    // First, calc classes
    Set<String> classes = calcClosure(getNeededClasses(reqClasses));

    preload(classes);

    runPostPreload(classes);

    // Instrument record, tehn replay
    File recordOutdir = new File(outdir, "record");
    visitClasses(classes, recordOutdir,
        new ClassVisitWrapper() {
          @Override
          public void visit(ClassReader cr, ClassVisitor cv) {
            ClassVisitor cv1 = new NativeMethodCleaner(cv, nms.getEntries(),
              ClassRecordLogCreator.RecordWrapperFcn, "initializeRecord");
            Util.message("Record Instrumenting class: " + cr.getClassName());
            // False -- is not replay
            cv1 = new ThreadInstrumentor(cv1, false);

            cr.accept(cv1, ClassReader.EXPAND_FRAMES);
          }
        });

    File replayOutdir = new File(outdir, "replay");
    visitClasses(classes, replayOutdir,
        new ClassVisitWrapper() {
          @Override
          public void visit(ClassReader cr, ClassVisitor cv) {
            ClassVisitor cv1 = new NativeMethodCleaner(cv, nms.getEntries(),
              ClassRecordLogCreator.ReplayWrapperFcn, "initializeReplay");
            Util.message("Replay instrumenting class: " + cr.getClassName());
            // true -- is replay
            cv1 = new ThreadInstrumentor(cv1, true);

            cr.accept(cv1, ClassReader.EXPAND_FRAMES);
          }
        });


    MetaDataInfoMaps.dump(outdir.getAbsolutePath() + "/rr.meta");
  }

  @Override
  protected void runPostPreload(Set<String> classes) {
    if (clFindNative.get()) {
      ArrayList<RecordEntry> entries = new ArrayList<RecordEntry>();
      visitClasses(classes, 
          new ClassVisitWrapper() {
            @Override
            public void visit(ClassReader cr, ClassVisitor cv) {
              cv = new NativeMethodFinder(cv, entries);
              cr.accept(cv, ClassReader.EXPAND_FRAMES);
            }
          });

      try (PrintWriter pr = new PrintWriter(clNativeMethodFile.get())) {
        for (RecordEntry re : entries) {
          pr.println(re.getTuple());
        }
      } catch (IOException ex) {
        ex.printStackTrace();
        System.exit(1);
      }
    }

    nms = new NativeMethodSet(clNativeMethodFile.get());

    // Now, pre-setup our record classes
    ClassRecordLogCreator crlc = new ClassRecordLogCreator(nms);
    crlc.createAllRecordClasses(outdir.getAbsolutePath());
  }

  @Override
  protected boolean shouldInstrument(String classname) {
    if (classname.startsWith("org.ddevec.slowpath.runtime")) {
      return false;
    }

    if (classname.startsWith("org.ddevec.record.runtime")) {
      return false;
    }

    return true;
  }


  @Override
  protected ClassVisitor getClassVisitor(ClassReader cr, ClassVisitor cv) {
    // We do this twice?  Once for record, once for replay
    assert false : "Should never call this -- urgh";
    return cv;
  }
}
