package org.ddevec.record.runtime;

import java.io.Serializable;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

import acme.util.Util;

public class RecordLogEntry implements Serializable {
  private static ThreadLocal<Boolean> inNativeWrapper =
      new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
          return Boolean.TRUE;
        }
      };

  private static ObjectOutputStream recorder;
  private static ObjectInputStream replayer;

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

  public static void initializeRecord() {
    String filename = "testRecordReplay.jobj";
    // Setup output
    try {
      recorder = new ObjectOutputStream(new FileOutputStream(filename));
    } catch (IOException ex) {
      Util.error(ex);
    }

    Util.message("Recording initialzied");
    
    setInNativeWrapper(false);
  }

  public static void initializeReplay() {
    String filename = "testRecordReplay.jobj";
    // Setup output
    try {
      replayer = new ObjectInputStream(new FileInputStream(filename));
    } catch (IOException ex) {
      Util.error(ex);
    }
    
    Util.message("Replay initialzied");
    setInNativeWrapper(false);
  }

  public static void saveRecordEntry(RecordLogEntry entry) {
    synchronized(recorder) {
      try {
        recorder.writeObject(entry);
      } catch (IOException ex) {
        Util.error(ex);
      }
    }
  }

  public static RecordLogEntry replayEntry(RecordLogEntry entry) {
    RecordLogEntry newEntry = null;
    synchronized(replayer) {
      try {
        newEntry = (RecordLogEntry)replayer.readObject();
      } catch (IOException ex) {
        Util.error(ex);
      } catch (ClassNotFoundException ex) {
        Util.error(ex);
      }
    }

    if (!entry.equals(newEntry)) {
      Util.error(new RecordReplayException("Unexpected call to replayEntry: " + entry));
    }

    return newEntry;
  }
}
