/**
 *  Responsible for managing splitting a visitor into two paths.
 */

package org.ddevec.slowpath.instr;

import rr.org.objectweb.asm.AnnotationVisitor;
import rr.org.objectweb.asm.Attribute;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.FieldVisitor;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.TypePath;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

// Okay, lets just give something similar a main and be done with it.
public class ClassVisitorSplitter extends ClassVisitor {

  private int nSplits = 0;

  // Only forwards the last call to visitCode and visitEnd...
  private class ForwardLastVisitor extends MethodVisitor {
    private int nVisits = 0;
    private int nEnds = 0;
    private int nSplits = 0;

    public ForwardLastVisitor(MethodVisitor mv, int nSplits) {
      super(Opcodes.ASM5, mv);
      this.nSplits = nSplits;
    }

    @Override
    public void visitCode() {
      if (nVisits == 0) {
        super.visitCode();
      }

      nVisits++;
    }

    @Override
    public void visitEnd() {
      nEnds++;
      if (nEnds == nSplits) {
        super.visitEnd();
      }
    }
  }

  private class VisitArgs {
    public int version;
    public int access;
    public String name;
    public String signature;
    public String superName;
    public String[] interfaces;

    public VisitArgs(int version, int access, String name, String signature,
        String superName, String[] interfaces) {
      this.version = version;
      this.access = access;
      this.name = name;
      this.signature = signature;
      this.superName = superName;
      this.interfaces = interfaces;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof VisitArgs)) {
        return false;
      }

      VisitArgs rhs = (VisitArgs)orhs;

