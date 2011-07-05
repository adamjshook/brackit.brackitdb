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
package org.brackit.server.store.page.keyvalue;

import java.util.Arrays;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.Handle;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class CachingKeyValuePageImpl extends KeyValuePageImpl {
	private class SlotCache {
		int keySize;
		byte[][] keys;
		int headerSize;
		int[] offsets;
	}

	protected SlotCache cache;

	public CachingKeyValuePageImpl(Buffer buffer, Handle handle, int reserved) {
		super(buffer, handle, reserved);

		cache = (SlotCache) handle.getCache();

		if (cache == null) {
			handle.setCache(new SlotCache());
			cache = (SlotCache) handle.getCache();
		}

		fillCache();
	}

	public CachingKeyValuePageImpl(Buffer buffer, Handle handle) {
		this(buffer, handle, 0);
	}

	@Override
	public void clear() {
		super.clear();
		clearCache();
	}

	private void clearCache() {
		int size = 10;
		byte[][] keys = new byte[size][];
		int[] offsets = new int[size];
		int[] lengths = new int[size];

		cache.keys = keys;
		cache.offsets = offsets;
		cache.keySize = 0;
		cache.headerSize = 0;
	}

	private void fillCache() {
		synchronized (cache) {
			if (cache.keys == null) {
				int recordCount = super.getRecordCount();
				int size = (recordCount != 0) ? recordCount : 10;
				byte[][] keys = new byte[size][];
				int[] offsets = new int[size];
				int[] lengths = new int[size];

				cache.keys = keys;
				cache.offsets = offsets;
				cache.keySize = 0;
				cache.headerSize = 0;

				for (int pos = 0; pos < recordCount; pos++) {
					getHeaderOffset(pos);
					getKey(pos);
				}
			}
		}
	}

	@Override
	public void delete(int pos) {
		super.delete(pos);

		deleteFromKeyCache(pos);
		deleteFromOffsetLengthCache(pos);
	}

	@Override
	protected int getHeaderOffset(int pos) {
		if ((pos >= 0) && (pos < cache.headerSize)) {
			return cache.offsets[pos];
		} else {
			int offset = super.getHeaderOffset(pos);

			if ((pos == cache.headerSize) && (pos < getRecordCount())) {
				insertIntoOffsetLengthCache(pos, offset, super
						.calcLengthFromEntry(offset));
			}

			return offset;
		}
	}

	@Override
	public byte[] getKey(int pos) {
		byte[] key;

		if ((pos >= 0) && (pos < cache.keySize)) {
			key = cache.keys[pos];
		} else {
			key = super.getKey(pos);

			if (pos == cache.keySize) {
				insertIntoKeyCache(pos, key);
			}
		}

		if (key != null) {
			key = Arrays.copyOf(key, key.length);
		}

		return key;
	}

	@Override
	public boolean insert(int pos, byte[] key, byte[] value, boolean compressed) {
		if (super.insert(pos, key, value, compressed)) {
			insertIntoKeyCache(pos, key);
			return true;
		}
		return false;
	}

	@Override
	protected int insertHeader(int pos, int offset, int length, byte flags) {
		insertIntoOffsetLengthCache(pos, offset, length);
		return super.insertHeader(pos, offset, length, flags);
	}

	@Override
	protected int updateHeader(int pos, int newOffset, int newLength, byte flags) {
		updateOffsetLengthCache(pos, newOffset, newLength);
		return super.updateHeader(pos, newOffset, newLength, flags);
	}

	@Override
	public boolean setKey(int pos, byte[] key) {
		if (super.setKey(pos, key)) {
			updateKeyCache(pos, key);
			return true;
		}
		return false;
	}

	@Override
	public boolean update(int pos, byte[] key, byte[] value) {
		if (super.update(pos, key, value)) {
			updateKeyCache(pos, key);
			return true;
		}
		return false;
	}

	private void deleteFromKeyCache(int pos) {
		if (cache.keySize > pos) {
			System.arraycopy(cache.keys, pos + 1, cache.keys, pos,
					cache.keySize - pos - 1);
			cache.keySize--;
		}
	}

	private void deleteFromOffsetLengthCache(int pos) {
		if (cache.headerSize > pos) {
			if ((pos < cache.headerSize - 1)
					&& (cache.offsets[pos + 1] != cache.offsets[pos])) {
				int oldLength = cache.offsets[pos + 1] - cache.offsets[pos];

				for (int i = cache.headerSize - 1; i > pos; i--) {
					cache.offsets[i] -= oldLength;
				}
			}

			System.arraycopy(cache.offsets, pos + 1, cache.offsets, pos,
					cache.headerSize - pos - 1);
			cache.headerSize--;
		}
	}

	private void insertIntoKeyCache(int pos, byte[] key) {
		if (cache.keys.length == cache.keySize) {
			// grow cache
			int newCapacity = (cache.keys.length * 3) / 2 + 1;
			cache.keys = Arrays.copyOf(cache.keys, newCapacity);
		}

		if (pos < cache.headerSize) {
			// shift following records right
			System.arraycopy(cache.keys, pos, cache.keys, pos + 1,
					cache.keySize - pos);
		}

		cache.keySize++;
		cache.keys[pos] = key;
	}

	private void updateKeyCache(int pos, byte[] key) {
		if (pos < cache.keySize) {
			cache.keys[pos] = key;
		}
	}

	private void insertIntoOffsetLengthCache(int pos, int offset, int length) {
		if (cache.offsets.length == cache.headerSize + 1) {
			// grow cache
			int newCapacity = (cache.offsets.length * 3) / 2 + 1;
			cache.offsets = Arrays.copyOf(cache.offsets, newCapacity);
		}

		if (pos < cache.headerSize) {
			// shift following records right
			System.arraycopy(cache.offsets, pos, cache.offsets, pos + 1,
					cache.headerSize - pos);

			// shift offsets following records before update if not already done
			// not necessary with compression because they were shifted via
			// update before
			if (cache.offsets[pos + 1] != offset + length) {
				for (int i = pos + 1; i <= cache.headerSize; i++) {
					cache.offsets[i] += length;
				}
			}
		}

		cache.headerSize++;
		cache.offsets[pos] = offset;
	}

	private void updateOffsetLengthCache(int pos, int offset, int newLength) {
		if (pos + 1 < cache.headerSize) {
			// shift offsets following records before update
			int oldLength = cache.offsets[pos + 1] - cache.offsets[pos];

			int diff = ((newLength - oldLength) + (offset - cache.offsets[pos]));

			if (diff != 0) {
				for (int i = pos + 1; i < cache.headerSize; i++) {
					cache.offsets[i] += diff;
				}
			}

			cache.offsets[pos] = offset;
		}
	}
}