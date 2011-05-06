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
package org.brackit.server.store.page.slot;

import java.util.Arrays;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.Handle;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class FieldCachingSlottedPage extends SlottedPage {
	private class SlotCache {
		byte[][] fields;
		int size;
	}

	private final int fieldNo;

	protected SlotCache cache;

	public FieldCachingSlottedPage(Buffer buffer, Handle handle) {
		this(buffer, handle, 0);
	}

	public FieldCachingSlottedPage(Buffer buffer, Handle handle, int fieldNo,
			int reserved) {
		super(buffer, handle, reserved);
		this.fieldNo = fieldNo;

		cache = (SlotCache) handle.getCache();

		if (cache == null) {
			handle.setCache(new SlotCache());
			cache = (SlotCache) handle.getCache();
		}

		fillCache();
	}

	public FieldCachingSlottedPage(Buffer buffer, Handle handle, int fieldNo) {
		this(buffer, handle, fieldNo, 0);
	}

	@Override
	public void clear() {
		clearCache();
		super.clear();
	}

	private void clearCache() {
		cache.fields = new byte[10][];
	}

	private void fillCache() {
		synchronized (cache) {
			if (cache.fields == null) {
				int recordCount = super.getRecordCount();
				cache.fields = new byte[(recordCount != 0) ? recordCount : 10][];

				for (int slotNo = 0; slotNo < recordCount; slotNo++) {
					try {
						insertIntoFieldCache(slotNo, super.readField(slotNo,
								fieldNo));
					} catch (Exception e) {
						System.out.println("Tried to read SlotNo " + slotNo);
						System.out.println("EntryCount " + recordCount);
					}
				}
			}
		}
	}

	@Override
	public void delete(int slotNo) {
		super.delete(slotNo);
		if (cache.size > slotNo) {
			System.arraycopy(cache.fields, slotNo + 1, cache.fields, slotNo,
					cache.size - slotNo - 1);
			cache.size--;
		}
	}

	@Override
	public byte[] readField(int slotNo, int fieldNo) {
		if ((slotNo < 0) || (slotNo >= cache.size)) {
			int recordCount = getRecordCount();
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		byte[] field;

		if (fieldNo == this.fieldNo) {
			field = cache.fields[slotNo];
		} else {
			field = super.readField(slotNo, fieldNo);
		}

		// if (field != null)
		// {
		// // only return a copy of the cached field to prevent inconsistency
		// // when the returned array is modified outside
		// field = Arrays.copyOf(field, field.length);
		// }

		return field;
	}

	@Override
	public boolean write(int slotNo, Tuple tuple, boolean update,
			boolean prefixCompression) {
		boolean success = super.write(slotNo, tuple, update, prefixCompression);

		if (success) {
			if ((update) && (slotNo < cache.size)) {
				cache.fields[slotNo] = tuple.get(fieldNo);
			} else {
				insertIntoFieldCache(slotNo, tuple.get(fieldNo));
			}
		}

		return success;
	}

	private void insertIntoFieldCache(int slotNo, byte[] field) {
		if (cache.fields.length == cache.size) {
			// grow cache
			int newCapacity = (cache.fields.length * 3) / 2 + 1;
			cache.fields = Arrays.copyOf(cache.fields, newCapacity);
		}

		if (slotNo < cache.size) {
			// shift following records right
			System.arraycopy(cache.fields, slotNo, cache.fields, slotNo + 1,
					cache.size - slotNo);
		}

		cache.size++;
		cache.fields[slotNo] = field;
	}

	@Override
	public boolean write(int slotNo, Tuple tuple, boolean update) {
		return write(slotNo, tuple, update, false);
	}

	@Override
	public boolean writeField(int slotNo, int fieldNo, byte[] newValue) {
		boolean success = super.writeField(slotNo, fieldNo, newValue);

		if ((success) && (fieldNo == this.fieldNo)) {
			cache.fields[slotNo] = newValue;
		}

		return success;
	}
}
