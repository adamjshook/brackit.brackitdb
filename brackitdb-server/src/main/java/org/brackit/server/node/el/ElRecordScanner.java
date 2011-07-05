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
package org.brackit.server.node.el;

import org.apache.log4j.Logger;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.store.index.aries.page.PageContextFactory;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElRecordScanner implements Stream<ElNode> {
	private static final Logger log = Logger.getLogger(ElRecordScanner.class);

	private final ElLocator locator;

	private final DocID docID;

	private final Elndex index;

	private final XTCdeweyID subtreeRootDeweyID;

	private final int subtreeRootLevel;

	private final boolean delete;

	private boolean first = true;

	private ElIndexIterator iterator;

	private ElNode[] stack;

	private int stackSize;

	private XTCdeweyID previousDeweyID;

	private XTCdeweyID startDeweyID;

	ElRecordScanner(Elndex index, XTCdeweyID subtreeRoot, XTCdeweyID start,
			ElLocator locator, boolean delete) throws DocumentException {
		this.locator = locator;
		this.docID = locator.docID;
		this.subtreeRootDeweyID = subtreeRoot;
		this.startDeweyID = (start != null) ? start : subtreeRoot;
		this.previousDeweyID = startDeweyID;
		this.subtreeRootLevel = (subtreeRootDeweyID != null) ? subtreeRootDeweyID
				.getLevel()
				: 0;
		this.delete = delete;
		this.index = index;
		this.stack = new ElNode[8];

		open();
	}

	private void open() throws DocumentException {
		try {
			OpenMode openMode = (delete) ? OpenMode.UPDATE : OpenMode.READ;
			iterator = index.open(locator.collection.getTX(),
					locator.rootPageID, SearchMode.LEAST_HAVING_PREFIX,
					startDeweyID.toBytes(), null, openMode);

			if (iterator.getKey() == null) {
				close();
				throw new DocumentException(
						"No record found with key having %s as prefix.",
						previousDeweyID);
			}

			if (subtreeRootLevel > 0) {
				fillQueue(true);
			} else {
				fillQueue(false);
				if (stackSize == stack.length) {
					ElNode[] newQueue = new ElNode[(stack.length * 3) / 2 + 1];
					System.arraycopy(stack, 0, newQueue, 0, stack.length);
					stack = newQueue;
				}
				stack[stackSize++] = new ElNode(locator);
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public boolean hasNext() throws DocumentException {
		try {
			if (stackSize > 0) {
				return true;
			}

			if (iterator == null) {
				return false;
			}

			if (delete) {
				if (first) {
					first = false;

					if (!iterator.next()) {
						// we directly reached the end of the subtree at the end
						// of the document
						deleteRoot();
						return false;
					}

					fillQueue(false);
				} else {
					iterator.delete();

					if (iterator.getKey() != null) {
						fillQueue(false);
					}
				}

				if (stackSize == 0) {
					// we reached the end of the subtree
					deleteRoot();
					return false;
				} else {
					return true;
				}
			} else {
				if (iterator.next()) {
					fillQueue(false);

					return (stackSize > 0);
				} else {
					// we reached the end of the subtree
					if (delete) {
						deleteRoot();
					}

					close();
					return false;
				}
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e, "Error accessing document index");
		}
	}

	private void deleteRoot() throws IndexAccessException {
		if (!iterator.previous()) {
			close();
			throw new IndexAccessException(
					"Could not step back to subtree root");
		}

		int placeHolderLevel = ((subtreeRootDeweyID != null) && (subtreeRootDeweyID
				.isAttribute())) ? subtreeRootLevel - 2 : subtreeRootLevel - 1;
		iterator.deletePrefixAware(placeHolderLevel);
	}

	private void fillQueue(boolean includeRoot) throws IndexAccessException,
			DocumentException {
		XTCdeweyID currentDeweyID = null;
		try {
			currentDeweyID = new XTCdeweyID(docID, iterator.getKey());
		} catch (Exception e) {
			try {
				Tx tx = locator.collection.getTX();
				PageContext page = new PageContextFactory(tx.getBufferManager())
						.getPage(tx, iterator.getCurrentPageID(), false, false);
				System.out.println(page.dump("current page "));
				page.cleanup();
			} catch (IndexOperationException e1) {
				e1.printStackTrace();
			}
		}
		int lcaLevel = previousDeweyID.calcLCALevel(currentDeweyID);

		if (lcaLevel < subtreeRootLevel) {
			return;
		}

		byte[] physicalRecord = iterator.getValue();
		ElNode currentNode = locator.fromBytes(currentDeweyID, physicalRecord);

		/*
		 * Add all (virtual) inner nodes that have not been processed yet. These
		 * are all nodes on the path from the current deweyID up to the least
		 * common ancestor of the previous deweyID. If the current node is an
		 * attribute we omit the direct parent level because it is the attribute
		 * root
		 */
		int startLevel = (includeRoot) ? lcaLevel : lcaLevel + 1;
		int endLevel = (currentNode.getKind() == Kind.ATTRIBUTE) ? currentDeweyID
				.getLevel() - 1
				: currentDeweyID.getLevel();
		int newStackSize = endLevel - startLevel;

		if (newStackSize >= stack.length) {
			int growFactor = (stack.length * 3) / 2 + 1;
			ElNode[] newQueue = new ElNode[newStackSize > growFactor ? newStackSize
					: growFactor];
			System.arraycopy(stack, 0, newQueue, 0, stack.length);
			stack = newQueue;
		}

		// add leaf node at end of queue leaving room for all ancestors
		stack[stackSize++] = currentNode;
		previousDeweyID = currentDeweyID;
		PSNode psNode = currentNode.psNode;

		for (int level = endLevel - 1; level >= startLevel; level--) {
			XTCdeweyID ancestorID = currentDeweyID.getAncestor(level);
			PSNode ancestorPsNode = ((stackSize == 1) && isContent(stackSize - 1)) ? psNode
					: psNode.getParent();
			stack[stackSize++] = new ElNode(locator, ancestorID,
					Kind.ELEMENT.ID, null, ancestorPsNode);
			psNode = ancestorPsNode;
		}
	}

	private boolean isContent(int pos) {
		Kind kind = stack[pos].getKind();
		return ((kind == Kind.TEXT) || (kind == Kind.COMMENT) || (kind == Kind.PROCESSING_INSTRUCTION));
	}

	public void close() {
		if (iterator != null) {
			try {
				iterator.close();
				iterator = null;
			} catch (IndexAccessException e) {
				log.error(e);
			}
		}
	}

	@Override
	public ElNode next() throws DocumentException {
		if ((stackSize == 0) && (!hasNext())) {
			close();
			return null;
		}

		return stack[--stackSize];
	}
}