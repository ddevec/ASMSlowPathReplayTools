package org.ddevec.record.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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

import rr.meta.ClassInfo;
import rr.meta.InstrumentationFilter;
import rr.meta.MetaDataInfoMaps;

import org.ddevec.record.instr.ClassRecordLogCreator;
import org.ddevec.record.instr.NativeMethodFinder;
import org.ddevec.record.instr.NativeMethodSet;
import org.ddevec.record.instr.NativeMethodCleaner;

import org.ddevec.slowpath.instr.Analysis;
import org.ddevec.slowpath.analysis.ReachingClassAnalysis;

import acme.util.Util;
import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;

/**
 * Wraps all native methods specified by the native method file
 */
public class NativeMethodInstrumentor {
  private List<String> classes = new ArrayList<String>();

  public static final CommandLineOption<String> clOutdir =
    CommandLine.makeString("outdir", "./test_out/NativeMethodInstrumentor",
        CommandLineOption.Kind.STABLE,
        "Output directroy for instrumented class files");

  private static final CommandLineOption<String> clNativeMethodFile =
    CommandLine.makeString("native-method-file", "native_methods.csv",
        CommandLineOption.Kind.STABLE,
        "File to read which Native methods should be analyzed for record/replay");

  private static String[] parseOptions(String[] args) {
    final CommandLine cl = new CommandLine("RRStarter", "???");

		cl.add(new CommandLineOption<Boolean>("help", false, false, CommandLineOption.Kind.STABLE, "Print this message.") {
			@Override
			protected void apply(String arg) {
				Util.error("\n\nEnvironment Variables");
				Util.error("---------------------");
				Util.error("  RR_MODE        either FAST or SLOW.  All asserts, logging, and debugging statements\n" +
				"                 should be nested inside a test ensuring that RR_MODE is SLOW.");
				Util.error("  RR_META_DATA   The directory created on previous run by -dump from which to reload\n" +
				"                 cached metadata and instrumented class files.\n");
				cl.usage();
				Util.exit(0);
			}
		});

		cl.addGroup("General");

		cl.add(clOutdir);
		cl.add(clNativeMethodFile);

		cl.addGroup("Instrumentor");
		cl.add(rr.tool.RR.nofastPathOption);
		cl.add(InstrumentationFilter.classesToWatch);
		cl.add(InstrumentationFilter.fieldsToWatch);
		cl.add(InstrumentationFilter.methodsToWatch);
		cl.add(InstrumentationFilter.linesToWatch);

		int n = cl.apply(args);

    String[] ret = Arrays.copyOfRange(args, n, args.length);

    if (ret.length <= 0) {
      Util.error("No arguments passed!");
      cl.usage();
      Util.exit(1);
    }

    return ret;
  }

  private static void handleError(Exception ex) {
    System.err.println("Error: " + ex);
    ex.printStackTrace();
    System.exit(1);
  }

  public static void main(String[] args) {
    NativeMethodInstrumentor inst = new NativeMethodInstrumentor(args);

    inst.go();

    // RR is funky -- force it to stop
    Util.exit(0);
  }

  private String[] args;

  public NativeMethodInstrumentor(String[] args) {
    this.args = args;
  }

  public void go() {
    // First, parse options
    args = parseOptions(args);

    List<String> baseClasses = Arrays.asList(args);

    baseClasses = cleanNames(baseClasses);
    File outdir = new File(clOutdir.get());

    // Then, get classes (closure)
    Iterable<String> classes = classes = calcClosure(baseClasses);

    // First, lets prep those native method wrappers
    NativeMethodSet nms = new NativeMethodSet(clNativeMethodFile.get());

    ClassRecordLogCreator crlc = new ClassRecordLogCreator(nms);
    crlc.createAllRecordClasses(clOutdir.get());
    
    // Instrument twice -- once to record wrappers once to replay wrappers
    // FIRST: Recording
    // Finally, foreach class instrument
    File recordOutdir = new File(outdir, "record");
    instrumentClasses(classes, nms, recordOutdir, ClassRecordLogCreator.RecordWrapperFcn, "initializeRecord");

    File replayOutdir = new File(outdir, "replay");
    instrumentClasses(classes, nms, replayOutdir, ClassRecordLogCreator.ReplayWrapperFcn, "initializeReplay");
  }

