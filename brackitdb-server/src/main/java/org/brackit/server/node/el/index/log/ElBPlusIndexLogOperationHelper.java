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
package org.brackit.server.node.el.index.log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.aries.log.UpdateLogOperation;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.LogOperationHelper;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElBPlusIndexLogOperationHelper implements LogOperationHelper {
	private static final ArrayList<Byte> operationTypes;

	static {
		operationTypes = new ArrayList<Byte>();
		operationTypes.add(ElUpdateLogOperation.USER_INSERT_SPECIAL);
		operationTypes.add(ElUpdateLogOperation.USER_DELETE_SPECIAL);
	}

	@Override
	public Collection<Byte> getOperationTypes() {
		return operationTypes;
	}

	@Override
	public LogOperation fromBytes(byte type, ByteBuffer buffer)
			throws LogException {
		PageID pageID = PageID.read(buffer);
		PageID rootPageID = PageID.read(buffer);

		switch (type) {
		case ElUpdateLogOperation.USER_INSERT_SPECIAL:
		case ElUpdateLogOperation.USER_DELETE_SPECIAL:
			return createUpdateLogOp(type, buffer, pageID, rootPageID);
		default:
			throw new LogException("Unknown operation type: %s.", type);
		}
	}

	public static UpdateLogOperation createUpdateLogOp(byte type,
			PageID pageID, PageID rootPageID, byte[] key, byte[] value,
			byte[] oldValue, int level) {
		return new ElUpdateLogOperation(type, pageID, rootPageID, key,
				oldValue, value, level);
	}

	private LogOperation createUpdateLogOp(byte type, ByteBuffer buffer,
			PageID pageID, PageID rootPageID) {
		byte[] key = new byte[buffer.getInt()];
		buffer.get(key);
		byte[] value = new byte[buffer.getInt()];
		buffer.get(value);
		byte[] oldValue = null;
		int level = buffer.get();

		return createUpdateLogOp(type, pageID, rootPageID, key, value,
				oldValue, level);
	}
}
