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
package org.brackit.server.store.index;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.external.IndexStatistics;
import org.brackit.server.store.Field;

/**
 * Iterator for easy access of opened indexes.
 * 
 * @author Sebastian Baechle
 * 
 */
public interface IndexIterator {
	/**
	 * Returns the key type of the index.
	 * 
	 * @return the key type of the index.
	 * @throws IndexAccessException
	 */
	public Field getKeyType() throws IndexAccessException;

	/**
	 * Returns the value type of the index.
	 * 
	 * @return the value type of the index
	 * @throws IndexAccessException
	 */
	public Field getValueType() throws IndexAccessException;

	/**
	 * Returns the maximum size for keys in the opened index in bytes.
	 * 
	 * @return maximum size for keys of the opened index in bytes.
	 * @throws IndexAccessException
	 */
	public int getMaxKeySize() throws IndexAccessException;

	/**
	 * Returns the maximum size for inlined values when the keys are at most of
	 * size <code>maxKeySize</code> in the opened index in bytes.
	 * 
	 * @param maxKeySize
	 *            maximum size of index keys
	 * @return maximum size for inlined values in the opened index in bytes.
	 * @throws IndexAccessException
	 */
	public int getMaxInlineValueSize(int maxKeySize)
			throws IndexAccessException;

	/**
	 * Returns the key of the current index entry
	 * 
	 * @return key of the current index entry
	 * @throws IndexAccessException
	 *             if the iterator is not pointing to a valid index entry, of if
	 *             there was an error reading the entry
	 */
	public byte[] getKey() throws IndexAccessException;

	/**
	 * Returns the value of the current index entry
	 * 
	 * @return value of the current index entry
	 * @throws IndexAccessException
	 *             if the iterator is not pointing to a valid index entry, of if
	 *             there was an error reading the entry
	 */
	public byte[] getValue() throws IndexAccessException;

	/**
	 * Inserts a new (key, value) pair at the current position
	 * 
	 * @param key
	 *            key of the new entry
	 * @param value
	 *            value of the new entry
	 * @throws IndexAccessException
	 *             if the insert violates the index integrity constraints or if
	 *             there was an error during the insert
	 */
	public void insert(byte[] key, byte[] value) throws IndexAccessException;

	/**
	 * Inserts a new (key, value) pair at the current position. The insertion is
	 * persistent even if the transaction is rolled back.
	 * 
	 * @param key
	 *            key of the new entry
	 * @param value
	 *            value value of the new entry
	 * @throws IndexAccessException
	 *             if the insert violates the index integrity constraints or if
	 *             there was an error during the insert
	 */
	public void insertPersistent(byte[] key, byte[] value)
			throws IndexAccessException;

	/**
	 * Updates the value of the current entry.
	 * 
	 * @param newValue
	 *            new value of the new entry
	 * @throws IndexAccessException
	 *             if the opened index does not support updates or if there was
	 *             an error during the update
	 */
	public void update(byte[] newValue) throws IndexAccessException;

	/**
	 * Updates the value of the current entry. The update is persistent even if
	 * the transaction is rolled back.
	 * 
	 * @param newValue
	 *            new value of the new entry
	 * @throws IndexAccessException
	 *             if the opened index does not support updates or if there was
	 *             an error during the update
	 */
	public void updatePersistent(byte[] newValue) throws IndexAccessException;

	/**
	 * Deletes the current entry from the index
	 * 
	 * @throws IndexAccessException
	 *             if the iterator is not pointing to a valid index entry, of if
	 *             there was an error deleting the entry
	 */
	public void delete() throws IndexAccessException;

	/**
	 * Deletes the current entry from the index. The deletion is persistent even
	 * if the transaction is rolled back.
	 * 
	 * @throws IndexAccessException
	 *             if the iterator is not pointing to a valid index entry, of if
	 *             there was an error deleting the entry
	 */
	public void deletePersistent() throws IndexAccessException;

	/**
	 * Moves the pointer to the next index entry
	 * 
	 * @return <code>TRUE</code>, iff the iterator has found another entry
	 * @throws IndexAccessException
	 *             if there was an error moving the pointer to the next record
	 */
	public boolean next() throws IndexAccessException;

	/**
	 * Move the pointer to the previous index entry
	 * 
	 * @return <code>TRUE</code>, iff the iterator has found another entry
	 * @throws IndexAccessException
	 *             if there was an error moving the pointer to the previous
	 *             record
	 */
	public boolean previous() throws IndexAccessException;

	/**
	 * Closes the iterator by releasing all held resources of the index
	 * 
	 * @throws IndexAccessException
	 *             if there was an error closing the index
	 */
	public void close() throws IndexAccessException;

	/**
	 * Returns the number of the current page
	 * 
	 * @return the number of the current page
	 * @throws IndexAccessException
	 *             if there was an error reading the pageID
	 */
	public PageID getCurrentPageID() throws IndexAccessException;

	/**
	 * Returns the LSN of the current page
	 * 
	 * @return the LSN of the current page
	 * @throws IndexAccessException
	 *             if there was an error reading the pageID
	 */
	public long getCurrentLSN() throws IndexAccessException;

	/**
	 * After index creation and filling via the iterator, statistics can be
	 * requested
	 * 
	 * @return IndexStatistics info object
	 */
	public IndexStatistics getStatistics();

	/**
	 * Calling this method right after opening the trigger will create index
	 * statistics during index building (see getStatistics())
	 */
	public void triggerStatistics();
}
