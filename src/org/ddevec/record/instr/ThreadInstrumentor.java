package org.ddevec.record.instr;

import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.MethodVisitor;
import rr.org.objectweb.asm.Opcodes;
import rr.org.objectweb.asm.Type;
import rr.org.objectweb.asm.Label;
import rr.org.objectweb.asm.commons.JSRInlinerAdapter;
import rr.org.objectweb.asm.util.CheckClassAdapter;

import rr.loader.MetaDataBuilder;
import rr.meta.ClassInfo;
import rr.meta.InstrumentationFilter;
import rr.meta.MetaDataInfoMaps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import acme.util.Util;

/**
 * Instruments common thread operations.
 *
 * Scan all function calls -- If any of these calls happen, add a replay
 * ordering constraint
 */

public class ThreadInstrumentor extends ClassVisitor implements Opcodes {
  private static final String RecordFcnName = "recordSyncOperation";
  private static final String ReplayFcnName = "replaySyncOperation";

  private static final String ReplayInitThrdFcn = "replayInitThread";
  private static final String RecordInitThrdFcn = "recordInitThread";

  private static final String RecordLogClass = ClassRecordLogCreator.RecordLogClass;

  private static class IdPair {
    public int startId;
    public int stopId;

    public IdPair(int startId, int stopId) {
      this.startId = startId;
      this.stopId = stopId;
    }
  }

  private static class ShimInfo {
    public String shimName;
    public int access;
    public String name;
    public String desc;
    public String signature;
    public String[] exceptions;

    public ShimInfo(String shimName, int access, String name, String desc, String signature, String[] exceptions) {
      this.shimName = shimName;
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.signature = signature;
      this.exceptions = exceptions;
    }
  }

  public static final Map<RecordEntry, IdPair> instrPoints = initInstrPoints();
  public static final int LockId = ClassRecordLogCreator.getNextId();

  private String classname;
  private String methodName;
  private String methodDesc;
  private boolean classIsRunnable;

  private ArrayList<ShimInfo> syncShims = new ArrayList<ShimInfo>();
  private boolean isReplay;
  
  private static Map<RecordEntry, IdPair> initInstrPoints() {
    HashMap<RecordEntry, IdPair> tmp = new HashMap<RecordEntry, IdPair>();
    tmp.put(new RecordEntry("java/lang/Thread", "start", "()V", false), new IdPair(ClassRecordLogCreator.getNextId(), ClassRecordLogCreator.getNextId()));
    tmp.put(new RecordEntry("java/lang/Thread", "join", "()V", false), new IdPair(ClassRecordLogCreator.getNextId(), ClassRecordLogCreator.getNextId()));
    tmp.put(new RecordEntry("java/util/concurrent/CyclicBarrier", "await", "()V", false), new IdPair(ClassRecordLogCreator.getNextId(), ClassRecordLogCreator.getNextId()));
    tmp.put(new RecordEntry("java/lang/Object", "wait", "()V", false), new IdPair(ClassRecordLogCreator.getNextId(), ClassRecordLogCreator.getNextId()));
    return Collections.unmodifiableMap(tmp);
  }

  private class ThreadInstrumentingMethodVisitor extends MethodVisitor {
    private boolean isSync;
    public ThreadInstrumentingMethodVisitor(MethodVisitor mv) {
      super(ASM5, mv);
    }
    
    @Override
    public void visitCode() {
      super.visitCode();

      // If run() method of runnable
      if (classIsRunnable && methodName.equals("run") && methodDesc.equals("()V")) {
        super.visitMethodInsn(INVOKESTATIC, RecordLogClass,
            (isReplay) ? ReplayInitThrdFcn : RecordInitThrdFcn,
            "()V", false);
      }
    }

