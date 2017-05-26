package org.ddevec.slowpath.test;

import rr.tool.RR;

import tools.fasttrack.FastTrackTool;


public class SimpleForkJoin {
    public static int value;

    public static void main(String[] args) {

        value = 0;

        Thread t1 = new Thread() {
            public void run() {
                value = value + 5;
            }
        };

        Thread t2 = new Thread() {
            public void run() {
                value = value + 7;
            }
        };


        try {
            t1.start();
            t1.join();
        } catch (InterruptedException ex) {
            System.err.println("Got exception: " + ex);
            System.exit(1);
        }

        try {
            t2.start();
            t2.join();
        } catch (InterruptedException ex) {
            System.err.println("Got exception: " + ex);
            System.exit(1);
        }

        System.err.println(value);
    }
}

