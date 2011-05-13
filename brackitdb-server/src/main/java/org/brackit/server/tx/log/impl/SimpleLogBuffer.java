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
package org.brackit.server.tx.log.impl;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.brackit.server.tx.log.Loggable;

/**
 * @author Ou Yi
 * 
 */
public class SimpleLogBuffer implements LogBuffer {
	private Queue<Loggable> logEntries = new LinkedList<Loggable>();

	private int maxCapacity = 1024 * 2000;

	private int size = 0;

	private long lastFlushedLSN = -1;

	@Override
	public synchronized boolean add(Loggable loggable) {
		if (size + loggable.getSize() <= maxCapacity) {
			logEntries.add(loggable);
			size += loggable.getSize();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public synchronized byte[] pollToFlush(long lsn) {
		if (lastFlushedLSN >= lsn) {
			return null;
		}

		List<Loggable> entriesToFlush = new LinkedList<Loggable>();
		int byteCount = 0;
		Loggable loggable = this.logEntries.poll();

		while (loggable != null) // && Loggable.getLSN() <= lsn)
		{
			entriesToFlush.add(loggable);
			byteCount += loggable.getSize() + Constants.FIELD_LENGTH_LEN;
			lastFlushedLSN = loggable.getLSN();

			loggable = logEntries.poll();
		}

		ByteBuffer bb = ByteBuffer.allocate(byteCount);

		for (Loggable entry : entriesToFlush) {
			byte[] bytes = entry.toBytes();

			if (entry.getSize() != bytes.length) {
				throw new RuntimeException(entry.toString());
			}

			bb.putInt(bytes.length);
			bb.put(bytes);
		}

		size = 0;
		return bb.array();
	}

	@Override
	public synchronized Loggable get(long lsn) {
		if (lsn >= lastFlushedLSN) {
			for (Loggable loggable : logEntries) {
				if (loggable.getLSN() == lsn) {
					return loggable;
				}
			}
		}

		return null;
	}
}
