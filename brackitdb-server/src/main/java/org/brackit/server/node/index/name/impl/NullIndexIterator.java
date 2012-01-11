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
package org.brackit.server.node.index.name.impl;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.external.IndexStatistics;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;

/**
 * Dummy iterator for non-existing node reference indexes.
 * 
 * @author Sebastian Baechle
 * 
 */
public class NullIndexIterator implements IndexIterator {
	private final Field keyType;

	private final Field valueType;

	public NullIndexIterator(Field keyType, Field valueType) {
		this.keyType = keyType;
		this.valueType = valueType;
	}

	@Override
	public void close() {
	}

	@Override
	public void delete() throws IndexAccessException {
		throw new IndexAccessException("No entry to delete");
	}

	@Override
	public void deletePersistent() throws IndexAccessException {
		throw new IndexAccessException("No entry to delete");
	}

	@Override
	public long getCurrentLSN() throws IndexAccessException {
		return -1;
	}

	@Override
	public PageID getCurrentPageID() throws IndexAccessException {
		return null;
	}

	@Override
	public byte[] getKey() throws IndexAccessException {
		return null;
	}

	@Override
	public Field getKeyType() throws IndexAccessException {
		return keyType;
	}

	@Override
	public byte[] getValue() throws IndexAccessException {
		return null;
	}

	@Override
	public Field getValueType() throws IndexAccessException {
		return valueType;
	}

	@Override
	public void insert(byte[] key, byte[] value) throws IndexAccessException {
		throw new IndexAccessException("Insert is not allowed");
	}

	@Override
	public void insertPersistent(byte[] key, byte[] value)
			throws IndexAccessException {
		throw new IndexAccessException("Insert is not allowed");
	}

	@Override
	public void update(byte[] newValue) throws IndexAccessException {
		throw new IndexAccessException("Update is not allowed");
	}

	@Override
	public void updatePersistent(byte[] newValue) throws IndexAccessException {
		throw new IndexAccessException("Update is not allowed");
	}

	@Override
	public boolean next() throws IndexAccessException {
		return false;
	}

	@Override
	public boolean previous() throws IndexAccessException {
		return false;
	}

	@Override
	public int getMaxInlineValueSize(int maxKeySize)
			throws IndexAccessException {
		return 0;
	}

	@Override
	public int getMaxKeySize() throws IndexAccessException {
		return 0;
	}

	@Override
	public IndexStatistics getStatistics() {
		return null;
	}

	@Override
	public void triggerStatistics() {
	}
}
