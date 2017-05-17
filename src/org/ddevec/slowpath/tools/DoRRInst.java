

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.Opcodes;

import rr.loader.LoaderContext;
import rr.loader.MetaDataBuilder;
import rr.tool.RR;

import tools.fasttrack.FastTrackTool;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.Messages;

import org.ddevec.slowpath.analysis.ReachingClassAnalysis;
import org.ddevec.slowpath.instr.Analysis;
import org.ddevec.slowpath.instr.RRInst;

/**
 * Does Roadrunner instrumentation --s taticaly.
 */
public class DoRRInst {
  private class CmdOptions {
    private boolean optionsValid = false;

    @Argument
    private List<String> classes = new ArrayList<String>();

    @Option(name="--outdir",
            usage="Output directory for instrumented class files",
            metaVar="<outdir>")
    private File outdir = new File("./test_out/DoRRInst");

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
  }

  private static void handleError(Exception ex) {
    System.err.println("Error: " + ex);
    ex.printStackTrace();
    System.exit(1);
  }

  public static void main(String[] args) {
    DoRRInst inst = new DoRRInst(args);

    inst.go();
  }

  private String[] args;

  public DoRRInst(String[] args) {
    this.args = args;
  }

  public void go() {
    // First, parse options
    CmdOptions opts = new CmdOptions(args);

    if (!opts.optionsValid()) {
      System.exit(1);
    }

    List<String> baseClasses = opts.classes();

    baseClasses = cleanNames(baseClasses);
    File outdir = opts.outdir();

    // Then, get classes (closure)
    Iterable<String> classes = classes = calcClosure(baseClasses);

    LoaderContext loader = new LoaderContext(getClass().getClassLoader());

    FastTrackTool tool = new FastTrackTool("FastTrack", null, null);
    //RR.toolOption.checkAndApply("tools/fasttrack/FastTrackTool");
    RR.setTool(tool);

    // Finally, foreach class instrument
    for (String classname : classes) {
      if (shouldInstrument(classname)) {
        // FIXME: do this --
        // Make reader for this class 
        // writer = RRInst.doVisit(reader)
        // Write bytes from writer to output location...
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

          // UGH -- mark class as preloaded b/c -- RR nonsense
          MetaDataBuilder.preLoad(loader, cr);

          ClassWriter cw = RRInst.doVisit(cr);
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
        Set<String> nC = new HashSet<String>();
        Analysis as = new Analysis() {
          @Override
          public org.objectweb.asm.ClassVisitor getClassVisitor() {
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
