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
package org.brackit.server.store.index.aries;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexVisitor;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.tx.Tx;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BPlusIndexWalker {
	private final BPlusTree tree;

	private final Tx transaction;

	private final PageID rootPageID;

	private final IndexVisitor visitor;

	public BPlusIndexWalker(Tx transaction, BPlusTree tree, PageID rootPageID,
			IndexVisitor visitor) {
		this.tree = tree;
		this.transaction = transaction;
		this.rootPageID = rootPageID;
		this.visitor = visitor;
	}

	public void traverse() throws IndexAccessException {
		visitor.start();

		PageID nextPageIDOfLastVisitedLeaf = traverseInternal(null, rootPageID);
		processPendingOverflowLeafes(nextPageIDOfLastVisitedLeaf, null);

		visitor.end();
	}

	private PageID traverseInternal(PageID nextPageIDOfLastVisitedLeaf,
			PageID pageID) throws IndexAccessException {
		PageContext page = null;

		try {
			page = tree.getPage(transaction, pageID, false, false);
			int pageType = page.getPageType();

			if (pageType == PageType.INDEX_TREE) {
				return processTreePage(nextPageIDOfLastVisitedLeaf, page);
			} else {
				processPendingOverflowLeafes(nextPageIDOfLastVisitedLeaf, page
						.getPageID());
				return processLeafPage(page);
			}
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Error while traversing index page %s.", pageID);
		} finally {
			if (page != null) {
				page.cleanup();
			}
		}
	}

	private PageID processLeafPage(PageContext page)
			throws IndexAccessException {
		try {
			visitor.visitLeafPage(page.createClone(), false);
			return page.getNextPageID();
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Error while processing leaf page %s.", page.getPageID());
		} catch (RuntimeException e) {
			throw new IndexAccessException(e,
					"Error while processing leaf page %s.", page.getPageID());
		}
	}

	private PageID processTreePage(PageID nextPageIDOfLastVisitedLeaf,
			PageContext page) throws IndexAccessException {
		try {
			visitor.visitTreePage(page.createClone());

			page.moveFirst();
			nextPageIDOfLastVisitedLeaf = traverseInternal(
					nextPageIDOfLastVisitedLeaf, page.getBeforePageID());
			do {
				nextPageIDOfLastVisitedLeaf = traverseInternal(
						nextPageIDOfLastVisitedLeaf, page.getAfterPageID());
			} while (page.hasNext());

			return nextPageIDOfLastVisitedLeaf;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Error while processing leaf page %s.", page.getPageID());
		} catch (RuntimeException e) {
			throw new IndexAccessException(e,
					"Error while processing leaf page %s.", page.getPageID());
		}
	}

	private PageID processPendingOverflowLeafes(
			PageID nextPageIDOfLastVisitedLeaf, PageID nextLeafPageID)
			throws IndexAccessException {
		PageContext overflowPage = null;

		try {
			while ((nextPageIDOfLastVisitedLeaf != null)
					&& (!nextPageIDOfLastVisitedLeaf.equals(nextLeafPageID))) {
				overflowPage = tree.getPage(transaction,
						nextPageIDOfLastVisitedLeaf, false, false);

				visitor.visitLeafPage(overflowPage.createClone(), true);

				nextPageIDOfLastVisitedLeaf = overflowPage.getNextPageID();
				overflowPage.cleanup();
				overflowPage = null;
			}
			return nextPageIDOfLastVisitedLeaf;
		} catch (IndexOperationException e) {
			overflowPage.cleanup();
			throw new IndexAccessException(e,
					"Error while processing overflow leafes.");
		} catch (RuntimeException e) {
			overflowPage.cleanup();
			throw new IndexAccessException(e,
					"Error while processing overflow leafes.");
		}
	}
}