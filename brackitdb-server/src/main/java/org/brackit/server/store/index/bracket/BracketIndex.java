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
package org.brackit.server.store.index.bracket;

import java.io.PrintStream;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketAttributeTuple;
import org.brackit.server.node.bracket.BracketLocator;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.xdm.DocumentException;

/**
 * @author Martin Hiller
 * 
 */
public interface BracketIndex {

	/**
	 * Performs a navigation in the index <code>rootPageID</code> and opens it
	 * in the given <code>openMode</code>.
	 * 
	 * @param tx
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param navMode
	 *            navigation modes that is supposed to be performed
	 * @param key
	 *            the reference node's DeweyID for the navigation
	 * @param openMode
	 *            defines whether the index should be opened for update
	 *            operations (load, insert, update, delete) or read-only
	 * @return {@link BracketIter} for iterating over the index search result;
	 *         or null, if the the specified node was not found
	 * @throws IndexAccessException
	 *             iff an error occurred while accessing the index
	 */
	public BracketIter open(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode) throws IndexAccessException;

	/**
	 * Performs a navigation in the index <code>rootPageID</code> and opens it
	 * in the given <code>openMode</code>.
	 * 
	 * @param tx
	 *            transaction that wants to open the index
	 * @param rootPageID
	 *            number of the root page
	 * @param navMode
	 *            navigation modes that is supposed to be performed
	 * @param key
	 *            the reference node's DeweyID for the navigation
	 * @param openMode
	 *            defines whether the index should be opened for update
	 *            operations (load, insert, update, delete) or read-only
	 * @param hintPageInfo
	 *            hint page information that makes the navigation much faster
	 * @return {@link BracketIter} for iterating over the index search result;
	 *         or null, if the the specified node was not found
	 * @throws IndexAccessException
	 *             iff an error occurred while accessing the index
	 */
	public BracketIter open(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode, HintPageInformation hintPageInfo)
			throws IndexAccessException;

	/**
	 * Drop the index <code>rootPageID</code>.
	 * 
	 * @param tx
	 *            tx that wants to drop the index
	 * @param rootPageID
	 *            number of the root page
	 * @throws IndexAccessException
	 *             iff an error occurred during index deletion
	 */
	public void dropIndex(Tx tx, PageID rootPageID) throws IndexAccessException;

	/**
	 * Creates a new index in container <code>containerNo</code>.
	 * 
	 * @param tx
	 *            tx that wants to create the index
	 * @param containerNo
	 *            number of the container where the index should be created
	 * @return the number of the index root page
	 * @throws IndexAccessException
	 *             iff an error occurred during index creation
	 */
	public PageID createIndex(Tx tx, int containerNo)
			throws IndexAccessException;

	/**
	 * Creates a new index in container <code>containerNo</code> for the given
	 * <code>keyType</code> and <code>valueType</code>.
	 * 
	 * @param tx
	 *            tx that wants to create the index
	 * @param containerNo
	 *            number of the container where the index should be created
	 * @param unitID
	 *            id of the unit the index belongs to
	 * @return the number of the index root page
	 * @throws IndexAccessException
	 *             iff an error occurred during index creation
	 */
	public PageID createIndex(Tx tx, int containerNo, int unitID)
			throws IndexAccessException;

	/**
	 * Dumps the content of the index.
	 * 
	 * @param tx
	 *            tx that wants to dump the index
	 * @param rootPageID
	 *            number if the root page
	 * @param out
	 *            stream to write to
	 * @throws IndexAccessException
	 *             iff an error occurs during the dump
	 */
	public void dump(Tx tx, PageID rootPageID, PrintStream out)
			throws IndexAccessException;

	public String printLeafScannerStats(NavigationMode navMode)
			throws IndexAccessException;

	public StreamIterator openChildStream(BracketLocator locator,
			XTCdeweyID parentDeweyID, HintPageInformation hintPageInfo,
			BracketFilter filter);

	public StreamIterator openSubtreeStream(BracketLocator locator,
			XTCdeweyID subtreeRoot, HintPageInformation hintPageInfo,
			BracketFilter filter, boolean self, boolean skipAttributes);

	public StreamIterator openAttributeStream(BracketLocator locator,
			XTCdeweyID elementDeweyID, HintPageInformation hintPageInfo,
			BracketFilter filter);

	/**
	 * Opens the index for a (bulk) insert operation.
	 */
	public InsertController openForInsert(Tx tx, PageID rootPageID,
			OpenMode openMode, XTCdeweyID startInsertKey)
			throws IndexAccessException;

	/**
	 * Sets an attribute. Returns the new attribute node and (if it is the case)
	 * the old overwritten attribute.
	 */
	public BracketAttributeTuple setAttribute(BracketNode element, QNm name,
			Atomic value) throws IndexAccessException, DocumentException;

	/**
	 * Opens a new AttributeStream at the position where the given iterator
	 * points to.
	 */
	public StreamIterator forkAttributeStream(StreamIterator origin,
			BracketFilter filter) throws DocumentException;

	/**
	 * Opens a new ChildStream at the position where the given iterator points
	 * to.
	 */
	public StreamIterator forkChildStream(StreamIterator origin,
			BracketFilter filter) throws DocumentException;

	/**
	 * Opens a new SubtreeStream at the position where the given iterator points
	 * to.
	 */
	public StreamIterator forkSubtreeStream(StreamIterator origin,
			BracketFilter filter, boolean self, boolean skipAttributes)
			throws DocumentException;
}
