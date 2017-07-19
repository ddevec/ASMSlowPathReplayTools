package org.ddevec.record.instr;

import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.Label;
import rr.org.objectweb.asm.util.CheckClassAdapter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.PrintWriter;

import java.util.HashSet;

import acme.util.Util;
import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;

public class ClassRecordLogCreator implements Opcodes {
  public static final String RecordWrapperFcn = "recordWrapper";
  public static final String ReplayWrapperFcn = "replayWrapper";

  public static final String RecordLogClass = "org/ddevec/record/runtime/RecordLogEntry";

  public NativeMethodSet nms;

  // Load to-record list

  public ClassRecordLogCreator(NativeMethodSet nms) {
    this.nms = nms;
  }

  private static int idVal = 0;

  public void createRecordClass(RecordEntry entry, ClassVisitor cv) {
    String classname = entry.getLogClassname();
    // Create a NativeMethodInfoClass (super.visit(classstuffs))
    cv.visit(52, ACC_PUBLIC,
        classname,
        null,
        RecordLogClass,
        null);
        //new String[] { "java.io.Serializable" });

    // Add fields to that class -- for the args
    // Figure out what kind of args we need
    Type methodType = Type.getMethodType(entry.desc);

    Type[] argTypes = methodType.getArgumentTypes();
    Type retType = methodType.getReturnType();

    // Add a field per arg type
    cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "TypeId", Type.INT_TYPE.getDescriptor(), null, new Integer(getNextId()));
    
    for (int i = 0; i < argTypes.length; i++) {
      Type argType = argTypes[i];
      cv.visitField(ACC_PUBLIC, "arg" + i, argType.getDescriptor(), null, null);
    }

    // Add a field for the ret
    if (!retType.equals(Type.VOID_TYPE)) {
      cv.visitField(ACC_PUBLIC, "ret", retType.getDescriptor(), null, null);
    }
    
    // Add constructor -- same args as method
    createConstructor(cv, classname, argTypes);

    // Add ret setter
    if (!retType.equals(Type.VOID_TYPE)) {
      createSetter(cv, classname, retType, "ret");
    }

    // Add methods to that class (checkRecord, checkReplay)
    createEquals(cv, classname, retType, argTypes);

    // And finally, the static wrapper method
    createRecordWrapper(cv, classname, retType, argTypes, entry, entry.isStatic);
    createReplayWrapper(cv, classname, retType, argTypes, entry, entry.isStatic);

