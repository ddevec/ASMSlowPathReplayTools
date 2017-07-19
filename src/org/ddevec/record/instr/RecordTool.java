/******************************************************************************

Copyright (c) 2010, Cormac Flanagan (University of California, Santa Cruz)
                    and Stephen Freund (Williams College) 

All rights reserved.  

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

 * Neither the names of the University of California, Santa Cruz
      and Williams College nor the names of its contributors may be
      used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 ******************************************************************************/

package org.ddevec.record.instr;

import rr.annotations.Abbrev;
import rr.event.AccessEvent;
import rr.event.AcquireEvent;
import rr.event.InterruptEvent;
import rr.event.InterruptedEvent;
import rr.event.NewThreadEvent;
import rr.event.JoinEvent;
import rr.event.NotifyEvent;
import rr.event.ReleaseEvent;
import rr.event.SleepEvent;
import rr.event.StartEvent;
import rr.event.WaitEvent;
import rr.state.ShadowThread;
import rr.tool.Tool;
import acme.util.StringMatchResult;
import acme.util.option.CommandLine;
import acme.util.option.CommandLineOption;
import acme.util.Util;

import rr.meta.ClassInfo;
import rr.meta.FieldInfo;
import rr.meta.InstrumentationFilter;
import rr.meta.MetaDataInfoMaps;
import rr.meta.MethodInfo;
import rr.meta.OperationInfo;
import rr.meta.ToolSpecificInstrumentationFilter;

import org.ddevec.record.runtime.RecordLogEntry;
import org.ddevec.record.runtime.SyncRecordLogEntry;


/**
 * Like LastTool, but keeps ShadowVar set to thread, so that
 * the fast path in the Instrumenter is triggered.
 * 
 * Use this only for performance tests.
 */

@Abbrev("Record")
final public class RecordTool extends Tool {

  // the count of the current thread operation -- start at 0
  private static long currentId = 0;
  // the tid of the current running tid -- invalid at start
  private static int currentTid = -1;

	@Override
	public String toString() {
		return "Record";
	}


  /*
  public static int opcodeAcquire;
  public static int opcodeRelease;
  public static int opcodeTestAcquire;
  public static int opcodeTestRelease;
  public static int opcodePreWait;
  public static int opcodePostWait;
  public static int opcodePreNotify;
  public static int opcodePostNotify;
  public static int opcodePreJoin;
  public static int opcodePostJoin;
  public static int opcodePreStart;
  public static int opcodePostStart;
  public static int opcodeInterrupted;
  public static int opcodeInterrupt;

  // Initial values for opcodes
  static {
    opcodeAcquire = ClassRecordLogCreator.getNextId();
    opcodeRelease = ClassRecordLogCreator.getNextId();
    opcodeTestAcquire = ClassRecordLogCreator.getNextId();
    opcodeTestRelease = ClassRecordLogCreator.getNextId();
    opcodePreWait = ClassRecordLogCreator.getNextId();
    opcodePostWait = ClassRecordLogCreator.getNextId();
    opcodePreNotify = ClassRecordLogCreator.getNextId();
    opcodePostNotify = ClassRecordLogCreator.getNextId();
    opcodePreJoin = ClassRecordLogCreator.getNextId();
    opcodePostJoin = ClassRecordLogCreator.getNextId();
    opcodePreStart = ClassRecordLogCreator.getNextId();
    opcodePostStart = ClassRecordLogCreator.getNextId();
    opcodeInterrupted = ClassRecordLogCreator.getNextId();
    opcodeInterrupt = ClassRecordLogCreator.getNextId();
  }
  */

	public RecordTool(String name, Tool next, CommandLine commandLine) {
		super(name, next, commandLine);

    if (InstrumentationFilter.ftRecordOnly.get()) {
      InstrumentationFilter.addToolSpecificInstrumentationFilter(new ToolSpecificInstrumentationFilter() {
        public boolean shouldInstrument(ClassInfo rrClass, boolean slowPath) { return true; }

        // We don't require instr on field accesses
        public boolean shouldInstrument(FieldInfo field, boolean slowPath) {
          return false;
        }

        public boolean shouldInstrument(MethodInfo rrMethod, boolean slowPath) { return true; }
        public boolean shouldInstrument(OperationInfo rrOp, boolean slowPath) {
          // Don't instrument any RW ops
          return false;
        }

        public boolean shouldInstrumentMonitor(MethodInfo rrMethod, int bci,
          boolean slowPath) {
          return true;
        }
      });
    }
    /*
    System.err.println("OpcodeAcquire: " + opcodeAcquire);
    System.err.println("OpcodeRelease: " + opcodeRelease);
    System.err.println("OpcodeTestAcquire: " + opcodeTestAcquire);
    System.err.println("OpcodeTestRelease: " + opcodeTestRelease);
    System.err.println("OpcodePreWait: " + opcodePreWait);
    System.err.println("OpcodePostWait: " + opcodePostWait);
    System.err.println("OpcodePreNotify: " + opcodePreNotify);
    System.err.println("OpcodePostNotify: " + opcodePostNotify);
    System.err.println("OpcodePreJoin: " + opcodePreJoin);
    System.err.println("OpcodePostJoin: " + opcodePostJoin);
    System.err.println("OpcodePreStart: " + opcodePreStart);
    System.err.println("OpcodePostStart: " + opcodePostStart);
    System.err.println("OpcodeInterrupted: " + opcodeInterrupted);
    System.err.println("OpcodeInterrupt: " + opcodeInterrupt);
    */
	}

