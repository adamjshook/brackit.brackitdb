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
package org.brackit.server.store.index.bracket.log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.page.bracket.BracketNodeSequence;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.LogOperationHelper;

/**
 * @author Martin Hiller
 * 
 */
public class BracketIndexLogOperationHelper implements LogOperationHelper {
	private static final ArrayList<Byte> operationTypes;

	static {
		operationTypes = new ArrayList<Byte>();

		operationTypes.add(BracketIndexLogOperation.LEAF_INSERT);
		operationTypes.add(BracketIndexLogOperation.LEAF_DELETE);
		operationTypes.add(BracketIndexLogOperation.LEAF_UPDATE);
		operationTypes.add(BracketIndexLogOperation.LEAF_SMO_INSERT);
		operationTypes.add(BracketIndexLogOperation.LEAF_SMO_DELETE);

		operationTypes.add(BracketIndexLogOperation.BRANCH_INSERT);
		operationTypes.add(BracketIndexLogOperation.BRANCH_DELETE);
		operationTypes.add(BracketIndexLogOperation.BRANCH_UPDATE);

		operationTypes.add(BracketIndexLogOperation.BEFORE_PAGE);
		operationTypes.add(BracketIndexLogOperation.NEXT_PAGE);
		operationTypes.add(BracketIndexLogOperation.PREV_PAGE);

		operationTypes.add(BracketIndexLogOperation.HIGHKEY_UPDATE);

		operationTypes.add(BracketIndexLogOperation.FORMAT);
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
		case BracketIndexLogOperation.BRANCH_INSERT:
		case BracketIndexLogOperation.BRANCH_DELETE:
		case BracketIndexLogOperation.BRANCH_UPDATE:
			return createBranchUpdateLogOperation(type, buffer, pageID,
					rootPageID);

		case BracketIndexLogOperation.LEAF_INSERT:
		case BracketIndexLogOperation.LEAF_DELETE:
		case BracketIndexLogOperation.LEAF_UPDATE:
		case BracketIndexLogOperation.LEAF_SMO_INSERT:
		case BracketIndexLogOperation.LEAF_SMO_DELETE:
			return createNodeSequenceLogOperation(type, buffer, pageID,
					rootPageID);

		case BracketIndexLogOperation.BEFORE_PAGE:
		case BracketIndexLogOperation.NEXT_PAGE:
		case BracketIndexLogOperation.PREV_PAGE:
			return createPointerLogOperation(type, buffer, pageID, rootPageID);

		case BracketIndexLogOperation.HIGHKEY_UPDATE:
			return createHighkeyLogOperation(buffer, pageID, rootPageID);
			
		case BracketIndexLogOperation.FORMAT:
			return createFormatLogOperation(buffer, pageID, rootPageID);

		default:
			throw new LogException("Unknown operation type: %s.", type);
		}
	}

	private LogOperation createBranchUpdateLogOperation(byte type,
			ByteBuffer buffer, PageID pageID, PageID rootPageID) {
		byte[] key = new byte[buffer.getInt()];
		buffer.get(key);
		byte[] value = new byte[buffer.getInt()];
		buffer.get(value);
		byte[] oldValue = null;

		if (type == BracketIndexLogOperation.BRANCH_UPDATE) {
			oldValue = new byte[buffer.getInt()];
			buffer.get(oldValue);
		}

		return new BranchUpdateLogOperation(type, pageID, rootPageID, key,
				oldValue, value);
	}

	private LogOperation createPointerLogOperation(byte type,
			ByteBuffer buffer, PageID pageID, PageID rootPageID) {
		PageID oldTarget = PageID.read(buffer);
		PageID target = PageID.read(buffer);

		return new PointerLogOperation(type, pageID, rootPageID, oldTarget,
				target);
	}

	private LogOperation createNodeSequenceLogOperation(byte type,
			ByteBuffer buffer, PageID pageID, PageID rootPageID) {

		int length = buffer.getShort() & 0xFFFF;
		BracketNodeSequence nodes = BracketNodeSequence.read(length, buffer);

		return new NodeSequenceLogOperation(type, pageID, rootPageID, nodes);
	}

	private LogOperation createHighkeyLogOperation(ByteBuffer buffer,
			PageID pageID, PageID rootPageID) {

		byte[] oldHighkey = null;
		byte[] newHighKey = null;

		int oldLength = buffer.getShort() & 0xFFFF;
		if (oldLength > 0) {
			oldHighkey = new byte[oldLength];
			buffer.get(oldHighkey);
		}

		int newLength = buffer.getShort() & 0xFFFF;
		if (newLength > 0) {
			newHighKey = new byte[newLength];
			buffer.get(newHighKey);
		}

		return new HighkeyLogOperation(pageID, rootPageID, oldHighkey,
				newHighKey);
	}

	private LogOperation createFormatLogOperation(ByteBuffer buffer,
			PageID pageID, PageID rootPageID) {

		byte flags = buffer.get();
		boolean oldLeaf = ((flags >>> 3) & 1) > 0;
		boolean leaf = ((flags >>> 2) & 1) > 0;
		boolean oldCompressed = ((flags >>> 1) & 1) > 0;
		boolean compressed = (flags & 1) > 0;

		int oldUnitID = buffer.getInt();
		int unitID = buffer.getInt();
		int oldHeight = buffer.get() & 0xFF;
		int height = buffer.get() & 0xFF;

		return new FormatLogOperation(pageID, rootPageID, oldLeaf, leaf,
				oldUnitID, unitID, oldHeight, height, oldCompressed, compressed);
	}
}
