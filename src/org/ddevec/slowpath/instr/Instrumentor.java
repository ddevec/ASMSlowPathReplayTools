package org.ddevec.slowpath.instr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;

import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;

public abstract class Instrumentor extends Analysis {
  String basedir;
  public Instrumentor(String basedir) {
    this.basedir = basedir;
  }

  ClassWriter writer;

  public void instrument(String classname) throws IOException {
    analyze(classname);

    OutputStream os = null;
    String ofilstr = getOutputName(classname);
    File ofil = new File(ofilstr);
    // Ensure the parent exists
    File parent = ofil.getParentFile();
    if (!parent.exists()) {
        parent.mkdirs();
    }
    os = new FileOutputStream(ofil);

    os.write(writer.toByteArray());
    os.close();
    writer = null;
  }

  public ClassVisitor getClassVisitor() {
    writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES +
        ClassWriter.COMPUTE_MAXS);

    return getClassVisitor(writer);
  }

  public abstract ClassVisitor getClassVisitor(ClassVisitor cv);

  public String getOutputName(String classname) {
    return basedir + '/' + classNameToClassFile(classname);
  }
}

