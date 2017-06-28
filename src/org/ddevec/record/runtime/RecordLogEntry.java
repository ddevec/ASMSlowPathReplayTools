package org.ddevec.record.runtime;

import java.io.Serializable;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.PrintStream;

import java.lang.reflect.Field;

public class RecordLogEntry implements Serializable {
  private static ThreadLocal<Boolean> inNativeWrapper =
      new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
          return Boolean.TRUE;
        }
      };

  private static ObjectOutputStream recorder;
  private static ObjectInputStream replayer;
  private static PrintStream debug;
  private static int logNumber = 0;

  long threadId;
  int opcode;

  public RecordLogEntry(int opcode) {
    this.threadId = Thread.currentThread().getId();
    this.opcode = opcode;
  }

  public static boolean isInNativeWrapper() {
    return inNativeWrapper.get();
  }

  public static void setInNativeWrapper(boolean val) {
    if (val) {
      inNativeWrapper.set(Boolean.TRUE);
    } else {
      inNativeWrapper.set(Boolean.FALSE);
    }
  }

  private static void initialize(String debugStreamName) {
    try {
    debug = new PrintStream(debugStreamName);
    } catch (FileNotFoundException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

  public static void initializeRecord() {
    String filename = "testRecordReplay.jobj";
    // Setup output
    try {
      recorder = new ObjectOutputStream(new FileOutputStream(filename));
    } catch (IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    }

    initialize("record_debug.txt");
    debug.println("Recording Initialzied");
    
    setInNativeWrapper(false);
  }

  public static void initializeReplay() {
    String filename = "testRecordReplay.jobj";
    // Setup output
    try {
      replayer = new ObjectInputStream(new FileInputStream(filename));
    } catch (IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
    
    initialize("replay_debug.txt");
    debug.println("Replay Initialzied");

    setInNativeWrapper(false);
  }

  public static void saveRecordEntry(RecordLogEntry entry) {
    assert inNativeWrapper.get() : "saveRecordEntry when Not in native wrapper?";
    //debug.println("Record Record Save: " + entry);
    //new Exception("StackTrace").printStackTrace(debug);
    synchronized(recorder) {
      try {
        dumpEntry(debug, entry);
        new Exception("StackTrace").printStackTrace(debug);
        recorder.writeObject(entry);
      } catch (IOException ex) {
        ex.printStackTrace(debug);
        System.exit(1);
      }
    }
  }

  public static RecordLogEntry replayEntry(RecordLogEntry entry) {
    assert inNativeWrapper.get() : "replayEntry when Not in native wrapper?";
    RecordLogEntry logEntry = null;
    //debug.println("Replay Record Fetch: " + entry);
    synchronized(replayer) {
      try {
        logEntry = (RecordLogEntry)replayer.readObject();
      } catch (IOException ex) {
        ex.printStackTrace(debug);
        System.exit(1);
      } catch (ClassNotFoundException ex) {
        ex.printStackTrace(debug);
        System.exit(1);
      }
    }

    debug.println("Debug entry: " + logNumber);
    dumpEntry(debug, logEntry);

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

  public void print() {
    print(System.out);
  }

  public void print(PrintStream out) {
    dumpEntry(out, this);
  }

  private static void dumpEntry(PrintStream out, RecordLogEntry entry) {
    Class c = entry.getClass();
    for (Field f : c.getFields()) {
      try {
        Object value = f.get(entry);
        if (value instanceof byte[]) {
          out.println("Field: " + f.getName() + ": Byte[]");
          for (byte b : (byte[])value) {
            out.println("  " + b);
          }
        } else {
          out.println("Field: " + f.getName() + ": " + value);
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
