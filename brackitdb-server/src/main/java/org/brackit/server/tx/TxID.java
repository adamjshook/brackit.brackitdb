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
package org.brackit.server.tx;

import java.nio.ByteBuffer;

/**
 * Unique transaction identifier. This class might someday be extended to
 * implement {@see javax.transaction.xa.Xid} to support JTA (XOpen/DTP).
 * 
 * @author Sebastian Baechle
 * 
 */
public class TxID implements Comparable<TxID> {
	private final long id;

	public TxID(long id) {
		this.id = id;
	}

	public void toBytes(ByteBuffer buffer) {
		buffer.putLong(id);
	}

	public static TxID fromBytes(ByteBuffer buffer) {
		return new TxID(buffer.getLong());
	}

	public static int getLength() {
		return Long.SIZE / 8;
	}

	public long longValue() {
		return id;
	}

	@Override
	public int hashCode() {
		return (int) (id ^ (id >> 32));
		// int key = (int) id;
		// key = (key+0x7ed55d16) + (key<<12);
		// key = (key^0xc761c23c) ^ (key>>19);
		// key = (key+0x165667b1) + (key<<5);
		// key = (key+0xd3a2646c) ^ (key<<9);
		// key = (key+0xfd7046c5) + (key<<3);
		// key = (key^0xb55a4f09) ^ (key>>16);
		// key = (key < 0) ? key * -1 : key;
		// return key;
	}

	@Override
	public int compareTo(TxID o) {
		if (id == o.id) {
			return 0;
		} else if (id < o.id) {
			return -1;
		} else {
			return 1;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TxID) {
			TxID txID = (TxID) obj;
			return id == txID.id;
		}

		return false;
	}

	@Override
	public String toString() {
		return Long.toString(id);
	}
}
