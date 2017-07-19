package org.ddevec.asm.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.ClassWriter;

import rr.loader.LoaderContext;
import rr.loader.MetaDataBuilder;
import rr.meta.ClassInfo;
import rr.meta.InstrumentationFilter;
import rr.meta.MetaDataInfoMaps;

import acme.util.Util;
import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;

/**
 * Wraps all native methods specified by the native method file
 */
public class AsmInstrumentor {
  private static CommandLine cl = new CommandLine("RRStarter", "???");

  public static final CommandLineOption<String> clOutdir =
    CommandLine.makeString("outdir", "./test_out/AsmInstrumentor",
        CommandLineOption.Kind.STABLE,
        "Output directroy for instrumented class files");

  private static final CommandLineOption<List<AsmInstrumentation>> clInstrumentationPasses =
    new CommandLineOption<List<AsmInstrumentation>>("instr", new ArrayList<AsmInstrumentation>(),
        true, CommandLineOption.Kind.STABLE,
        "Adds an IAsmInstrumentation class pass to the instrumentor") {
      @Override
      public void apply(String arg) {
        // Try to get an instance of arg from the instrumentor
        Util.message("instr apply: " + arg);
        try {
          Class<?> analysisClass = Class.forName(arg);

          // Ensure we have an AsmInstrumentation class
          if (!AsmInstrumentation.class.isAssignableFrom(analysisClass)) {
            Util.error(new Exception(arg + " is not an AsmInstrumentation"));
          }

          Constructor<?> cons = analysisClass.getConstructor(
              new Class[] { CommandLine.class });

          // If (success), add our commandline infos
          AsmInstrumentation newInst = (AsmInstrumentation)cons.newInstance(cl);
          val.add(newInst);
        } catch (ClassNotFoundException ex) {
          Util.error(ex);
        } catch (NoSuchMethodException ex) {
          Util.error(ex);
        } catch (InstantiationException ex) {
          Util.error(ex);
        } catch (IllegalAccessException ex) {
          Util.error(ex);
        } catch (InvocationTargetException ex) {
          Util.error(ex);
        }
      }
    };

  private abstract class ClassInstrumentor {
    public abstract ClassVisitor getCv(ClassVisitor cv);
  }

  private abstract class ClassVisitWrapper {
    public abstract void visit(ClassReader rd);
  }

  private static String[] parseOptions(String[] args) {

    ArrayList<String> newArgsList = new ArrayList<String>();
    // Pre-process for instrumentors -- add their options
    String instrPrefix = "-" + clInstrumentationPasses.getId() + "=";
    for (String arg : args) {
      if (arg.startsWith(instrPrefix)) {
        clInstrumentationPasses.checkAndApply(arg.substring(instrPrefix.length()));
      } else {
        newArgsList.add(arg);
      }
    }
    args = newArgsList.toArray(new String[newArgsList.size()]);

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

    cl.add(clInstrumentationPasses);
		cl.add(clOutdir);

		cl.addGroup("Instrumentor");
		// cl.add(rr.tool.RR.nofastPathOption);
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
    AsmInstrumentor inst = new AsmInstrumentor(args);

    inst.go();

    // RR is funky -- force it to stop
    Util.exit(0);
  }

  private String[] args;

  public AsmInstrumentor(String[] args) {
    this.args = args;
  }

  public void go() {
    // First, parse options
    args = parseOptions(args);

    Set<String> baseClasses = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(args)));

    baseClasses = cleanNames(baseClasses);

    File outdir = new File(clOutdir.get());

    LoaderContext loader = new LoaderContext(getClass().getClassLoader());
    HashSet<String> preloadedClasses = new HashSet<String>();

    for (AsmInstrumentation inst : clInstrumentationPasses.get()) {
      inst.prepare(outdir, loader, preloadedClasses, baseClasses);

      inst.instrument();
    }
  }

  private static Set<String> cleanNames(Collection<String> names) {
    Set<String> ret = new HashSet<String>();

    for (String name : names) {
      ret.add(name.replace('.', '/'));
    }

    return Collections.unmodifiableSet(ret);
  }
}