    cv.visitEnd();
  }

  // FIXME: Make persistent across executions, without requiring static classes
  //   init same way?
  public static int getNextOpcode(String name) {
    return getNextId();
  }

  private static int getNextId() {
    int ret = idVal;
    idVal++;
    return ret;
  }

  private static void createConstructor(ClassVisitor cv, String classname, Type[] argTypes) {
    MethodVisitor consVisitor = cv.visitMethod(ACC_PUBLIC,
        "<init>",
        Type.getMethodDescriptor(Type.VOID_TYPE, argTypes),
        null,
        null);

    consVisitor.visitCode();
    // Super with our "id" field
    // Load "this", "id"
    consVisitor.visitVarInsn(ALOAD, 0);
    // Stack is this this
    consVisitor.visitInsn(DUP);
    consVisitor.visitFieldInsn(GETSTATIC, classname, "TypeId", Type.INT_TYPE.getDescriptor());
    // invokespecial org.ddevec.record.runtime.RecordLogEntry(id)
    consVisitor.visitMethodInsn(INVOKESPECIAL, RecordLogClass,
        "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE), false);

    int idx = 0;
    int idxOffs = 1;
    // Store the args in the fields
    for (Type t : argTypes) {
      // dupe "this"
      consVisitor.visitInsn(DUP);
      // Load the arg
      consVisitor.visitVarInsn(t.getOpcode(Opcodes.ILOAD), idxOffs);
      idxOffs += t.getSize();

      // If it is an array, or object, create a copy of it -- ugh
      int sort = t.getSort();
      if (sort == Type.ARRAY) {
        Type elmType = t.getElementType();
        consVisitor.visitInsn(DUP);
        consVisitor.visitInsn(ARRAYLENGTH);
        //consVisitor.visitFieldInsn(GETFIELD, t.getDescriptor(), "length", Type.INT_TYPE.getDescriptor());
        // Call Arrays.copyOf(array, array.length)
        consVisitor.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "copyOf",
            Type.getMethodDescriptor(t, t, Type.INT_TYPE), false);
        
      } else if (sort == Type.OBJECT) {
        /*
        System.err.println("Have type: " + t);
        System.err.println("Don't support object types yet");
        new Exception("Unsupported").printStackTrace();
        System.exit(1);
        */
      }

      consVisitor.visitFieldInsn(PUTFIELD, classname, "arg" + idx, t.getDescriptor());

      idx++;
    }
    // Clear the stack
    consVisitor.visitInsn(POP);

    // Return
    consVisitor.visitInsn(RETURN);

    consVisitor.visitMaxs(5, idxOffs);

    consVisitor.visitEnd();
  }

  public static String getSetterName(String fieldName) {
    return "set_" + fieldName;
  }

  private static void createSetter(ClassVisitor cv, String classname, Type fieldType, String fieldName) {
    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC,
        getSetterName(fieldName),
        Type.getMethodDescriptor(Type.VOID_TYPE, fieldType),
        null,
        null);

    mv.visitCode();

    // load this
    mv.visitVarInsn(ALOAD, 0);

    // load the argument
    mv.visitVarInsn(fieldType.getOpcode(ILOAD), 1);

    mv.visitFieldInsn(PUTFIELD, classname, fieldName, fieldType.getDescriptor());

    mv.visitInsn(RETURN);
    int maxSizes = 1 + fieldType.getSize();
    mv.visitMaxs(maxSizes, maxSizes);
    mv.visitEnd();
  }

  private static void createEquals(ClassVisitor cv, String classname,
      Type retType, Type[] argTypes) {
    MethodVisitor eqVis = cv.visitMethod(ACC_PUBLIC,
        "equals",
        "(Ljava/lang/Object;)Z",
        null,
        null);

    eqVis.visitCode();
    Label retLabel = new Label();
    Label retBadLabel = new Label();

    // Replace rhs (arg1) with (MyType)rhs
    eqVis.visitVarInsn(ALOAD, 1);
    eqVis.visitTypeInsn(CHECKCAST, classname);
    eqVis.visitVarInsn(ASTORE, 1);

    /* -- don't deal with ret -- we don't want to check it in eq
    // If we have a ret, add a check for that
    if (!retType.equals(Type.VOID_TYPE)) {
      // Load the two objects
      eqVis.visitVarInsn(ALOAD, 0);
      eqVis.visitFieldInsn(GETFIELD, classname, "ret", retType.getDescriptor());

      eqVis.visitVarInsn(ALOAD, 1);
      eqVis.visitFieldInsn(GETFIELD, classname, "ret", retType.getDescriptor());

      // Do the compare
      cmpAndBranch(eqVis, retType, retBadLabel);
    }
    */

    // Now, do an equality check for the argtypes and their otherstuffs
    // If the type is 
    int idx = 0;
    for (Type t : argTypes) {
      // Load arg[i]
      int sort = t.getSort();

      // Load the two objects
      eqVis.visitVarInsn(ALOAD, 0);
      eqVis.visitFieldInsn(GETFIELD, classname, "arg" + idx, t.getDescriptor());

      eqVis.visitVarInsn(ALOAD, 1);
      eqVis.visitFieldInsn(GETFIELD, classname, "arg" + idx, t.getDescriptor());

      cmpAndBranch(eqVis, t, retBadLabel);

      idx++;
    }

    // Do base.equals()

    // If we got here, return true
    eqVis.visitInsn(ICONST_1);
    eqVis.visitJumpInsn(GOTO, retLabel);

    eqVis.visitLabel(retBadLabel);
    eqVis.visitInsn(ICONST_0);
    // Mark the return
    eqVis.visitLabel(retLabel);
    eqVis.visitInsn(IRETURN);

    eqVis.visitMaxs(6, 3);
    eqVis.visitEnd();
  }

  public static void cmpAndBranch(MethodVisitor mv, Type type, Label failLabel) {
    int sort = type.getSort();
    if (sort == Type.OBJECT) {
      // Do .equals
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);

      // If the result is equal to 0, return
      mv.visitJumpInsn(IFEQ, failLabel);
    } else if (sort == Type.ARRAY) {
      // Arrays.equals()
      Type elmType = type.getElementType();
      int arraySort = elmType.getSort();
      if (arraySort == Type.OBJECT) {
        // Call Arrays.equals(Object[], Object[])
        mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", "([Ljava/lang/Object;[Ljava/lang/Object;)Z", false);
      } else {
        // Call Arrays.equals(t, t)
        mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, type, type), false);
      }
      // If the result is equal to 0, return
      mv.visitJumpInsn(IFEQ, failLabel);
    } else if (sort == Type.DOUBLE) {
      mv.visitInsn(DCMPG);
      mv.visitJumpInsn(IFNE, failLabel);
    } else if (sort == Type.FLOAT) {
      mv.visitInsn(FCMPG);
      mv.visitJumpInsn(IFNE, failLabel);
    } else if (sort == Type.LONG) {
      mv.visitInsn(LCMP);
      mv.visitJumpInsn(IFNE, failLabel);
    } else {
      mv.visitJumpInsn(IF_ICMPNE, failLabel);
    }
  }

  public static String getRecordWrapperDesc(RecordEntry entry) {
    Type thisType = Type.getType("L" + entry.classname + ";");
    Type methodType = Type.getMethodType(entry.desc);
    Type retType = methodType.getReturnType();
    Type[] argTypes = methodType.getArgumentTypes();

    Type[] newArgTypes;
    if (entry.isStatic) {
      newArgTypes = argTypes;
    } else {
      newArgTypes = new Type[argTypes.length + 1];

      newArgTypes[0] = thisType;
      System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);
    }

    return Type.getMethodDescriptor(retType, newArgTypes);
  }

  private static void createRecordWrapper(ClassVisitor cv, String classname,
      Type retType, Type[] argTypes, RecordEntry entry, boolean isStatic) {

    Type[] thisArgTypes;
    int argOffs;

    if (isStatic) {
      thisArgTypes = argTypes;
      argOffs = 0;
    } else {
      thisArgTypes = new Type[argTypes.length+1];
      thisArgTypes[0] = Type.getType("L" + entry.classname + ";");
      // Copy the old args through
      System.arraycopy(argTypes, 0, thisArgTypes, 1, argTypes.length);
      argOffs = 1;
    }

    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC,
        RecordWrapperFcn,
        // Same descriptor as parent
        Type.getMethodDescriptor(retType, thisArgTypes),
        null,
        null);

    int maxLocal = calcTypeSizes(thisArgTypes);

    mv.visitCode();

    Label callAndExitLabel = new Label();
    Label returnLabel = new Label();

    // First, check if native wrapper is set
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "isInNativeWrapper", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
    // If so, return the result of calling our native method
    mv.visitJumpInsn(IFNE, callAndExitLabel);

    // Otherwise, record we're calling our native wrapper
    // Set that we're in a native method
    mv.visitInsn(ICONST_1);
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "setInNativeWrapper", Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE), false);

    // Call the native method
    // Load args for the native method
    loadArgs(mv, thisArgTypes, 0);
    if (isStatic) {
      mv.visitMethodInsn(INVOKESTATIC, entry.classname, entry.methodname, entry.desc, false);
    } else {
      mv.visitMethodInsn(INVOKEVIRTUAL, entry.classname, entry.methodname, entry.desc, false);
    }

    // Store the return position -- if there is one
    int retPos = maxLocal;
    if (!retType.equals(Type.VOID_TYPE)) {
      maxLocal += retType.getSize();
      mv.visitVarInsn(retType.getOpcode(ISTORE), retPos);
    }

    // Allocate our recordlogtype
    mv.visitTypeInsn(NEW, classname);

    // Dup it, so we have a version later
    mv.visitInsn(DUP);

    // Now, args
    loadArgs(mv, argTypes, argOffs);

    // Invoke init
    mv.visitMethodInsn(INVOKESPECIAL, classname, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, argTypes), false);

    // Now store the ret type (if not void)
    if (!retType.equals(Type.VOID_TYPE)) {
      mv.visitInsn(DUP);
      mv.visitVarInsn(retType.getOpcode(ILOAD), retPos);
      mv.visitMethodInsn(INVOKEVIRTUAL, classname, getSetterName("ret"), Type.getMethodDescriptor(Type.VOID_TYPE, retType), false);
    }

    // Now, save the syscall wrapper
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "saveRecordEntry", "(L" + RecordLogClass + ";)V", false);

    // Don't forget to Unset the native flag
    mv.visitInsn(ICONST_0);
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "setInNativeWrapper", Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE), false);

    // Then return the ret type
    if (!retType.equals(Type.VOID_TYPE)) {
      mv.visitVarInsn(retType.getOpcode(ILOAD), retPos);
    }
    mv.visitJumpInsn(GOTO, returnLabel);

    // Save it (-- pass as object to static method in RecordLogEntry)
    mv.visitLabel(callAndExitLabel);
    // Load this
    loadArgs(mv, thisArgTypes, 0);

    // Then call native method
    if (isStatic) {
      mv.visitMethodInsn(INVOKESTATIC, entry.classname, entry.methodname, entry.desc, false);
    } else {
      mv.visitMethodInsn(INVOKEVIRTUAL, entry.classname, entry.methodname, entry.desc, false);
    }
    mv.visitLabel(returnLabel);

    mv.visitInsn(retType.getOpcode(IRETURN));
    mv.visitMaxs(maxLocal+2, maxLocal+2);
    mv.visitEnd();
  }

  private static void createReplayWrapper(ClassVisitor cv, String classname,
      Type retType, Type[] argTypes, RecordEntry entry, boolean isStatic) {

    Type[] thisArgTypes;
    int argOffs;

    if (isStatic) {
      thisArgTypes = argTypes;
      argOffs = 0;
    } else {
      thisArgTypes = new Type[argTypes.length+1];
      thisArgTypes[0] = Type.getType("L" + entry.classname + ";");
      // Copy the old args through
      System.arraycopy(argTypes, 0, thisArgTypes, 1, argTypes.length);
      argOffs = 1;
    }

    MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_STATIC,
        ReplayWrapperFcn,
        // Same descriptor as parent
        Type.getMethodDescriptor(retType, thisArgTypes),
        null,
        null);

    int maxLocal = calcTypeSizes(thisArgTypes);

    mv.visitCode();

    Label callAndExitLabel = new Label();
    Label returnLabel = new Label();

    // First, check if native wrapper is set
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "isInNativeWrapper", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);
    // If so, return the result of calling our native method
    mv.visitJumpInsn(IFNE, callAndExitLabel);

    // Otherwise, record we're calling our native wrapper
    // Set that we're in a native method
    mv.visitInsn(ICONST_1);
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "setInNativeWrapper", Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE), false);

    // First create an argument wrapper
    // Then allocate our new wrapper
    mv.visitTypeInsn(NEW, classname);

    // Dup it, so we have a version later
    mv.visitInsn(DUP);

    // Now, args
    loadArgs(mv, argTypes, argOffs);

    // Invoke init
    mv.visitMethodInsn(INVOKESPECIAL, classname, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, argTypes), false);

    // Save our value to a local
    int wrapperLocal = maxLocal;
    // Its an object -- size 1
    maxLocal += Type.getType("L" + classname + ";").getSize();
    mv.visitVarInsn(ASTORE, wrapperLocal);
    // Immediately reload...
    mv.visitVarInsn(ALOAD, wrapperLocal);
    
    // Then pass that wrapper into the replay fcn
    //  -- The replay wrapper fcn will verify correctness
    Type recordEntryType = Type.getType("L" + RecordLogClass + ";");
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "replayEntry",
        Type.getMethodDescriptor(recordEntryType, recordEntryType), false);

    // If we have a ret
    if (!retType.equals(Type.VOID_TYPE)) {
      // Cast to a "this" type
      mv.visitTypeInsn(CHECKCAST, classname);

      // Got ret out of our struct
      mv.visitFieldInsn(GETFIELD, classname, "ret", retType.getDescriptor());
    } else {
      // Otherwise, just pop it
      mv.visitInsn(POP);
    }

    // Don't forget to Unset the native flag
    mv.visitInsn(ICONST_0);
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "setInNativeWrapper", Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE), false);
    mv.visitJumpInsn(GOTO, returnLabel);

    // Just forward to the native method
    mv.visitLabel(callAndExitLabel);
    loadArgs(mv, thisArgTypes, 0);
    // Then call the native method
    if (isStatic) {
      mv.visitMethodInsn(INVOKESTATIC, entry.classname, entry.methodname, entry.desc, false);
    } else {
      mv.visitMethodInsn(INVOKEVIRTUAL, entry.classname, entry.methodname, entry.desc, false);
    }

    // Then return the ret
    mv.visitLabel(returnLabel);
    mv.visitInsn(retType.getOpcode(IRETURN));

    mv.visitMaxs(maxLocal+2, maxLocal+2);
    mv.visitEnd();
  }

  private static int calcTypeSizes(Type[] types) {
    int ret = 0;
    for (Type t : types) {
      ret += t.getSize();
    }
    return ret;
  }

  private static int loadArgs(MethodVisitor mv, Type[] argTypes, int firstIdx) {
    int idx = firstIdx;
    for (Type t : argTypes) {
      mv.visitVarInsn(t.getOpcode(ILOAD), idx);
      idx += t.getSize();
    }
    return idx;
  }

  // Just force all our new method/class creations on visitEnd
  public void createAllRecordClasses(String sOutdir) {
    for (RecordEntry entry : nms.getEntries()) {
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
      CheckClassAdapter cca = new CheckClassAdapter(cw);
      createRecordClass(entry, cca);

      File outdir = new File(sOutdir);
      String oFileName = getOutputName(outdir.getPath(), entry.getLogClassname());

      File oFile = new File(oFileName);
      File parent = oFile.getParentFile();
      if (!parent.exists()) {
        parent.mkdirs();
      }

      System.err.println("  Saving Record: " + oFile);
      try (OutputStream os = new FileOutputStream(oFile)) {
        os.write(cw.toByteArray());
      } catch (IOException ex) {
        Util.error(ex);
      }
    }
  }

  /// TESTING ONLY
  private static final CommandLineOption<String> clOutdir =
    CommandLine.makeString("outdir", "./test_out/RecordTestInst",
        CommandLineOption.Kind.STABLE,
        "Output directroy for instrumented class files");

  private static final CommandLineOption<String> clNativeMethodFile =
    CommandLine.makeString("native-method-file", "native_methods.csv",
        CommandLineOption.Kind.STABLE,
        "File to read which Native methods should be analyzed for record/replay");

  public static void main(String[] args) {
    final CommandLine cl = new CommandLine("ClassRecordLogCreatorTest", "???");
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
    cl.add(clNativeMethodFile);
    cl.add(clOutdir);

    cl.apply(args);

    ClassRecordLogCreator crlc = new ClassRecordLogCreator(new NativeMethodSet(clNativeMethodFile.get()));
    crlc.createAllRecordClasses(clOutdir.get());
  }

  private static String getOutputName(String basedir, String classname) {
    return basedir + '/' + classNameToClassFile(classname);
  }

  private static String classNameToClassFile(String classname) {
    return classname.replace('.', '/') + ".class";
  }
}

