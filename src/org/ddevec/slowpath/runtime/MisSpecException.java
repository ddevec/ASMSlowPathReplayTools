package org.ddevec.slowpath.runtime;

public class MisSpecException extends Exception {
    private String info;
    public MisSpecException(String info) {
        this.info = info;
    }
}

