package org.ddevec.slowpath.instr;

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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Finds new instructions not followed by invocations of \<init\>.
 */

public class CallArgMover extends MethodVisitor implements Opcodes {
  MethodVisitor next = null;
  String classname;

  private class DangerousCallInfo {
    public int newInsn;
    public int dangerInsn;

    public DangerousCallInfo(int newInsn, int dangerInsn) {
      this.dangerInsn = dangerInsn;
      this.newInsn = newInsn;
    }
  }

  public CallArgMover(int api, String classname,
      int access, String name, String desc, String signature, String[] exceptions,
      MethodVisitor next) {
    super(api, new MethodNode(api, access, name, desc, signature, exceptions));
    this.classname = classname;
    this.next = next;
  }

  // visitEnd -- woot woot
  @Override
  public void visitEnd() {
    MethodNode mn = (MethodNode)mv;
    System.err.println("CallArgMover: Visit method: " + classname + "." + mn.name);

    // Skip init -- its special
    if (!mn.name.equals("<init>")) {

      // First get list of dangerous calls
      List<DangerousCallInfo> calls = findDangerousCalls(classname);

      // Now, create graph of motion dependencies --
      //   load -> call.next
      //   newlocal -> store
      //   call -> store
      //   store -> new
      //   call_deps -> call (as appropriate)
      DirectedGraph<AbstractInsnNode, DefaultEdge> depGraph =
        new DefaultDirectedGraph<AbstractInsnNode, DefaultEdge>(DefaultEdge.class);

      Set<AbstractInsnNode> toRemove = new HashSet<AbstractInsnNode>();
      Set<AbstractInsnNode> toAdd = new HashSet<AbstractInsnNode>();

      // Now, move any deps of the calls above the "new" insn
      for (DangerousCallInfo dci : calls) {
        int newInsn = dci.newInsn;
        int callInsn = dci.dangerInsn;
        System.err.println("  Found dangerous call at: " + callInsn + 
            " with new: " + newInsn);

        AbstractInsnNode newInsnNode = mn.instructions.get(newInsn);
        MethodInsnNode callInsnNode = (MethodInsnNode)mn.instructions.get(callInsn);
        AbstractInsnNode afterCallNode = callInsnNode.getNext();

        // Add nodes for the important insns in the method
        depGraph.addVertex(newInsnNode);
        depGraph.addVertex(callInsnNode);
        depGraph.addVertex(afterCallNode);
        toRemove.add(callInsnNode);
        toAdd.add(callInsnNode);

        // Add nodes for the NEW insns in the method
        Type varType = Type.getType(callInsnNode.desc).getReturnType();
        NewLocalNode nln = new NewLocalNode(varType);
        AbstractInsnNode storeNode = new DelayedStoreNode(varType, ()-> nln.getIndex());
        AbstractInsnNode loadNode = new DelayedLoadNode(varType, ()-> nln.getIndex());

        depGraph.addVertex(nln);
        depGraph.addVertex(storeNode);
        depGraph.addVertex(loadNode);
        toAdd.add(nln);
        toAdd.add(storeNode);
        toAdd.add(loadNode);

        // Now, add edges for the nodes in the graph
        depGraph.addEdge(loadNode, afterCallNode);
        depGraph.addEdge(nln, storeNode);
        depGraph.addEdge(callInsnNode, storeNode);
        depGraph.addEdge(storeNode, newInsnNode);

        // Add deps for callInsn to depGraph
        List<AbstractInsnNode> addedDeps = addDeps(callInsn, depGraph);
        toRemove.addAll(addedDeps);
        toAdd.addAll(addedDeps);

        /*
        for (int toMove : callDeps) {
          // Now, move below newInsn
          AbstractInsnNode toMoveNode = mn.instructions.get(toMove);
          mn.instructions.remove(toMoveNode);
          mn.instructions.insertBefore(newInsnNode, toMoveNode);
        }

        // Now, after callInsnNode, add a local, and save the ret to it
        Type varType = Type.getType(callInsnNode.desc).getReturnType();
        mn.instructions.insert(callInsnNode, nln);
        mn.instructions.insert(nln, new DelayedStoreNode(varType, 
              ()-> nln.getIndex()));

        // THEN, before afterCallNode load said local
        mn.instructions.insertBefore(afterCallNode, new DelayedLoadNode(varType,
              ()-> nln.getIndex()));
          */
      }

      // Now, remove all nodes in the toRemove set
      //FIXME: Debugging
      /*
      toRemove.forEach((AbstractInsnNode nd)-> mn.instructions.remove(nd));
      CycleDetector<AbstractInsnNode, DefaultEdge> cd =
        new CycleDetector<AbstractInsnNode, DefaultEdge>(depGraph);
      assert !cd.detectCycles() : "Have cycle in Insn ordering???";

      // Then, do a reverse topological visit of the graph:
      TopologicalOrderIterator<AbstractInsnNode, DefaultEdge> toi = new
        TopologicalOrderIterator<AbstractInsnNode, DefaultEdge>(depGraph);

      //   If node->prev in toAdd: instructions.insertBefore(node, prev)
      while (toi.hasNext()) {
        AbstractInsnNode nd = toi.next();
        System.err.println("TopoVisit: " + nd);
      }
      */

      /*
      if (calls.size() > 0) {
        System.err.println(mn.name + " -- Instructions:");
        for (ListIterator<AbstractInsnNode> iter = mn.instructions.iterator(); iter.hasNext();) {
          AbstractInsnNode nd = iter.next();
          System.err.println("  " + nd);
        }
      }
      */
    }

    if (next != null) {
      mn.accept(next);
    }
  }

