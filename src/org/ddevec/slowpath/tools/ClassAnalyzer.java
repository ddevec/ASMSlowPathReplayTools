package org.ddevec.slowpath.tools;

import java.lang.reflect.Constructor;
import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ddevec.slowpath.analysis.ReachingClassAnalysis;
import org.ddevec.slowpath.instr.Analysis;
import org.ddevec.slowpath.instr.JVMVersionNumberFixer;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.Messages;

/* FIXME: Needed? */
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class ClassAnalyzer {

  private static class CmdOptions {
    private boolean optionsValid = false;

    @Argument
    private List<String> classes = new ArrayList<String>();

    @Option(name="-a", aliases="--analysis-class", required=true,
            usage="The analysis to run on the class files",
            metaVar="<analysis_class>")
    private String analysis = null;
    private Class<?> analysisClass = null;
    private Constructor<?> analysisCons = null;

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
          analysisClass = Class.forName(analysis);
        } catch (ClassNotFoundException ex) {
          throw new CmdLineException(parser, analysis + " class not found.", ex);
        }

        if (!ClassVisitor.class.isAssignableFrom(analysisClass)) {
          throw new CmdLineException(parser, Messages.ILLEGAL_PATH,
              analysis + " is not a ClassVisitor");
        }

        try {
          analysisCons = analysisClass.getConstructor(
              new Class[] {});
        } catch (NoSuchMethodException ex) {
          throw new CmdLineException(parser,
              analysis + " does not have valid Constructor", ex);
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

    public Class analysis() {
      return analysisClass;
    }

    public Constructor analysisCons() {
      return analysisCons;
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

    Constructor analysisCons = opts.analysisCons();

    Iterable<String> classes = baseClasses;

    for (String classname : classes) {
      Analysis ana = new Analysis() {
          @Override
          public ClassVisitor getClassVisitor() {
            try {
              return (ClassVisitor)analysisCons.newInstance();
            } catch (Exception ex) {
              handleError(ex);
              return null;
            }
          }
      };

      try {
        System.err.println("Analyzing: " + classname);
        ana.analyze(classname);
      } catch (IOException ex) {
        handleError(ex);
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

  private static boolean shouldInstrument(String classname) {
    /*
    if (classname.startsWith("java/lang")) {
      return false;
    }
    if (classname.startsWith("java/security")) {
      return false;
    }
    if (classname.startsWith("java/io")) {
      return false;
    }
    */
    if (classname.startsWith("java")) {
      return false;
    }
    if (classname.startsWith("javax")) {
      return false;
    }
    if (classname.startsWith("sun")) {
      return false;
    }
    if (classname.startsWith("jdk")) {
      return false;
    }
    if (classname.startsWith("org/ddevec/slowpath/runtime")) {
      return false;
    }

    return true;
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