  // NEW PLAN
  // Only 1 "sync" operation.  
  //    Have a "current" sync thread.
  //    Have a "current" sync count.
  //
  // Get:
  //    if (current_count > max_count):
  //      Read ahead until next sync thread -- get count
  //      set log_thread_id, max_count
  //      signal waiters
  //    else
  //      while (current_thread_id != log_thread_id):
  //        Wait until next packet
  //
  //    If next entry is not "sync thread id", wait
  //    Sync operation inserted w/ id to stop

  // Lock -- modifies shared data
  private static synchronized void writeNextEntry(int tid) {

    // Common case, 
    if (tid == currentTid) {
      currentId++;
    // Initialize
    } else if (currentTid == -1) {
      currentId++;
      currentTid = tid;
    // Need to create new entry
    } else {
      // Save off our thread switch to start
      SyncRecordLogEntry rle = new SyncRecordLogEntry(currentId, currentTid);
      RecordLogEntry.saveRecordEntry(rle);

      currentId++;
      currentTid = tid;
    }
  }

  private abstract class EventHandler {
    public abstract void event();
  }

  private final static void doRecordEntry(int tid, EventHandler handler) {
    try (RecordLogEntry.EnterLogging el = new RecordLogEntry.EnterLogging(tid)) {
      if (el.shouldLog()) {
        writeNextEntry(tid);
        handler.event();
      }
    }
  }

  private abstract class EventHandlerBool {
    public abstract boolean event();
  }

  private final static boolean doRecordEntryBool(int tid, EventHandlerBool handler) {
    try (RecordLogEntry.EnterLogging el = new RecordLogEntry.EnterLogging(tid)) {
      if (el.shouldLog()) {
        writeNextEntry(tid);
        return handler.event();
      }
    }
    return true;
  }

	@Override
	public void acquire(AcquireEvent ae) {
    doRecordEntry(ae.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.acquire(ae);
          }
        });
  }

	@Override
	public void release(ReleaseEvent re) {
    doRecordEntry(re.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.release(re);
          }
        });
  }

	@Override
	public boolean testAcquire(AcquireEvent ae) {
    return doRecordEntryBool(ae.getThread().getTid(),
        new EventHandlerBool() {
          @Override
          public boolean event() {
            return next.testAcquire(ae);
          }
        });
  }

	@Override
	public boolean testRelease(ReleaseEvent re) {
    return doRecordEntryBool(re.getThread().getTid(),
        new EventHandlerBool() {
          @Override
          public boolean event() {
            return next.testRelease(re);
          }
        });
  }

	@Override
	public void preWait(WaitEvent we) {
    doRecordEntry(we.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.preWait(we);
          }
        });
  }

	@Override
	public void postWait(WaitEvent we) {
    doRecordEntry(we.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.postWait(we);
          }
        });
  }

	@Override
	public void preNotify(NotifyEvent ne) {
    doRecordEntry(ne.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.preNotify(ne);
          }
        });
  }

	@Override
	public void postNotify(NotifyEvent ne) {
    doRecordEntry(ne.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.postNotify(ne);
          }
        });
  }

	@Override
	public void preJoin(JoinEvent je) {
    doRecordEntry(je.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.preJoin(je);
          }
        });
  }

	@Override
	public void postJoin(JoinEvent je) {
    doRecordEntry(je.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.postJoin(je);
          }
        });
  }

	@Override
	public void preStart(StartEvent se) {
    doRecordEntry(se.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.preStart(se);
          }
        });
  }

	@Override
	public void postStart(StartEvent se) {
    doRecordEntry(se.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.postStart(se);
          }
        });
  }

  @Override
  public void create(NewThreadEvent e) {
    super.create(e);
    RecordLogEntry.recordInitThread(e.getThread().getTid());
  }
	
	@Override
	public void interrupted(InterruptedEvent e) {
    doRecordEntry(e.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.interrupted(e);
          }
        });
  }

	@Override
	public void preInterrupt(InterruptEvent me) {
    doRecordEntry(me.getThread().getTid(),
        new EventHandler() {
          @Override
          public void event() {
            next.preInterrupt(me);
          }
        });
  }
}

