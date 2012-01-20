/*
 * [New BSD License]
 * Copyright (c) 2011-2012, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Brackit Project Team nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.session.async;

import org.brackit.server.tx.Tx;
import org.brackit.server.tx.thread.ThreadCB;

/**
 * These Threads do the workload of a transaction. Every thread is either
 * running dedicated to one specific transaction or is pooled for further
 * requests.
 * 
 * Several threads can be assigned to one transaction to support intra
 * transaction parallelity.
 * 
 * @author Sebastian Baechle
 * 
 */
public class WorkerThread extends Thread {
	private final JobScheduler scheduler;

	private Tx transaction;

	private boolean alive;

	private boolean debug = false;

	public WorkerThread(JobScheduler scheduler, String name) {
		super(new ThreadGroup(name + "Group"), name);
		this.scheduler = scheduler;
		setDaemon(true);
	}

	public WorkerThread(JobScheduler scheduler, ThreadGroup group, String name) {
		super(group, name);
		this.scheduler = scheduler;
		setDaemon(true);
	}

	@Override
	public synchronized void start() {
		this.alive = true;
		super.start();
	}

	@Override
	public void run() {
		Job job = null;

		if (debug)
			System.out.println(String.format("[%s] alive", getName()));

		while (alive) {
			if (debug)
				System.out.println(String.format("[%s] pop job", getName()));

			job = scheduler.popJob();

			if (job != null) {
				Thread supervisor = job.getSupervisor();

				if (supervisor != null)
					ThreadCB.get().supervisor = supervisor;

				runJob(job);

				ThreadCB.get().supervisor = null;
			} else {
				alive = false;
			}
		}

		if (debug)
			System.out.println(String.format("[%s] die", getName()));
	}

	private void runJob(Job job) {
		try {
			if (debug)
				System.out.println(String.format("[%s] running: %s", getName(),
						job.toString()));

			job.doWork();

			if (debug)
				System.out.println(String.format(
						"[%s] finished successfully: %s", getName(), job
								.toString()));
		} catch (Throwable e) {
			if (debug)
				System.out.println(String.format("[%s] got error: %s",
						getName(), e.getMessage()));

			job.setThrowable(e);
			job.cleanup();
		} finally {
			job.markFinished();
			synchronized (job) {
				job.notifyAll();
			}
		}
	}

	@Override
	public String toString() {
		return getName();
	}
}
