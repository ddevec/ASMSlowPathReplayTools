package org.ddevec.slowpath.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.net.URL;

import acme.util.Util;

import rr.meta.MetaDataInfoMaps;
import rr.instrument.Instrumentor;
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
import rr.tool.RR;
import rr.tool.Tool;
import rr.tool.ToolVisitor;

import tools.fasttrack.FastTrackTool;

import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;

public class RRStarter {
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

    return ret;
  }

  public static void main(String[] args) {

    args = parseOptions(args);

    System.err.println("RRStarter: IN main?");
    MetaDataInfoMaps.load(args[0]);
    RR.createDefaultToolIfNecessary();
    //RR.setTool(new FastTrackTool("FastTrack", RR.getTool(), null));
    //RR.setTool(new FastTrackTool("FastTrack", RR.getTool(), null));
    RR.createTool("tools.fasttrack.FastTrackTool");
    System.err.println("RRStarter: tool set");

    String[] newargs = Arrays.copyOfRange(args, 2, args.length);

    Method m = null;
    try {
      String classname = args[1];

      Class<?> classArg = Class.forName(classname);

      m = (Method)classArg.getMethod("main", String[].class);
    } catch (Exception ex) {
      System.err.println("Well, we failed on RRStarter: " + ex);
      System.exit(1);
    }
      
    try {
      Instant start = Instant.now();
      m.invoke(null, (Object)newargs);
      Instant end = Instant.now();
      System.err.println("Instrumented time: " +
          Duration.between(start, end).toMillis());
    } catch (IllegalAccessException e) {
      System.err.println("Invoke failed with: " + e);
      e.printStackTrace();
      System.exit(1);
    } catch (InvocationTargetException e) {
      System.err.println("Invoke failed with: " + e);
      e.printStackTrace();
      System.exit(1);
    }

    Util.exit(0);
  }
}

