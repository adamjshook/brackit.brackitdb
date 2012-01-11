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
package org.brackit.server.store.index.blink;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.blink.page.PageContext;
import org.brackit.server.tx.Tx;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BlinkIndexWalker {
	private final BlinkTree tree;

	private final Tx transaction;

	private final PageID rootPageID;

	private final IndexVisitor visitor;

	public BlinkIndexWalker(Tx transaction, BlinkTree tree, PageID rootPageID,
			IndexVisitor visitor) {
		this.tree = tree;
		this.transaction = transaction;
		this.rootPageID = rootPageID;
		this.visitor = visitor;
	}

	public void traverse() throws IndexAccessException {
		visitor.start();
		traverseInternal(rootPageID, null);
		visitor.end();
	}

	private void traverseInternal(PageID pageID, PageID nextSiblingPageID)
			throws IndexAccessException {
		PageContext page = null;

		try {
			page = tree.getPage(transaction, pageID, false, false);
			int pageType = page.getPageType();

			if (pageType == PageType.BRANCH) {
				processBranchPage(page, nextSiblingPageID);
			} else {
				processLeafPage(page, nextSiblingPageID);
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

	private void processLeafPage(PageContext page, PageID nextSiblingPageID)
			throws IndexAccessException {
		try {
			visitor.visitLeafPage(page.createClone(), false);
			processInSplitSiblings(page, nextSiblingPageID);
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Error while processing leaf page %s.", page.getPageID());
		} catch (RuntimeException e) {
			throw new IndexAccessException(e,
					"Error while processing leaf page %s.", page.getPageID());
		}
	}

	private void processInSplitSiblings(PageContext page,
			PageID nextSiblingPageID) throws IndexOperationException {
		PageContext inSplitSibling = null;

		try {
			if ((!page.isLastInLevel())) {
				page.moveLast();
				PageID pageID = page.getValueAsPageID();

				if ((nextSiblingPageID != null)
						&& (!pageID.equals(nextSiblingPageID))) {
					inSplitSibling = tree.getPage(transaction, pageID, false,
							false);
					visitor.visitLeafPage(inSplitSibling.createClone(), false);
					inSplitSibling.moveLast();
					pageID = (inSplitSibling.isLastInLevel()) ? inSplitSibling
							.getValueAsPageID() : null;

					if ((nextSiblingPageID != null)
							&& (!pageID.equals(nextSiblingPageID))) {
						PageContext temp = tree.getPage(transaction, pageID,
								false, false);
						inSplitSibling.cleanup();
						inSplitSibling = temp;
					}
				}
			}
		} finally {
			if (inSplitSibling != null) {
				inSplitSibling.cleanup();
			}
		}
	}

	private void processBranchPage(PageContext page, PageID nextSiblingPageID)
			throws IndexAccessException {
		try {
			visitor.visitTreePage(page.createClone());

			if (!page.moveFirst()) {
				throw new IndexOperationException("Accessed empty branch page");
			}

			PageID childNextSiblingPageID = (page.getPosition() < page
					.getEntryCount()) ? page.getValueAsPageID() : null;

			if (page.getLowPageID() != null) {
				traverseInternal(page.getLowPageID(), childNextSiblingPageID);
			}
			while (page.getPosition() < page.getEntryCount()) {
				PageID pageID = page.getValueAsPageID();
				page.hasNext();
				childNextSiblingPageID = (page.getPosition() < page
						.getEntryCount()) ? page.getValueAsPageID() : null;
				traverseInternal(page.getPageID(), childNextSiblingPageID);
			}
			while (page.hasNext())
				;

			processInSplitSiblings(page, nextSiblingPageID);
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Error while processing branch page %s.", page.getPageID());
		} catch (RuntimeException e) {
			throw new IndexAccessException(e,
					"Error while processing branch page %s.", page.getPageID());
		}
	}
}