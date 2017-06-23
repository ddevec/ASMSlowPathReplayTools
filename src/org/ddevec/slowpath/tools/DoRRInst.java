
package org.ddevec.slowpath.tools;

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

import rr.loader.LoaderContext;
import rr.loader.MetaDataBuilder;
import rr.meta.ClassInfo;
import rr.meta.MetaDataInfoMaps;
import rr.instrument.ClassContext;
import rr.instrument.Instrumentor;
import rr.state.agent.ThreadStateFieldExtension;
import rr.state.agent.StateExtensionTransformer;
import rr.tool.RR;

import rr.instrument.classes.ArrayAllocSiteTracker;
import rr.instrument.classes.CloneFixer;
import rr.instrument.classes.ThreadDataThunkInserter;
import rr.loader.InstrumentingDefineClassLoader;
import rr.meta.InstrumentationFilter;
import rr.replay.RRReplay;
import rr.state.AbstractArrayStateCache;
import rr.state.ArrayStateFactory;
import rr.state.ShadowThread;
import rr.state.agent.ThreadStateExtensionAgent;
import rr.state.agent.ThreadStateExtensionAgent.InstrumentationMode;
import rr.state.update.Updaters;
import rr.tool.Tool;
import rr.tool.ToolVisitor;

import tools.fasttrack.FastTrackTool;

import org.ddevec.slowpath.analysis.ReachingClassAnalysis;
import org.ddevec.slowpath.instr.Analysis;
import org.ddevec.slowpath.instr.RRInst;
import org.ddevec.slowpath.instr.RRSlowpathInst;

import org.ddevec.record.instr.NativeMethodFinder;

import acme.util.Util;
import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;

/**
 * Does Roadrunner instrumentation --s taticaly.
 */
public class DoRRInst {
	private static final String FIELD_ACCESSOR_NAME_PREFIX = "ts_get";

  private StateExtensionTransformer SET = new StateExtensionTransformer();

  private List<String> classes = new ArrayList<String>();

  public static final CommandLineOption<String> clOutdir =
    CommandLine.makeString("outdir", "./test_out/DoRRInst",
        CommandLineOption.Kind.STABLE,
        "Output directroy for instrumented class files");

  public static final CommandLineOption<String> clTool =
    CommandLine.makeString("tool", "tools/fasttrack/FastTrackTool",
        CommandLineOption.Kind.STABLE,
        "Tool to be instrumented");

  private static CommandLineOption<Boolean> clNoInst = CommandLine.makeBoolean("noinst", false,
      CommandLineOption.Kind.STABLE, "Does no instrumentation, just passes " +
      "the bc files through");

  private static CommandLineOption<Boolean> clFindNative = CommandLine.makeBoolean("findnative", false,
      CommandLineOption.Kind.STABLE, "Prints the names of all observed native methods ");

	private class ToolClassVisitor extends ClassVisitor implements Opcodes {
		String owner;

    public ToolClassVisitor(ClassVisitor cv, String owner) {
      super(ASM5, cv);
      this.owner = owner;
    }

    @Override
      public void visit(int version, int access, String name,
          String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
      }

    @Override
      public MethodVisitor visitMethod(int access, String name, String desc,
          String signature, String[] exceptions) {
        if (name.startsWith(FIELD_ACCESSOR_NAME_PREFIX)) {
          System.err.println("Add extension");
          ThreadStateFieldExtension f = new ThreadStateFieldExtension(owner, "rr/state/ShadowThread", name.substring(7), Type.getReturnType(desc).getDescriptor());
          SET.addField(f);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
      }

  }

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

		cl.add(clTool);
		cl.add(clNoInst);
		cl.add(clFindNative);
		cl.add(RRSlowpathInst.clNoSlowPath);
		cl.add(clOutdir);

		cl.add(rr.tool.RR.classPathOption);
		cl.add(rr.tool.RR.toolPathOption);
		cl.add(rr.tool.RR.toolOption);
		cl.add(rr.tool.RR.printToolsOption);

		cl.add(rr.loader.LoaderContext.repositoryPathOption);

		cl.addGroup("Instrumentor");
		//cl.add(noInstrumentOption);
		//cl.add(instrumentOption);
		cl.add(rr.tool.RR.nofastPathOption);
		cl.add(InstrumentationFilter.classesToWatch);
		cl.add(InstrumentationFilter.fieldsToWatch);
		cl.add(InstrumentationFilter.methodsToWatch);
		cl.add(InstrumentationFilter.linesToWatch);

