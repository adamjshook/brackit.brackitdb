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
package org.brackit.server.tx.log.impl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.brackit.server.io.buffer.log.PageLogOperationHelper;
import org.brackit.server.node.el.index.log.ElBPlusIndexLogOperationHelper;
import org.brackit.server.store.index.aries.log.BPlusIndexLogOperationHelper;
import org.brackit.server.store.index.bracket.log.BracketIndexLogOperationHelper;
import org.brackit.server.tx.TxID;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.LogOperationHelper;
import org.brackit.server.tx.log.Loggable;
import org.brackit.server.tx.log.LoggableHelper;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class LogRecordHelper implements LoggableHelper {
	private static final Set<LogOperationHelper> helperSet;

	private final HashMap<Byte, LogOperationHelper> helpers;

	static {
		helperSet = new HashSet<LogOperationHelper>();
		helperSet.add(new PageLogOperationHelper());
		helperSet.add(new BPlusIndexLogOperationHelper());
		helperSet.add(new ElBPlusIndexLogOperationHelper());
		helperSet.add(new BracketIndexLogOperationHelper());
	}

	public LogRecordHelper() {
		helpers = new HashMap<Byte, LogOperationHelper>();

		for (LogOperationHelper helper : helperSet) {
			for (Byte type : helper.getOperationTypes()) {
				if (helpers.put(type, helper) != null) {
					throw new RuntimeException(String.format(
							"Operation type %s registered twice.", type));
				}
			}
		}
	}

	public LogOperation fromBytes(byte type, ByteBuffer buffer)
			throws LogException {
		LogOperationHelper helper = helpers.get(type);

		if (helper == null) {
			throw new LogException("Unknown log operation type: %s.", type);
		}

		return helper.fromBytes(type, buffer);
	}

	public Loggable createEOT(TxID taID, long prevLSN) {
		return new LogRecord(Loggable.TYPE_EOT, taID, prevLSN, null, -1);
	}

	public Loggable createUpdate(TxID taID, long prevLSN,
			LogOperation createOperation) {
		return new LogRecord(Loggable.TYPE_UPDATE, taID, prevLSN,
				createOperation, -1);
	}

	public Loggable createDummyCLR(TxID taID, long prevLSN, long undoNextLSN) {
		return new LogRecord(Loggable.TYPE_DUMMY, taID, prevLSN, null,
				undoNextLSN);
	}

	public Loggable createCLR(TxID taID, long prevLSN,
			LogOperation createOperation, long undoNextLSN) {
		return new LogRecord(Loggable.TYPE_CLR, taID, prevLSN, createOperation,
				undoNextLSN);
	}
	
	public Loggable createUpdateSpecial(TxID taID, long prevLSN,
			LogOperation createOperation, long undoNextLSN) {
		return new LogRecord(Loggable.TYPE_UPDATE_SPECIAL, taID, prevLSN, createOperation,
				undoNextLSN);
	}

	public Loggable fromBytes(ByteBuffer buffer) throws LogException {
		byte logRecordType = buffer.get();
		TxID taId = TxID.fromBytes(buffer);
		long prevLSN = buffer.getLong();
		long undoNextLSN = -1;
		byte logOperationType = -1;
		LogOperation logOperation = null;

		switch (logRecordType) {
		case Loggable.TYPE_CLR:
		case Loggable.TYPE_UPDATE_SPECIAL:
			undoNextLSN = buffer.getLong();
			logOperationType = buffer.get();
			logOperation = fromBytes(logOperationType, buffer.slice());
			return new LogRecord(logRecordType, taId, prevLSN,
					logOperation, undoNextLSN);
		case Loggable.TYPE_DUMMY:
			undoNextLSN = buffer.getLong();
			return new LogRecord(logRecordType, taId, prevLSN, null,
					undoNextLSN);
		case Loggable.TYPE_UPDATE:
			logOperationType = buffer.get();
			logOperation = fromBytes(logOperationType, buffer.slice());
			return new LogRecord(logRecordType, taId, prevLSN,
					logOperation, -1);
		case Loggable.TYPE_EOT:
			return new LogRecord(logRecordType, taId, prevLSN, null, -1);
		default:
			return new LogRecord(logRecordType, taId, prevLSN, null, -1);
		}
	}
}