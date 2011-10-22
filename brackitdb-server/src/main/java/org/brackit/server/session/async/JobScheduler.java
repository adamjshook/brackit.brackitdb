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
package org.brackit.server.session.async;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.brackit.xquery.util.Cfg;

public class JobScheduler {
	public static final String MIN_WORKER_THREADS = "org.brackit.server.tx.thread.jobScheduler.minWorkerThreads";

	public static final String MAX_WORKER_THREADS = "org.brackit.server.tx.thread.jobScheduler.maxWorkerThreads";

	public static final String MAX_WORKER_THREAD_POOL_TIME = "org.brackit.server.tx.thread.jobScheduler.maxWorkerThreadPoolTime";

	private LinkedBlockingQueue<Job> jobQueue;

	private ArrayList<Thread> workerThreads;

	private boolean debug = false;

	private int minWorkerThreads;

	private int maxWorkerThreads;

	private int maxWorkerThreadPoolTime;

	private int freeWorkerCount;

	private int workerThreadIDSequence;

	private boolean active = false;

	public JobScheduler() {
		this.minWorkerThreads = Cfg.asInt(MIN_WORKER_THREADS, 1);
		this.maxWorkerThreads = Cfg.asInt(MAX_WORKER_THREADS, 10);
		this.maxWorkerThreadPoolTime = Cfg.asInt(MAX_WORKER_THREAD_POOL_TIME,
				20000);
		this.freeWorkerCount = 0;

		jobQueue = new LinkedBlockingQueue<Job>();
		workerThreads = new ArrayList<Thread>();
		workerThreadIDSequence = 0;

		for (int i = 0; i < minWorkerThreads; i++)
			workerThreads.add(new WorkerThread(this, "WorkerThread"
					+ workerThreadIDSequence++));

		this.active = true;
		for (Thread worker : workerThreads)
			worker.start();
	}

	public void shutdown() {
		synchronized (this) {
			this.active = false;
			jobQueue.notifyAll();
		}
	}

	/**
	 * Schedules the job. The job will be assigned to one a worker thread to
	 * invoke {{@link #doWork()}. If needed this methods blocks until the job
	 * was successfully executed or an error occured.
	 * 
	 * @param job
	 *            the job to be scheduled
	 * @param wait
	 *            indicates whether the calling thread should block until end of
	 *            execution or not
	 * @return <code>true</code> if and only if the job was executed without any
	 *         errors.
	 */
	public boolean schedule(Job job, boolean wait) {
		// enqueue job
		synchronized (jobQueue) {
			if ((freeWorkerCount == 0)
					&& (workerThreads.size() < maxWorkerThreads)) // spawn new
			// worker if
			// necessary
			{
				WorkerThread workerThread = new WorkerThread(this,
						"WorkerThread" + workerThreadIDSequence++);
				// System.out.println("Spawn worker thread " + workerThread);
				workerThreads.add(workerThread);
				workerThread.start();
			}

			jobQueue.add(job);
			jobQueue.notifyAll();

			// System.out.println("Scheduled job. current queue size " +
			// jobQueue.size() + " current free worker count " + freeWorkerCount
			// + "/" + workerThreads.size());
		}

		// wait for execution
		if (wait)
			join(job);

		return (job.getThrowable() == null);
	}

	public void join(Job job) {
		synchronized (job) {
			if (!job.isFinished()) { // job is probably already executed
				try {
					if (debug)
						System.out.println(String.format(
								"[%s] waiting for job execution: %s", Thread
										.currentThread().getName(), job));
					job.wait();
					if (!job.isFinished())
						throw new RuntimeException(String.format(
								"Job %s was not executed", job));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public Job popJob() {
		Job job = null;
		freeWorkerCount++;

		synchronized (jobQueue) {
			while (active) {
				job = jobQueue.poll();

				if (job == null) {
					if (debug)
						System.out.println(String.format(
								"[%s] waiting for job", Thread.currentThread()
										.getName()));

					long startTime = System.currentTimeMillis();
					try {
						jobQueue.wait(maxWorkerThreadPoolTime);
					} catch (InterruptedException e) { /* ignore */
					}
					long endTime = System.currentTimeMillis();

					if (active) {
						job = jobQueue.poll();

						if (job == null) {
							if ((endTime - startTime > maxWorkerThreadPoolTime)
									&& (workerThreads.size() > minWorkerThreads)) // reduce
							// number
							// of
							// worker
							// threads
							{
								// System.out.println("Freeing worker thread " +
								// Thread.currentThread());
								workerThreads.remove(Thread.currentThread());
								break;
							}
						} else {
							break;
						}
					}
				} else {
					break;
				}
			}
		}
		freeWorkerCount--;

		return job;
	}
}