    // Hacky fasttrack specific options
		cl.add(InstrumentationFilter.chordFilename);
		cl.add(InstrumentationFilter.chordElideFilename);
		cl.add(InstrumentationFilter.bbFilename);
		cl.add(InstrumentationFilter.lstFilename);
		cl.add(InstrumentationFilter.lglFilename);
		cl.add(InstrumentationFilter.ftNoRWInst);
		//cl.add(InstrumentationFilter.ftNoSyncInst);
		cl.add(InstrumentationFilter.ftDoStats);
		cl.add(InstrumentationFilter.ftDoSplits);
		cl.add(InstrumentationFilter.ftDoSplitsPrint);
		cl.add(InstrumentationFilter.ftNoMiss);
		cl.add(InstrumentationFilter.ftMeasureFramework);
    // End disgusting options

		cl.add(InstrumentationFilter.methodsSupportThreadStateParam);
		cl.add(InstrumentationFilter.noOpsOption);
		cl.add(rr.tool.RR.valuesOption);
		cl.add(ThreadDataThunkInserter.noConstructorOption);
		cl.add(CloneFixer.noCloneOption);
		cl.add(rr.tool.RR.noEnterOption);
		cl.add(rr.tool.RR.noShutdownHookOption);
		cl.add(Instrumentor.dumpClassOption);
    cl.add(rr.instrument.classes.ClassInitNotifier.loadClassInitMapping);
    cl.add(rr.instrument.classes.ClassInitNotifier.saveClassInitMapping);
		cl.add(InstrumentingDefineClassLoader.sanityOption);
		cl.add(Instrumentor.fancyOption);
		cl.add(Instrumentor.verifyOption);
		cl.add(Instrumentor.trackArraySitesOption);
		cl.add(Instrumentor.trackReflectionOption);
		cl.add(ThreadStateExtensionAgent.noDecorationInline);
		cl.addOrderConstraint(ThreadStateExtensionAgent.noDecorationInline, rr.tool.RR.toolOption);


		cl.addGroup("Monitor");
		cl.add(rr.tool.RR.xmlFileOption);
		cl.add(rr.tool.RR.noxmlOption);
		cl.add(rr.tool.RR.stackOption);
		cl.add(rr.tool.RR.pulseOption);
		cl.add(rr.tool.RR.noTidGCOption);
		cl.add(rr.tool.RREventGenerator.noJoinOption);
		cl.add(rr.tool.RREventGenerator.indicesToWatch);
		cl.add(rr.tool.RREventGenerator.multiClassLoaderOption);
		cl.add(rr.tool.RR.forceGCOption);
		cl.add(Updaters.updateOptions);
		cl.add(ArrayStateFactory.arrayOption);
		cl.add(Instrumentor.fieldOption);
		cl.add(rr.barrier.BarrierMonitor.noBarrier);
		cl.add(RR.noEventReuseOption);
		cl.add(AbstractArrayStateCache.noOptimizedArrayLookupOption);
		//cl.add(infinitelyRunningThreadsOption);
		cl.add(rr.instrument.methods.ThreadDataInstructionAdapter.callSitesOption);
		cl.add(rr.tool.RR.trackMemoryUsageOption);

		cl.addGroup("Limits");
		cl.add(rr.tool.RR.timeOutOption);
		cl.add(rr.tool.RR.memMaxOption);
		cl.add(rr.tool.RR.maxTidOption);
		cl.add(rr.RRMain.availableProcessorsOption);
		cl.add(rr.error.ErrorMessage.maxWarnOption);


		cl.addOrderConstraint(rr.tool.RR.classPathOption, rr.tool.RR.toolOption);
		cl.addOrderConstraint(rr.tool.RR.toolPathOption, rr.tool.RR.toolOption);
		cl.addOrderConstraint(rr.tool.RR.toolOption, rr.tool.RR.toolOption);
		cl.addOrderConstraint(rr.barrier.BarrierMonitor.noBarrier, rr.tool.RR.toolOption);

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
    DoRRInst inst = new DoRRInst(args);

    inst.go();

    // RR is funky -- force it to stop
    Util.exit(0);
  }

  private String[] args;

  public DoRRInst(String[] args) {
    this.args = args;
  }

