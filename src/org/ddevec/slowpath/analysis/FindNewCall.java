package org.ddevec.slowpath.analysis;

import org.ddevec.utils.ArrayUtils;

import org.ddevec.slowpath.analysis.basicblock.BasicBlockAnalyzer;
import org.ddevec.slowpath.analysis.defuse.DefUseAnalyzer;
import org.ddevec.slowpath.analysis.defuse.DefUseFrame;
import org.ddevec.slowpath.analysis.defuse.DefUseInterpreter;
import org.ddevec.slowpath.analysis.defuse.Variable;
import org.ddevec.slowpath.analysis.defuse.Value;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Finds new instructions not followed by invocations of \<init\>.
 */

public class FindNewCall extends ClassVisitor implements Opcodes {
  ClassVisitor next = null;

  public FindNewCall() {
    super(ASM5, new ClassNode(ASM5));
  }

  public FindNewCall(int api, ClassVisitor cv) {
    super(api, new ClassNode(api));

    next = cv;
  }

  @Override
  public void visitEnd() {
    ClassNode cn = (ClassNode)cv;

    System.err.println("Visit class: " + cn.name);

    for (MethodNode mn : cn.methods) {
      // Skip init -- its special
      if (mn.name.equals("<init>")) {
        continue;
      }

      System.err.println("  Method: " + mn.name);

      int[] calls = findDangerousCalls(cn.name, mn);


      // Now, optimize above the call
      for (int insnId : calls) {
        System.err.println("    WARNING: Have call before <init>: " +
            ((MethodInsnNode)mn.instructions.get(insnId)).name);

        int[] deps = getDeps(cn.name, mn, insnId);

        System.err.println("      Depends on:");
        for (int instNum : deps) {
              System.err.println("          :" +
                  ((MethodInsnNode)mn.instructions.get(instNum)).name);
        }
      }
    }

    if (next != null) {
      cn.accept(next);
    }
  }

  private int[] findDangerousCalls(String classname, MethodNode mn) {
    ArrayList<Integer> dangerList = new ArrayList<Integer>();

    BasicBlockAnalyzer<BasicValue> bbAnalyzer =
      new BasicBlockAnalyzer<BasicValue>(new BasicInterpreter());

    try {
      bbAnalyzer.analyze(classname, mn);
    } catch (AnalyzerException ex) {
      System.err.println("ERROR: Couldn't analyze " + classname + "." +
          mn.name);
      System.err.println("Exception: " + ex);
      return null;
    }

    int[] leaders = bbAnalyzer.getLeaders();

    ArrayList<Integer> insns = new ArrayList<Integer>();
    InsnList il = mn.instructions;
    for (int i = 0; i < il.size(); i++) {
      AbstractInsnNode insn = il.get(i);
      if (insn.getOpcode() == Opcodes.NEW) {
        insns.add(i);
      } else if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
        MethodInsnNode mni = (MethodInsnNode)insn;
        if (mni.name.equals("<init>")) {
          if (insns.size() == 0) {
            System.err.println("    ERROR: <init> without NEW???");
          } else {
            // Assert that the NEW is in the same BB as the <init>...
            assert leaders[i] == leaders[insns.get(insns.size()-1)] : 
              "New is not on same BB as init";
            
            insns.remove(insns.size()-1);
          }
        }

        if (insns.size() > 0) {
          dangerList.add(i);
        }
      }
    }

    return ArrayUtils.toArray(dangerList, new int[0]);
  }

  private int[] getDeps(String classname, MethodNode mn, int insn) {
    // Now we do def-use analysis, and find transitive set of deps -- oh boy
    // First find all def-uses
    DefUseInterpreter interpreter = new DefUseInterpreter();
    BasicBlockAnalyzer<Value> bbAnalyzer = new BasicBlockAnalyzer<Value>(interpreter);
    DefUseAnalyzer analyzer = new DefUseAnalyzer(bbAnalyzer, interpreter);
    try {
      analyzer.analyze(classname, mn);
    } catch (AnalyzerException ex) {
      System.err.println("ERROR: Couldn't analyze " + classname + "." +
          mn.name);
      System.err.println("Exception: " + ex);
      return null;
    }

    Variable[] variables = analyzer.getVariables();
    DefUseFrame[] frames = analyzer.getDefUseFrames();

    // Now, calc closure of instructions...
    Set<Variable> useClosure = new HashSet<Variable>();
    ArrayList<Integer> depList = new ArrayList<Integer>();
    int[] leaders = bbAnalyzer.getLeaders();

    for (int i = 0; i < mn.instructions.size(); i++) {
      Set<Variable> uses = frames[i].getUses();
      if (uses.size() > 0) {
        AbstractInsnNode ain = mn.instructions.get(i);
        if (ain instanceof MethodInsnNode) {
          MethodInsnNode min = (MethodInsnNode)ain;
          System.err.println("Instr: " + i + " -- call: " + min.name + 
              " has " + uses.size() + " uses");
        } else {
          System.err.println("Instr: " + i + " -- " + ain + 
              " has " + uses.size() + " uses");
        }
        for (Variable v : uses) {
          System.err.println("   Use: " + v);
        }
      }
    }


    System.err.println("DUSEARCH: Have method: " + mn.name);
    System.err.println("DUSEARCH: Have insn: " + insn);
    System.err.println("DUSEARCH: Have leader: " + leaders[insn]);
    System.err.println("DUSEARCH: Have USES.size(): " +
        frames[insn].getUses().size());

    for (Variable use : frames[insn].getUses()) {
      System.err.println("DUSEARCH: init USE: " + use);
      useClosure.add(use);
    }

    for (int i = insn; i >= leaders[insn]; i--) {
      boolean added = false;
      // Check if insn[i] defines a use
      // If so, add to depList
      for (Variable def : frames[i].getDefinitions()) {
        if (useClosure.contains(def)) {
          System.err.println("DUSEARCH: found DEF Match: " + def);
          depList.add(i);
          for (Variable use : frames[insn].getUses()) {
            useClosure.add(use);
          }
          // Don't need to add multiple times
          break;
        }
      }
    }

    return ArrayUtils.toArray(depList, new int[0]);
  }
}