      return rhs.version == version &&
        rhs.access == access &&
        rhs.name.equals(name) &&
        (rhs.signature == null && signature == null || rhs.signature.equals(signature)) &&
        Arrays.equals(rhs.interfaces, interfaces);
    }

    @Override
    public int hashCode() {
      int ret = 0;
      ret += version * 997;
      ret += access * 1003;
      ret += name.hashCode() * 7;
      if (signature != null) {
        ret += signature.hashCode() * 19;
      }
      ret += superName.hashCode() * 23;
      ret += Arrays.deepHashCode(interfaces) * 1003;
      return ret;
    }
  }
  private HashSet<VisitArgs> visits = new HashSet<VisitArgs>();

  private class AnnotationArgs {
    public String desc;
    public boolean visible;

    public AnnotationArgs(String desc, boolean visible) {
      this.desc = desc;
      this.visible = visible;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof AnnotationArgs)) {
        return false;
      }

      AnnotationArgs rhs = (AnnotationArgs)orhs;

      return rhs.desc.equals(desc) && visible == rhs.visible;
    }

    @Override
    public int hashCode() {
      int ret = 993;
      ret = desc.hashCode() * 1003 ^ (visible ? 0 : 1003);
      return ret;
    }
  }
  private HashMap<AnnotationArgs, AnnotationVisitor> visitAnnotations =
    new HashMap<AnnotationArgs, AnnotationVisitor>();

  private HashSet<Attribute> visitAttributes =
    new HashSet<Attribute>();

  private boolean visitEnds = false;

  private class FieldArgs {
    public int access;
    public String name;
    public String desc;
    public String signature;
    public Object value;

    public FieldArgs(int access, String name, String desc,
        String signature, Object value) {
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.signature = signature;
      this.value = value;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof FieldArgs)) {
        return false;
      }

      FieldArgs rhs = (FieldArgs)orhs;

      return rhs.access == access &&
        rhs.name.equals(name) &&
        rhs.desc.equals(desc) &&
        ((rhs.signature == null && signature == null) || rhs.signature.equals(signature)) &&
        ((rhs.value == null && value == null) || rhs.value.equals(value));
    }

    @Override
    public int hashCode() {
      int ret = access * 1003;
      ret += name.hashCode() * 7;
      ret += desc.hashCode();
      if (signature != null) {
        ret += signature.hashCode() * 19;
      }
      if (value != null) {
        ret += value.hashCode() * 17;
      }
      return ret;
    }
  }
  private HashMap<FieldArgs, FieldVisitor> visitFields =
    new HashMap<FieldArgs, FieldVisitor>();

  private class InnerClassArgs {
    public String name;
    public String outerName;
    public String innerName;
    public int access;

    public InnerClassArgs(String name, String outerName, String innerName,
        int access) {
      this.name = name;
      this.outerName = outerName;
      this.innerName = innerName;
      this.access = access;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof InnerClassArgs)) {
        return false;
      }
      InnerClassArgs rhs = (InnerClassArgs)orhs;

      return rhs.name.equals(name) &&
        rhs.outerName.equals(outerName) &&
        rhs.innerName.equals(innerName) &&
        rhs.access == access;
    }

    @Override
    public int hashCode() {
      int ret = name.hashCode();
      if (outerName != null) {
        ret += outerName.hashCode() * 7;
      }
      if (innerName != null) {
        ret += innerName.hashCode() * 17;
      }
      ret += access * 1003;
      return ret;
    }
  }
  private HashSet<InnerClassArgs> visitInnerClasses =
    new HashSet<InnerClassArgs>();

  private class MethodArgs {
    public int access;
    public String name;
    public String desc;
    public String signature;
    public String[] exceptions;

    public MethodArgs(int access, String name, String desc, String signature,
        String[] exceptions) {
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.signature = signature;
      this.exceptions = exceptions;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof MethodArgs)) {
        return false;
      }
      MethodArgs rhs = (MethodArgs)orhs;

      return rhs.access == access &&
        rhs.name.equals(name) &&
        rhs.desc.equals(desc) &&
        ((rhs.signature == null && signature == null) || rhs.signature.equals(signature)) &&
        Arrays.equals(rhs.exceptions, exceptions);
    }

    @Override
    public int hashCode() {
      int ret = access;
      ret += name.hashCode() * 7;
      ret += desc.hashCode() * 17;
      if (signature != null) {
        ret += signature.hashCode() * 19;
      }
      ret += Arrays.deepHashCode(exceptions) * 1003;
      return ret;
    }
  }
  private HashMap<MethodArgs, MethodVisitor> visitMethods =
    new HashMap<MethodArgs, MethodVisitor>();

  private class OuterClassArgs {
    private String owner;
    private String name;
    private String desc;

    public OuterClassArgs(String owner, String name, String desc) {
      this.owner = owner;
      this.name = name;
      this.desc = desc;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof OuterClassArgs)) {
        return false;
      }
      OuterClassArgs rhs = (OuterClassArgs)orhs;

      return rhs.owner.equals(owner) &&
        rhs.name.equals(name) &&
        rhs.desc.equals(desc);
    }

    @Override
    public int hashCode() {
      int ret = owner.hashCode();
      if (name != null) {
        ret += name.hashCode() * 7;
      }
      if (desc != null) {
        ret += desc.hashCode() * 19;
      }
      return ret;
    }
  }
  private HashSet<OuterClassArgs> visitOuterClasses =
    new HashSet<OuterClassArgs>();

  public class SourceArgs {
    private String source;
    private String debug;

    public SourceArgs(String source, String debug) {
      this.source = source;
      this.debug = debug;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof SourceArgs)) {
        return false;
      }
      SourceArgs rhs = (SourceArgs)orhs;

      return rhs.source.equals(source) &&
        ((rhs.debug == null && debug == null) || rhs.debug.equals(debug));
    }

    @Override
    public int hashCode() {
      int ret = source.hashCode();
      if (debug != null) {
        ret += debug.hashCode() * 1003;
      }
      return ret;
    }
  }
  private HashSet<SourceArgs> visitSources =
    new HashSet<SourceArgs>();

  private class TypeAnnotationArgs {
    private int typeRef;
    private TypePath typePath;
    private String desc;
    private boolean visible;

    public TypeAnnotationArgs(int typeRef, TypePath typePath, String desc,
        boolean visible) {
      this.typeRef = typeRef;
      this.typePath = typePath;
      this.desc = desc;
      this.visible = visible;
    }

    @Override
    public boolean equals(Object orhs) {
      if (orhs == null) {
        return false;
      }
      if (orhs == this) {
        return true;
      }
      if (!(orhs instanceof TypeAnnotationArgs)) {
        return false;
      }

      TypeAnnotationArgs rhs = (TypeAnnotationArgs)orhs;

      return rhs.typeRef == typeRef &&
        rhs.typePath.equals(typePath) &&
        rhs.desc.equals(desc) &&
        rhs.visible == visible;
    }

    @Override
    public int hashCode() {
      int ret = typeRef * 1003;
      ret += typePath.hashCode() * 23;
      ret += desc.hashCode() * 53;
      ret += (visible ? 0 : 993);
      return ret;
    }
  }
  private HashMap<TypeAnnotationArgs, AnnotationVisitor> visitTypeAnnotations =
    new HashMap<TypeAnnotationArgs, AnnotationVisitor>();

  public ClassVisitorSplitter(ClassVisitor cv, int nSplits) {
    super(Opcodes.ASM5, cv);
    this.nSplits = nSplits;
  }

  @Override
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    VisitArgs args = new VisitArgs(version, access, name, signature, superName,
        interfaces);

    if (!visits.contains(args)) {
      visits.add(args);
      super.visit(version, access, name, signature, superName, interfaces);
    }
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    AnnotationArgs args = new AnnotationArgs(desc, visible);

    AnnotationVisitor visitor;
    if (!visitAnnotations.containsKey(args)) {
      visitor = super.visitAnnotation(desc, visible);
      visitAnnotations.put(args, visitor);
    } else {
      visitor = visitAnnotations.get(args);
    }

    return visitor;
  }

  @Override
  public void visitAttribute(Attribute attr) {
    if (!visitAttributes.contains(attr)) {
      visitAttributes.add(attr);
      super.visitAttribute(attr);
    }
  }

  @Override
  public void visitEnd() {
    if (!visitEnds) {
      super.visitEnd();
      visitEnds = true;
    }
  }

  @Override
  public FieldVisitor visitField(int access, String name, String desc,
      String signature, Object value) {
    FieldArgs args = new FieldArgs(access, name, desc, signature, value);

    if (visitFields.containsKey(args)) {
      return visitFields.get(args);
    } else {
      FieldVisitor visitor = super.visitField(access, name, desc, signature,
          value);
      visitFields.put(args, visitor);
      return visitor;
    }
  }

  @Override
  public void visitInnerClass(String name, String outerName,
      String innerName, int access) {
    InnerClassArgs args = new InnerClassArgs(name, outerName,
        innerName, access);

    if (!visitInnerClasses.contains(args)) {
      super.visitInnerClass(name, outerName, innerName, access);
      visitInnerClasses.add(args);
    }
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    MethodArgs args = new MethodArgs(access, name, desc, signature, exceptions);

    if (!visitMethods.containsKey(args)) {
      MethodVisitor visitor = super.visitMethod(access, name, desc, signature,
          exceptions);
      visitor = new ForwardLastVisitor(visitor, nSplits);
      visitMethods.put(args, visitor);
      return visitor;
    } else {
      return visitMethods.get(args);
    }
  }

  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    OuterClassArgs args = new OuterClassArgs(owner, name, desc);

    if (!visitOuterClasses.contains(args)) {
      visitOuterClasses.add(args);
      super.visitOuterClass(owner, name, desc);
    }
  }

  @Override
  public void visitSource(String source, String debug) {
    SourceArgs args = new SourceArgs(source, debug);

    if (!visitSources.contains(args)) {
      visitSources.add(args);
      // System.err.println("VisitSource: " + source + " -!- " + debug);
      super.visitSource(source, debug);
    }
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath,
      String desc, boolean visible) {
    TypeAnnotationArgs args = new TypeAnnotationArgs(typeRef, typePath, desc,
        visible);

    if (!visitTypeAnnotations.containsKey(args)) {
      AnnotationVisitor visitor = super.visitTypeAnnotation(typeRef, typePath,
          desc, visible);
      visitTypeAnnotations.put(args, visitor);
      return visitor;
    } else {
      return visitTypeAnnotations.get(args);
    }
  }
}
