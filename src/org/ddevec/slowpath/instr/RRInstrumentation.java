package org.ddevec.slowpath.instr;

import org.ddevec.asm.tools.AsmInstrumentation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import rr.org.objectweb.asm.util.Printer;
import rr.org.objectweb.asm.util.Textifier;
import rr.org.objectweb.asm.util.TraceClassVisitor;
import rr.org.objectweb.asm.util.TraceMethodVisitor;

import rr.instrument.ClassContext;
import rr.instrument.Instrumentor;
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
import rr.loader.InstrumentingDefineClassLoader;
import rr.loader.Loader;
import rr.loader.LoaderContext;
import rr.loader.MetaDataBuilder;
import rr.meta.ClassInfo;
import rr.meta.InstrumentationFilter;
import rr.meta.MetaDataInfoMaps;
import rr.replay.RRReplay;
import rr.state.AbstractArrayStateCache;
import rr.state.ArrayStateFactory;
import rr.state.ShadowThread;
import rr.state.agent.ThreadStateExtensionAgent;
import rr.state.agent.ThreadStateExtensionAgent.InstrumentationMode;
import rr.state.agent.ThreadStateFieldExtension;
import rr.state.agent.StateExtensionTransformer;
import rr.state.update.Updaters;
import rr.tool.RR;

import rr.tool.Tool;
import rr.tool.ToolVisitor;

import tools.fasttrack.FastTrackTool;

import org.ddevec.slowpath.analysis.ReachingClassAnalysis;
import org.ddevec.slowpath.instr.Analysis;

import org.ddevec.record.instr.NativeMethodFinder;

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


public class RRInstrumentation extends AsmInstrumentation {
	private static final String FIELD_ACCESSOR_NAME_PREFIX = "ts_get";

  private static CommandLineOption<Boolean> clNoInst = CommandLine.makeBoolean("noinst", false,
      CommandLineOption.Kind.STABLE, "Does no instrumentation, just passes " +
      "the bc files through");

  private static CommandLineOption<Boolean> clDoSlowPath = CommandLine.makeBoolean("doSlowPath", false,
      CommandLineOption.Kind.STABLE, "Does slowpath instrumentation");

  private StateExtensionTransformer SET = new StateExtensionTransformer();

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

  // Load up our NativeMethodSet...
  public RRInstrumentation(CommandLine cl) {
    super(cl);
  }

  @Override
  protected void setupCl(CommandLine cl) {
    System.err.println("RRInstr: Setup cl");
    cl.addGroup("RRInstrumentation");
    cl.add(clNoInst);
    cl.add(clDoSlowPath);

		cl.add(rr.tool.RR.classPathOption);
		cl.add(rr.tool.RR.toolPathOption);
		cl.add(rr.tool.RR.toolOption);
		cl.add(rr.tool.RR.printToolsOption);

		cl.add(rr.loader.LoaderContext.repositoryPathOption);

		cl.addGroup("Instrumentor");
		//cl.add(noInstrumentOption);
		//cl.add(instrumentOption);
		cl.add(rr.tool.RR.nofastPathOption);
    /*
		cl.add(InstrumentationFilter.classesToWatch);
		cl.add(InstrumentationFilter.fieldsToWatch);
		cl.add(InstrumentationFilter.methodsToWatch);
		cl.add(InstrumentationFilter.linesToWatch);
    */

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
  }

  @Override
  protected void preload(Set<String> classes) {
    classes.add("org/ddevec/slowpath/runtime/MisSpecException");
    super.preload(classes);
  }


  @Override
  protected void runPostPreload(Set<String> classes) {
    RR.createDefaultToolIfNecessary();
    Consumer<Loader.ClassDefnArgs> definer = new Consumer<Loader.ClassDefnArgs>() {
      public void accept(Loader.ClassDefnArgs args) {
        String name = args.name;
        byte[] data = args.data;

        // Convert .s in name to /s
        name = name.replace('.', '/');
        // Open otudir + name
        String oFileName = getOutputName(new File(outdir, "classes").getAbsolutePath(), name);

        File oFile = new File(oFileName);

        File parent = oFile.getParentFile();

        if (!parent.exists()) {
          parent.mkdirs();
        }

        try (OutputStream os = new FileOutputStream(oFile)) {
          os.write(data);
        } catch (IOException ex) {
          Util.error(ex);
        }
      }
    };

    Loader.setClassDefiner(definer);
  }

