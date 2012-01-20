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
package org.brackit.server.store.index.bracket;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketLocator;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.page.bracket.BracketNodeSequence;
import org.brackit.server.tx.Tx;

/**
 * @author Martin Hiller
 * 
 */
public final class InsertController {

	private final PageID rootPageID;
	private final BracketTree tree;
	private final Tx tx;
	private final boolean doLog;
	private final XTCdeweyID startInsertKey;

	private Leaf page;

	/**
	 * Opens the index for insertion of the given DeweyID. If this DeweyID is
	 * null, the index will be opened for the insertion of a new document.
	 */
	public InsertController(Tx tx, PageID rootPageID, BracketTree tree,
			OpenMode openMode, XTCdeweyID startInsertKey)
			throws IndexAccessException {
		this.rootPageID = rootPageID;
		this.tree = tree;
		this.tx = tx;
		if (!openMode.forUpdate()) {
			throw new IndexAccessException(
					"InsertController can only be opened for updates.");
		}
		this.doLog = openMode.doLog();

		try {

			// open index for insertion
			if (startInsertKey != null) {
				page = tree.openInternal(tx, rootPageID,
						NavigationMode.TO_INSERT_POS, startInsertKey, openMode,
						null, null);
			} else {
				// open index at the last record
				// for an empty index, it returns the (empty) root page
				page = tree.openInternal(tx, rootPageID,
						NavigationMode.LAST, null, openMode, null, null);
				
				int newDocNumber = 0;
				if (!page.isBeforeFirst()) {
					// assign new document number
					newDocNumber = page.getKey().docID.getDocNumber() + 1;
				}
				
				// new DocID assigned
				startInsertKey = new XTCdeweyID(new DocID(rootPageID.value(), newDocNumber));

				// if there is an empty last page, we have to decide where to
				// insert (depending on the highkey)
				if (page.getNextPageID() != null) {
					
					if (startInsertKey.compareTo(page.getHighKey()) >= 0) {
						// insert into next page
						// load next page and release current page
						page = tree.getNextPage(tx, rootPageID, page, openMode, true);
					}
				}
			}
			
			this.startInsertKey = startInsertKey;

			if (page == null) {
				throw new IndexAccessException(
						"Index could not navigate to the initial insert position.");
			}

		} catch (IndexOperationException e) {
			if (page != null) {
				page.cleanup();
				page = null;
			}
			throw new IndexAccessException(e);
		}
	}

	public void insert(XTCdeweyID deweyID, byte[] value, int ancestorsToInsert)
			throws IndexAccessException {

		try {

			// create sequence from record
			BracketNodeSequence nodesToInsert = tree.recordToSequence(tx, page,
					deweyID, value, ancestorsToInsert);

			// insert into page
			if (!page
					.insertSequenceAfter(nodesToInsert, false, doLog, -1, true)) {
				// split necessary

				// log already inserted nodes
				page.bulkLog(false, -1);

				// split + insert
				page = tree.splitInsert(tx, rootPageID, page,
						nodesToInsert, doLog);
			}

		} catch (IndexOperationException e) {
			close();
			throw new IndexAccessException(e);
		}
	}

	public void close() throws IndexAccessException {
		if (page != null) {
			try {
				// log remaining insert operations
				page.bulkLog(false, -1);
			} catch (IndexOperationException e) {
				throw new IndexAccessException(e);
			} finally {
				page.cleanup();
				page = null;
			}
		}
	}

	public XTCdeweyID getStartInsertKey() {
		return startInsertKey;
	}
}
