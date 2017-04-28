package org.ddevec.slowpath.instr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
    writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    return getClassVisitor(writer);
  }

  public abstract ClassVisitor getClassVisitor(ClassVisitor cv);

  public String getOutputName(String classname) {
    return basedir + '/' + classNameToClassFile(classname);
  }
}

