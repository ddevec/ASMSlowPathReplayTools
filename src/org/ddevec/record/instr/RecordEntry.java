package org.ddevec.record.instr;

import java.io.Serializable;

public class RecordEntry implements Serializable {
  public String classname;
  public String methodname;
  public String desc;
  public boolean isStatic;

  public RecordEntry(String classname, String methodname, String desc,
      boolean isStatic) {
    this.classname = classname;
    this.methodname = methodname;
    this.desc = desc;
    this.isStatic = isStatic;
  }

  public static RecordEntry parse(String commaSep) {
    System.err.println("Parsing line: " + commaSep);
    commaSep = commaSep.trim();

    // Ignore empty line
    if (commaSep.startsWith("#")) {
      return null;
    }

    String[] elms = commaSep.split(",");

    if (elms.length != 4) {
      return null;
    }

    String classname = elms[0].trim();
    String methodname = elms[1].trim();
    String desc = elms[2].trim();
    boolean isStatic = Boolean.parseBoolean(elms[3].trim());

    RecordEntry ret = new RecordEntry(classname, methodname, desc, isStatic);
    System.err.println("Got entry: " + ret);
    return ret;
  }

  @Override
  public String toString() {
    return "RecordEntry(" + classname + ", " + methodname + ", " + desc + ")";
  }

  @Override
  public int hashCode() {
    int ret = classname.hashCode() * 21;
    ret += methodname.hashCode() * 993;
    ret += desc.hashCode() * 1003;
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
    if (!(orhs instanceof RecordEntry)) {
      return false;
    }

    RecordEntry rhs = (RecordEntry)orhs;

    return rhs.classname.equals(classname) &&
      rhs.methodname.equals(methodname) &&
      rhs.desc.equals(desc);
  }

  public String getTuple() {
    return classname + "," + methodname + "," + desc + "," + isStatic;
  }

  public String getLogClassname() {
    String[] classbase_set = classname.split("[\\./]");
    String classbase = classbase_set[classbase_set.length-1];
    String classPath = "org/ddevec/record/loaders/";
    /*
    for (int i = 0; i < classbase_set.length -1; i++) {
      String s = classbase_set[i];
      classPath += s + "/";
    }
    */
    return classPath + "__ddevecRecord_NativeMethodArgs_" + classbase + "_" + methodname;
  }
}
