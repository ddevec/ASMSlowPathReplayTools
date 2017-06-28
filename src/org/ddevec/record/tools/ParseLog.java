package org.ddevec.record.tools;

import org.ddevec.record.runtime.RecordLogEntry;

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

import acme.util.Util;

/**
 *  Parses log files and prints their contents
 */
public class ParseLog {
  public static void main(String[] args) {
    // Parse command line options
    if (args.length < 1) {
      System.err.println("Unexpected argumants! Usage: ParseLog <logfile>");
      System.exit(1);
    }

    String filename = args[0];

    // Open the log file
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
      // Read objects from it
      while (true) {
        // Cast them to RecordLogEntry
        RecordLogEntry entry = (RecordLogEntry)ois.readObject();
        
        // Print them (using reflection dump garbage)
        entry.print();
      }
    } catch (EOFException ex) {
      // all good.
    } catch (IOException ex) {
      Util.error(ex);
    } catch (ClassNotFoundException ex) {
      Util.error(ex);
    }
  }
}


