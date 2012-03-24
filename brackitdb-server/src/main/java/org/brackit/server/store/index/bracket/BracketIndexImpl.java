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

import java.io.PrintStream;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketAttributeTuple;
import org.brackit.server.node.bracket.BracketLocator;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.page.bracket.RecordInterpreter;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Martin Hiller
 * 
 */
public class BracketIndexImpl implements BracketIndex {

	private static final boolean USE_HINTPAGE = true;

	protected final BracketTree tree;

	protected final BufferMgr bufferMgr;

	public BracketIndexImpl(BufferMgr bufferMgr) {
		this(new BracketTree(bufferMgr), bufferMgr);
	}

	protected BracketIndexImpl(BracketTree tree, BufferMgr bufferMgr) {
		this.tree = tree;
		this.bufferMgr = bufferMgr;
	}

	@Override
	public PageID createIndex(Tx tx, int containerNo)
			throws IndexAccessException {
		return createIndex(tx, containerNo, -1);
	}

	@Override
	public void dropIndex(Tx tx, PageID rootPageID) throws IndexAccessException {
		// TODO Auto-generated method stub

	}

	@Override
	public void dump(Tx tx, PageID rootPageID, PrintStream out)
			throws IndexAccessException {
		tree.dumpLeafs(tx, rootPageID, out);
	}

	@Override
	public InsertController openForInsert(Tx tx, PageID rootPageID,
			OpenMode openMode, XTCdeweyID startInsertKey)
			throws IndexAccessException {
		return new InsertController(tx, rootPageID, tree, openMode,
				startInsertKey);
	}

	@Override
	public BracketIter open(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode) throws IndexAccessException {
		return open(tx, rootPageID, navMode, key, openMode, null);
	}

	@Override
	public PageID createIndex(Tx tx, int containerNo, int unitID)
			throws IndexAccessException {
		Leaf root = null;

		try {
			root = tree.allocateLeaf(tx, containerNo, unitID, null, true);
			PageID rootPageID = root.getPageID();
			root.cleanup();

			return rootPageID;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Could not create index root page.");
		}
	}

	@Override
	public BracketIter open(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode, HintPageInformation hintPageInfo)
			throws IndexAccessException {

		Leaf leaf = tree.openInternal(tx, rootPageID, navMode, key, openMode,
				(USE_HINTPAGE ? hintPageInfo : null), null);
		if (leaf != null) {
			return new BracketIterImpl(tx, tree, rootPageID, leaf, openMode);
		} else {
			return null;
		}
	}

	@Override
	public String printLeafScannerStats(NavigationMode navMode)
			throws IndexAccessException {
		return tree.printLeafScannerStats(navMode);
	}

	@Override
	public StreamIterator openChildStream(BracketLocator locator,
			XTCdeweyID parentDeweyID, HintPageInformation hintPageInfo,
			BracketFilter filter) {
		return new ChildStream(locator, tree, parentDeweyID, hintPageInfo,
				filter);
	}

	@Override
	public StreamIterator openMultiChildStream(BracketLocator locator,
			XTCdeweyID parentDeweyID, HintPageInformation hintPageInfo,
			BracketFilter... filters) {
		
		// ensure to use the MultiChildStream only if necessary
		if (filters.length == 0) {
			return new ChildStream(locator, tree, parentDeweyID, hintPageInfo, null);
		} else if (filters.length == 1) {
			return new ChildStream(locator, tree, parentDeweyID, hintPageInfo, filters[0]);
		} else {
			return new MultiChildStream(locator, tree, parentDeweyID, hintPageInfo,
					filters);
		}
	}

	@Override
	public StreamIterator openDocumentStream(BracketLocator locator,
			BracketFilter filter) {
		return new DocumentStream(locator, tree, null, null, filter);
	}

	@Override
	public StreamIterator forkChildStream(StreamIterator origin,
			BracketFilter filter) throws DocumentException {
		return new ChildStream(origin, filter);
	}
	
	@Override
	public StreamIterator forkMultiChildStream(StreamIterator origin,
			BracketFilter... filters) throws DocumentException {
		return new MultiChildStream(origin, filters);
	}

	@Override
	public StreamIterator openAttributeStream(BracketLocator locator,
			XTCdeweyID elementDeweyID, HintPageInformation hintPageInfo,
			BracketFilter filter) {
		return new AttributeStream(locator, tree, elementDeweyID, hintPageInfo,
				filter);
	}

	@Override
	public StreamIterator forkAttributeStream(StreamIterator origin,
			BracketFilter filter) throws DocumentException {
		return new AttributeStream(origin, filter);
	}

	@Override
	public StreamIterator openSubtreeStream(BracketLocator locator,
			XTCdeweyID subtreeRoot, HintPageInformation hintPageInfo,
			BracketFilter filter, boolean self, boolean skipAttributes) {
		if (skipAttributes) {
			return new SubtreeStreamSkipAttr(locator, tree, subtreeRoot,
					hintPageInfo, filter, self);
		} else {
			return new SubtreeStream(locator, tree, subtreeRoot, hintPageInfo,
					filter, self);
		}
	}

