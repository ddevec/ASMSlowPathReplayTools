package org.ddevec.asm.tools;

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

public abstract class AsmInstrumentation {
  protected abstract class ClassVisitWrapper {
    public abstract void visit(ClassReader rd, ClassVisitor cv);
  }

  protected File outdir;
  protected LoaderContext loader;
  protected Set<String> preloadedClasses;
  protected Set<String> reqClasses;

  // Interface functions...
  public AsmInstrumentation(CommandLine cl) {
    setupCl(cl);
  }

  public void prepare(File outdir,
      LoaderContext loader,
      Set<String> preloadedClasses,
      Set<String> reqClasses) {
    this.outdir = outdir;
    this.loader = loader;
    this.preloadedClasses = preloadedClasses;
    this.reqClasses = reqClasses;
  }

  public void instrument() {
    // First, calc classes
    Set<String> classes = new HashSet<String>(getNeededClasses(reqClasses));

    if (doClassClosure()) {
      classes = calcClosure(classes);
    }

    preload(classes);

    runPostPreload(classes);

    runInstrumentation(classes, outdir);

    postInstrumentation();
  }

  /**
   * Classes that need to be instrumented by this instrumentor (closure will be
   * calculated later).
   */
  public Collection<String> getNeededClasses(Collection<String> reqClasses) {
    return reqClasses;
  }

  protected void postInstrumentation() {
  }

  /**
   * Does any needed prelaoding.
   *
   * Default is to just do standard rr preloading
   */
  protected void preload(Set<String> classes) {
    // FIXME: Does this ignore some classes? (it shouldn't?)
    visitClasses(classes,
        new ClassVisitWrapper() {
          @Override
          public void visit(ClassReader cr, ClassVisitor cv) {
            String className = cr.getClassName();
            if (!preloadedClasses.contains(className)) {
              preloadedClasses.add(className);

              System.err.println("Doing preload: " + className);

              MetaDataBuilder.preLoadFully(loader, cr);
              initFileHandles(cr.getClassName());
            }
          }
        }, false);
  }

  /**
   * Runs anything that needs to happena fter rpeloading, but before
   * instrumentation.
   */
  protected void runPostPreload(Set<String> classes) {
  }

  /**
   * Gets a visitor for this instrumentation
   */
  protected abstract ClassVisitor getClassVisitor(ClassReader cr, ClassVisitor cv);

  /**
   * Meat and potatoes of the instrumentation.
   *
   * Runs a class visitor/writer on each class
   */
  protected void runInstrumentation(Iterable<String> classes, File outdir) {
    visitClasses(classes, outdir,
        new ClassVisitWrapper() {
          @Override
          public void visit(ClassReader cr, ClassVisitor cv) {
            cv = getClassVisitor(cr, cv);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
          }
        });
  }

  protected final void visitClasses(Iterable<String> classes,
      ClassVisitWrapper cvw) {
    visitClasses(classes, null, cvw, true);
  }

  protected final void visitClasses(Iterable<String> classes,
      ClassVisitWrapper cvw, boolean checkInstrument) {
    visitClasses(classes, null, cvw, checkInstrument);
  }

  protected final void visitClasses(Iterable<String> classes,
      File outdir,
      ClassVisitWrapper cvw) {
    visitClasses(classes, outdir, cvw, true);
  }

  protected final void visitClasses(Iterable<String> classes,
      File outdir,
      ClassVisitWrapper cvw,
      boolean checkInstrument) {
    for (String classname : classes) {
      if (!checkInstrument || shouldInstrument(classname)) {
        //System.err.println("Instrumenting: " + classname);
        URL resource = getClassFile(classname);

        if (resource == null) {
          System.err.println("WARNING: Couldn't find Resource: " + classname);
          continue;
        }

        ClassWriter cw = null;
        try (InputStream is = resource.openStream()) {

          ClassReader cr = new ClassReader(is);

          cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
          //ClassVisitor cv = new CheckClassAdapter(cw);

          // Do actual instrumentation
          cvw.visit(cr, cw);
        } catch (IOException ex) {
          Util.error(ex);
        }

        if (outdir != null) {
          File newOutdir = null;
          if (isBootClass(classname)) {
            newOutdir = new File(outdir, "bootstrap");
          } else {
            newOutdir = new File(outdir, "classes");
          }

          String oFileName = getOutputName(newOutdir.getPath(), classname);
          File oFile = new File(oFileName);
          File parent = oFile.getParentFile();

          if (!parent.exists()) {
            parent.mkdirs();
          }

          try (OutputStream os = new FileOutputStream(oFile)) {
            os.write(cw.toByteArray());
          } catch (IOException ex) {
            Util.error(ex);
          }
        }
      }
    }
  }

  protected boolean isBootClass(String classname) {
    if (classname.startsWith("java") ||
        classname.startsWith("jdk") ||
        classname.startsWith("sun") ||
        classname.startsWith("org.ddevec.record.runtime") ||
        classname.startsWith("org.ddevec.slowpath.runtime")) {
      return true;
    }

    return false;
  }

  /**
   * Adds any analysis specific CommandLine arguments.
   *
   * Can be overwitten by subclass if commandLine options are needed.
   */
  protected void setupCl(CommandLine cl) {
  }

  /**
   * Determines if instrumentation should be run.
   *
   * Can be overwritten by superclasses -- if different shouldInstrument
   * behavior is needed.
   */
  protected boolean shouldInstrument(String classname) {
    if (classname.startsWith("org.ddevec.slowpath.runtime")) {
      return false;
    }
    if (classname.startsWith("org.ddevec.record.runtime")) {
      return false;
    }

    ClassInfo thisClass = MetaDataInfoMaps.getClass(classname);

    // Go conservative -- slowpath variable true
    return InstrumentationFilter.shouldInstrument(thisClass, true);
  }

  public boolean doClassClosure() {
    return true;
  }

  public static Set<String> calcClosure(Collection<String> classes) {
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

        try {
          new Analysis() {
            @Override
            public ClassVisitor getClassVisitor() {
              return new ReachingClassAnalysis(Opcodes.ASM5, null, nC);
            }
          }.analyze(cName);
        } catch (IOException ex) {
          Util.error(ex);
        }

        for (String cn : nC) {
          if (!ret.contains(cn) && !cn.startsWith("[")) {
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

  private URL getClassFile(String classname) {
    ClassLoader cl = getClass().getClassLoader();

    String resourceName = classNameToClassFile(classname);

    URL resource = cl.getResource(resourceName);
    return resource;
  }

  public static String classNameToClassFile(String classname) {
    return classname.replace('.', '/') + ".class";
  }

  public static String getOutputName(String basedir, String classname) {
    return basedir + '/' + classNameToClassFile(classname);
  }

  public void initFileHandles(String classname) {
    /*
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
    */
  }
}

