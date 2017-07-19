package org.ddevec.record.runtime;

import java.io.Serializable;

import org.ddevec.record.instr.ClassRecordLogCreator;

public class SyncRecordLogEntry extends RecordLogEntry implements Serializable {
  public static final transient int Opcode;

  static {
    Opcode = ClassRecordLogCreator.getNextOpcode("SyncEntry");
  }

  public long id;
  public int tid;

  public SyncRecordLogEntry(long id, int tid) {
    super(Opcode);
    this.id = id;
    this.tid = tid;
  }

  @Override
  public int hashCode() {
    int ret = super.hashCode();
    ret += id * 733;
    ret += tid * 1003;
    return ret;
  }

  @Override
  public boolean equals(Object orhs) {
    if (orhs == null) {
      return false;
    }
    if (orhs == this) {
      return true;
    }
    SyncRecordLogEntry rhs = (SyncRecordLogEntry)orhs;
    if (rhs == null) {
      return false;
    }

    return this.id == rhs.id &&
      this.tid == rhs.tid &&
      super.equals(rhs);
  }
}