	@Override
	public StreamIterator forkSubtreeStream(StreamIterator origin,
			BracketFilter filter, boolean self, boolean skipAttributes)
			throws DocumentException {
		if (skipAttributes) {
			return new SubtreeStreamSkipAttr(origin, filter, self);
		} else {
			return new SubtreeStream(origin, filter, self);
		}
	}

	@Override
	public BracketAttributeTuple setAttribute(BracketNode element, QNm name,
			Atomic value) throws IndexAccessException, DocumentException {

		BracketLocator locator = element.getLocator();
		XTCdeweyID attributeDeweyID = null;

		Tx tx = locator.collection.getTX();

		PSNode attributePsNode = locator.pathSynopsis.getChild(
				element.getPCR(), name, Kind.ATTRIBUTE.ID, null);

		byte[] physicalRecord = ElRecordAccess.createRecord(
				attributePsNode.getPCR(), Kind.ATTRIBUTE.ID,
				value.stringValue());

		Leaf page = null;
		Leaf next = null;
		try {

			page = tree.openInternal(tx, locator.rootPageID,
					NavigationMode.TO_KEY, element.getDeweyID(),
					OpenMode.UPDATE, element.hintPageInfo, null);
			if (page == null) {
				throw new IndexAccessException("Element node not found.");
			}

			// Scan all attributes of this element and check if attribute with
			// specified name is already available
			NavigationStatus navStatus = null;
			BracketNode oldAttribute = null;
			while (true) {
				while ((navStatus = page.moveNextAttribute()) == NavigationStatus.FOUND) {

					RecordInterpreter oldRecord = page.getRecord();
					if (oldRecord.getPCR() == attributePsNode.getPCR()) {
						// create old attribute node
						oldAttribute = page.load(locator.bracketNodeLoader);

						// we will reuse current deweyID and only update current
						// record
						break;
					}
				}
				// reached end of attributes (NOT_EXISTENT) or end of page
				// (AFTER_LAST)
				if (oldAttribute != null
						|| navStatus == NavigationStatus.NOT_EXISTENT) {
					break;
				} else {
					// load next page
					next = tree.getNextPage(tx, locator.rootPageID, page,
							OpenMode.UPDATE, false);
					if (next == null) {
						// no next page available
						break;
					} else {
						// check whether next page contains further attributes
						XTCdeweyID nextLowKey = next.getLowKey();
						if (nextLowKey == null || !nextLowKey.isAttribute()) {
							// no further attributes -> decide in which page to
							// insert
							XTCdeweyID lastKey = page.getKey();
							attributeDeweyID = (lastKey.isAttribute() ? XTCdeweyID
									.newBetween(lastKey, null) : lastKey
									.getNewAttributeID());
							if (attributeDeweyID.compareReduced(page
									.getHighKey()) >= 0) {
								// insert in next page
								page.cleanup();
								next.assignDeweyIDBuffer(page);
								page = next;
								next = null;
							} else {
								next.cleanup();
								next = null;
							}
							break;
						} else {
							// next page contains attributes
							page.cleanup();
							next.assignDeweyIDBuffer(page);
							page = next;
							next = null;
							continue;
						}
					}
				}
			}

			// at this point, the context either points to the old attribute
			// with the same name or to the last attribute of this element
			if (oldAttribute != null) {
				// update attribute
				page = tree.updateInLeaf(tx, locator.rootPageID, page,
						physicalRecord, -1);
				attributeDeweyID = oldAttribute.getDeweyID();
			} else {
				// insert new attribute
				if (attributeDeweyID == null) {
					XTCdeweyID lastKey = page.getKey();
					attributeDeweyID = (lastKey.isAttribute() ? XTCdeweyID
							.newBetween(lastKey, null) : lastKey
							.getNewAttributeID());
				}
				page = tree.insertIntoLeaf(tx, locator.rootPageID, page,
						attributeDeweyID, physicalRecord, 0, true, -1);
			}

			BracketNode newAttribute = new BracketNode(locator,
					attributeDeweyID, Kind.ATTRIBUTE.ID, value, attributePsNode);
			newAttribute.hintPageInfo = page.getHintPageInformation();

			page.cleanup();
			page = null;

			return new BracketAttributeTuple(oldAttribute, newAttribute);

		} catch (IndexOperationException e) {
			if (page != null) {
				page.cleanup();
			}
			if (next != null) {
				next.cleanup();
			}
			throw new IndexAccessException(e);
		}
	}

	@Override
	public void deleteSubtree(BracketLocator locator, XTCdeweyID key,
			HintPageInformation hintPageInfo, SubtreeDeleteListener listener)
			throws DocumentException {

		Tx tx = locator.collection.getTX();

		try {

			Leaf leaf = tree.openInternal(tx, locator.rootPageID,
					NavigationMode.TO_KEY, key, OpenMode.UPDATE, hintPageInfo,
					null);
			if (leaf == null) {
				throw new DocumentException(
						"The specified node does not exist and can not be deleted.");
			}

			tree.deleteSubtree(tx, locator.rootPageID, leaf, listener);

		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}
}
