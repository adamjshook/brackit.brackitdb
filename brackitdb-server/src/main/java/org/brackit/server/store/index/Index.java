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
package org.brackit.server.store.index;

import java.io.PrintStream;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.tx.Tx;

/**
 * Interface of a key/value-based index.
 * 
 * @author Sebastian Baechle
 * 
 */
public interface Index {
	public static final String PAGE_VERSION = "org.brackit.server.store.index.pageVersion";

	/**
	 * Reads the <code>value</code> of the entry with the given <code>key</code>
	 * of index <code>rootPageID</code>. In a non-unique index only the first
	 * entry is returned
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param key
	 *            key of the entry that should be read
	 * @return <code>value</code> of the index entry with the given
	 *         <code>key</code>
	 * @throws IndexAccessException
	 *             iff an error occurred while accessing the index
	 */
	public byte[] read(Tx transaction, PageID rootPageID, byte[] key)
			throws IndexAccessException;

	/**
	 * Inserts an entry with the given <code>key</code> and the given
	 * <code>value</code> into index <code>rootPageID</code>.
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param key
	 *            key of the entry/entries that should be deleted
	 * @param value
	 *            value of the entry/entries that should be deleted
	 * @throws DuplicateKeyException
	 *             if this is a unique index and there is already an entry with
	 *             this key
	 * @throws IndexAccessException
	 *             if an error occurred while accessing the index, if the
	 */
	public void insert(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException;

	/**
	 * Inserts an entry with the given <code>key</code> and the given
	 * <code>value</code> into index <code>rootPageID</code>. The insertion is
	 * persistent even if the transaction is rolled back.
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param key
	 *            key of the entry/entries that should be deleted
	 * @param value
	 *            value of the entry/entries that should be deleted
	 * @throws DuplicateKeyException
	 *             if this is a unique index and there is already an entry with
	 *             this key
	 * @throws IndexAccessException
	 *             if an error occurred while accessing the index, if the
	 */
	public void insertPersistent(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException;

	/**
	 * Updates the value of the entry with the given <code>key</code> and the
	 * given <code>oldValue</code> in index <code>idxNo</code> with
	 * <code>newValue</code>.
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param key
	 *            key of the entry/entries that should be deleted
	 * @param oldValue
	 *            old value of the entry/entries that should be updated
	 * @param newValue
	 *            new value that should be set
	 * @throws DuplicateKeyException
	 *             if this is a unique index and there is already an entry with
	 *             this key
	 * @throws IndexAccessException
	 *             if an error occurred while accessing the index, if the
	 */
	public void update(Tx transaction, PageID rootPageID, byte[] key,
			byte[] oldValue, byte[] newValue) throws IndexAccessException;

	/**
	 * Deletes the entries with the given <code>key</code> and the given
	 * <code>value</code> from index <code>rootPageID</code>. If the given
	 * <code>value</code> is <code>NULL</code>, any entry with the given
	 * <code>key</code> will be deleted.
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param key
	 *            key of the entry/entries that should be deleted
	 * @param value
	 *            value of the entry/entries that should be deleted
	 * @throws IndexAccessException
	 *             iff an error occurred while accessing the index
	 */
	public void delete(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException;

	/**
	 * Deletes the entries with the given <code>key</code> and the given
	 * <code>value</code> from index <code>rootPageID</code>. If the given
	 * <code>value</code> is <code>NULL</code>, any entry with the given
	 * <code>key</code> will be deleted. The deletion is persistent even if the
	 * transaction is rolled back.
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param key
	 *            key of the entry/entries that should be deleted
	 * @param value
	 *            value of the entry/entries that should be deleted
	 * @throws IndexAccessException
	 *             iff an error occurred while accessing the index
	 */
	public void deletePersistent(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException;

	/**
	 * Open index <code>rootPageID</code> in the given <code>openMode</code>
	 * relative to the given <code>key</code> and <code>value</code>.
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param searchMode
	 *            search mode for the index
	 * @param key
	 *            key that is used by relative {@link SearchMode OpenModes} for
	 *            positioning the iterator
	 * @param value
	 *            value that is used by relative {@link SearchMode OpenModes}
	 *            for positioning the iterator
	 * @param openMode
	 *            defines whether the index should be opened for update
	 *            operations (load, insert, update, delete) or read-only
	 * @return {@link IndexIterator} for iterating over the index search result
	 * @throws IndexAccessException
	 *             iff an error occurred while accessing the index
	 */
	public IndexIterator open(Tx transaction, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode)
			throws IndexAccessException;

	/**
	 * Open index <code>rootPageID</code> in the given <code>openMode</code>
	 * relative to the given <code>key</code> and <code>value</code>.
	 * 
	 * @param transaction
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param searchMode
	 *            search mode for the index
	 * @param key
	 *            key that is used by relative {@link SearchMode OpenModes} for
	 *            positioning the iterator
	 * @param value
	 *            alue that is used by relative {@link SearchMode OpenModes} for
	 *            positioning the iterator
	 * @param openMode
	 *            defines whether the index should be opened for update
	 *            operations (load, insert, update, delete) or read-only
	 * @param hintPageID
	 *            number of the possible target leaf page, which should be used
	 *            optimistically (search key must be bound in page)
	 * @param LSN
	 *            LSN of the possible target leaf page
	 * @return {@link IndexIterator} for iterating over the index search result
	 * @throws IndexAccessException
	 *             iff an error occurred while accessing the index
	 */
	public IndexIterator open(Tx transaction, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode,
			PageID hintPageID, long LSN) throws IndexAccessException;

	/**
	 * Creates a new index in container <code>containerNo</code> for the given
	 * <code>keyType</code> and <code>valueType</code>.
	 * 
	 * @param transaction
	 *            transaction that wants to create the index
	 * @param containerNo
	 *            number of the container where the index should be created
	 * @param keyType
	 *            type of the key fields
	 * @param valueType
	 *            type of the value fields
	 * @param unique
	 *            indicates whether this is a unique index or not
	 * @return the number of the index root page
	 * @throws IndexAccessException
	 *             iff an error occurred during index creation
	 */
	public PageID createIndex(Tx transaction, int containerNo, Field keyType,
			Field valueType, boolean unique) throws IndexAccessException;

	/**
	 * Drop the index <code>rootPageID</code>.
	 * 
	 * @param transaction
	 *            transaction that wants to drop the index
	 * @param rootPageID
	 *            number of the root page
	 * @throws IndexAccessException
	 *             iff an error occurred during index deletion
	 */
	public void dropIndex(Tx transaction, PageID rootPageID)
			throws IndexAccessException;

	/**
	 * Creates a new index in container <code>containerNo</code> for the given
	 * <code>keyType</code> and <code>valueType</code>.
	 * 
	 * @param transaction
	 *            transaction that wants to create the index
	 * @param containerNo
	 *            number of the container where the index should be created
	 * @param keyType
	 *            type of the key fields
	 * @param valueType
	 *            type of the value fields
	 * @param unique
	 *            indicates whether this is a unique index or not
	 * @param compression
	 *            indicates whether this index uses compression or not
	 * @param unitID
	 *            id of the unit the index belongs to
	 * @return the number of the index root page
	 * @throws IndexAccessException
	 *             iff an error occurred during index creation
	 */
	public PageID createIndex(Tx transaction, int containerNo, Field keyType,
			Field valueType, boolean unique, boolean compression, int unitID)
			throws IndexAccessException;

	/**
	 * Traverses the index.
	 * 
	 * @param transaction
	 *            transaction that wants to traverse the index
	 * @param rootPageID
	 *            number if the root page
	 * @param visitor
	 *            vistor
	 * @throws IndexAccessException
	 *             iff an error occurs during the traversal
	 */
	public void traverse(Tx transaction, PageID rootPageID, IndexVisitor visitor)
			throws IndexAccessException;

	/**
	 * Dumps the content of the index.
	 * 
	 * @param transaction
	 *            transaction that wants to dump the index
	 * @param rootPageID
	 *            number if the root page
	 * @param out
	 *            stream to write to
	 * @throws IndexAccessException
	 *             iff an error occurs during the dump
	 */
	public void dump(Tx transaction, PageID rootPageID, PrintStream out)
			throws IndexAccessException;
}