    private RecordEntry findRecordMatch(String owner, String name, String desc, boolean isStatic) {
      ClassInfo ci = MetaDataInfoMaps.getClass(owner);
      if (ci == null || ci.getName() == null) {
        Util.message("WARNING(ThreadInstrumentor): ClassInfo Error: " + owner);
        RecordEntry re = new RecordEntry(ci.getName(), name, desc, isStatic);
        if (instrPoints.containsKey(re)) {
          return re;
        }
        return null;
      }

      while (!ci.getName().equals("java/lang/Object")) {
        RecordEntry re = new RecordEntry(ci.getName(), name, desc, isStatic);
        Util.message("checking re: " + re);
        if (instrPoints.containsKey(re)) {
          return re;
        }

        ci = ci.getSuperClass();
        if (ci == null || ci.getName() == null) {
          Util.message("WARNING(ThreadInstrumentor): ClassInfo Error: " + owner);
          return null;
        }
      }

      RecordEntry re = new RecordEntry(ci.getName(), name, desc, isStatic);
      Util.message("final checking re: " + re);
      if (instrPoints.containsKey(re)) {
        return re;
      }

      return null;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      boolean isStatic = (opcode == INVOKESTATIC);
      // Get entryId from this RecordEntry
      /*
      RecordEntry re = new RecordEntry(owner, name, desc, isStatic);
      if (instrPoints.containsKey(re))
      */
      RecordEntry re = findRecordMatch(owner, name, desc, isStatic);
      if (re != null) {
        IdPair idPair = instrPoints.get(re);
        // Call our shim method...
        // Our shim method is -- defined somewhere?  and does something?
        // -- We need to gererate a shim class?
        //      Or perhaps we just pass in an ID (loaded at instrument-time?)
        //  UGH -- I guess we just do the whole call thing
        if (isReplay) {
          createReplayWrapper(mv, opcode, owner, name, desc, itf, re, idPair);
        } else {
          createRecordWrapper(mv, opcode, owner, name, desc, itf, re, idPair);
        }
      } else {
        // Just do the super thing
        super.visitMethodInsn(opcode, owner, name, desc, itf);
      }
    }
    
    @Override
    public void visitInsn(int opcode) {
      if (opcode == MONITORENTER) {
        if (isReplay) {
          // Don't get the lock first -- we may have to wait!
          doVisit(mv, LockId, ReplayFcnName);

          // We probably don't actually need to grab the lock -- we will just in
          // case though
          super.visitInsn(opcode);
        } else {
          // First, get the lock
          super.visitInsn(opcode);

          // Now, record that we got it
          doVisit(mv, LockId, RecordFcnName);
        }
      } else if (opcode == MONITOREXIT) {
        // Record just before we release the lock
        doVisit(mv, LockId, (isReplay) ? ReplayFcnName : RecordFcnName);
        super.visitInsn(opcode);
      } else {
        // If its not monitor enter/exit ignore
        super.visitInsn(opcode);
      }
    }

    @Override public void visitMaxs(int maxStack, int maxLocals) {
      super.visitMaxs(maxStack+2, maxLocals);
    }

    private void createReplayWrapper(MethodVisitor mv, int opcode, String owner, String name, String desc, boolean itf,
        RecordEntry re, IdPair idPair) {
      doWrapper(mv, opcode, owner, name, desc, itf, re, idPair, ReplayFcnName);
    }

    private void createRecordWrapper(MethodVisitor mv, int opcode, String owner, String name, String desc, boolean itf,
        RecordEntry re, IdPair idPair) {
      doWrapper(mv, opcode, owner, name, desc, itf, re, idPair, RecordFcnName);
    }


    private void doWrapper(MethodVisitor mv, int opcode, String owner, String name, String desc, boolean itf,
        RecordEntry re, IdPair idPair, String fcnName) {
      doVisit(mv, idPair.startId, fcnName);

      //  -- Do call
      mv.visitMethodInsn(opcode, owner, name, desc, itf);

      doVisit(mv, idPair.stopId, fcnName);
    }
  }

  public ThreadInstrumentor(ClassVisitor cv, boolean isReplay) {
    super(ASM5, cv);
    this.isReplay = isReplay;
    this.classIsRunnable = false;
  }

  @Override
  public void visit(int version, int access, String name, String signature,
      String superName, String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    this.classname = name;

    for (String iface : interfaces) {
      if (iface.equals("java/lang/Runnable")) {
        classIsRunnable = true;
      }
    }
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc,
      String signature, String[] exceptions) {
    MethodVisitor mv;

    ClassInfo info = MetaDataInfoMaps.getClass(classname);
    if (!InstrumentationFilter.shouldInstrument(info, true)) {
      return super.visitMethod(access, name, desc, signature, exceptions);
    }

    // If the method is syncrhonized, make a shim method.
    if ((access & ACC_SYNCHRONIZED) != 0) {
      // Oh boy, we need a shim
      String shimName = "__ddevecShim_" + name;
      int shimAccess = access & ~ACC_SYNCHRONIZED;
      syncShims.add(new ShimInfo(shimName, shimAccess, name, desc, signature, exceptions));
      mv = super.visitMethod(access, shimName, desc, signature, exceptions);

      mv = new ThreadInstrumentingMethodVisitor(mv);
    } else {
      // Otherwise, visit the method
      mv = super.visitMethod(access, name, desc, signature, exceptions);

      mv = new ThreadInstrumentingMethodVisitor(mv);
    }

    mv = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
    methodName = name;
    methodDesc = desc;

    return mv;
  }

