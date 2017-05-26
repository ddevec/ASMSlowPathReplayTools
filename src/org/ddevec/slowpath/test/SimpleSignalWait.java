package org.ddevec.slowpath.test;

import rr.tool.RR;

import tools.fasttrack.FastTrackTool;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

public class SimpleSignalWait {
    public static int valuea;
    public static boolean t1_done;
    public static Lock lk = new ReentrantLock(); 
    public static Condition cond = lk.newCondition();

    public static void main(String[] args) {

        valuea = 0;
        t1_done = false;

        Thread t1 = new Thread() {
            public void run() {
                valuea = valuea + 5;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) { }
                lk.lock();
                try {
                    t1_done = true;
                    cond.signal();
                } finally {
                    lk.unlock();
                }
            }
        };

        Thread t2 = new Thread() {
            public void run() {
                lk.lock();
                try {
                    if (!t1_done) {
                        try {
                            cond.await();
                        } catch (InterruptedException ex) { }
                    }
                } finally {
                    lk.unlock();
                }
                valuea = valuea + 7;
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

        System.err.println(valuea);
    }
}

