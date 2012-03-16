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
package org.brackit.server.store.page.slot;

import java.util.ArrayList;
import java.util.Arrays;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.Handle;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class CachingSlottedPage extends SlottedPage {
	private class SlotCache {
		ArrayList<Tuple> tuples;
	}

	protected SlotCache cache;

	public CachingSlottedPage(Buffer buffer, Handle handle) {
		super(buffer, handle);

		cache = (SlotCache) handle.getCache();

		if (cache == null) {
			handle.setCache(new SlotCache());
			cache = (SlotCache) handle.getCache();
		}

		fillCache();
	}

	@Override
	public void clear() {
		clearCache();
		super.clear();
	}

	private void clearCache() {
		cache.tuples = new ArrayList<Tuple>();
	}

	private void fillCache() {
		synchronized (cache) {
			if (cache.tuples == null) {
				int recordCount = super.getRecordCount();
				ArrayList<Tuple> tuples = new ArrayList<Tuple>();

				for (int slotNo = 0; slotNo < recordCount; slotNo++) {
					tuples.add(super.read(slotNo, DEFAULT_PROJECTION, null));
				}
				cache.tuples = tuples;
			}
		}
	}

	@Override
	public void delete(int slotNo) {
		super.delete(slotNo);
		cache.tuples.remove(slotNo);
	}

	@Override
	public Tuple read(int slotNo, Projection projection, Tuple tuple) {
		if ((slotNo < 0) || (slotNo >= cache.tuples.size())) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		Tuple read = cache.tuples.get(slotNo);

		if (tuple == null) {
			tuple = new ArrayTuple(read.getSize());
		}

		int pos = 0;
		for (int i = 0; i < read.getSize(); i++) {
			if (projection.selectField(i)) {
				tuple.set(pos++, read.get(i));
			}
		}

		return tuple;
	}

	@Override
	public Tuple read(int slotNo) {
		if ((slotNo < 0) || (slotNo >= cache.tuples.size())) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		Tuple tuple = cache.tuples.get(slotNo);

		if (tuple != null) {
			tuple = new ArrayTuple(tuple.toArray());
		}

		return tuple;
	}

	@Override
	public byte[] readField(int slotNo, int fieldNo) {
		if ((slotNo < 0) || (slotNo >= cache.tuples.size())) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		Tuple read = cache.tuples.get(slotNo);

		if ((fieldNo < 0) || (read.getSize() < fieldNo)) {
			throw new RuntimeException(String.format(
					"Invalid field number: %s", fieldNo));
		}

		byte[] field = read.get(fieldNo);

		if (field != null) {
			// only return a copy of the cached field to prevent inconsistency
			// when the returned array is modified outside
			field = Arrays.copyOf(field, field.length);
		}

		return field;
	}

	@Override
	public boolean write(int slotNo, Tuple tuple, boolean update,
			boolean prefixCompression) {
		boolean success = super.write(slotNo, tuple, update, prefixCompression);

		if (success) {
			if ((update) && (slotNo < cache.tuples.size())) {
				cache.tuples.set(slotNo, tuple);
			} else {
				cache.tuples.add(slotNo, tuple);
			}
		}

		return success;
	}

	@Override
	public boolean write(int slotNo, Tuple tuple, boolean update) {
		return write(slotNo, tuple, update, false);
	}

	@Override
	public boolean writeField(int slotNo, int fieldNo, byte[] newValue) {
		boolean success = super.writeField(slotNo, fieldNo, newValue);

		if (success) {
			cache.tuples.get(slotNo).set(fieldNo, newValue);
		}

		return success;
	}
}
