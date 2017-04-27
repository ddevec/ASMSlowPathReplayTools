package org.ddevec.slowpath.test;

class TestArray {
  static int size = 50;

  public static void main(String[] args) {
    Object []objs = new Object[size];

    for (int i = 0; i < size; i++) {
      objs[i] = new Integer(i);
    }
  }
}


