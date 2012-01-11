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
package org.brackit.server.tx.log.impl;

/**
 * 
 * @author Ou Yi
 * @author Sebastian Baechle
 * 
 */
public class LogMonitor {
	private int maxLSN = -1;
	private int appendedCount = 0;

	private long flushCount = 0;
	private long flushByteCount = 0;
	private int minFlushSize = Integer.MAX_VALUE;
	private int maxFlushSize = Integer.MIN_VALUE;

	private long appendByteCount = 0;
	private int minAppendSize = Integer.MAX_VALUE;
	private int maxAppendSize = Integer.MIN_VALUE;

	long getFlushByteCount() {
		return this.flushByteCount;
	}

	void logFlushed(int flushSize) {
		this.flushCount++;
		this.flushByteCount += flushSize;
		this.minFlushSize = (flushSize < minFlushSize ? flushSize
				: minFlushSize);
		this.maxFlushSize = (flushSize > maxFlushSize ? flushSize
				: maxFlushSize);
	}

	void logAppended(int loggableSize) {
		if (appendedCount % 1000 == 0) {
			// System.out.println("current lsn: " + nextLSN);
		}

		this.appendByteCount += loggableSize;
		this.minAppendSize = (loggableSize < minAppendSize ? loggableSize
				: minAppendSize);
		this.maxAppendSize = (loggableSize > maxAppendSize ? loggableSize
				: maxAppendSize);

	}

	long getFlushCount() {
		return this.flushCount;
	}

	int getMinFlushSize() {
		return this.minFlushSize;
	}

	int getMaxFlushSize() {
		return maxFlushSize;
	}

	int getAvgFlushSize() {
		return (int) (flushCount == 0 ? 0 : flushByteCount / flushCount);
	}

	long getAppendByteCount() {
		return appendByteCount;
	}

	int getMinAppendSize() {
		return minAppendSize;
	}

	int getMaxAppendSize() {
		return maxAppendSize;
	}

	int getAvgAppendSize() {
		return (int) (appendedCount == 0 ? 0 : appendByteCount / appendedCount);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();

		sb.append("\n");
		sb.append("max lsn: " + maxLSN + "\n");
		sb.append("appended: " + appendedCount + "\n");
		sb.append(appendByteCount / 1024 + "K bytes appended" + "\n");
		sb.append("min append size: " + minAppendSize + "\n");
		sb.append("max append size: " + maxAppendSize + "\n");
		sb.append("avg append size: " + getAvgAppendSize() + "\n");
		sb.append("the log totally flushed " + getFlushCount() + " times"
				+ "\n");
		sb.append(flushByteCount / 1024 + " K bytes written" + "\n");
		sb.append("min flush size: " + minFlushSize + "\n");
		sb.append("max flush size: " + maxFlushSize + "\n");
		sb.append("avg flush size: " + getAvgFlushSize() + "\n");
		sb.append("\n");

		return sb.toString();
	}
}