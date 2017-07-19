package org.ddevec.record.runtime;

import rr.tool.RR;

import java.io.Serializable;

import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.PrintStream;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class RecordLogEntry implements Serializable {
  public transient static final String DebugFilePropertyName = "org.ddevec.record.debugfile";
  public transient static final String RecordLogFilePropertyName = "org.ddevec.record.recordlogfile";

  public transient static LinkedList<RecordLogEntry> entryBuffer = new LinkedList<RecordLogEntry>();

  public final static class EnterLogging implements AutoCloseable {
    boolean oldValue;
    int tid;

    public EnterLogging(int tid) {
      this.tid = tid;
      this.oldValue = isInNativeWrapper(tid);
      setInNativeWrapper(tid, true);
    }

    public final boolean shouldLog() {
      return oldValue == false;
    }

    public final void close() {
      setInNativeWrapper(tid, oldValue);
    }
  }

  /*
  private static ThreadLocal<Boolean> inNativeWrapper =
      new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
          return Boolean.TRUE;
        }
      };
      */
  private static boolean inNativeWrapper[];

  private static ReentrantLock replayLock;
  private static Condition threadWaitCV;
  private static RecordLogEntry nextEntry;

  private static ObjectOutputStream recorder;
  private static ObjectInputStream replayer;
  private static PrintStream debug;
  private static int logNumber = 0;

  public byte threadId;
  public byte opcode;

  public RecordLogEntry(int opcode) {
    long tid = Thread.currentThread().getId();
    assert tid < 256 : "Thread tid too large?";
    this.threadId = (byte)Thread.currentThread().getId();
    this.opcode = (byte)opcode;
  }

  public static boolean isInNativeWrapper(int tid) {
    return inNativeWrapper[tid];
  }

  public boolean equals(Object orhs) {
    if (orhs == null) {
      return false;
    }
    if (!(orhs instanceof RecordLogEntry)) {
      return false;
    }
    if (orhs == this) {
      return true;
    }
    RecordLogEntry rhs = (RecordLogEntry)orhs;

    return rhs.threadId == this.threadId && rhs.opcode == this.opcode;
  }

  public int hashCode() {
    int ret = 0xFF8271;
    ret += threadId * 1003;
    ret += opcode * 997;
    return ret;
  }

  public static void setInNativeWrapper(int tid, boolean val) {
    inNativeWrapper[tid] = val;
  }

  private static String getRecordLogName() {
    return System.getProperty(RecordLogFilePropertyName, RR.recordLogOption.get());
  }

  private static void initialize(String debugStreamName) {
    try {
      debug = new PrintStream(debugStreamName);
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
      System.exit(1);
    }

    replayLock = new ReentrantLock();
    threadWaitCV = replayLock.newCondition();
  }

  public static void initializeRecord() {
    initialize(System.getProperty(DebugFilePropertyName, RR.recordDebugFileOption.get()));

    String filename = getRecordLogName();
    // Setup output
    try {
      recorder = new ObjectOutputStream(new FileOutputStream(filename));
    } catch (IOException ex) {
      ex.printStackTrace(debug);
      System.exit(1);
    }

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          recorder.flush();
          recorder.close();
          debug.flush();
          debug.close();
        } catch (Exception ex) {
          System.err.println("SHUTDOWN ERROR: " +  ex);
        }
      }
    });

    debug.println("Recording Initialzied");
    
    setInNativeWrapper(0, false);
  }

  public static void initializeReplay() {
    initialize(System.getProperty(DebugFilePropertyName, RR.recordDebugFileOption.get()));

    String filename = getRecordLogName();
    if (filename == null) {
      debug.println("record logfile not set!");
      System.exit(1);
    }
    // Setup output
    try {
      replayer = new ObjectInputStream(new FileInputStream(filename));
    } catch (IOException ex) {
      ex.printStackTrace(debug);
      System.exit(1);
    }

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        try {
          replayer.close();
          debug.flush();
          debug.close();
        } catch (Exception ex) {
          System.err.println("SHUTDOWN ERROR: " + ex);
        }
      }
    });

    debug.println("Replay Initialzied");

    setInNativeWrapper(0, false);
  }

  // NOTE: Assumes replayLock is locked
  public static RecordLogEntry readEntry() {
    RecordLogEntry ret = null;

    if (!entryBuffer.isEmpty()) {
      ret = entryBuffer.pollFirst();
    } else {
      try {
        ret = (RecordLogEntry)replayer.readObject();
      // At EOF we just return a null value -- nothing left
      } catch (EOFException ex) {
        ret = null;

      // These are errors -- we have to stop :(
      } catch (IOException ex) {
        ex.printStackTrace(debug);
        System.exit(1);
      } catch (ClassNotFoundException ex) {
        ex.printStackTrace(debug);
        System.exit(1);
      }
    }

    return ret;
  }

  public static RecordLogEntry findNext(int opcode) {
    replayLock.lock();
    try {
      for (RecordLogEntry rle : entryBuffer) {
        if (rle.opcode == opcode) {
          return rle;
        }
      }

      // Wasn't in the buffer... scan the log!
      while (true) {
        try {
          RecordLogEntry rle = (RecordLogEntry)replayer.readObject();

          entryBuffer.add(rle);

          if (rle.opcode == opcode) {
            return rle;
          }
        } catch (EOFException ex) {
          return null;
        } catch (IOException ex) {
          ex.printStackTrace(debug);
          System.exit(1);
        } catch (ClassNotFoundException ex) {
          ex.printStackTrace(debug);
          System.exit(1);
        }
      }
    } finally {
      replayLock.unlock();
    }
  }

  public static RecordLogEntry fetchNextEntry(long threadId) {
    RecordLogEntry ret = null;
    replayLock.lock();
    try {
      // If we're null, fetch a new one
      if (nextEntry == null) {
        nextEntry = (RecordLogEntry)replayer.readObject();
      }

      while (nextEntry.threadId != threadId) {
        try {
          threadWaitCV.await();
        } catch (InterruptedException ex) {
          // Ignore me
        }
      }

      ret = nextEntry;

      try {
        nextEntry = (RecordLogEntry)replayer.readObject();
      } catch (EOFException ex) {
        nextEntry = null;
      }

      if (nextEntry != null && nextEntry.threadId != threadId) {
        threadWaitCV.signalAll();
      }
    } catch (IOException ex) {
      ex.printStackTrace(debug);
      System.exit(1);
    } catch (ClassNotFoundException ex) {
      ex.printStackTrace(debug);
      System.exit(1);
    } finally {
      replayLock.unlock();
    }

    return ret;
  }

  public static void saveRecordEntry(RecordLogEntry entry) {
    //debug.println("Record Record Save: " + entry);
    //new Exception("StackTrace").printStackTrace(debug);
    synchronized(recorder) {
      try {
        /*
        debug.println("RecordEntry: " + logNumber);
        dumpEntry(debug, entry, 1);
        new Exception("StackTrace").printStackTrace(debug);
        */
        logNumber++;
        recorder.writeObject(entry);
      } catch (IOException ex) {
        ex.printStackTrace(debug);
        System.exit(1);
      }
    }
  }

  public static RecordLogEntry replayEntry(RecordLogEntry entry) {
    RecordLogEntry logEntry = null;

    // Okay -- what mechanism do I need here?
    // First, get the next entry
    logEntry = fetchNextEntry(entry.threadId);

    /*
    debug.println("Debug entry: " + logNumber);
    dumpEntry(debug, logEntry, 1);
    */

    // If their threadId != my threadId -- wait, maybe someone else will come
    // along...

    if (!entry.equals(logEntry)) {
      debug.println("Unexpected call to replayEntry: " + entry);
      dumpEntry(debug, entry);
      debug.println("  Expected call to replayEntry: " + logEntry);
      dumpEntry(debug, logEntry);

      // Dump a diff if they are of the same class
      if (logEntry.getClass().equals(entry.getClass())) {
        dumpDiff(logEntry, entry);
      }

      Exception ex = new Exception("Record/Replay Exception");
      ex.printStackTrace(debug);
      System.exit(1);
    }
    logNumber++;

    return logEntry;
  }

  public static void recordSyncOperation(RecordLogEntry entry) {
    saveRecordEntry(entry);
  }

  public static void replaySyncOperation(RecordLogEntry entry) {
    replayEntry(entry);
  }

  public static void replayInitThread(int tid) {
    if (inNativeWrapper == null) {
      int numTids = rr.tool.RR.maxTidOption.get();
      inNativeWrapper = new boolean[numTids];
    }
    inNativeWrapper[tid] = false;
  }

  public static void recordInitThread(int tid) {
    if (inNativeWrapper == null) {
      int numTids = rr.tool.RR.maxTidOption.get();
      inNativeWrapper = new boolean[numTids];
    }
    inNativeWrapper[tid] = false;
  }

  public void print(PrintStream out) {
    dumpEntry(out, this, 0);
  }

  public void print(PrintStream out, int tabCount) {
    dumpEntry(out, this, tabCount);
  }

  private static void dumpEntry(PrintStream out, RecordLogEntry entry) {
    dumpEntry(out, entry, 0);
  }

  private static void dumpEntry(PrintStream out, RecordLogEntry entry, int tabCount) {
    Class c = entry.getClass();
    String tabstr = "";
    for (int i = 0; i < tabCount; i++) {
      tabstr += "\t";
    }

    for (Field f : c.getFields()) {
      // Skip transient feilds
      if (Modifier.isTransient(f.getModifiers())) {
        continue;
      }
      try {
        Object value = f.get(entry);
        if (value instanceof byte[]) {
          out.println(tabstr + "Field: " + f.getName() + ": Byte[]");
          for (byte b : (byte[])value) {
            out.println(tabstr + "  " + b);
          }
        } else {
          out.println(tabstr + "Field: " + f.getName() + ": " + value);
        }
      } catch (IllegalAccessException ex) {
        ex.printStackTrace(out);
        System.exit(1);
      }
    }
  }

  private static void dumpDiff(RecordLogEntry lhs, RecordLogEntry rhs) {
    Class c = lhs.getClass();
    debug.println("Entry Number: " + logNumber);

    for (Field f : c.getFields()) {
      // Skip transient feilds
      if (Modifier.isTransient(f.getModifiers())) {
        continue;
      }

      try {
        Object lhsValue = f.get(lhs);
        Object rhsValue = f.get(rhs);

        if (lhsValue instanceof byte[]) {
          assert rhsValue instanceof byte[] : "Unexpected types?";
          byte[] lhsArray = (byte[])lhsValue;
          byte[] rhsArray = (byte[])rhsValue;
          boolean haveDiff = false;
          if (lhsArray.length != rhsArray.length) {
            haveDiff = true;
          } else {
            for (int i = 0; i < Math.min(lhsArray.length, rhsArray.length); i++) {
              if (lhsArray[i] != rhsArray[i]) {
                haveDiff = true;
              }
            }
          }

          if (haveDiff) {
            debug.println("Field: " + f.getName() + ": Byte[]");
            for (int i = 0; i < Math.max(lhsArray.length, rhsArray.length); i++) {
              String lhsStr = "X";
              String rhsStr = "X";
              if (i < rhsArray.length) {
                rhsStr = Byte.toString(rhsArray[i]);
              }
              if (i < lhsArray.length) {
                lhsStr = Byte.toString(lhsArray[i]);
              }

              if (!lhsStr.equals(rhsStr)) {
                debug.println("  [" + i + "] " + lhsStr + " != " + rhsStr);
              }
            }
          }
        } else {
          if (!lhsValue.equals(rhsValue)) {
            debug.println("Field: " + f.getName() + ": " + lhsValue + " != " + rhsValue);
          }
        }
      } catch (IllegalAccessException ex) {
        ex.printStackTrace(debug);
        System.exit(1);
      }
    }
  }
}
