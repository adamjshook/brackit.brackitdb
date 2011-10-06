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

/**
 * Common base for a work request of a transaction. Jobs will be executed by
 * {@see WorkerThread}.
 * 
 * @author Sebastian Baechle
 * 
 */
public abstract class Job {
	private Throwable throwable;

	private boolean finished = false;

	private Thread supervisor;

	public Job() {
	}

	/**
	 * Returns the object thrown during job execution
	 * 
	 * @return the object thrown during job execution
	 */
	public final Throwable getThrowable() {
		return throwable;
	}

	public final void setThrowable(Throwable throwable) {
		this.throwable = throwable;
	}

	public final Thread getSupervisor() {
		return supervisor;
	}

	public final void setSupervisor(Thread supervisor) {
		this.supervisor = supervisor;
	}

	public final boolean isFinished() {
		synchronized (this) {
			return finished;
		}
	}

	public final void markFinished() {
		synchronized (this) {
			finished = true;
		}
	}

	/**
	 * Invoked by the worker threads. Override to implement the functionality of
	 * the job.
	 */
	public abstract void doWork() throws Throwable;

	// called after execution of a job
	public void cleanup() {
	}
}
