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
package org.brackit.server.node.util;

/**
 * @author Sebastian Baechle
 * 
 */
public class OperationStatistics {
	private final String opName;
	private int counter[];
	private long times[];
	private int maxLevel;

	OperationStatistics(String name) {
		this.opName = name;
		this.counter = new int[20];
		this.times = new long[20];
	}

	long getAggregatedAvgTimings(int minLevel, int maxLevel) {
		long sumTimes = 0;
		long sumCounter = 0;
		for (int i = minLevel; i <= maxLevel; i++) {
			sumTimes += times[i];
			sumCounter += counter[i];
		}

		return (sumCounter > 0) ? sumTimes / sumCounter : -1;
	}

	int getCounter(int level) {
		return counter[level];
	}

	long getAvgTimings(int level) {
		return (counter[level] > 0) ? times[level] / counter[level] : -1;
	}

	void addTiming(int level, long timing) {
		maxLevel = Math.max(maxLevel, level);

		if (level >= counter.length) {
			int[] newCounter = new int[2 * counter.length];
			long[] newTimes = new long[2 * counter.length];
			System.arraycopy(counter, 0, newCounter, 0, counter.length);
			System.arraycopy(times, 0, newTimes, 0, times.length);
			counter = newCounter;
			times = newTimes;
		}
		counter[level]++;
		times[level] += timing;
	}

	public String getName() {
		return opName;
	}

	public int getMaxLevel() {
		return maxLevel;
	}
}
