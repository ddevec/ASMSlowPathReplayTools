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

public abstract class Analysis {
  public Analysis() { }

  public void analyze(String classname) throws IOException {
    URL resource = getClassFile(classname);

    if (resource == null) {
      throw new IOException("Couldn't find Resource: " + classname);
    }

    InputStream is = null;
    is = resource.openStream();

    ClassReader cr = null;

    cr = new ClassReader(is);

    ClassVisitor cv = getClassVisitor();
    cr.accept(cv, 0);

    is.close();
  }

  public abstract ClassVisitor getClassVisitor();

  public URL getClassFile(String classname) {
    ClassLoader cl = getClass().getClassLoader();

    String resourceName = classNameToClassFile(classname);

    URL resource = cl.getResource(resourceName);
    return resource;
  }

  public final static String classNameToClassFile(String classname) {
    return classname.replace('.', '/') + ".class";
  }
}

