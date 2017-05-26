/**
 *  Responsible for doing RoadRunner level instrumentation.
 *
 *  Mostly copies from RR source -- hopefully it works?
 */

package org.ddevec.slowpath.instr;

import rr.org.objectweb.asm.ClassReader;
import rr.org.objectweb.asm.ClassVisitor;
import rr.org.objectweb.asm.ClassWriter;
import rr.org.objectweb.asm.Opcodes;

import rr.instrument.classes.AbstractOrphanFixer;
import rr.instrument.classes.ArrayAllocSiteTracker;
import rr.instrument.classes.ClassInitNotifier;
import rr.instrument.classes.CloneFixer;
import rr.instrument.classes.GuardStateInserter;
import rr.instrument.classes.InterfaceThunkInserter;
import rr.instrument.classes.InterruptFixer;
import rr.instrument.classes.JVMVersionNumberFixer;
import rr.instrument.classes.NativeMethodSanityChecker;
import rr.instrument.classes.SyncAndMethodThunkInserter;
import rr.instrument.classes.ThreadDataThunkInserter;
import rr.instrument.classes.ToolSpecificClassVisitorFactory;
import rr.instrument.noinst.NoInstSanityChecker;

import rr.meta.ClassInfo;
import rr.meta.MetaDataInfoMaps;

// Okay, lets just give something similar a main and be done with it.
public class RRInst {
    public static ClassWriter doVisit(ClassReader cr) {
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES |
                ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = cw;

        // If the class is abstract...
        if ((cr.getAccess() & Opcodes.ACC_INTERFACE) == 0) {
            String classname = cr.getClassName();
            ClassInfo currentClass = MetaDataInfoMaps.getClass(classname);

            ClassVisitor cv1 = new NativeMethodSanityChecker(cw);
            cv1 = new GuardStateInserter(cv1);
            cv1 = new InterruptFixer(cv1);
            cv1 = new CloneFixer(cv1);
            //cv1 = new ClassInitNotifier(currentClass, loader, cv1);
            cv1 = new ArrayAllocSiteTracker(currentClass, cv1);
            cv1 = new AbstractOrphanFixer(cv1);
            ClassVisitor cv2 = new ThreadDataThunkInserter(cv1, true);
            ClassVisitor cv2forThunks = new ThreadDataThunkInserter(cv1, false);
            cv = new SyncAndMethodThunkInserter(cv2, cv2forThunks);
        }

        // FIXME: Tool specific visitors :(
        //cv = insertToolSpecificVisitors(cv);

        cv = new JVMVersionNumberFixer(cv);

        cr.accept(cv, ClassReader.EXPAND_FRAMES);

        return cw;
    }
}