  private List<DangerousCallInfo> findDangerousCalls(String classname) {
    MethodNode mn = (MethodNode)mv;
    ArrayList<DangerousCallInfo> dangerList = new ArrayList<DangerousCallInfo>();

    BasicBlockAnalyzer<BasicValue> bbAnalyzer =
      new BasicBlockAnalyzer<BasicValue>(new BasicInterpreter());

    try {
      bbAnalyzer.analyze(classname, mn);
    } catch (AnalyzerException ex) {
      System.err.println("ERROR: Couldn't analyze " + classname + "." +
          mn.name);
      System.err.println("Exception: " + ex);
      ex.printStackTrace();
      return null;
    }

    int[] leaders = bbAnalyzer.getLeaders();

    ArrayList<Integer> newInsns = new ArrayList<Integer>();
    InsnList il = mn.instructions;
    int addCount = 0;
    int latestNew = -1;
    for (int i = 0; i < il.size(); i++) {
      AbstractInsnNode insn = il.get(i);
      if (insn.getOpcode() == Opcodes.NEW) {
        System.err.println("FOUND NEW: "+ i);
        newInsns.add(i);
        latestNew = i;
      } else if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
        MethodInsnNode mni = (MethodInsnNode)insn;
        if (mni.name.equals("<init>")) {
          if (newInsns.size() == 0) {
            System.err.println("    ERROR: <init> without NEW???");
          } else {
            System.err.println("FOUND INIT: " + i);
            // Assert that the NEW is in the same BB as the <init>...
            System.err.println("  leaders[" + i + "]: " + leaders[i]);
            System.err.println("  leaders[top (" + newInsns.get(newInsns.size()-1)
                +")]: " +
                leaders[newInsns.get(newInsns.size()-1)]);
            assert leaders[i] == leaders[newInsns.get(newInsns.size()-1)] : 
              "New is not on same BB as init";
            
            if (addCount != 0) {
              assert latestNew >= 0;
              dangerList.add(new DangerousCallInfo(latestNew, i));
              System.err.println("Adding call: " +
                  mni.owner + "." + mni.name + " at " + i);
            }
            newInsns.remove(newInsns.size()-1);
            if (newInsns.size() > 0) {
              latestNew = newInsns.get(newInsns.size()-1);
            } else {
              latestNew = -1;
            }
            addCount = 0;
          }
        }

        if (newInsns.size() > 0) {
          addCount++;
          dangerList.add(new DangerousCallInfo(latestNew, i));
        }
      }
    }

    return dangerList;
  }

  private List<AbstractInsnNode> addDeps(int insn,
      DirectedGraph<AbstractInsnNode, DefaultEdge> depGraph) {
    MethodNode mn = (MethodNode)mv;
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
      ex.printStackTrace();
      return null;
    }

    Value[] variables = analyzer.getVariables();
    DefUseFrame[] frames = analyzer.getDefUseFrames();

    // Now, calc closure of instructions...
    Map<Value, AbstractInsnNode> useClosure =
      new HashMap<Value, AbstractInsnNode>();

    ArrayList<AbstractInsnNode> depList = new ArrayList<AbstractInsnNode>();

    int[] leaders = bbAnalyzer.getLeaders();

    //System.err.println("DUSEARCH: Have method: " + name);
    //System.err.println("DUSEARCH: Have insn: " + insn);
    //System.err.println("DUSEARCH: Have leader: " + leaders[insn]);
    //System.err.println("DUSEARCH: Have USES.size(): " +
    //    frames[insn].getUses().size());

    AbstractInsnNode curNode = mn.instructions.get(insn);
    for (Value use : frames[insn].getUses()) {
      //System.err.println("DUSEARCH: init USE: " + use);
      useClosure.put(use, curNode);
    }

    int earliest = leaders[insn];
    if (earliest < 0) {
      earliest = 0;
    }

    for (int i = insn; i >= earliest; i--) {
      AbstractInsnNode ain = mn.instructions.get(i);

      if (ain instanceof LabelNode ||
          ain instanceof LineNumberNode) {
        continue;
      }
      if (ain instanceof FrameNode) {
        System.err.println("Warning: FrameNode: " + ain);
        continue;
      }

      boolean added = false;
      // Check if insn[i] defines a use
      // If so, add to depList
      for (Value def : frames[i].getDefinitions()) {
        AbstractInsnNode useNode = useClosure.get(def);
        if (useNode != null) {
          // Add edge from def to use
          depGraph.addVertex(ain);
          depGraph.addEdge(ain, useNode);
          depList.add(ain);
          frames[i].getUses().forEach((Value use)-> useClosure.put(use, ain));
          // Don't need to add multiple times
          break;
        }
      }
    }

    return depList;
  }
}