  private void instrumentClasses(Iterable<String> classes, NativeMethodSet nms,
      File outdir, String wrapperFcn, String initFcn) {
    for (String classname : classes) {
      if (shouldInstrument(classname)) {
        System.err.println("Instrumenting: " + classname);
        URL resource = getClassFile(classname);

        if (resource == null) {
          System.err.println("WARNING: Couldn't find Resource: " + classname);
          continue;
        }

        ClassWriter cw = null;
        try (InputStream is = resource.openStream()) {

          ClassReader cr = new ClassReader(is);

          cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
          ClassVisitor cv = new CheckClassAdapter(cw);
          // Do actual instrumentation
          cv = new NativeMethodCleaner(cv, nms.getEntries(), wrapperFcn, initFcn);

          cr.accept(cv, ClassReader.EXPAND_FRAMES);
        } catch (IOException ex) {
          handleError(ex);
        }

        String oFileName = getOutputName(outdir.getPath(), classname);
        File oFile = new File(oFileName);
        File parent = oFile.getParentFile();

        if (!parent.exists()) {
          parent.mkdirs();
        }

        try (OutputStream os = new FileOutputStream(oFile)) {
          os.write(cw.toByteArray());
        } catch (IOException ex) {
          handleError(ex);
        }
      }
    }
  }

  private static boolean shouldInstrument(String classname) {
    if (classname.startsWith("org.ddevec.slowpath.runtime")) {
      return false;
    }
    if (classname.startsWith("org.ddevec.record.runtime")) {
      return false;
    }

    ClassInfo thisClass = MetaDataInfoMaps.getClass(classname);
    // Go conservative -- slowpath true
    return InstrumentationFilter.shouldInstrument(thisClass, true);
  }

  private static List<String> cleanNames(List<String> names) {
    List<String> ret = new ArrayList<String>();

    for (String name : names) {
      ret.add(name.replace('.', '/'));
    }

    return ret;
  }

  /**
   * Get the closure of classes we need to visit.  This includes classes,
   * subclasses, etc.
   *
   * Also invokes RR intiailization classes for those classes -- ensuring their
   * metadata is set up for the actual instrumentor
   */
  private static Iterable<String> calcClosure(List<String> classes) {
    Set<String> ret = new HashSet<String>();

    List<String> newClasses = new ArrayList<String>();
    for (String cName : classes) {
      newClasses.add(cName);
      ret.add(cName);
    }

    boolean updating = true;
    while (updating) {
      updating = false;
      classes = newClasses;
      newClasses = new ArrayList<String>();
      for (String cName : classes) {
        Set<String> nC = new HashSet<String>();
        Analysis as = new Analysis() {
          @Override
          public ClassVisitor getClassVisitor() {
            return new ReachingClassAnalysis(Opcodes.ASM5, null, nC);
          }
        };

        try {
          as.analyze(cName);
        } catch (IOException ex) {
          System.err.println("WARNING: IOError handling: " + cName);
          System.err.println(ex);
        }

        for (String cn : nC) {
          if (!ret.contains(cn) &&
              !cn.startsWith("[")) {
            ret.add(cn);
            newClasses.add(cn);
            updating = true;
          }
        }
      }
    }

    System.err.println("Identified " + ret.size() +
        " potentially reachable classes");

    return ret;
  }

  public URL getClassFile(String classname) {
    ClassLoader cl = getClass().getClassLoader();

    String resourceName = classNameToClassFile(classname);

    URL resource = cl.getResource(resourceName);
    return resource;
  }

  public String classNameToClassFile(String classname) {
    return classname.replace('.', '/') + ".class";
  }

  public String getOutputName(String basedir, String classname) {
    return basedir + '/' + classNameToClassFile(classname);
  }
}
