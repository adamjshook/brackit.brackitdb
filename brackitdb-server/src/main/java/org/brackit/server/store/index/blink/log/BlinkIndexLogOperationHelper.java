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
package org.brackit.server.store.index.blink.log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.LogOperationHelper;

/**
 * @author Sebastian Baechle
 * 
 */
public class BlinkIndexLogOperationHelper implements LogOperationHelper {
	private static final ArrayList<Byte> operationTypes;

	static {
		operationTypes = new ArrayList<Byte>();
		operationTypes.add(BlinkIndexLogOperation.USER_INSERT);
		operationTypes.add(BlinkIndexLogOperation.USER_DELETE);
		operationTypes.add(BlinkIndexLogOperation.USER_UPDATE);

		operationTypes.add(BlinkIndexLogOperation.SMO_INSERT);
		operationTypes.add(BlinkIndexLogOperation.SMO_UPDATE);
		operationTypes.add(BlinkIndexLogOperation.SMO_DELETE);

		operationTypes.add(BlinkIndexLogOperation.BEFORE_PAGE);
		operationTypes.add(BlinkIndexLogOperation.NEXT_PAGE);
		operationTypes.add(BlinkIndexLogOperation.PREV_PAGE);

		operationTypes.add(BlinkIndexLogOperation.FORMAT);
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
		case BlinkIndexLogOperation.USER_INSERT:
		case BlinkIndexLogOperation.USER_DELETE:
		case BlinkIndexLogOperation.USER_UPDATE:
		case BlinkIndexLogOperation.SMO_INSERT:
		case BlinkIndexLogOperation.SMO_DELETE:
		case BlinkIndexLogOperation.SMO_UPDATE:
			return createUpdateLogOperation(type, buffer, pageID, rootPageID);
		case BlinkIndexLogOperation.BEFORE_PAGE:
		case BlinkIndexLogOperation.NEXT_PAGE:
		case BlinkIndexLogOperation.PREV_PAGE:
			return createrPointerLogOperation(type, buffer, pageID, rootPageID);
		case BlinkIndexLogOperation.FORMAT:
			return createFormatLogOperation(buffer, pageID, rootPageID);
		default:
			throw new LogException("Unknown operation type: %s.", type);
		}
	}

	public static UpdateLogOperation createUpdateLogOperation(byte type,
			PageID pageID, PageID rootPageID, byte[] key, byte[] value,
			byte[] oldValue) {
		return new UpdateLogOperation(type, pageID, rootPageID, key, oldValue,
				value);
	}

	public static FormatLogOperation createFormatLogOperation(PageID pageID,
			int oldUnitID, int unitID, PageID rootPageID, int oldPageType,
			int pageType, Field oldKeyType, Field keyType, Field oldValueType,
			Field valueType, int oldHeight, int height, boolean oldUnique,
			boolean unique, boolean oldCompression, boolean compression) {
		return new FormatLogOperation(pageID, oldUnitID, unitID, rootPageID,
				oldPageType, pageType, oldKeyType, keyType, oldValueType,
				valueType, oldHeight, height, oldUnique, unique,
				oldCompression, compression);
	}

	public static PointerLogOperation createrPointerLogOperation(byte type,
			PageID pageID, PageID rootPageID, PageID oldTarget, PageID target) {
		return new PointerLogOperation(type, pageID, rootPageID, oldTarget,
				target);
	}

	private LogOperation createUpdateLogOperation(byte type, ByteBuffer buffer,
			PageID pageID, PageID rootPageID) {
		byte[] key = new byte[buffer.getInt()];
		buffer.get(key);
		byte[] value = new byte[buffer.getInt()];
		buffer.get(value);
		byte[] oldValue = null;

		if ((type == BlinkIndexLogOperation.USER_UPDATE)
				|| (type == BlinkIndexLogOperation.SMO_UPDATE)) {
			oldValue = new byte[buffer.getInt()];
			buffer.get(oldValue);
		}

		return createUpdateLogOperation(type, pageID, rootPageID, key, value,
				oldValue);
	}

	private LogOperation createrPointerLogOperation(byte type,
			ByteBuffer buffer, PageID pageID, PageID rootPageID) {
		PageID oldTarget = PageID.read(buffer);
		PageID target = PageID.read(buffer);

		return createrPointerLogOperation(type, pageID, rootPageID, oldTarget,
				target);
	}

	private LogOperation createFormatLogOperation(ByteBuffer buffer,
			PageID pageID, PageID rootPageID) {
		int oldUnitID = buffer.getInt();
		int unitID = buffer.getInt();
		int oldPageType = buffer.get();
		int pageType = buffer.get();
		Field oldKeyType = Field.fromId(buffer.get());
		Field keyType = Field.fromId(buffer.get());
		Field oldValueType = Field.fromId(buffer.get());
		Field valueType = Field.fromId(buffer.get());
		int oldHeight = buffer.get();
		int height = buffer.getInt();
		boolean oldUnique = (buffer.get() != 0);
		boolean unique = (buffer.get() != 0);
		boolean oldCompression = (buffer.get() != 0);
		boolean compression = (buffer.get() != 0);

		return createFormatLogOperation(pageID, oldUnitID, unitID, rootPageID,
				oldPageType, pageType, oldKeyType, keyType, oldValueType,
				valueType, oldHeight, height, oldUnique, unique,
				oldCompression, compression);
	}
}
