package org.ddevec.slowpath.fork;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkProc {
  static {
    System.loadLibrary("java_fork");
  }

  private enum Status {
    SLEEPING,
    RUNNING,
    DEAD
  }

  private Status status;
  private int pid;
  private File pipeFile;

  private ForkProc(int pid, Status status, File pipeFile) {
    this.pid = pid;
    this.status = status;
    this.pipeFile = pipeFile;
  }
  
  private static native int nativeDoFork();
  private static native boolean nativeKill(int forkId, int signal);
  private static native int createPipe(String pipeName, int mode);
  private static native int nativeGetPid();

  public static void main(String[] args) {
    System.err.println("Pre-fork");
    ForkProc fork = doForkAndSuspend();
    System.err.println("post-fork");
    if (fork == null) {
      System.err.println("Child");
    } else {
      System.err.println("Parent");
      fork.resume();
    }
  }

  static AtomicInteger pipeNum = new AtomicInteger(0);

  public static ForkProc doForkAndSuspend() {
    ForkProc child = null;

    File pipeFile = new File("/tmp/java_suspend_pipe" + nativeGetPid() + "." + pipeNum.getAndIncrement());
    assert !pipeFile.exists() : "Trying to create a pipe file that already exists";

    System.err.println("Makepipe: " + pipeFile);
    int rc = createPipe(pipeFile.getAbsolutePath(), 0600);
    if (rc != 0) {
      // Blerg!
      System.err.println("ERROR: Failed to make named pipe: " + rc + "!\n");
      System.exit(1);
    }

    pipeFile.deleteOnExit();

    int pid = nativeDoFork();

    // Child -- suspend
    if (pid == 0) {
      try (FileReader fr = new FileReader(pipeFile)) {
        char[] cbuf = new char[1];
        // blocks until other end writes
        fr.read(cbuf);
      } catch (IOException ex) {
        ex.printStackTrace();
        System.exit(1);
      }
    // Parent -- setup fork proc, and return
    } else {

      child = new ForkProc(pid, Status.SLEEPING, pipeFile);
    }

    return child;
  }

  public static ForkProc doFork() {
    int pid = nativeDoFork();
    ForkProc child = null;

    // Child -- suspend
    if (pid != 0) {
      child = new ForkProc(pid, Status.RUNNING, null);
    }

    return child;
  }

  public int getPid() {
    return pid;
  }

  public synchronized void resume() {
    if (status == Status.RUNNING) {
      return;
    }

    status = Status.RUNNING;

    try (FileWriter fw = new FileWriter(pipeFile)) {
      char[] data = { '\0' };
      fw.write(data);
    } catch (IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

  public void kill() {
    nativeKill(pid, 9);
  }
}
