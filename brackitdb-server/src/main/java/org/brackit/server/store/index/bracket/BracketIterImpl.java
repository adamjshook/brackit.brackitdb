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
package org.brackit.server.store.index.bracket;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.bulkinsert.BulkInsertContext;
import org.brackit.server.store.index.bracket.page.BracketNodeLoader;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.page.bracket.BracketNodeSequence;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.RecordInterpreter;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.server.tx.Tx;

/**
 * @author Martin Hiller
 * 
 */
public final class BracketIterImpl implements BracketIter {

	private static Logger log = Logger.getLogger(BracketIterImpl.class);

	private final Tx tx;
	private final BracketTree tree;
	private final PageID rootPageID;
	private final OpenMode openMode;
	private Leaf page;
	private DeweyIDBuffer deweyIDBuffer;

	private XTCdeweyID key;
	private HintPageInformation hintPageInfo;

	public BracketIterImpl(Tx tx, BracketTree tree, PageID rootPageID,
			Leaf page, OpenMode openMode)
			throws IndexAccessException {
		try {
			this.tx = tx;
			this.tree = tree;
			this.rootPageID = rootPageID;
			this.page = page;
			this.deweyIDBuffer = page.getDeweyIDBuffer();
			this.openMode = openMode;
			if (!page.isBeforeFirst()) {
				this.key = page.getKey();
				this.hintPageInfo = page.getHintPageInformation();
			}
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e, "Error initializing iterator");
		}
	}

	@Override
	public void close() throws IndexAccessException {
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
	public BracketNode load(BracketNodeLoader loader) throws IndexAccessException {
		
		assureContextValidity();
		
		try {
			return page.load(loader);
		} catch (IndexOperationException e) {
			close();
			throw new IndexAccessException(e);
		}
	}

	@Override
	public boolean navigate(NavigationMode navMode) throws IndexAccessException {

		try {

			if (page != null) {
				// scan current page
				NavigationStatus navStatus = page.navigate(navMode);
				if (navStatus == NavigationStatus.FOUND) {
					key = page.getKey();
					hintPageInfo = page.getHintPageInformation();
					return true;
				} else if ((navStatus == NavigationStatus.NOT_EXISTENT)) {
					page.cleanup();
					page = null;
					return false;
				}
				// use BracketTree to continue the scan
				page = tree.navigateAfterHintPageFail(tx, rootPageID, navMode,
						key, openMode, page, deweyIDBuffer, navStatus);
			} else {
				// no current page given -> use index
				page = tree.navigateViaIndexAccess(tx, rootPageID, navMode,
						key, openMode, deweyIDBuffer);
			}

			if (page == null) {
				return false;
			}

			key = page.getKey();
			hintPageInfo = page.getHintPageInformation();

			return true;
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		} catch (IndexOperationException e) {
			close();
			throw new IndexAccessException(e,
					"Error navigating to specified record.");
		}
	}

	@Override
	public boolean next() throws IndexAccessException {

		assureContextValidity();

		try {
			if (!page.moveNext()) {
				page = tree.getNextPage(tx, rootPageID, page, openMode, true);
				if (page != null && !page.moveFirst()) {
					page.cleanup();
					page = null;
				}
			}

			if (page == null) {
				return false;
			}

			key = page.getKey();
			hintPageInfo = page.getHintPageInformation();

			return true;
		} catch (IndexAccessException e) {
			page = null;
			close();
			throw e;
		} catch (IndexOperationException e) {
			close();
			throw new IndexAccessException(e, "Error moving to next record.");
		}
	}

	@Override
	public void update(byte[] newValue) throws IndexAccessException {

		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		assureContextValidity();

		try {
			page = tree.updateInLeaf(tx, rootPageID, page, newValue, -1);
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
	public void deleteSubtree(SubtreeDeleteListener deleteListener)
			throws IndexAccessException {

		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		assureContextValidity();

		try {
			tree.deleteSubtree(tx, rootPageID, page, deleteListener,
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

	@Override
	public RecordInterpreter getRecord() throws IndexAccessException {		
		
		assureContextValidity();
		
		try {
			return page.getRecord();
		} catch (IndexOperationException e) {
			close();
			throw new IndexAccessException(e);
		}
	}
}
