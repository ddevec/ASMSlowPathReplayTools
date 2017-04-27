package org.ddevec.slowpath.test;

import org.ddevec.slowpath.runtime.MisSpecException;

public class Class1 {
  public static void main(String[] args) throws MisSpecException {
    Class1 c1 = new Class1();

    c1.printFoo();
    c1.printBar();
    c1.copyFoo();
    c1.printBar();
    try {
        if (System.out != null) {
            System.out.println("Hello world");
        } else {
            System.err.println("No stdout?");
        }
    } catch (NullPointerException ex) {
        System.err.println("Null?\n");
    } finally {
        System.out.println("Dun");
    }
  }

  private int foo;
  private int bar;
  public Class1() {
    foo = 5;
    bar = 0;
  }

  public void copyFoo() throws MisSpecException {
    bar = foo;
    throw new MisSpecException("test");
  }

  public void printFoo() {
    System.out.println("foo is: " + foo);
  }

  public void printBar() {
    System.out.println("bar is: " + bar);
  }
}

