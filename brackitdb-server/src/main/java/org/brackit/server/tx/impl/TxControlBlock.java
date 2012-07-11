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
package org.brackit.server.tx.impl;

import java.util.LinkedList;

import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxState;

/**
 * Services for intra-transaction concurrency. Implements join/leave and
 * abort/commit voting mechanism for a kind of a vm-wide 2PC.
 * 
 * @author Sebastian Baechle
 * 
 */
class TxControlBlock {
	enum Vote {
		NONE, COMMIT, ROLLBACK
	};

	private Vote vote;

	private TxState state;

	private LinkedList<Thread> assignedThreads;

	TxControlBlock() {
		this.state = TxState.RUNNING;
		this.vote = Vote.NONE;
		this.assignedThreads = new LinkedList<Thread>();
	}

	synchronized void setState(TxState state) {
		this.state = state;
	}

	public synchronized TxState getState() {
		return this.state;
	}

	synchronized void waitEOT() {
		while (state.isActive()) {
			try {
				wait();
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}

	synchronized void signalEOT(boolean success) {
		state = (success) ? TxState.COMMITTED : TxState.ROLLEDBACK;
		notifyAll();
	}

	synchronized boolean voteCommit() {
		boolean firstVote = (vote == Vote.NONE);

		vote = (vote != Vote.ROLLBACK) ? Vote.COMMIT : Vote.ROLLBACK;

		Thread me = Thread.currentThread();
		assignedThreads.remove(me);

		while (assignedThreads.size() > 0) {
			try {
				wait();
			} catch (InterruptedException e) {
				// ignore
			}
		}
		notifyAll();

		return firstVote && (vote == Vote.COMMIT);
	}

	synchronized boolean voteRollback() {
		boolean firstVote = ((vote == Vote.NONE) || (vote == Vote.COMMIT));
		vote = Vote.ROLLBACK;

		Thread me = Thread.currentThread();
		assignedThreads.remove(me);

		while (assignedThreads.size() > 0) {
			try {
				wait();
			} catch (InterruptedException e) {
				// ignore
			}
		}
		notifyAll();

		return firstVote;
	}

	public synchronized boolean join() {
		Thread me = Thread.currentThread();

		if (!assignedThreads.contains(me)) {
			if ((vote != Vote.NONE) || (state != TxState.RUNNING)) {
				// new joins are only allowed when tx is still in normal
				// processing
				return false;
			}
			assignedThreads.add(me);
		}

		return true;
	}

	public synchronized void leave() throws TxException {
		Thread me = Thread.currentThread();

		if (!assignedThreads.remove(me)) {
			throw new TxException("Did not join %s.", toString());
		}
		notifyAll();
	}
}