package org.ddevec.slowpath.test;

import rr.tool.RR;

import tools.fasttrack.FastTrackTool;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class SimpleBarrierRace {
    public static int valueb;
    public static int valuea;
    public static CyclicBarrier barrier = new CyclicBarrier(2);

    public static void main(String[] args) {

        valueb = 0;
        valuea = 0;

        Thread t1 = new Thread() {
            public void run() {
                valuea = valuea + 5;
                try {
                    barrier.await();
                } catch (BrokenBarrierException ex) {
                    System.err.println(ex);
                } catch (InterruptedException ex) {
                    System.err.println(ex);
                }
                valueb = valueb + 5;
            }
        };

        Thread t2 = new Thread() {
            public void run() {
                valuea = valuea + 7;
                try {
                    barrier.await();
                } catch (InterruptedException ex) {
                    System.err.println(ex);
                } catch (BrokenBarrierException ex) {
                    System.err.println(ex);
                }
                valueb = valueb + 7;
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

        System.err.println(valueb);
        System.err.println(valuea);
    }
}

