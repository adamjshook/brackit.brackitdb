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
package org.brackit.server.store.page.keyvalue;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.store.page.RecordFlag;
import org.brackit.server.store.page.slot.ArrayTuple;
import org.brackit.server.store.page.slot.FieldCachingSlottedPage;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SlottedKeyValuePage extends FieldCachingSlottedPage implements
		KeyValuePage {
	public SlottedKeyValuePage(Buffer buffer, Handle handle, int reserved) {
		super(buffer, handle, 0, reserved);
	}

	public SlottedKeyValuePage(Buffer buffer, Handle handle) {
		super(buffer, handle, 0);
	}

	@Override
	public byte[] getKey(int pos) {
		return readField(pos, 0);
	}

	@Override
	public int getUsedSpace(int pos) {
		return usedSpace(pos);
	}

	@Override
	public byte[] getValue(int pos) {
		return readField(pos, 1);
	}

	@Override
	public int calcMaxInlineValueSize(int minNoOfEntries, int maxKeySize) {
		int maxRequiredBytesForKey = (maxKeySize < 15) ? 1
				: (maxKeySize < 255) ? 3 : 5;
		int leftForValuesTotal = getUsableSpace()
				- (minNoOfEntries * (headerSize + maxRequiredBytesForKey));
		int leftForValue = leftForValuesTotal / 3;
		leftForValue -= (leftForValue < 255) ? 1 : 3;
		return leftForValue;
	}

	@Override
	public boolean insert(int pos, byte[] key, byte[] value, boolean compressed) {
		return super.write(pos, new ArrayTuple(new byte[][] { key, value }),
				false, compressed);
	}

	@Override
	public int requiredSpaceForInsert(int pos, byte[] insertKey,
			byte[] insertValue, boolean compressed) {
		return requiredSpaceForInsert(pos, new ArrayTuple(new byte[][] {
				insertKey, insertValue }), compressed);
	}

	@Override
	public int requiredSpaceForUpdate(int pos, byte[] key, byte[] value,
			boolean compressed) {
		return requiredSpaceForUpdate(pos, new ArrayTuple(new byte[][] { key,
				value }), compressed);
	}

	@Override
	public boolean setKey(int pos, byte[] key) {
		return writeField(pos, 0, key);
	}

	@Override
	public boolean setValue(int pos, byte[] value) {
		return writeField(pos, 1, value);
	}

	@Override
	public boolean update(int pos, byte[] key, byte[] value) {
		return super.write(pos, new ArrayTuple(new byte[][] { key, value }),
				true, checkFlag(pos, RecordFlag.PREFIX_COMPRESSION));
	}
}
