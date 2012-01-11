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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.tx.locking;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.external.LockServiceStats;
import org.brackit.server.tx.locking.external.LockTypeStats;
import org.brackit.server.tx.locking.services.LockService;
import org.brackit.server.tx.thread.Latch;
import org.brackit.server.tx.thread.SyncLatch;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public final class LockServiceClientCB extends SyncLatch implements Latch {
	private static final Logger log = Logger
			.getLogger(LockServiceClientCB.class);

	protected final Tx transaction;

	protected final LockService lockService;

	protected int count;

	protected int blockCount;

	protected long blockTime;

	protected int requestCount;

	public LockServiceClientCB(LockService lockService, Tx tx) {
		this.lockService = lockService;
		this.transaction = tx;
	}

	public LockService getLockService() {
		return lockService;
	}

	public Tx getTransaction() {
		return transaction;
	}

	public synchronized int getCount() {
		return count;
	}

	public synchronized int getBlockCount() {
		return blockCount;
	}

	public synchronized long getBlockTime() {
		return blockTime;
	}

	public synchronized int getRequestCount() {
		return requestCount;
	}

	public synchronized void useRequest() {
		requestCount++;
	}

	public synchronized void addRequest() {
		count++;
		requestCount++;
	}

	public synchronized void removeRequest() {
		count--;
	}

	public synchronized void addBlockTime(long time) {
		blockTime += time;
		blockCount++;
	}

	public synchronized LockServiceStats getStatistics() {
		LockServiceStats stats = new LockServiceStats(lockService.getName(),
				blockCount, blockTime);
		stats.addLockTypeStatistics(new LockTypeStats(lockService.getName(),
				count, requestCount));
		return stats;
	}
}