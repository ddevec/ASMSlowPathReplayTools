package org.ddevec.slowpath.tools;

import java.lang.reflect.Constructor;
import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ddevec.slowpath.analysis.ReachingClassAnalysis;
import org.ddevec.slowpath.instr.Instrumentor;
import org.ddevec.slowpath.instr.Analysis;
import org.ddevec.slowpath.instr.JVMVersionNumberFixer;
import org.ddevec.slowpath.utils.InstrumentationFilter;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.Messages;

/* FIXME: Needed? */
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.Opcodes;

public class ClassInstrumentor {

  private static class CmdOptions {
    private boolean optionsValid = false;

    @Argument
    private List<String> classes = new ArrayList<String>();

    @Option(name="--outdir",
            usage="Output directory for instrumented class files",
            metaVar="<outdir>")
    private File outdir = new File("./test_out/ClassInstrumentor");

    @Option(name="-i", aliases="--instr-class", required=true,
            usage="The instrumentor to use to instrument class files",
            metaVar="<instr_class>")
    private String instr = null;
    private Class<?> instrClass = null;
    private Constructor<?> instrCons = null;

    public CmdOptions(String[] args) {
      ParserProperties prop = ParserProperties.defaults();
      prop = prop.withUsageWidth(80);
      CmdLineParser parser = new CmdLineParser(this, prop);

      try {
        parser.parseArgument(args);

        if (classes.size() == 0) {
          throw new CmdLineException(parser, Messages.ILLEGAL_LIST,
              "No class specified");
        }

        try {
          //String instrName = instr.replace('.', '/');
          //System.out.println("instrName: " + instrName);
          instrClass = Class.forName(instr);
        } catch (ClassNotFoundException ex) {
          throw new CmdLineException(parser, instr + " class not found.", ex);
        }

        if (!ClassVisitor.class.isAssignableFrom(instrClass)) {
          throw new CmdLineException(parser, Messages.ILLEGAL_PATH,
              instr + " is not a ClassVisitor");
        }

        try {
          instrCons = instrClass.getConstructor(
              new Class[] {int.class, ClassVisitor.class});
        } catch (NoSuchMethodException ex) {
          throw new CmdLineException(parser,
              instr + " does not have valid Constructor", ex);
        }

      } catch(CmdLineException e) {
        System.err.println(e.getMessage());
        parser.printUsage(System.err);
        System.err.println();
        return;
      }

      optionsValid = true;
    }

    public boolean optionsValid() {
      return optionsValid;
    }

    public List<String> classes() {
      return classes;
    }

    public File outdir() {
      return outdir;
    }

    public Class instr() {
      return instrClass;
    }

    public Constructor instrCons() {
      return instrCons;
    }
  }

  private static void handleError(Exception ex) {
    System.err.println("Error: " + ex);
    ex.printStackTrace();
    System.exit(1);
  }

  public static void main(String[] args) {
    CmdOptions opts = new CmdOptions(args);

    if (!opts.optionsValid()) {
      System.exit(1);
    }

    List<String> baseClasses = opts.classes();

    baseClasses = cleanNames(baseClasses);

    File outdir = opts.outdir();
    Constructor instrCons = opts.instrCons();

    Iterable<String> classes = classes = calcClosure(baseClasses);

    for (String classname : classes) {
      if (InstrumentationFilter.shouldInstrument(classname)) {
        Instrumentor inst = new Instrumentor(outdir.getPath()) {
            @Override
            public ClassVisitor getClassVisitor(ClassVisitor cv) {
              try {
                cv = (ClassVisitor)instrCons.newInstance(Opcodes.ASM5, cv);
                return new JVMVersionNumberFixer(Opcodes.ASM5, cv);
              } catch (Exception ex) {
                handleError(ex);
                return null;
              }
            }
        };

        try {
          System.err.println("Instrumenting: " + classname);
          inst.instrument(classname);
        } catch (IOException ex) {
          handleError(ex);
        }
      }
    }
  }

  private static List<String> cleanNames(List<String> names) {
    List<String> ret = new ArrayList<String>();

    for (String name : names) {
      ret.add(name.replace('.', '/'));
    }

    return ret;
  }


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
        //System.out.println("Analyze prep: " + cName);
        Set<String> nC = new HashSet<String>();
        Analysis as = new Analysis() {
          @Override
          public ClassVisitor getClassVisitor() {
            return new ReachingClassAnalysis(Opcodes.ASM5, null, nC);
          }
        };

        //System.out.println("Analyze start: " + cName);
        try {
          as.analyze(cName);
        } catch (IOException ex) {
          System.err.println("WARNING: IOError handling: " + cName);
          System.err.println(ex);
          //handleError(ex);
        }

        //System.err.println("Analyze done: " + cName);
        for (String cn : nC) {
          if (!ret.contains(cn) &&
              !cn.startsWith("[")) {
            //System.err.println("Found new class: " + cn);
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
}

