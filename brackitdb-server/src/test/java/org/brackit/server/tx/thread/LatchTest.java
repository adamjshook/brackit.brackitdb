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
package org.brackit.server.tx.thread;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class LatchTest {
	private int running;

	private boolean signal;

	private boolean go = false;

	private Latch latch;

	private class Runner extends Thread {
		private final LatchTest test;
		private final Latch latch;
		private final int iterations;
		private final Random rand;
		private final int writerShare;
		private final int updateShare;
		private final AtomicInteger sharedValue;

		Runner(LatchTest test, Latch latch, AtomicInteger sharedValue,
				int iterations, int writerShare, int updateShare, String name) {
			super(name);
			this.test = test;
			this.latch = latch;
			this.sharedValue = sharedValue;
			this.iterations = iterations;
			this.writerShare = writerShare;
			this.updateShare = updateShare;
			this.rand = new Random();

			setDaemon(true);
		}

		@Override
		public void run() {
			try {
				test.waitForKickOff();

				for (int i = 0; !test.isSignaled() && i < iterations; i++) {
					boolean write = (1 + rand.nextInt(100) <= writerShare);
					boolean update = (1 + rand.nextInt(100) <= updateShare);

					if (write) {
						doExclusive(update);
					} else {
						doShared(update);
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				test.end();
			}
		}

		private void doExclusive(boolean update) {
			if (update) {
				if (rand.nextBoolean()) {
					if (!latch.latchUC()) {
						latch.latchU();
					}
					latch.upX();
					doWriteChecks();
					latch.unlatch();
				} else {
					latch.latchU();
					latch.upX();
					doWriteChecks();
					latch.unlatch();
				}
			} else {
				if (rand.nextBoolean()) {
					latch.latchX();
					doWriteChecks();
					latch.unlatch();
				} else {
					if (!latch.latchXC()) {
						latch.latchX();
					}
					doWriteChecks();
					latch.unlatch();
				}
			}
		}

		private void doShared(boolean update) {
			if (update) {
				if (rand.nextBoolean()) {
					latch.latchU();
					latch.downS();
					doReadChecks();
					latch.unlatch();
				} else {
					if (!latch.latchUC()) {
						latch.latchU();
					}
					latch.downS();
					doReadChecks();
					latch.unlatch();
				}
			} else {
				if (rand.nextBoolean()) {
					latch.latchS();
					doReadChecks();
					latch.unlatch();
				} else {
					if (!latch.latchSC()) {
						latch.latchS();
					}
					doReadChecks();
					latch.unlatch();
				}
				// latch.latchSharedInstant();
			}
		}

		private void doReadChecks() {
			int initial = sharedValue.get();
			int numberOfChecks = rand.nextInt(1000);

			for (int i = 0; i < numberOfChecks; i++) {
				int recheck = sharedValue.get();

				if (recheck != initial) {
					System.err.println(String.format(
							"Shared read %s failed: %s != %s", i, recheck,
							initial));
					test.signal(true);
					break;
				}
			}
		}

		private void doWriteChecks() {
			int initial = sharedValue.get();
			int numberOfChecks = rand.nextInt(200);

			for (int i = 0; i < numberOfChecks; i++) {
				int recheck = sharedValue.incrementAndGet();

				if (recheck != ++initial) {
					System.err.println(String.format(
							"Exclusive write %s failed: %s != %s", i, recheck,
							initial));
					test.signal(true);
					break;
				}
			}
		}
	}

	public static void main(String[] args) {
		LatchTest test = new LatchTest();

		Latch[] latches = new Latch[] { new SyncLatch() };

		for (Latch latch : latches) {
			System.out.println(latch.getClass().getSimpleName() + ":\n");
			// warmup
			test.runTest(latch, 2, 10000, 0, 30);
			test.runTest(latch, 2, 10000, 0, 30);

			for (int updateShare = 0; updateShare <= 30; updateShare += 10) {
				for (int threadCount = 1; threadCount <= 40; threadCount += (threadCount < 5) ? 1
						: 5) {
					for (int writerShare = 0; writerShare <= 100; writerShare += 10) {
						int opCount = 10000;
						int noOfRuns = 5;
						double time = 0;

						for (int i = 0; i < noOfRuns; i++) {
							time += test.runTest(latch, threadCount, opCount,
									writerShare, updateShare)
									/ (double) 1000000;
						}

						time /= noOfRuns;
						double opsPerMs = (threadCount * opCount)
								/ time;
						// System.out.println(String.format("%2s threads %3s%% exclusive, %3s%% used up-/downgrade: %+6.3fms %+6.3fops/ms",
						// threadCount, writerShare, updateShare, time,
						// opsPerMs));
						System.out.println(String.format(
								"%s\t%s\t%s\t%6.3f\t%6.3f\t", threadCount,
								writerShare, updateShare, time, opsPerMs));
					}
					System.out.println();
				}
				System.out.println();
			}
			System.out.println("\n\n");
		}
	}

	public long runTest(Latch latch, int threadCount, int iterationsPerClient,
			int writerShare, int updateShare) {
		AtomicInteger sharedValue = new AtomicInteger(0);
		Thread[] runners = new Thread[threadCount];

		for (int i = 0; i < runners.length; i++) {
			runners[i] = new Runner(this, latch, sharedValue,
					iterationsPerClient, writerShare, updateShare, "Runner" + i);
			runners[i].start();
		}

		// (new Thread()
		// {
		// public void run()
		// {
		// for (;;)
		// {
		// try { sleep(3000); } catch (InterruptedException e) {}
		// System.out.println(latch.info());
		// }
		// }
		// }).start();

		// for (int i = 0; i < runners.length; i++)
		// {
		// try { runners[i].join(); } catch (InterruptedException e) {}
		// }

		return kickOff();
	}

	private synchronized void waitForKickOff() {
		while (!go) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}

		running++;
	}

	private synchronized void signal(boolean signal) {
		this.signal = signal;
		notifyAll();
	}

	public synchronized boolean isSignaled() {
		return signal;
	}

	private synchronized void waitForSignal() {
		while (go) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
			// System.out.println(latch.info());
		}
	}

	private synchronized void end() {
		running--;

		if (running == 0) {
			go = false;
			notifyAll();
		}
	}

	private synchronized long kickOff() {
		go = true;
		long start = System.nanoTime();
		notifyAll();
		waitForSignal();
		long end = System.nanoTime();
		return end - start;
	}
}
