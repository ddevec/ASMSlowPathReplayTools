package org.ddevec.record.instr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import acme.util.Util;

public class NativeMethodSet {

  private HashSet<RecordEntry> entries = new HashSet<RecordEntry>();

  public NativeMethodSet(String filename) {
    try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
      String line;
      while ((line = br.readLine()) != null) {
        RecordEntry re = RecordEntry.parse(line);
        if (re != null) {
          if (entries.contains(re)) {
            System.err.println("WARNING: entries already contains: " + re);
          } else {
            entries.add(re);
          }
        }
      }
    } catch (IOException ex) {
      Util.error(ex);
    }

    System.err.println("Loaded: " + entries.size() + " Records");
  }

  public Set<RecordEntry> getEntries() {
    return Collections.unmodifiableSet(entries);
  }
}