  @Override
  public void visitEnd() {
    for (ShimInfo inf : syncShims) {
      createShimMethod(inf);
    }
  }

  private static String recordReplayFcn(boolean isReplay) {
    return isReplay ? ReplayFcnName : RecordFcnName;
  }

  private void createShimMethod(ShimInfo inf) {
    MethodVisitor mv = super.visitMethod(inf.access, inf.name, inf.desc, inf.signature, inf.exceptions);

    mv.visitCode();

    // Call pre-lock
    doVisit(mv, LockId, recordReplayFcn(isReplay));

    // Now, load our argumetns on to the stack
    Type methodType = Type.getMethodType(inf.desc);

    Type[] argTypes = methodType.getArgumentTypes();
    Type retType = methodType.getReturnType();
    boolean isStatic = (inf.access & ACC_STATIC) != 0;
    boolean isInterface = (inf.access & ACC_INTERFACE) != 0;

    // Load up the arg types
    int index = 0;
    int maxArgs = 0;

    // Handle "this" if appropriate
    if (!isStatic) {
      mv.visitVarInsn(ALOAD, 0);
      // sizeof (object) = 1
      maxArgs += 1;
      index += 1;
    }

    // Handle args...
    for (Type argType : argTypes) {
      mv.visitVarInsn(argType.getOpcode(ILOAD), index);
      index += argType.getSize();
      maxArgs += argType.getSize();
    }
    
    // Prep for the call
    int opcode = (isStatic)    ? INVOKESTATIC : 
                 (isInterface) ? INVOKEINTERFACE : INVOKEVIRTUAL;
    // Call method
    mv.visitMethodInsn(opcode, classname, inf.shimName, inf.desc, opcode == INVOKEINTERFACE);

    // Call pre-unlock
    doVisit(mv, LockId, recordReplayFcn(isReplay));

    // Do return
    mv.visitInsn(retType.getOpcode(IRETURN));
    mv.visitMaxs(index+2, maxArgs+2);
    mv.visitEnd();
  }

  private static void doVisit(MethodVisitor mv, int id, String calledFcn) {
    Label nativeAbort = new Label();
    checkAndSetInNative(mv, nativeAbort);
    // First, construct RecordLogEntry for this thing (Using my ID)
    initRecordEntry(mv, id);
    // Call the RecordLogEntry function to save this entry
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, calledFcn, "(L" + RecordLogClass + ";)V", false);
    clearInNative(mv);
    mv.visitLabel(nativeAbort);
  }

  private static void initRecordEntry(MethodVisitor mv, int id) {
    // Allocate new RecordLogEntry
    mv.visitTypeInsn(NEW, RecordLogClass);

    mv.visitInsn(DUP);
    // Load constant opcode (entryId)
    mv.visitLdcInsn(new Integer(id));
    // Run constructor <init>
    mv.visitMethodInsn(INVOKESPECIAL, RecordLogClass, "<init>", "(I)V", false);
  }

  private static void checkAndSetInNative(MethodVisitor mv, Label nativeAbort) {
    // Check if we're in native code -- if so jump to abort
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "isInNativeWrapper", Type.getMethodDescriptor(Type.BOOLEAN_TYPE), false);

    // If so, return the result of calling our native method
    mv.visitJumpInsn(IFNE, nativeAbort);

    // Set that we're in a native method
    mv.visitInsn(ICONST_1);
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "setInNativeWrapper", Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE), false);
  }

  private static void clearInNative(MethodVisitor mv) {
    // Set that we're done with the native method
    mv.visitInsn(ICONST_0);
    mv.visitMethodInsn(INVOKESTATIC, RecordLogClass, "setInNativeWrapper", Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE), false);
  }
}