  private class DumpMethodsVisitor extends ClassVisitor implements Opcodes {
    public DumpMethodsVisitor(ClassVisitor cv) {
      super(ASM5, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
      Printer p = new Textifier(Opcodes.ASM5) {
        @Override
        public void visitMethodEnd() {
          print(new PrintWriter(System.err));
        }
      };
      mv = new TraceMethodVisitor(mv, p);

      return mv;
    }
  }

  @Override
  protected ClassVisitor getClassVisitor(ClassReader cr, ClassVisitor cv) {
    System.err.println("Get cv for: " + cr.getClassName());
    if (!clNoInst.get()) {
      // If the class is abstract...
      if ((cr.getAccess() & Opcodes.ACC_INTERFACE) == 0) {
        String classname = cr.getClassName();
        ClassInfo currentClass = MetaDataInfoMaps.getClass(classname);

        ClassVisitor cv1 = new NativeMethodSanityChecker(cv);
        cv1 = new GuardStateInserter(cv1);
        cv1 = new InterruptFixer(cv1);
        cv1 = new CloneFixer(cv1);
        cv1 = new ClassInitNotifier(currentClass, cv1);
        cv1 = new ArrayAllocSiteTracker(currentClass, cv1);
        cv1 = new AbstractOrphanFixer(cv1);
        //cv1 = new CheckClassAdapter(cv1);
        if (clDoSlowPath.get()) {
          // Manages merging together stuffs from our two visit paths
          cv1 = new DebugVisitor(cv1, "Debug4");

          //cv1 = new CheckClassAdapter(cv1);
          cv1 = new ClassVisitorSplitter(cv1, 2);

          cv1 = new DebugVisitor(cv1, "Debug3");

          ClassVisitor cv2 = cv1;

          ClassVisitor cv2nt = new ThreadDataThunkInserter(cv1, true, false);
          cv2nt = new DebugVisitor(cv2nt, "Debug2nt");
          ClassVisitor cv2forThunks = new ThreadDataThunkInserter(cv1, false,
              false);
          cv2forThunks = new DebugVisitor(cv2forThunks, "Debug2thunk");
          cv2 = new SyncAndMethodThunkInserter(cv2nt, cv2forThunks);
          //cv2 = new CheckClassAdapter(cv2);

          ClassVisitor cv1Sp = cv1;
          cv1Sp = new DebugVisitor(cv1Sp, "Debug2sp");
          ClassVisitor cvSp = new ThreadDataThunkInserter(cv1Sp, true, true);
          //cvSp = new CheckClassAdapter(cvSp);
          ClassVisitor cv2forThunksSp = new ThreadDataThunkInserter(cv1, false,
              true);
          cvSp = new SyncAndMethodThunkInserter(cvSp, cv2forThunksSp);
          cv2 = new DebugVisitor(cv2, "Debug1");
          cvSp = new DebugVisitor(cvSp, "Debug1sp");
          //cv2 = new CheckClassAdapter(cv2);
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
      cv = Instrumentor.insertToolSpecificVisitors(cv);
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

      cv = new JVMVersionNumberFixer(cv);
    }

    return cv;
  }

  @Override
  protected void postInstrumentation() {
    // Instrument the tools...
    List<String> toolInstrClasses = new ArrayList<String>();
    //toolInstrClasses.add(toolClass);
    toolInstrClasses.add("tools/fasttrack/FastTrackTool");
    toolInstrClasses.add("tools/fasttrack/FastTrackBarrierState");
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

        String oFileName = getOutputName(new File(outdir, "classes").getAbsolutePath(), toolClassName);
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
        Util.error(ex);
      } catch (IllegalClassFormatException ex) {
        Util.error(ex);
      }
    }

    MetaDataInfoMaps.dump(outdir.getAbsolutePath() + "/rr.meta");
  }

  private static URL getClassFile(String classname) {
    ClassLoader cl = ClassLoader.getSystemClassLoader();

    String resourceName = classNameToClassFile(classname);

    URL resource = cl.getResource(resourceName);
    return resource;
  }
}
