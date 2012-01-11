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
package org.brackit.server.procedure.xmark.util;

import java.util.Arrays;

import org.brackit.xquery.ErrorCode;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.Tuple;
import org.brackit.xquery.operator.Cursor;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class Split {
	private final Cursor in;

	private Tuple[] buffer;

	private int[] pos;

	private int clientCount;

	private int openCount;

	private int closeCount;

	public Split(Cursor in) {
		super();
		this.in = in;
		this.pos = new int[2];
	}

	private class SplitClient implements Cursor {
		private final Split parent;

		private final int clientID;

		public SplitClient(Split parent, int clientID) {
			super();
			this.parent = parent;
			this.clientID = clientID;
		}

		@Override
		public void close(QueryContext ctx) {
			parent.close(ctx, clientID);
		}

		@Override
		public Tuple next(QueryContext ctx) throws QueryException {
			return parent.next(ctx, clientID);
		}

		@Override
		public void open(QueryContext ctx) throws QueryException {
			parent.open(ctx, clientID);
		}
	}

	public Cursor createClient() {
		if (openCount > 0) {
			throw new RuntimeException("Clients already running");
		}

		int clientID = clientCount++;

		if (clientID == pos.length) {
			pos = Arrays.copyOf(pos, (pos.length * 3) / 2 + 1);
		}

		pos[clientID] = -1;
		return new SplitClient(this, clientID);
	}

	private synchronized void open(QueryContext ctx, int clientID)
			throws QueryException {
		if (openCount == 0) {
			in.open(ctx);
			buffer = new Tuple[0];
		}
		openCount++;
		pos[clientID] = -1;
	}

	private synchronized void close(QueryContext ctx, int clientID) {
		if (closeCount == 1) {
			in.close(ctx);
			openCount = 0;
		}
		closeCount++;
		pos[clientID] = -1;
	}

	private synchronized Tuple next(QueryContext ctx, int clientID)
			throws QueryException {
		if (openCount != clientCount) {
			throw new QueryException(ErrorCode.BIT_DYN_RT_ILLEGAL_STATE_ERROR);
		}

		if ((++pos[clientID]) < buffer.length) {
			Tuple o = buffer[pos[clientID]];

			if (o == null) {
				pos[clientID]--;
			}

			return o;
		}

		int minPos = Integer.MAX_VALUE;

		for (int i = 0; i < clientCount; i++) {
			if (pos[i] >= 0)
				minPos = Math.min(minPos, pos[i]);
		}

		int fillPos;

		if (minPos > 0) {
			// sh
			for (int i = 0; i < clientCount; i++) {
				pos[i] -= minPos;
			}

			fillPos = pos[clientID];
		} else {
			int oldLength = buffer.length;
			int newLength = (oldLength == 0) ? 10 : ((oldLength * 3) / 2 + 1);
			buffer = Arrays.copyOf(buffer, newLength);

			fillPos = oldLength;
		}

		Tuple o = null;
		Arrays.fill(buffer, fillPos, buffer.length, null);
		while ((fillPos < buffer.length) && ((o = in.next(ctx)) != null)) {
			buffer[fillPos++] = o;
		}

		return buffer[pos[clientID]];
	}
}
