/*
 * [New BSD License]
 * Copyright (c) 2011, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.tx.locking.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.protocol.TaDOM3Plus;
import org.brackit.server.tx.locking.protocol.URIX;
import org.brackit.server.tx.locking.table.TreeLockNameFactory;
import org.brackit.server.tx.locking.util.DefaultLockName;
import org.brackit.server.tx.log.LogException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class GenericLockServiceTest {
	private final static String SERVICE_NAME = "LOCKBUFFER_TEST_SERVICE";

	private final static int MAX_LOCKS = 1000;

	private final static int PERFORMANCE_BLOCK_SIZE = 100;

	private final static int PERFORMANCE_MAX_LOCKS = 400000;

	private final static int PERFORMANCE_MONITOR_INTERVALL = 100000;

	private final static int PERFORMANCE_LOCKS_PER_REQUEST = 4;

	private final static int MAX_TRANSACTIONS = 3;

	private TxMgr taMgr;

	private GenericLockServiceImpl<URIX.Mode> lockService;

	private Tx t1;

	private Tx t2;

	private Tx t3;

	// private class LockRequestJob extends TransactionJob {
	// private int[][] locks;
	// private int lockMode;
	// private LockBuffer lockBuffer;
	//
	// private LockRequestJob(Tx transaction, LockBuffer lockBuffer,
	// int[][] locks, int lockMode)
	// {
	// super(transaction);
	// this.locks = locks;
	// this.lockMode = lockMode;
	// this.lockBuffer = lockBuffer;
	// }
	//
	// @Override
	// public void doWork() throws Throwable
	// {
	// lockBuffer.request(transaction, locks, LockType.GENERIC,
	// LockClass.COMMIT_DURATION, lockMode, false);
	// }
	// }

	private final static SimpleLockNameFactory buildLockNames(int... resources) {
		return new SimpleLockNameFactory(resources);
	}

	private static class SimpleLockNameFactory implements TreeLockNameFactory {
		private final int[] resources;

		public SimpleLockNameFactory(int... resources) {
			super();
			this.resources = resources;
		}

		@Override
		public LockName getLockName(int level) {
			return new DefaultLockName(resources[level]);
		}

		@Override
		public int getTargetLevel() {
			return resources.length - 1;
		}
	}

	@Test
	public void lockCount() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4, 5);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4, 5, 6, 7,
				8);

		lockService.request(t1, lockNames1, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count A1", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("ta lock count A2", 5, t1.getLockCB().get(lockService)
				.getLockServiceCB().getCount());

		lockService.request(t1, lockNames2, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count B1", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("ta lock count B2", 5, t1.getLockCB().get(lockService)
				.getLockServiceCB().getCount());

		lockService.release(t1, lockNames2);

		assertEquals("ta lock count C1", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("ta lock count C2", 5, t1.getLockCB().get(lockService)
				.getLockServiceCB().getCount());

		t1.commit();

		assertEquals("ta lock count after commit", 0, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void testReRequest() throws TxException {
		lockService.request(t1, buildLockNames(1), LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);
		lockService.request(t1, buildLockNames(1), LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);

		assertEquals("ta lock count", 1, t1.getLockCB().get(lockService)
				.getLocks().size());

		t1.commit();

		assertEquals("ta lock count after commit", 0, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void testBlockUnblock() throws TxException {
		boolean t1Failed = false;
		boolean t2Failed = false;
		lockService.request(t1, buildLockNames(1), LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);
		t2.leave();

		new Thread() {
			public void run() {
				try {
					t2.join();
					lockService.request(t2, buildLockNames(1),
							LockClass.COMMIT_DURATION, URIX.Mode.X, false);
				} catch (TxException e) {
					fail("ta 2 failed: " + e.getMessage());
				}
			}
		}.start();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		t1.commit();
	}

	@Test
	public void testBlockUnblockContinually() throws TxException {
		boolean t1Failed = false;
		boolean t2Failed = false;
		t2.leave();

		new Thread() {
			public void run() {
				try {
					t2.join();
					for (int i = 0; i < 100000; i++) {
						lockService.request(t2, buildLockNames(1),
								LockClass.COMMIT_DURATION, URIX.Mode.X, false);
						t2.getLockCB().getLockServiceClients()[0]
								.freeResources();
					}
				} catch (TxException e) {
					fail("ta 2 failed: " + e.getMessage());
				}
			}
		}.start();

		for (int i = 0; i < 100000; i++) {
			lockService.request(t1, buildLockNames(1),
					LockClass.COMMIT_DURATION, URIX.Mode.R, false);
			t1.getLockCB().getLockServiceClients()[0].freeResources();
		}

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		t1.commit();
	}

	@Test
	public void testDeadlock() throws TxException {
		boolean t1Failed = false;
		boolean t2Failed = false;
		lockService.request(t1, buildLockNames(1), LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);
		lockService.request(t2, buildLockNames(1), LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);
		t2.leave();

		new Thread() {
			public void run() {
				try {
					t2.join();
					lockService.request(t2, buildLockNames(1),
							LockClass.COMMIT_DURATION, URIX.Mode.X, false);
				} catch (TxException e) {
					try {
						t2.rollback();
					} catch (TxException e1) {
						fail("Rollback of killed ta 2 failed");
					}
				}
			}
		}.start();

		try {
			lockService.request(t1, buildLockNames(1),
					LockClass.COMMIT_DURATION, URIX.Mode.X, false);
		} catch (TxException e) {

		}

		assertTrue("One transaction was killed to resolve the deadlock", t1
				.getState() == TxState.ABORTED
				^ t2.getState() == TxState.ABORTED);

		assertEquals("ta lock count", 1, t1.getLockCB().get(lockService)
				.getLocks().size());

		t1.commit();

		assertEquals("ta lock count after commit", 0, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void testDeadlock2() throws TxException {
		boolean t1Failed = false;
		boolean t2Failed = false;
		lockService.request(t1, buildLockNames(1).getLockName(0),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);
		lockService.request(t2, buildLockNames(1).getLockName(0),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);
		t2.leave();

		new Thread() {
			public void run() {
				try {
					t2.join();
					lockService.request(t2, buildLockNames(1).getLockName(0),
							LockClass.COMMIT_DURATION, URIX.Mode.X, false);
				} catch (TxException e) {
					try {
						t2.rollback();
					} catch (TxException e1) {
						fail("Rollback of killed ta 2 failed");
					}
				}
			}
		}.start();

		try {
			lockService.request(t1, buildLockNames(1).getLockName(0),
					LockClass.COMMIT_DURATION, URIX.Mode.X, false);
		} catch (TxException e) {

		}

		assertTrue("One transaction was killed to resolve the deadlock", t1
				.getState() == TxState.ABORTED
				^ t2.getState() == TxState.ABORTED);

		assertEquals("ta lock count", 1, t1.getLockCB().get(lockService)
				.getLocks().size());

		t1.commit();

		assertEquals("ta lock count after commit", 0, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	// @Test
	// public void testDowngrade2TA() throws BrackitException
	// {
	// lockService.request(buildLockNames(1), LockClass.COMMIT_DURATION,
	// URIX.Mode.U, false);
	//
	// Thread me = Thread.currentThread();
	// LockRequestJob concurrentRequest = new LockRequestJob(t2, lockService,
	// buildLockNames(1), URIX.Mode.U);
	// concurrentRequest.setSupervisor(me);
	// scheduler.schedule(concurrentRequest, false);
	//
	// try
	// {
	// Thread.sleep(2000);
	// }
	// catch (InterruptedException e)
	// {
	// // ignore
	// }
	//
	// lockService.request(buildLockNames(1), LockClass.COMMIT_DURATION,
	// URIX.Mode.R, false);
	//
	// List<XTClock> locks = ctx.getLockCB().get(lockService).getLocks();
	// assertEquals("ta lock count", 1, locks.size());
	// ctx.commit();
	//
	// scheduler.join(concurrentRequest);
	//
	// assertEquals("ta1 lock count after commit", 0,
	// ctx.getLockCB().get(lockService).getLocks().size());
	// assertEquals("ta2 lock count after commit of ta1", 1,
	// t2.getLockCB().get(lockService).getLocks().size());
	// }
	//
	// @Test
	// public void testUpgrade2TA() throws BrackitException
	// {
	// lockService.request(buildLockNames(1), LockClass.COMMIT_DURATION,
	// URIX.Mode.U, false);
	//
	// Thread me = Thread.currentThread();
	// LockRequestJob concurrentRequest = new LockRequestJob(t2, lockService,
	// buildLockNames(1), URIX.Mode.U);
	// concurrentRequest.setSupervisor(me);
	// scheduler.schedule(concurrentRequest, false);
	//
	// try
	// {
	// Thread.sleep(1000);
	// }
	// catch (InterruptedException e)
	// {
	// }
	//
	// lockService.request(buildLockNames(1), LockClass.COMMIT_DURATION,
	// URIX.Mode.X, false);
	//
	// assertEquals("ta lock count", 1,
	// ctx.getLockCB().get(lockService).getLocks().size());
	// ctx.commit();
	//
	// scheduler.join(concurrentRequest);
	//
	// assertEquals("ta1 lock count after commit", 0,
	// ctx.getLockCB().get(lockService).getLocks().size());
	// assertEquals("ta2 lock count after commit of ta1", 1,
	// t2.getLockCB().get(lockService).getLocks().size());
	// }
	//
	// @Test
	// public void testUpgrade3TA() throws BrackitException
	// {
	// lockService.request(buildLockNames(1), LockClass.COMMIT_DURATION,
	// URIX.Mode.R, false);
	// lockService.request(buildLockNames(1), LockClass.COMMIT_DURATION,
	// URIX.Mode.U, false);
	//
	// Thread me = Thread.currentThread();
	// LockRequestJob t3concurrentRequest = new LockRequestJob(t3, lockService,
	// buildLockNames(1), URIX.Mode.U);
	// LockRequestJob t2concurrentRequest = new LockRequestJob(t2, lockService,
	// buildLockNames(1), URIX.Mode.X);
	// t3concurrentRequest.setSupervisor(me);
	// scheduler.schedule(t3concurrentRequest, false);
	// try
	// {
	// Thread.sleep(1000);
	// }
	// catch (InterruptedException e)
	// {
	// }
	// scheduler.schedule(t2concurrentRequest, false);
	// try
	// {
	// Thread.sleep(1000);
	// }
	// catch (InterruptedException e)
	// {
	// }
	//
	// ctx.commit();
	// assertEquals("ta1 lock count after commit", 0,
	// ctx.getLockCB().get(lockService).getLocks().size());
	// assertEquals("ta2 lock count after commit of ta1", 1,
	// t2.getLockCB().get(lockService).getLocks().size());
	//
	// List<XTClock> locks = t2.getLockCB().get(lockService).getLocks();
	// assertEquals("ta lock count", 1, locks.size());
	// assertEquals("ta2 has X", locks.get(0).getMode(), URIX.Mode.X);
	//
	// scheduler.join(t2concurrentRequest);
	//
	// taMgr.commitWork(t2);
	//
	// scheduler.join(t3concurrentRequest);
	//
	// locks = t3.getLockCB().get(lockService).getLocks();
	// assertEquals("ta3 lock count", 1, locks.size());
	// assertEquals("ta3 has X", locks.get(0).getMode(), URIX.Mode.U);
	// }

	@Test
	public void requestAndCommit1TA() throws TxException {
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());

		t1.commit();

		assertEquals("ta lock count after commit", 0, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void requestAndRelease1() throws TxException {
		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);
		lockService.request(t1, lockNames, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		lockService.release(t1, lockNames);
		assertEquals("ta lock count after release", 0, t1.getLockCB().get(
				lockService).getLockServiceCB().getCount());
	}

	@Test
	public void requestAndRelease2() throws TxException {
		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 6, 7);

		lockService.request(t1, lockNames, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);
		lockService.request(t1, lockNames2, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count", 7, t1.getLockCB().get(lockService)
				.getLocks().size());

		lockService.release(t1, lockNames);

		assertEquals("ta lock count after release", 5, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void requestAndRelease3() throws TxException {
		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);
		lockService.request(t1, lockNames2, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());

		lockService.release(t1, lockNames);

		assertEquals("ta lock count after release", 3, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void requestAndRelease4() throws TxException {
		GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.LR, false);
		lockService.request(t1, lockNames2, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.NR, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());

		lockService.release(t1, lockNames);

		assertEquals("ta lock count after release", 5, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void requestAndRelease3TA() throws TxException {
		final SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3);
		lockService.request(t1, lockNames, LockClass.SHORT_DURATION,
				URIX.Mode.R, false);

		t2.leave();
		Thread user2 = new Thread() {
			public void run() {
				try {
					t2.join();
					lockService.request(t2, lockNames,
							LockClass.SHORT_DURATION, URIX.Mode.U, false);
				} catch (TxException e) {
					fail();
				}
			}
		};
		user2.start();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}

		t3.leave();
		Thread user3 = new Thread() {
			public void run() {
				try {
					t3.join();
					lockService.request(t3, lockNames,
							LockClass.SHORT_DURATION, URIX.Mode.U, false);
				} catch (TxException e) {
					fail();
				}
			}
		};
		user3.start();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		lockService.release(t1, lockNames);

		System.out.println(lockService.listLocks());
	}

	@Test
	public void requestConvertAndRelease3TA() throws TxException {
		final GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		final SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3);
		lockService.request(t1, lockNames, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.SR, false);
		t1.getLockCB().setTimeout(60 * 60 * 1000);
		t2.getLockCB().setTimeout(60 * 60 * 1000);
		t3.getLockCB().setTimeout(60 * 60 * 1000);

		t2.leave();
		Thread user2 = new Thread() {
			public void run() {
				try {
					t2.join();
					// System.out.println("T2 before SU" +
					// lockService.listLocks());
					lockService
							.request(t2, lockNames, LockClass.SHORT_DURATION,
									TaDOM3Plus.Mode.SU, false);
					// System.out.println("T2 after SU" +
					// lockService.listLocks());
					lockService
							.request(t2, lockNames, LockClass.SHORT_DURATION,
									TaDOM3Plus.Mode.SX, false);
					// System.out.println("T2 after SX" +
					// lockService.listLocks());

					if ((t1.getState() == TxState.RUNNING)) {
						// System.out.println(lockService.listLocks());
						fail();
					}
				} catch (TxException e) {
					fail();
				} finally {
					try {
						t2.rollback();
					} catch (TxException e) {
					}
				}
				// System.out.println("t2 end");
			}
		};
		user2.start();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}

		t3.leave();
		Thread user3 = new Thread() {
			public void run() {
				try {
					t3.join();
					// System.out.println("T3 before SU" +
					// lockService.listLocks());
					lockService
							.request(t3, lockNames, LockClass.SHORT_DURATION,
									TaDOM3Plus.Mode.SU, false);
					// System.out.println("T3 after SU " +
					// lockService.listLocks());

					if ((t1.getState() == TxState.RUNNING)
							|| (t2.getState() == TxState.RUNNING)) {
						// System.out.println(lockService.listLocks());
						fail();
					}

					lockService
							.request(t3, lockNames, LockClass.SHORT_DURATION,
									TaDOM3Plus.Mode.SX, false);
				} catch (TxException e) {
					fail();
				} finally {
					try {
						t3.rollback();
					} catch (TxException e) {
					}
				}
				System.out.println("t3 end");
			}
		};
		user3.start();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		// try { System.out.println("press enter to continue... ");new
		// BufferedReader(new InputStreamReader(System.in)).readLine(); } catch
		// (IOException e) {}
		t1.commit();
		// System.out.println("ctx end");

		try {
			user2.join();
		} catch (InterruptedException e) {
		}
		try {
			user3.join();
		} catch (InterruptedException e) {
		}
	}

	@Test
	public void requestAndCommit2TA() throws TxException {
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);
		lockService.request(t2, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);

		assertEquals("ta 1 lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("ta 2 lock count", 5, t2.getLockCB().get(lockService)
				.getLocks().size());

		t1.commit();

		assertEquals("ta 1 lock count after commit", 0, t1.getLockCB().get(
				lockService).getLocks().size());
		assertEquals("ta 2 lock count after commit", 5, t2.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void requestAndRollback2TA() throws TxException {
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);
		lockService.request(t2, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);

		assertEquals("ta 1 lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("ta 2 lock count", 5, t2.getLockCB().get(lockService)
				.getLocks().size());

		t1.rollback();

		assertEquals("ta 1 lock count after rollback", 0, t1.getLockCB().get(
				lockService).getLocks().size());
		assertEquals("ta 2 lock count after rollback", 5, t2.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void requestAndRollback1TA() throws TxException {
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());

		t1.rollback();

		assertEquals("ta lock count after rollback", 0, t1.getLockCB().get(
				lockService).getLocks().size());
	}

	@Test
	public void overruleLock1() throws TxException {
		lockService.request(t1, buildLockNames(1, 2, 3),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void overruleLock2() throws TxException {
		lockService.request(t1, buildLockNames(1, 2, 3),
				LockClass.COMMIT_DURATION, URIX.Mode.X, false);
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.R, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void overruleLock3() throws TxException {
		lockService.request(t1, buildLockNames(1, 2, 3),
				LockClass.COMMIT_DURATION, URIX.Mode.X, false);
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, URIX.Mode.X, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void overruleLock4() throws TxException {
		GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		lockService.request(t1, buildLockNames(1, 2, 3),
				LockClass.COMMIT_DURATION, TaDOM3Plus.Mode.LR, false);
		lockService.request(t1, buildLockNames(1, 2, 3, 4),
				LockClass.COMMIT_DURATION, TaDOM3Plus.Mode.NR, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void overruleLock5() throws TxException {
		GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		lockService.request(t1, buildLockNames(1, 2, 3),
				LockClass.COMMIT_DURATION, TaDOM3Plus.Mode.LR, false);
		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, TaDOM3Plus.Mode.NR, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void overruleLock6() throws TxException {
		GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		lockService.request(t1, buildLockNames(1, 2, 3),
				LockClass.COMMIT_DURATION, TaDOM3Plus.Mode.LR, false);
		lockService.request(t1, buildLockNames(1, 2, 3, 4),
				LockClass.COMMIT_DURATION, TaDOM3Plus.Mode.NR, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());

		lockService.request(t1, buildLockNames(1, 2, 3, 4, 5),
				LockClass.COMMIT_DURATION, TaDOM3Plus.Mode.SX, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void maxLockDepth0() throws TxException {
		t1.setLockDepth(0);

		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count", 1, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.R, lockService.getLock(t1,
				lockNames.getLockName(0)).getMode());
	}

	@Test
	public void maxLockDepth2() throws TxException {
		t1.setLockDepth(2);

		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.X, lockService.getLock(t1,
				lockNames.getLockName(2)).getMode());
	}

	@Test
	public void maxLockDepthNegative() throws TxException {
		t1.setLockDepth(-1);

		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 1, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.X, lockService.getLock(t1,
				lockNames.getLockName(0)).getMode());
	}

	@Test
	public void implicitGrantedChildModeInPath() throws TxException {
		GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames1, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.LR, false);
		lockService.request(t1, lockNames2, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.NR, false);

		assertTrue("Level lock implies node read at child level", t1
				.getLockCB().get(lockService).getLocks().size() == 5);
		assertEquals("lock mode", TaDOM3Plus.Mode.IR, lockService.getLock(t1,
				lockNames2.getLockName(0)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.IR, lockService.getLock(t1,
				lockNames2.getLockName(1)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.LR, lockService.getLock(t1,
				lockNames2.getLockName(2)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.IR, lockService.getLock(t1,
				lockNames2.getLockName(3)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.NR, lockService.getLock(t1,
				lockNames2.getLockName(4)).getMode());
	}

	@Test
	public void implicitGrantedChildModeInPathWithIntentionUpdate()
			throws TxException {
		GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames1, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.LR, false);
		lockService.request(t1, lockNames2, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.NX, false);

		assertTrue("Level lock implies node read at child level", t1
				.getLockCB().get(lockService).getLocks().size() == 5);
		assertEquals("lock mode", TaDOM3Plus.Mode.IX, lockService.getLock(t1,
				lockNames2.getLockName(0)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.IX, lockService.getLock(t1,
				lockNames2.getLockName(1)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.LRIX, lockService.getLock(t1,
				lockNames2.getLockName(2)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.CX, lockService.getLock(t1,
				lockNames2.getLockName(3)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.NX, lockService.getLock(t1,
				lockNames2.getLockName(4)).getMode());
	}

	@Test
	public void implicitGrantedChildMode() throws TxException {
		GenericLockService<TaDOM3Plus.Mode> lockService = new GenericLockServiceImpl<TaDOM3Plus.Mode>(
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4);

		lockService.request(t1, lockNames1, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.LR, false);
		lockService.request(t1, lockNames2, LockClass.SHORT_DURATION,
				TaDOM3Plus.Mode.NR, false);

		assertTrue("Level lock implies node read at child level", t1
				.getLockCB().get(lockService).getLocks().size() == 3);
		assertEquals("lock mode", TaDOM3Plus.Mode.IR, lockService.getLock(t1,
				lockNames2.getLockName(0)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.IR, lockService.getLock(t1,
				lockNames2.getLockName(1)).getMode());
		assertEquals("lock mode", TaDOM3Plus.Mode.LR, lockService.getLock(t1,
				lockNames2.getLockName(2)).getMode());
	}

	@Test
	public void instantSingle() throws TxException {
		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);

		URIX.Mode granted = lockService.request(t1, lockNames,
				LockClass.INSTANT_DURATION, URIX.Mode.X, false);

		assertNotNull("instant lock was granted", granted);
		assertEquals("ta lock count", 0, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void instantDeeperLevel1() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4);

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());

		URIX.Mode granted = lockService.request(t1, lockNames2,
				LockClass.INSTANT_DURATION, URIX.Mode.X, false);

		assertNotNull("instant lock was granted", granted);
		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void instantDeeperLevel2() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());

		URIX.Mode granted = lockService.request(t1, lockNames2,
				LockClass.INSTANT_DURATION, URIX.Mode.X, false);

		assertNotNull("instant lock was granted", granted);
		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void instantSameLevel() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3);

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());

		URIX.Mode granted = lockService.request(t1, lockNames1,
				LockClass.INSTANT_DURATION, URIX.Mode.X, false);

		assertNotNull("instant lock was granted", granted);
		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void lockDowngrade() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.U, lockService.getLock(t1,
				lockNames1.getLockName(4)).getMode());

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.R, lockService.getLock(t1,
				lockNames1.getLockName(4)).getMode());
	}

	@Test
	public void lockUpgrade() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.U, lockService.getLock(t1,
				lockNames1.getLockName(4)).getMode());

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.X, lockService.getLock(t1,
				lockNames1.getLockName(4)).getMode());
	}

	@Test
	public void lockUpgradeBlock() throws TxException {
		final SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);

		lockService.request(t2, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);
		assertEquals("ta lock count", 5, t2.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t2,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t2,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t2,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t2,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.U, lockService.getLock(t2,
				lockNames1.getLockName(4)).getMode());

		t2.leave();
		Thread t = new Thread() {
			public void run() {
				try {
					t2.join();
					lockService.request(t2, lockNames1,
							LockClass.COMMIT_DURATION, URIX.Mode.X, false);
				} catch (TxException e) {
					fail("ta 2 failed: " + e.getMessage());
				} finally {
					try {
						t2.leave();
					} catch (TxException e) {
						fail("ta 2 failed: " + e.getMessage());
					}
				}
			}
		};
		t.start();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		t1.commit();

		try {
			t.join();
		} catch (InterruptedException e) {
		}

		t2.join();
		assertEquals("ta lock count", 5, t2.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t2,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t2,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t2,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t2,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.X, lockService.getLock(t2,
				lockNames1.getLockName(4)).getMode());
	}

	@Test
	public void lockUpdateLockBlock() throws TxException {
		final SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4, 5);

		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);
		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.U, lockService.getLock(t1,
				lockNames1.getLockName(4)).getMode());

		t2.leave();
		Thread t = new Thread() {
			public void run() {
				try {
					t2.join();
					t2.getLockCB().setTimeout(100);
					lockService.request(t2, lockNames1,
							LockClass.COMMIT_DURATION, URIX.Mode.U, false);
					fail("ta 2 was granted update lock");
				} catch (TxException e) {
					System.out.println(e.getMessage());
					// expected
				}
			}
		};
		t.start();

		try {
			t.join();
		} catch (InterruptedException e) {
		}
	}

	@Test
	public void lockConversion1() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4, 5);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3, 4, 6);
		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);
		lockService.request(t1, lockNames2, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 6, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.R, lockService.getLock(t1,
				lockNames1.getLockName(4)).getMode());
		assertEquals("lock mode", URIX.Mode.X, lockService.getLock(t1,
				lockNames2.getLockName(4)).getMode());
	}

	@Test
	public void lockConversion2() throws TxException {
		SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4, 5);
		SimpleLockNameFactory lockNames2 = buildLockNames(1, 2, 3);
		lockService.request(t1, lockNames1, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);
		lockService.request(t1, lockNames2, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames1.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.X, lockService.getLock(t1,
				lockNames1.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames1.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.R, lockService.getLock(t1,
				lockNames1.getLockName(4)).getMode());
	}

	@Test
	public void downgradeConversion1() throws TxException {
		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);
		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);
		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IR, lockService.getLock(t1,
				lockNames.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.R, lockService.getLock(t1,
				lockNames.getLockName(4)).getMode());
	}

	@Test
	public void upgradeConversion1() throws TxException {
		SimpleLockNameFactory lockNames = buildLockNames(1, 2, 3, 4, 5);
		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.U, false);
		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.X, false);

		assertEquals("ta lock count", 5, t1.getLockCB().get(lockService)
				.getLocks().size());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames.getLockName(0)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames.getLockName(1)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames.getLockName(2)).getMode());
		assertEquals("lock mode", URIX.Mode.IX, lockService.getLock(t1,
				lockNames.getLockName(3)).getMode());
		assertEquals("lock mode", URIX.Mode.X, lockService.getLock(t1,
				lockNames.getLockName(4)).getMode());
	}

	@Test
	public void escalation1() throws TxException {
		lockService.getClient(t1).setMaxEscalationCount(10);
		lockService.getClient(t1).setEscalationGain(-1);

		for (int i = 0; i < 13; i++) {
			SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4,
					i + 10);
			lockService.request(t1, lockNames1, LockClass.SHORT_DURATION,
					URIX.Mode.R, false);

			if (i < 10) {
				assertEquals("ta lock count", 4 + 1 + i, t1.getLockCB().get(
						lockService).getLocks().size());
			} else {
				assertEquals("ta lock count", 4 + 10, t1.getLockCB().get(
						lockService).getLocks().size());
			}
		}
	}

	@Test
	public void escalation2() throws TxException {
		lockService.getClient(t1).setMaxEscalationCount(10);
		lockService.getClient(t1).setEscalationGain(-1);

		for (int i = 0; i < 30; i++) {
			SimpleLockNameFactory lockNames1 = buildLockNames(1, 2, 3, 4,
					i + 10);
			lockService.request(t1, lockNames1, LockClass.SHORT_DURATION,
					URIX.Mode.R, false);

			if (i < 10) {
				assertEquals("ta lock count", 4 + 1 + i, t1.getLockCB().get(
						lockService).getLocks().size());
			} else {
				assertEquals("ta lock count", 4 + 10, t1.getLockCB().get(
						lockService).getLocks().size());
			}
		}

		System.out.println(t1.getLockCB().get(lockService).listLocks());
	}

	@Test
	public void IntentionExclusiveInstantAfterRead() throws TxException {
		SimpleLockNameFactory lockNames = buildLockNames(1);
		lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
				URIX.Mode.R, false);
		lockService.request(t1, lockNames, LockClass.INSTANT_DURATION,
				URIX.Mode.IX, false);
		System.out.println(t1.getLockCB().get(lockService).listLocks());
	}

	@Test
	public void testLockTableScalability() throws TxException, IOException {
		int roundStart = 0;
		// BufferedReader in = new BufferedReader(new
		// InputStreamReader(System.in));
		// while (in.readLine() != "")
		// {
		long start = 0;
		long end = 0;
		long start2 = 0;
		long end2 = 0;
		long testStart = System.nanoTime();
		int[] lockObjects = new int[PERFORMANCE_LOCKS_PER_REQUEST];
		List<LockName> added = new ArrayList<LockName>();

		for (int i = 1; i <= (PERFORMANCE_MAX_LOCKS / PERFORMANCE_LOCKS_PER_REQUEST); i++) {
			if ((i
					% (PERFORMANCE_MONITOR_INTERVALL / PERFORMANCE_LOCKS_PER_REQUEST) == 0)
					|| (i == 1)) {
				start = System.nanoTime();
			}

			// start = System.nanoTime();
			for (int j = 0; j < PERFORMANCE_LOCKS_PER_REQUEST; j++)
				lockObjects[j] = i * 5 + j + roundStart;
			SimpleLockNameFactory lockNames = buildLockNames(lockObjects);
			lockService.request(t1, lockNames, LockClass.COMMIT_DURATION,
					URIX.Mode.X, false);
			// for (int j = 0; j < lockNames.getTargetLevel(); j++) {
			// added.add(lockNames.getLockName(j));
			// for (int k = 0; k < added.size(); k++) {
			// XTClock find = lockService.getLock(t1, added.get(k));
			// if (find == null) {
			// lockService.getLock(t1, added.get(k));
			// throw new RuntimeException("Kann " + k + " von " + added.size() +
			// " nicht finden");
			// }
			// }
			// }
			// end = System.nanoTime();
			// System.out.println("LOCK BUFFER request time  " + ((end - start))
			// + " ns");

			if (i
					% (PERFORMANCE_MONITOR_INTERVALL / PERFORMANCE_LOCKS_PER_REQUEST) == (PERFORMANCE_MONITOR_INTERVALL / PERFORMANCE_LOCKS_PER_REQUEST) - 1) {
				end = System.nanoTime();
				System.out.println((i + 1) * PERFORMANCE_LOCKS_PER_REQUEST
						+ " locks acquired -> Lock Buffer average lock time ("
						+ PERFORMANCE_MONITOR_INTERVALL + " locks) "
						+ ((end - start) / PERFORMANCE_MONITOR_INTERVALL)
						+ " ns");
			}
		}
		long testEnd = System.nanoTime();
		System.out.println("average time per lock with "
				+ PERFORMANCE_MAX_LOCKS + " locks requested = "
				+ ((testEnd - testStart) / PERFORMANCE_MAX_LOCKS) + " ns");
		// System.out.println("total time for " + PERFORMANCE_MAX_LOCKS +
		// " locks = " + ((testEnd - testStart) / 1000000.0) + " ms");
		// System.out.println(ctx.getLockCB().get(lockService).getLockServiceCB().getCount());
		// System.out.println("Lock Table Statistics:");
		// System.out.println(lockService.toString());
		// roundStart++;
		// System.gc();

		for (int i = 1; i <= (PERFORMANCE_MAX_LOCKS / PERFORMANCE_LOCKS_PER_REQUEST); i++) {
			for (int j = 0; j < PERFORMANCE_LOCKS_PER_REQUEST; j++)
				lockObjects[j] = i * 5 + j + roundStart;
			SimpleLockNameFactory lockNames = buildLockNames(lockObjects);

			for (int j = 0; j < lockNames.getTargetLevel(); j++)
				assertNotNull(lockService.getLock(t1, lockNames.getLockName(j)));
		}
	}

	@Before
	public void setUp() throws LogException, TxException {
		taMgr = new TaMgrMockup();
		lockService = new GenericLockServiceImpl<URIX.Mode>(SERVICE_NAME,
				MAX_LOCKS, MAX_TRANSACTIONS);

		t1 = taMgr.begin();
		t2 = taMgr.begin();
		t3 = taMgr.begin();
	}

	@After
	public void tearDown() {
	}
}
