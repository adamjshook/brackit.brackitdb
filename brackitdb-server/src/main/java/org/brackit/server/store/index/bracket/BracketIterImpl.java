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

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.bulkinsert.BulkInsertContext;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.tx.Tx;

/**
 * @author Martin Hiller
 * 
 */
public final class BracketIterImpl implements BracketIter {

	private static Logger log = Logger.getLogger(BracketIterImpl.class);
	private static int BULK_INSERT_MAX_SEPARATORS = 512;

	private final Tx tx;
	private final BracketTree tree;
	private final PageID rootPageID;
	private final OpenMode openMode;
	private Leaf page;
	private DeweyIDBuffer deweyIDBuffer;
	private XTCdeweyID insertKey;

	private XTCdeweyID key;
	private byte[] value;
	private HintPageInformation hintPageInfo;

	private BulkInsertContext bulkContext;

	public BracketIterImpl(Tx tx, BracketTree tree, PageID rootPageID,
			Leaf page, OpenMode openMode, XTCdeweyID insertKey)
			throws IndexAccessException {
		try {
			this.tx = tx;
			this.tree = tree;
			this.rootPageID = rootPageID;
			this.page = page;
			this.deweyIDBuffer = page.getDeweyIDBuffer();
			this.openMode = openMode;
			this.key = page.getKey();
			this.value = page.getValue();
			this.hintPageInfo = page.getHintPageInformation();
			this.insertKey = insertKey;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e, "Error initializing iterator");
		}
	}

	@Override
	public void close() throws IndexAccessException {
		// if bulk insert is not finished yet
		if (bulkContext != null) {
			endBulkInsert();
		}

		if (page != null) {
			page.cleanup();
			page = null;
		}
		deweyIDBuffer = null;
	}

	@Override
	public XTCdeweyID getKey() throws IndexAccessException {
		return key;
	}

	@Override
	public byte[] getValue() throws IndexAccessException {
		return value;
	}

	@Override
	public void insert(XTCdeweyID deweyID, byte[] value, int ancestorsToInsert)
			throws IndexAccessException {
		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		if (bulkContext != null) {
			bulkInsert(deweyID, value, ancestorsToInsert);
			return;
		}
		
		assureContextValidity();

		try {
			page = tree
					.insertIntoLeaf(tx, rootPageID, page, deweyID, value,
							ancestorsToInsert, openMode.compact(),
							openMode.doLog(), -1);
			this.key = deweyID;
			this.value = value;
			this.insertKey = null;
			this.hintPageInfo = page.getHintPageInformation();
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		}
	}

	private void bulkInsert(XTCdeweyID deweyID, byte[] value,
			int ancestorsToInsert) throws IndexAccessException {
		// assert(page is exclusively latched)

		try {

			page = tree
					.insertIntoLeafBulk(tx, rootPageID, page, deweyID, value,
							ancestorsToInsert, bulkContext, openMode.doLog(),
							-1);
			this.key = deweyID;
			this.value = value;
			this.hintPageInfo = page.getHintPageInformation();
			this.insertKey = null;
		} catch (IndexAccessException e) {
			page = null;
			bulkContext.cleanup();
			bulkContext = null;
			throw e;
		}
	}

	@Override
	public boolean navigate(NavigationMode navMode) throws IndexAccessException {
		if (insertKey != null) {
			close();
			throw new IndexAccessException(
					"Navigation not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}

		try {

			page = tree.navigate(tx, rootPageID, navMode, key, openMode, page,
					deweyIDBuffer);

			if (page == null) {
				return false;
			}

			key = page.getKey();
			value = page.getValue();
			hintPageInfo = page.getHintPageInformation();

			return true;
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		} catch (IndexOperationException e) {
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			close();
			throw new IndexAccessException(e,
					"Error navigating to specified record.");
		}
	}

	@Override
	public boolean next() throws IndexAccessException {
		if (insertKey != null) {
			close();
			throw new IndexAccessException(
					"Navigation not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}
		
		assureContextValidity();

		try {
			page = tree.moveNext(tx, rootPageID, page, openMode);

			if (page == null) {
				return false;
			}

			key = page.getKey();
			value = page.getValue();
			hintPageInfo = page.getHintPageInformation();

			return true;
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		} catch (IndexOperationException e) {
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			close();
			throw new IndexAccessException(e, "Error moving to next record.");
		}
	}

	@Override
	public void update(byte[] newValue) throws IndexAccessException {
		if (insertKey != null) {
			close();
			throw new IndexAccessException(
					"Update not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}

		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}
		
		assureContextValidity();

		try {
			page = tree.updateInLeaf(tx, rootPageID, page, newValue, -1);
			value = newValue;
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		}
	}

	@Override
	public HintPageInformation getPageInformation() throws IndexAccessException {
		return hintPageInfo;
	}

	@Override
	public void startBulkInsert() throws IndexAccessException {
		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		// create new bulk insert context
		bulkContext = new BulkInsertContext(page, BULK_INSERT_MAX_SEPARATORS);
	}

	@Override
	public void endBulkInsert() throws IndexAccessException {
		tree.completeBulkInsert(tx, rootPageID, bulkContext, openMode.doLog());
		bulkContext = null;
	}

	@Override
	public void insertPrefixAware(XTCdeweyID deweyID, byte[] value,
			int ancestorsToInsert) throws IndexAccessException {
		if (bulkContext != null) {
			throw new RuntimeException(
					"BulkInsert not supported for the prefix-aware insertion!");
		}

		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}
		
		assureContextValidity();

		try {
			// check whether insertion is supposed to take place in this or the
			// next page
			if (page.isLast()) {
				XTCdeweyID highKey = page.getHighKey();
				if (highKey != null && deweyID.compareDivisions(highKey) >= 0) {
					// insertion in NEXT page
					Leaf nextPage = (Leaf) tree.getPage(tx,
							page.getNextPageID(), true, false);
					page.cleanup();
					page = nextPage;
				}
			}

			page = tree
					.insertIntoLeaf(tx, rootPageID, page, deweyID, value,
							ancestorsToInsert, openMode.compact(),
							openMode.doLog(), -1);
			this.key = deweyID;
			this.value = value;
			this.insertKey = null;
			this.hintPageInfo = page.getHintPageInformation();
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		} catch (IndexOperationException e) {
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			close();
			throw new IndexAccessException(e, "Error fetching next page.");
		}
	}

	@Override
	public void deleteSubtree(SubtreeDeleteListener deleteListener)
			throws IndexAccessException {
		
		if (insertKey != null) {
			close();
			throw new IndexAccessException(
					"Deletion not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}

		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}
		
		assureContextValidity();

		try {
			tree.deleteFromLeaf(tx, rootPageID, page, deleteListener, -1,
					openMode.doLog());
			page = null;
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		}
	}

	private void assureContextValidity() throws IndexAccessException {
		if (page == null) {
			// no page context exists due to failed navigation
			// -> go to last known position
			try {
				page = tree.openInternal(tx, rootPageID, NavigationMode.TO_KEY,
						key, openMode, hintPageInfo, deweyIDBuffer);
			} catch (IndexAccessException e) {
				page = null;
				throw e;
			}
		}
	}
}
