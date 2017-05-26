/**
 *  Responsible for doing RoadRunner level instrumentation.
 *
 *  Mostly copies from RR source -- hopefully it works?
 */

package org.ddevec.slowpath.utils;

public class InstrumentationFilter {
  public static boolean shouldInstrument(String classname) {
    /*
    if (classname.startsWith("java/lang")) {
      return false;
    }
    if (classname.startsWith("java/security")) {
      return false;
    }
    if (classname.startsWith("java/io")) {
      return false;
    }
    */
    if (classname.startsWith("java")) {
      return false;
    }
    if (classname.startsWith("javax")) {
      return false;
    }
    if (classname.startsWith("sun")) {
      return false;
    }
    if (classname.startsWith("jdk")) {
      return false;
    }
    if (classname.startsWith("org/ddevec/slowpath/runtime")) {
      return false;
    }

    return true;
  }
}
