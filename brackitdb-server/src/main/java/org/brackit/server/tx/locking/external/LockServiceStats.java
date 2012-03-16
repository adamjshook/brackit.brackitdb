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
package org.brackit.server.tx.locking.external;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sebastian Baechle
 * 
 */
public class LockServiceStats {
	private String name;
	private int blockCount;
	private long blockTime;
	private List<LockTypeStats> lockTypeStats;

	public LockServiceStats(String name, int blockCount, long blockTime) {
		super();
		this.name = name;
		this.blockCount = blockCount;
		this.blockTime = blockTime;
		lockTypeStats = new ArrayList<LockTypeStats>();
	}

	public int getLockCount() {
		int lockCount = 0;
		for (LockTypeStats stats : lockTypeStats)
			lockCount += stats.getLockCount();

		return lockCount;
	}

	public final int getRequestCount() {
		int requestCount = 0;
		for (LockTypeStats stats : lockTypeStats)
			requestCount += stats.getRequestCount();

		return requestCount;
	}

	public void addLockTypeStatistics(LockTypeStats stats) {
		lockTypeStats.add(stats);
	}

	public List<LockTypeStats> getLockTypeStatistics() {
		return lockTypeStats;
	}

	public long getBlockTime() {
		return blockTime;
	}

	public int getBlockCount() {
		return blockCount;
	}

	public String getName() {
		return this.name;
	}

	public String dump() {
		StringBuilder out = new StringBuilder();
		out.append(name);
		out.append(": block count=");
		out.append(blockCount);
		out.append(", block time=");
		out.append(blockTime);
		out.append("ms");
		for (LockTypeStats stat : lockTypeStats) {
			out.append("\n    ");
			out.append(stat.dump());
		}
		return out.toString();
	}
}
