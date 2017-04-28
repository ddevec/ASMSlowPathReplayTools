package org.ddevec.slowpath.runtime;

public class MisSpecException extends Exception {
    public static boolean slowFlag = false;

    private String info;

    public static void doMisSpec() {
        slowFlag = true;
    }

    public MisSpecException(String info) {
        this.info = info;
    }
}

