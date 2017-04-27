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

public abstract class Instrumentor {
  String basedir;
  public Instrumentor(String basedir) {
    this.basedir = basedir;
  }

  public void instrument(String classname) throws IOException {
    URL resource = getClassFile(classname);

    if (resource == null) {
      throw new IOException("Couldn't find Resource: " + classname);
    }

    InputStream is = null;
    is = resource.openStream();

    ClassReader cr = null;

    cr = new ClassReader(is);
    

    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
    ClassVisitor cv = getClassVisitor(cw);
    cr.accept(cv, 0);

    is.close();

    OutputStream os = null;
    String ofilstr = getOutputName(classname);
    File ofil = new File(ofilstr);
    // Ensure the parent exists
    File parent = ofil.getParentFile();
    if (!parent.exists()) {
        parent.mkdirs();
    }
    os = new FileOutputStream(ofil);

    os.write(cw.toByteArray());
    os.close();
  }

  public abstract ClassVisitor getClassVisitor(ClassVisitor cv);

  public URL getClassFile(String classname) {
    ClassLoader cl = getClass().getClassLoader();

    String resourceName = classNameToClassFile(classname);

    URL resource = cl.getResource(resourceName);
    return resource;
  }

  public String getOutputName(String classname) {
    return basedir + '/' + classNameToClassFile(classname);
  }

  public final static String classNameToClassFile(String classname) {
    return classname.replace('.', '/') + ".class";
  }
}