  public void go() {
    // First, parse options
    args = parseOptions(args);

    List<String> baseClasses = Arrays.asList(args);
    String toolClass = clTool.get();

    baseClasses = cleanNames(baseClasses);
    File outdir = new File(clOutdir.get());

    baseClasses.add("org.ddevec.slowpath.runtime.MisSpecException");

    // Then, get classes (closure)
    Iterable<String> classes = classes = calcClosure(baseClasses);

    LoaderContext loader = new LoaderContext(getClass().getClassLoader());

    // Run RR Pre-loadng on all of the classes...
    for (String cname : classes) {
      try {
        //System.err.println("Getting resource: " + cname);
        URL resource = getClassFile(cname);
        if (resource == null) {
          System.err.println("  WARNING: Couldn't find resource!");
          continue;
        }
        InputStream is = resource.openStream();

        ClassReader cr = new ClassReader(is);

        // UGH -- mark class as preloaded b/c -- RR nonsense
        MetaDataBuilder.preLoadFully(loader, cr);

        InitFileHandles(cr.getClassName());

        is.close();

      } catch (IOException ex) {
        handleError(ex);
      }
    }

    //RR.toolOption.checkAndApply("tools/fasttrack/FastTrackTool");
    RR.createDefaultToolIfNecessary();
    //FastTrackTool tool = new FastTrackTool("FastTrack", RR.getTool(), null);
    //RR.setTool(tool);

    // Finally, foreach class instrument
    for (String classname : classes) {
      if (shouldInstrument(classname)) {
        System.err.println("Instrumenting: " + classname);
        URL resource = getClassFile(classname);

        if (resource == null) {
          System.err.println("WARNING: Couldn't find Resource: " + classname);
          continue;
          // handleError(new IOException("Couldn't find Resource: " + classname));
        }

        InputStream is;
        try {
          is = resource.openStream();

          ClassReader cr = new ClassReader(is);

          ClassWriter cw;
          if (clNoInst.get()) {
            cw = new ClassWriter(cr, 0);
            cr.accept(cw, 0);
          } else if(clFindNative.get()) {
            cw = NativeMethodFinder.doVisit(cr);
          } else {
            cw = RRSlowpathInst.doVisit(cr);
          }
          is.close();

          String oFileName = getOutputName(outdir.getPath(), classname);
          File oFile = new File(oFileName);

          File parent = oFile.getParentFile();

          if (!parent.exists()) {
            parent.mkdirs();
          }
          OutputStream os = new FileOutputStream(oFile);

          os.write(cw.toByteArray());
          os.close();
        } catch (IOException ex) {
          handleError(ex);
        }
      }
    }

    List<String> toolInstrClasses = new ArrayList<String>();
    //toolInstrClasses.add(toolClass);
    toolInstrClasses.add("tools/fasttrack/FastTrackTool");
    toolInstrClasses.add("rr/state/ShadowThread");
    // Must also instrument fasttrack tools with ThreadStateFieldExtension
    // Pretty sure that's just FastTrackTool
    for (String toolClassName : toolInstrClasses) {
      System.err.println("Instrumenting tool: " + toolClassName);
      URL toolResource = getClassFile(toolClassName);
      SET.addToolClassToWatchList(toolClassName);

      if (toolResource == null) {
        System.err.println("WARNING: Couldn't find Resource: " +
            toolClassName);
        System.exit(1);
      }

      InputStream is;
      try {
        is = toolResource.openStream();

        ClassReader cr = new ClassReader(is);

        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES |
            ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = new ToolClassVisitor(cw, toolClassName);

        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        is.close();

        String oFileName = getOutputName(outdir.getPath(), toolClassName);
        File oFile = new File(oFileName);

        File parent = oFile.getParentFile();

        if (!parent.exists()) {
          parent.mkdirs();
        }
        OutputStream os = new FileOutputStream(oFile);

        byte[] xform = cw.toByteArray();

        byte[] xform2 = SET.transform(null, toolClassName, null, null, xform);

        os.write(xform2);
        os.close();
      } catch (IOException ex) {
        handleError(ex);
      } catch (IllegalClassFormatException ex) {
        handleError(ex);
      }
    }

    // Finally, write out any classes defined by the analysis
    HashMap<String, byte[]> defineMap = LoaderContext.getDefineMap();

    for (Map.Entry<String, byte[]> entry : defineMap.entrySet()) {
      String classname = entry.getKey();
      byte[] data = entry.getValue();
      try {
          String oFileName = getOutputName(outdir.getPath(), classname);
          File oFile = new File(oFileName);

          File parent = oFile.getParentFile();

          if (!parent.exists()) {
            parent.mkdirs();
          }
          OutputStream os = new FileOutputStream(oFile);

          os.write(data);
          os.close();
      } catch (IOException ex) {
        handleError(ex);
      }
    }

    MetaDataInfoMaps.dump(outdir.getPath() + "/rr.meta");
  }

  private static boolean shouldInstrument(String classname) {
    if (classname.startsWith("org.ddevec.slowpath.runtime")) {
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

  public void InitFileHandles(String classname) {
			// This is the "default" guess at source file name if we can't 
			// extract it from the class file.
			String fileName = classname;
			ClassInfo currentClass = MetaDataInfoMaps.getClass(fileName);
			if (fileName.contains("$")) {
				fileName = fileName.substring(0, fileName.indexOf("$"));
			}
			fileName += ".java";
			final ClassContext ctxt = Instrumentor.classContext.get(currentClass);
			ctxt.setFileName(fileName);
  }
}
