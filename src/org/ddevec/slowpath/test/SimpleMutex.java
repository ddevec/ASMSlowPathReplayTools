package org.ddevec.slowpath.test;

import rr.tool.RR;

import tools.fasttrack.FastTrackTool;


public class SimpleMutex {
    public static int value;
    public static Object mutexa = new Object();

    public static void main(String[] args) {

        value = 0;

        Thread t1 = new Thread() {
            public void run() {
                synchronized (mutexa) {
                    value = value + 5;
                }
            }
        };

        Thread t2 = new Thread() {
            public void run() {
                synchronized (mutexa) {
                    value = value + 7;
                }
            }
        };

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ex) {
            System.err.println("Got exception: " + ex);
            System.exit(1);
        }

        System.err.println(value);
    }
}

