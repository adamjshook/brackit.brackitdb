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

import java.nio.ByteBuffer;

import org.brackit.server.tx.TxID;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.Loggable;
import org.brackit.server.tx.log.SizeConstants;

/**
 * 
 * @author Ou Yi
 * 
 */
public class LogRecord implements Loggable {
	private static final int BASE_SIZE = (SizeConstants.BYTE_SIZE
			+ TxID.getLength() + SizeConstants.LONG_SIZE);

	private long LSN;

	// fields available for all types of log records
	private byte type;
	private TxID txID;
	private long prevLSN;

	// only in type UPDATE and CLR (but not in DUMMY)
	private LogOperation logOperation;

	// only in type CLR and DUMMY
	private long undoNextLSN;

	// non-persist field
	private int sizeInBytes;

	/**
	 * @param type
	 * @param taId
	 * @param prevLSN
	 * @param logOperation
	 * @param undoNextLSN
	 */
	LogRecord(byte type, TxID taId, long prevLSN, LogOperation logOperation,
			long undoNextLSN) {
		super();
		this.LSN = -1;
		this.type = type;
		this.txID = taId;
		this.prevLSN = prevLSN;

		this.logOperation = logOperation;
		this.undoNextLSN = undoNextLSN;

		this.sizeInBytes = BASE_SIZE
				+ (((undoNextLSN != -1) || (type == TYPE_DUMMY) || (type == TYPE_UPDATE_SPECIAL)) ? SizeConstants.LONG_SIZE
						: 0)
				+ ((logOperation != null) ? SizeConstants.BYTE_SIZE
						+ logOperation.getSize() : 0);
	}

	public long getLSN() {
		return LSN;
	}

	public void setLSN(long lsn) {
		LSN = lsn;
	}

	public byte getType() {
		return this.type;
	}

	public TxID getTxID() {
		return this.txID;
	}

	public long getPrevLSN() {
		return this.prevLSN;
	}

	public LogOperation getLogOperation() {
		return this.logOperation;
	}

	public long getUndoNextLSN() {
		return this.undoNextLSN;
	}

	public int getSize() {
		return this.sizeInBytes;
	}

	public byte[] toBytes() {
		ByteBuffer bb = ByteBuffer.allocate(sizeInBytes);
		bb.put(type);
		txID.toBytes(bb);
		bb.putLong(prevLSN);

		switch (type) {
		case TYPE_UPDATE:
			bb.put(logOperation.getType());
			logOperation.toBytes(bb);
			break;
		case TYPE_CLR:
		case TYPE_UPDATE_SPECIAL:
			bb.putLong(undoNextLSN);
			bb.put(logOperation.getType());
			logOperation.toBytes(bb);
			break;
		case TYPE_DUMMY:
			bb.putLong(undoNextLSN);
			break;

		default:
			break;
		}

		return bb.array();
	}
}
