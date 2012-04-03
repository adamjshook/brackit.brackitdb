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
package org.brackit.server.store.index.blink;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.blink.page.PageContext;
import org.brackit.server.store.index.blink.page.PageContextFactory;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxStats;
import org.brackit.server.tx.thread.ThreadCB;
import org.brackit.xquery.util.log.Logger;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BlinkTree extends PageContextFactory {
	private static final Logger log = Logger.getLogger(BlinkTree.class);

	public BlinkTree(BufferMgr bufferMgr) {
		super(bufferMgr);
	}

	protected long logDummyCLR(Tx tx, long undoNextLSN)
			throws IndexOperationException {
		try {
			long lsn = tx.logDummyCLR(undoNextLSN);
			return lsn;
		} catch (TxException e) {
			throw new IndexOperationException(e,
					"Could not write dummy CLR to log.");
		}
	}

	public PageContext descendToPosition(Tx tx, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, boolean forUpdate,
			boolean forInsert) throws IndexAccessException {
		PageContext leaf = descend(tx, rootPageID, rootPageID, searchMode, key,
				forUpdate);
		return scan(tx, rootPageID, leaf, searchMode, key, value, forUpdate,
				forInsert);
	}

	private PageContext scan(Tx tx, PageID rootPageID, PageContext leaf,
			SearchMode searchMode, byte[] key, byte[] value, boolean forUpdate,
			boolean forInsert) throws IndexAccessException {
		try {
			while (true) {
				Field keyType = leaf.getKeyType();
				Field valueType = leaf.getValueType();
				int result = leaf.search(searchMode, key, value);

				if (result <= 0) {
					return leaf;
				}

				if ((searchMode.isRandom()) || (leaf.isLastInLevel())) {
					if (searchMode.moveAfterLast()) {
						leaf.moveAfterLast();
					}
					return leaf;
				} else {
					// move to high key
					leaf.moveNext();
					// reached end of current page -> continue in next page
					PageContext next = getPage(tx, leaf.getValueAsPageID(),
							forUpdate, false);
					leaf.cleanup();
					leaf = next;
				}
			}
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e, "Error during leaf scan");
		}
	}

	private PageContext descend(Tx tx, PageID rootPageID, PageID pageID,
			SearchMode searchMode, byte[] key, boolean forUpdate)
			throws IndexAccessException {
		return descend(tx, rootPageID, searchMode, key, 0, forUpdate);
	}

	private PageContext descend(Tx tx, PageID rootPageID,
			SearchMode searchMode, byte[] key, int targetHeight,
			boolean forUpdate) throws IndexAccessException {
		PageID pageID = rootPageID;
		PageContext page = null;

		try {
			while (true) {
				if (log.isTraceEnabled()) {
					log.trace(String.format("Descending to page %s.", pageID));
				}

				int retries = 0;
				while (true) {
					try {
						page = getPage(tx, pageID, forUpdate, forUpdate);
						break;
					} catch (IndexOperationException e) {
						// If we could not access the index root, the index does
						// not exist at all.
						// This is generally a severe error.
						// If this happens for a non-root page, it is likely
						// because we tried to
						// descend to a deleted page and we should simply try to
						// restart the traversal.
						// Note that we could perform additional checks to
						// ensure that a page deletion
						// is the real cause for this exception. However, this
						// case is rare anyway and
						// we simply save us the programming time to do it.
						if ((page.getPageID().equals(rootPageID))
								|| (++retries > 3)) {
							throw new IndexAccessException(e,
									"Error fetching index page %s.", pageID);
						}
						pageID = rootPageID;
					}
				}

				if (page.getPageType() == PageType.LEAF) {
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Reached leaf page %s searching for %s %s.",
								page, searchMode,
								page.getKeyType().toString(key)));
						log.trace(page.dump("leaf page"));
					}

					if (forUpdate) {
						page.upX();
					}
					return page;
				} else if (page.getHeight() == targetHeight) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Reached branch page %s "
								+ "with height %s for search key %s.", page,
								searchMode, page.getKeyType().toString(key)));
						log.trace(page.dump("branch page"));
					}

					if (forUpdate) {
						page.upX();
					}
					return page;
				}

				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Passing branch page %s and searching for %s %s.",
							page, searchMode, page.getKeyType().toString(key)));
					log.trace(page.dump("tree page"));
				}

				if (forUpdate) {
					page.downS();
				}

				PageID childPageID = page.searchNextPageID(searchMode, key);

				// perform side-steps while we keep the latch on the current
				// page
				while ((page.getPosition() == page.getEntryCount())
						&& (!page.isLastInLevel())
						&& (childPageID.equals(page.getValueAsPageID()))) {
					PageContext next = getPage(tx, childPageID, false, false);
					page.cleanup();
					page = next;
					childPageID = page.searchNextPageID(searchMode, key);
				}

				page.cleanup();
				pageID = childPageID;
			}
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Error inspecting index page %s",
					pageID);
		}
	}

	protected PageContext moveNext(Tx tx, PageID rootPageID, PageContext page,
			OpenMode openMode) throws IndexAccessException {
		byte[] currentKey = null;
		byte[] currentValue = null;

		try {
			if (openMode != OpenMode.LOAD) {
				if (openMode.forUpdate()) {
					downgradeLockEntry(tx, rootPageID, page, currentKey,
							currentValue);
				}
			}

			if (page.moveNext()) {
				if (openMode != OpenMode.LOAD) {
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					}
				}
				return page;
			}

			if (page.isLastInLevel()) {
				if (log.isTraceEnabled()) {
					log.trace("Reached end of index.");
				}

				if (openMode != OpenMode.LOAD) {
					// lock EOF
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					}
				}

				return page;
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("Reached end of current page %s. "
						+ "Attempting to proceed to next page %s.", page,
						page.getValueAsPageID()));
			}

			PageContext next = getPage(tx, page.getValueAsPageID(),
					openMode.forUpdate(), false);

			if (log.isTraceEnabled()) {
				log.trace(String.format("Switching to next page %s.", next));
			}
			page.cleanup();
			try {
				next.moveFirst();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// lock key or EOF
			if (openMode != OpenMode.LOAD) {
				if (openMode.forUpdate()) {
					updateLockEntry(tx, rootPageID, page, page.getKey(),
							page.getValue());
				} else {
					readLockEntry(tx, rootPageID, page, page.getKey(),
							page.getValue());
				}
			}

			return next;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not move to next entry");
		}
	}

	protected PageContext movePrevious(Tx tx, PageID rootPageID,
			PageContext page, OpenMode openMode) throws IndexAccessException {
		page.cleanup();
		throw new IndexAccessException("Not implemented yet");
	}

	public PageContext insertIntoLeaf(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean compact,
			boolean logged, long undoNextLSN) throws IndexAccessException {
		leaf = assureLeafInsert(tx, rootPageID, leaf, key, value, logged);
		return insertIntoPage(tx, rootPageID, leaf, key, value, false, compact,
				logged, undoNextLSN);
	}

	protected PageContext assureLeafInsert(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean logged)
			throws IndexAccessException {
		return leaf;
	}

	protected void readLockEntry(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value) throws IndexOperationException {
	}

	protected void updateLockEntry(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value) throws IndexOperationException {
	}

	protected void downgradeLockEntry(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value)
			throws IndexOperationException {
	}

	public PageContext deleteFromLeaf(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, long undoNextLSN,
			boolean logged) throws IndexAccessException {
		try {
			if (value == null) {
				value = leaf.getValue();
			}

			leaf = assureLeafDelete(tx, rootPageID, leaf, key, value, logged);

			int position = leaf.getPosition();
			int entryCount = leaf.getEntryCount();
			boolean deleteLastInLeaf = (entryCount == 1);

			if (!deleteLastInLeaf) {
				// no page deletion will be necessary
				leaf = deleteFromPage(tx, rootPageID, leaf, key, false, logged,
						undoNextLSN);
			} else {
				/*
				 * Deletions leading to a page deletion are always processed as
				 * forward operations. This ensures that we do not end up with
				 * an unintended empty page in the index when the emptied page
				 * is written to disk and the system crashes before the delete
				 * propagation is at least logged to stable log. See ARIES/IM
				 * for more details on this. In the undo case, however, we will
				 * write a CLR after the completed deletion.
				 */
				leaf = deleteFromPage(tx, rootPageID, leaf, key, false, true,
						-1);

				if (undoNextLSN != -1) {
					try {
						logDummyCLR(tx, undoNextLSN);
					} catch (IndexOperationException e) {
						leaf.cleanup();
						throw new IndexAccessException(e,
								"Could not log dummy CLR after page deletion.");
					}
				}
			}

			if (leaf.getKey() == null) {
				leaf = moveNext(tx, rootPageID, leaf, OpenMode.UPDATE);
			}

			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e, "Error deleting from index %s.",
					rootPageID);
		}
	}

	protected PageContext assureLeafDelete(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean logged)
			throws IndexAccessException {
		// TODO
		return leaf;
	}

	public PageContext openInternal(Tx tx, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode,
			PageID hintPageID, long LSN) throws IndexAccessException {
		PageContext leaf = null;

		if (hintPageID != null) {
			try {
				PageContext hintPage = getPage(tx, hintPageID,
						openMode.forUpdate(), openMode.forUpdate());

				if ((hintPage.getLSN() != LSN)
						|| (!hintPage.getRootPageID().equals(rootPageID))) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Page %s can not be used for "
								+ "direct access because it was "
								+ "modified after the update or "
								+ "it is no longer part of index %s.",
								hintPageID, rootPageID));
					}

					hintPage.cleanup();
				} else {
					hintPage.search(searchMode, key, value);

					if ((hintPage.getPosition() == 1)
							|| (hintPage.getPosition() == hintPage
									.getEntryCount())) {
						if (log.isTraceEnabled()) {
							log.trace(String
									.format("Page %s can not be used"
											+ " for direct access because"
											+ " the search key is not bound to this page.",
											hintPageID));
						}

						ThreadCB.get().countPageHintMiss();

						hintPage.cleanup();
					} else {
						ThreadCB.get().countPageHintHit();
						return hintPage;
					}
				}
			} catch (IndexOperationException e) {
				if (log.isTraceEnabled()) {
					log.trace(String.format("Page %s could not be fixed.",
							hintPageID));
				}
			}
		}

		try {
			leaf = descendToPosition(tx, rootPageID, searchMode, key, value,
					openMode.forUpdate(), false);

			readLockEntry(tx, rootPageID, leaf, leaf.getKey(), leaf.getValue());

			if (!openMode.doLog()) {
				tx.addFlushHook(rootPageID.getContainerNo());
			}

			return leaf;

		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e);
		}
	}

	protected PageContext insertIntoPage(Tx tx, PageID rootPageID,
			PageContext page, byte[] insertKey, byte[] insertValue,
			boolean isStructureModification, boolean compact, boolean logged,
			long undoNextLSN) throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				Field keyType = page.getKeyType();
				Field valueType = page.getValueType();
				log.trace(page.dump(String.format(
						"Page %s for insert of (%s, %s) at %s", page,
						keyType.toString(insertKey),
						valueType.toString(insertValue), page.getPosition())));
			}

			/*
			 * Optimistically try to insert record in page saving the
			 * computation of required space. If this fails we will have to
			 * perform a split
			 */
			while (!page.insert(insertKey, insertValue,
					isStructureModification, logged, undoNextLSN)) {
				if (log.isTraceEnabled()) {
					Field keyType = page.getKeyType();
					Field valueType = page.getValueType();
					log.trace(String.format(
							"Splitting page %s for insert of (%s, %s) at %s",
							page, keyType.toString(insertKey),
							valueType.toString(insertValue), page.getPosition()));
				}

				// Split and propagate if necessary.
				if (page.getPageID().equals(rootPageID)) {
					page = splitRoot(tx, rootPageID, page, insertKey,
							insertValue, compact, logged);
				} else {
					page = splitNonRoot(tx, rootPageID, page, insertKey,
							insertValue, compact, logged);
				}

				if (log.isTraceEnabled()) {
					Field keyType = page.getKeyType();
					Field valueType = page.getValueType();
					log.trace(page.dump(String.format(
							"Splitted page %s before insert of (%s, %s) at %s",
							page, keyType.toString(insertKey),
							valueType.toString(insertValue), page.getPosition())));
				}
			}

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after insert"));
			}

			int insertType = (page.getPageType() == PageType.LEAF) ? TxStats.BTREE_INSERTS
					: TxStats.BTREE_BRANCH_INSERTS;
			tx.getStatistics().increment(insertType);

			return page;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not log record insertion.");
		}
	}

	public PageContext updateInLeaf(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] newValue, byte[] oldValue, long undoNextLSN)
			throws IndexAccessException {
		leaf.cleanup();
		throw new IndexAccessException("Not implemented yet");
	}

	protected PageContext splitNonRoot(Tx tx, PageID rootPageID,
			PageContext left, byte[] insertKey, byte[] insertValue,
			boolean compact, boolean logged) throws IndexAccessException {
		PageID leftPageID = left.getPageID();
		PageID rightPageID = null;
		PageContext right = null;
		PageContext target = null;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Splitting non-root page %s.",
						leftPageID));
				log.trace(left.dump("Split Page"));
			}

			// Remember LSN of previously logged action.
			long rememberedLSN = tx.checkPrevLSN();

			// find out where to split
			int leftPageType = left.getPageType();
			int insertPosition = left.getPosition();
			int splitPosition = chooseSplitPosition(left, insertPosition,
					compact);
			left.moveTo(splitPosition - 1);
			// also high key in left page
			byte[] separatorKey = ((insertPosition == splitPosition) && (insertPosition <= left
					.getEntryCount())) ? insertKey : left.getKey();
			left.moveNext();

			boolean leafSplit = (leftPageType == PageType.LEAF);
			boolean insertIntoLeft = (insertPosition <= splitPosition);
			int newInsertPosition = insertIntoLeft ? insertPosition
					: (leafSplit ? (insertPosition - splitPosition + 1)
							: ((insertPosition == splitPosition) ? 1
									: (insertPosition - splitPosition)));

			// allocate and format new right page
			Field keyType = left.getKeyType();
			Field valueType = left.getValueType();
			right = allocate(tx, -1, left.getUnitID(), leftPageType,
					rootPageID, keyType, valueType, left.getHeight(),
					left.isUnique(), left.isCompressed(), logged);
			rightPageID = right.getPageID();

			if (!leafSplit) {
				// promote page pointer to low page of right page
				// and update after page pointer to right page
				right.setLowPageID(left.getValueAsPageID(), logged, -1);
				separatorKey = left.getKey();
				left.setValue(rightPageID.getBytes(), true, logged, -1);
				left.moveNext();
			}

			// shift remaining second half to right page
			while (!left.isAfterLast()) {
				right.insert(left.getKey(), left.getValue(), true, logged, -1);
				right.moveNext();
				left.delete(true, logged, -1);
			}

			if (leafSplit) {
				// add high key pointing to left page
				left.moveAfterLast();
				left.insert(separatorKey, rightPageID.getBytes(), true, logged,
						-1);
			}

			// set previous page in right page
			right.setPrevPageID(leftPageID, logged, -1);

			// update previous pointer in next page if it exists
			if ((!left.isLastInLevel()) && (right.getEntryCount() > 0)) {
				right.moveLast();
				PageContext next = getPage(tx, right.getValueAsPageID(), true,
						false);
				try {
					next.setPrevPageID(right.getPageID(), logged, -1);
				} finally {
					next.cleanup();
				}
			}

			// promote last in level flag
			right.setLastInLevel(left.isLastInLevel());
			left.setLastInLevel(false);

			// split at this level is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of non-root page %s completed.",
						left));
				log.trace(left.dump("Left page (splitted)"));
				log.trace(right.dump("Right page (new)"));
			}

			// write dummy CLR to make split at this level invisible to undo
			// processing
			rememberedLSN = logDummyCLR(tx, rememberedLSN);

			// verifySplitPages(left, right, keyType, insertKey, separatorKey,
			// insertPosition, splitPosition, insertIntoLeft,
			// newInsertPosition);

			insertSeparator(tx, rootPageID, separatorKey, leftPageID,
					rightPageID, left.getHeight(), compact, logged);

			// write dummy CLR to make also split propagation invisible to undo
			// processing
			logDummyCLR(tx, rememberedLSN);

			int splitType = leafSplit ? TxStats.BTREE_LEAF_ALLOCATIONS
					: TxStats.BTREE_BRANCH_ALLOCATE_COUNT;
			tx.getStatistics().increment(splitType);

			// Free unneeded split page
			if (insertIntoLeft) {
				// unlatch and unfix right split page
				right.cleanup();
				right = null;
				target = left;
				left = null;
				target.moveTo(newInsertPosition);
			} else {
				// unlatch and unfix right left split page and switch to right
				// page
				left.cleanup();
				left = null;
				target = right;
				right = null;
				target.moveTo(newInsertPosition);
			}

			return target;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Could not log page split operations.");
		} finally {
			if (left != null) {
				left.cleanup();
			}
			if (right != null) {
				right.cleanup();
			}
		}
	}

	private void insertSeparator(Tx tx, PageID rootPageID, byte[] separatorKey,
			PageID leftPageID, PageID rightPageID, int height, boolean compact,
			boolean logged) throws IndexAccessException {
		// find insert position for separator entry in parent page
		// we may keep the target pages latched exclusively because traversals
		// do not perform latch coupling
		PageContext parent = descendToParent(tx, rootPageID, rootPageID,
				separatorKey, leftPageID, height + 1);

		byte[] value = rightPageID.getBytes();
		try {
			// After page deletions it may happen that the separator pointing
			// to the (unsplitted) left page is greater or equal the new
			// separator. In this case, the old separator must be updated to
			// point to the new right page and the new separator must be
			// pointing
			// to the left page.
			if ((!parent.isAfterLast())
					&& (parent.getValueAsPageID().equals(leftPageID))) {
				parent.setPageIDAsValue(rightPageID, logged, -1);
				value = leftPageID.getBytes();
			}
		} catch (IndexOperationException e) {
			parent.cleanup();
			throw new IndexAccessException(e);
		}

		if (log.isTraceEnabled()) {
			Field keyType = parent.getKeyType();
			log.trace(String.format(
					"Insert separator (%s, %s) in parent page %s.",
					keyType.toString(separatorKey), rightPageID, parent));
		}

		parent = insertIntoPage(tx, rootPageID, parent, separatorKey, value,
				true, compact, logged, -1);
		parent.cleanup();
	}

	protected int chooseSplitPosition(PageContext splitPage,
			int insertPosition, boolean compact) throws IndexOperationException {
		int entryCount = splitPage.getEntryCount();
		int pageType = splitPage.getPageType();
		Field keyType = splitPage.getKeyType();
		Field valueType = splitPage.getValueType();
		byte[] separatorKey = null;

		if (entryCount < 3) {
			log.error(String.format("Cannot split page %s because it "
					+ "contains less than three records.",
					splitPage.getPageID()));
			log.error(splitPage.dump("Page to split"));
			throw new IndexOperationException(
					"Cannot split page %s because it "
							+ "contains less than three records.",
					splitPage.getPageID());
		}

		if (compact) {
			if (insertPosition == entryCount + 1) {
				return (pageType == PageType.LEAF) ? entryCount + 1
						: entryCount;
			} else {
				return (pageType == PageType.LEAF) ? entryCount
						: entryCount - 1;
			}
		} else {
			return (entryCount / 2) + 1;
		}
	}

	protected PageContext splitRoot(Tx tx, PageID rootPageID, PageContext root,
			byte[] insertKey, byte[] insertValue, boolean compact,
			boolean logged) throws IndexAccessException {
		long rememberedLSN = tx.checkPrevLSN();
		PageContext left = null;
		PageContext right = null;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Begin split of root page %s.",
						rootPageID));
				log.trace(root.dump("Root page"));
			}

			// fetch and latch new left and right page
			int rootPageType = root.getPageType();
			Field keyType = root.getKeyType();
			Field valueType = root.getValueType();
			left = allocate(tx, -1, root.getUnitID(), rootPageType, rootPageID,
					keyType, valueType, root.getHeight(), root.isUnique(),
					root.isCompressed(), logged);
			right = allocate(tx, -1, root.getUnitID(), rootPageType,
					rootPageID, keyType, valueType, root.getHeight(),
					root.isUnique(), root.isCompressed(), logged);

			// find out where to split
			int insertPosition = root.getPosition();
			int splitPosition = chooseSplitPosition(root, insertPosition,
					compact);
			root.moveTo(splitPosition - 1);
			byte[] separatorKey = ((insertPosition == splitPosition) && (insertPosition <= root
					.getEntryCount())) ? insertKey : root.getKey();
			root.moveNext();

			boolean leafSplit = (rootPageType == PageType.LEAF);
			boolean insertIntoLeft = insertPosition <= splitPosition;
			int newInsertPosition = insertIntoLeft ? insertPosition
					: (leafSplit ? (insertPosition - splitPosition + 1)
							: ((insertPosition == splitPosition) ? 1
									: (insertPosition - splitPosition)));

			if (!leafSplit) {
				// promote page pointer to low page of right page and drop it
				right.setLowPageID(root.getValueAsPageID(), logged, -1);
				separatorKey = root.getKey();
				root.delete(true, logged, -1);

				// copy low page ID to left page
				left.setLowPageID(root.getLowPageID(), logged, -1);
			}

			// shift second half to right page
			while (!root.isAfterLast()) {
				right.insert(root.getKey(), root.getValue(), true, logged, -1);
				right.moveNext();
				root.delete(true, logged, -1);
			}

			// shift first half to left page
			left.moveFirst();
			root.moveFirst();
			while (!root.isAfterLast()) {
				left.insert(root.getKey(), root.getValue(), true, logged, -1);
				left.moveNext();
				root.delete(true, logged, -1);
			}

			// set previous page in right page
			right.setPrevPageID(left.getPageID(), logged, -1);

			// reformat root page
			root.format(PageType.BRANCH, rootPageID, keyType, Field.PAGEID,
					root.getHeight() + 1, root.isUnique(), root.isCompressed(),
					logged, -1);
			root.setLastInLevel(true);
			// reposition context in converted root page
			root.moveFirst();

			// add right link from left page to right page
			left.insert(separatorKey, right.getPageID().getBytes(), true,
					logged, -1);

			// mark right page as last in this level
			right.setLastInLevel(true);

			// insert separator in converted root page
			root.insert(separatorKey, right.getPageID().getBytes(), true,
					logged, -1);
			root.setLowPageID(left.getPageID(), logged, -1);

			// root split is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of root page %s completed.",
						root));
				log.trace(root.dump("Root page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			// write dummy CLR to make also split propagation invisible to undo
			// processing
			logDummyCLR(tx, rememberedLSN);

			// statistic accounting for root page splits -> index height
			tx.getStatistics().increment(TxStats.BTREE_ROOT_SPLITS);

			// verifySplitPages(left, right, keyType, insertKey, separatorKey,
			// insertPosition, splitPosition, insertIntoLeft,
			// newInsertPosition);

			// Free unneeded split page and return
			if (insertIntoLeft) {
				// unlatch and unfix right split page
				left.moveTo(newInsertPosition);
				right.cleanup();
				root.cleanup();
				return left;
			} else {
				// unlatch and unfix left split page
				right.moveTo(newInsertPosition);
				left.cleanup();
				root.cleanup();
				return right;
			}
		} catch (IndexOperationException e) {
			if (left != null) {
				left.cleanup();
			}
			if (right != null) {
				right.cleanup();
			}
			root.cleanup();
			throw new IndexAccessException(e, "Error during root split.");
		}
	}

	private void verifySplitPages(PageContext left, PageContext right,
			Field keyType, byte[] insertKey, byte[] separatorKey,
			int insertPosition, int splitPosition, boolean insertIntoLeft,
			int newInsertPosition) throws IndexOperationException {
		right.moveFirst();
		left.moveLast();
		// TODO add support for non-unique indexes
		// right low key must be greater than left high key
		if (keyType.compare(right.getKey(), left.getKey()) <= 0) {
			splitError(left, right, keyType, insertKey, separatorKey,
					insertPosition, splitPosition, insertIntoLeft,
					newInsertPosition);
		}
		// right low key must be greater than separator key
		if (keyType.compare(right.getKey(), separatorKey) <= 0) {
			splitError(left, right, keyType, insertKey, separatorKey,
					insertPosition, splitPosition, insertIntoLeft,
					newInsertPosition);
		}
		// left high key must be separator key
		if (keyType.compare(left.getKey(), separatorKey) != 0) {
			splitError(left, right, keyType, insertKey, separatorKey,
					insertPosition, splitPosition, insertIntoLeft,
					newInsertPosition);
		}
		if (insertIntoLeft) {
			if ((left.getPageType() == PageType.LEAF)
					&& (splitPosition == insertPosition)) {
				// when insert is expected to be at the split position, use
				// insert key as separator
				if (keyType.compare(insertKey, separatorKey) != 0) {
					splitError(left, right, keyType, insertKey, separatorKey,
							insertPosition, splitPosition, insertIntoLeft,
							newInsertPosition);
				}
			} else {
				// insert key must be less than high key (= separator key) in
				// left page
				if (keyType.compare(insertKey, separatorKey) >= 0) {
					splitError(left, right, keyType, insertKey, separatorKey,
							insertPosition, splitPosition, insertIntoLeft,
							newInsertPosition);
				}
			}
		}
		if (!insertIntoLeft) {
			// insert key must be greater than separator key
			if (keyType.compare(insertKey, separatorKey) <= 0) {
				splitError(left, right, keyType, insertKey, separatorKey,
						insertPosition, splitPosition, insertIntoLeft,
						newInsertPosition);
			}
		}
		if (insertIntoLeft) {
			// verifiy that we can move context to insert position
			if (!left.moveTo(newInsertPosition)) {
				splitError(left, right, keyType, insertKey, separatorKey,
						insertPosition, splitPosition, insertIntoLeft,
						newInsertPosition);
			}
			// insert key must be smaller than key at insert position
			if (keyType.compare(left.getKey(), insertKey) < 0) {
				splitError(left, right, keyType, insertKey, separatorKey,
						insertPosition, splitPosition, insertIntoLeft,
						newInsertPosition);
			}
		} else {
			// verifiy that we can move context to insert position
			if (!right.moveTo(newInsertPosition)) {
				splitError(left, right, keyType, insertKey, separatorKey,
						insertPosition, splitPosition, insertIntoLeft,
						newInsertPosition);
			}
			if (right.isAfterLast()) {
				if (right.hasPrevious()) {
					// insert key must be greater than previous key
					if (keyType.compare(right.getKey(), insertKey) >= 0) {
						splitError(left, right, keyType, insertKey,
								separatorKey, insertPosition, splitPosition,
								insertIntoLeft, newInsertPosition);
					}
				}
			}
			// insert key must be smaller than key at insert position
			else if (keyType.compare(right.getKey(), insertKey) < 0) {
				splitError(left, right, keyType, insertKey, separatorKey,
						insertPosition, splitPosition, insertIntoLeft,
						newInsertPosition);
			}
		}
	}

	private void splitError(PageContext left, PageContext right, Field keyType,
			byte[] insertKey, byte[] separatorKey, int insertPosition,
			int splitPosition, boolean insertIntoLeft, int newInsertPosition)
			throws IndexOperationException {
		System.out.println("insert=" + keyType.toString(insertKey) + " sep="
				+ keyType.toString(separatorKey) + " insertPos="
				+ insertPosition + " splitPos=" + splitPosition + " leftPos="
				+ left.getPosition() + " rightPos" + right.getPosition()
				+ " insertIntoLeft=" + insertIntoLeft + " newInsertPostion="
				+ newInsertPosition);
		System.out.println(left.dump("left"));
		System.out.println(right.dump("right"));
		throw new RuntimeException();
	}

	protected PageContext descendToParent(Tx tx, PageID rootPageID,
			PageID pageID, byte[] separatorKey, PageID targetPageID,
			int targetHeight) throws IndexAccessException {
		PageContext parentPage = null;
		PageContext page = descend(tx, rootPageID, SearchMode.GREATER_OR_EQUAL,
				separatorKey, targetHeight, true);

		// We are now already at the correct level.
		// Move right until we find the separator we are looking for.
		try {
			Field keyType = page.getKeyType();
			Field valueType = page.getValueType();

			while (true) {
				// try to locate separator in this page
				int search = page.search(SearchMode.GREATER_OR_EQUAL,
						separatorKey, null);
				if (search <= 0) {
					// The context is positioned at an entry that is
					// greater or equal than the separator. The parent page
					// may be left of this entry (e.g.

					// inspect previous pointer
					PageID p;
					if (page.getPosition() > 1) {
						page.hasPrevious();
						p = page.getValueAsPageID();
						page.moveNext();
					} else {
						p = page.getLowPageID();
					}

					if (p.equals(targetPageID)) {
						parentPage = page;
					} else if ((search == 0)
							&& (page.getValueAsPageID().equals(targetPageID))) {
						parentPage = page;
					}
				} else if (!page.isAfterLast()) {
					// check if we find the separator after the current position
					// in this page, i.e.,
					// we look for the last pointer in this page or there may be
					// duplicate separator keys
					do {
						if (page.getValueAsPageID().equals(targetPageID)) {
							page.moveNext();
							parentPage = page;
							break;
						}
					} while ((page.hasNext())
							&& (keyType.compare(separatorKey, page.getKey()) <= 0));
				}

				if (parentPage != null) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Found parent page %s of %s.",
								page.getPageID(), targetPageID));
					}

					return page;
				}

				// follow "next" pointer of this page while
				// we hold the latch on this page
				pageID = page.getValueAsPageID();
				PageContext next = getPage(tx, pageID, true, false);
				page.cleanup();
				page = next;
			}
		} catch (IndexOperationException e) {
			if (page != null) {
				page.cleanup();
			}
			throw new IndexAccessException(e,
					"An error occured while accessing an index page.");
		}
	}

	protected PageContext deleteFromPage(Tx tx, PageID rootPageID,
			PageContext page, byte[] deleteKey,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		try {
			boolean leafDelete = page.getPageType() == PageType.LEAF;

			if (log.isTraceEnabled()) {
				Field keyType = page.getKeyType();
				Field valueType = page.getValueType();
				log.trace(String.format("Deleting (%s, %s) from page %s.",
						keyType.toString(deleteKey),
						valueType.toString(page.getValue()), page.getPageID()));
			}

			page.delete(isStructureModification, logged, undoNextLSN);

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after delete"));
			}

			int deleteType = leafDelete ? TxStats.BTREE_DELETES
					: TxStats.BTREE_BRANCH_DELETES;
			tx.getStatistics().increment(deleteType);

			if ((page.isLastInLevel())
					|| ((leafDelete) ? (page.getEntryCount() > 1) : (page
							.getEntryCount() > 0))) {
				// never propagate that last page in level underflows
				return page;
			}

			// only one entry in non-last leaf page is left (high key pointer)
			// or branch page is empty and its next page pointer was converted
			// to low page
			byte[] highKey = (leafDelete) ? page.getKey() : deleteKey;
			return handleUnderflow(tx, rootPageID, page, highKey, logged);
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Could not perform record deletion.");
		}
	}

	protected PageContext handleUnderflow(Tx tx, PageID rootPageID,
			PageContext page, byte[] highKey, boolean logged)
			throws IndexAccessException {
		boolean leafDelete = (page.getPageType() == PageType.LEAF);
		long rememberedLSN = tx.checkPrevLSN();
		PageContext parent = null;
		PageContext next = null;

		try {
			page.moveLast();
			next = unchain(tx, page, logged);
			// write CLR to skip tree reorganization during undo
			logDummyCLR(tx, rememberedLSN);

			try {
				// remove separator from parent (may propagate up the tree)
				// note, we may keep the page X-latched because no split from
				// lower levels can block
				parent = deleteSeparator(tx, rootPageID, page.getPageID(),
						page.getHeight(), highKey, logged);
			} catch (IndexAccessException e) {
				page.cleanup();
				throw e;
			}

			// reduce index height when parent is emptied root
			if ((parent.getEntryCount() == 0)
					&& (parent.getPageID().equals(rootPageID))) {
				// This situation implies the following:
				// a) parent is empty but was not cleaned up: it is the last
				// page in it's level
				// b) parent is empty: the right sibling is the last page in
				// this level is drained empty
				// c) page is not the last page in this level: it is the left
				// sibling

				// collapse root if right page is not splitted concurrently
				// separator is missing
				if (next.isLastInLevel()) {
					PageContext root = collapseRoot(tx, parent, page, next,
							logged);
					// write CLR to skip tree reorganization during undo
					logDummyCLR(tx, rememberedLSN);
					return root;
				}
			}
			parent.cleanup();

			// get the next page we should continue with
			while ((!next.isLastInLevel()) && (next.getEntryCount() < 2)) {
				next.moveLast();
				PageContext tmp = getPage(tx, next.getValueAsPageID(), true,
						false);
				next.cleanup();
				next = tmp;
			}

			// "Reset" page properties to get the required undo information in
			// the log
			page.setPrevPageID(null, logged, -1);
			page.setLowPageID(null, logged, -1);
			page.format(page.getPageType(), rootPageID, page.getKeyType(),
					page.getValueType(), page.getHeight(), page.isUnique(),
					page.isCompressed(), logged, -1);
			page.deletePage();
			page = null;

			// write CLR to skip tree reorganization during undo
			logDummyCLR(tx, rememberedLSN);

			return next;
		} catch (IndexOperationException e) {
			if (parent != null) {
				parent.cleanup();
			}
			if (next != null) {
				next.cleanup();
			}
			if (page != null) {
				page.cleanup();
			}
			throw new IndexAccessException(e);
		}
	}

	private PageContext deleteSeparator(Tx tx, PageID rootPageID,
			PageID pageID, int height, byte[] highKey, boolean logged)
			throws IndexAccessException {
		PageContext parent = null;
		try {
			parent = descendToParent(tx, rootPageID, rootPageID, highKey,
					pageID, height + 1);

			// parent context is positioned one record after the separator
			if (parent.hasPrevious()) {
				// simply delete separator to page from parent
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Deleting separator to leaf page %s"
									+ " from parent %s", pageID, parent));
				}
				parent = deleteFromPage(tx, rootPageID, parent,
						parent.getKey(), true, logged, -1);
			} else {
				// make next page new before page in parent
				if (log.isTraceEnabled()) {
					log.trace(String.format("Deleting separator to "
							+ "new before leaf page %s "
							+ "(current value = %s) in parent %s.", pageID,
							parent.getValueAsPageID(), parent));
				}

				parent.setLowPageID(parent.getValueAsPageID(), logged, -1);
				parent = deleteFromPage(tx, rootPageID, parent,
						parent.getKey(), true, logged, -1);
			}
			return parent;
		} catch (IndexOperationException e) {
			parent.cleanup();
			throw new IndexAccessException(e);
		}
	}

	private PageContext unchain(Tx tx, PageContext page, boolean logged)
			throws IndexAccessException {
		PageContext previous = null;
		PageContext next = null;
		PageID previousPageID;
		int retry = 0;

		// unchain leaf
		try {
			while ((previousPageID = page.getPrevPageID()) != null) {
				// unlatch is save here because page is empty
				// and others will not modify its content
				page.unlatch();
				try {
					previous = getPage(tx, previousPageID, true, false);
				} catch (IndexOperationException e) {
					page.latchX();
					if (++retry == 1000) {
						// avoid starvation of this thread
						throw new IndexOperationException(e,
								"Failed %s times to grab"
										+ " previous page for unchain. "
										+ "Aborting to avoid starvation", retry);
					}
					continue;
				}

				page.latchX();
				previous.moveLast();
				PageID checkPrevPageID = page.getPrevPageID();

				if (checkPrevPageID == null) {
					previous.cleanup();
					previous = null;
					break;
				}
				if (checkPrevPageID.equals(previousPageID)) {
					break;
				}
			}

			PageID nextPageID = (page.getPageType() == PageType.LEAF) ? page
					.getValueAsPageID() : page.getLowPageID();
			next = getPage(tx, nextPageID, true, false);
			next.setPrevPageID(previousPageID, logged, -1);

			if (previous != null) {
				previous.setValue(nextPageID.getBytes(), true, logged, -1);
				previous.cleanup();
				previous = null;
			}

			return next;
		} catch (IndexOperationException e) {
			if (next != null) {
				next.cleanup();
			}
			if (previous != null) {
				previous.cleanup();
			}
			page.cleanup();
			throw new IndexAccessException(e);
		}
	}

	private PageContext collapseRoot(Tx tx, PageContext root, PageContext left,
			PageContext right, boolean logged) throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Starting collapse of root page %s.",
						root));
				log.trace(root.dump("Root page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			if (left.getPageType() == PageType.BRANCH) {
				// switch root page type and update pointers
				root.setLowPageID(null, logged, -1);
				root.format(PageType.BRANCH, root.getPageID(),
						left.getKeyType(), left.getValueType(),
						left.getHeight(), left.isUnique(), left.isCompressed(),
						logged, -1);
				root.setLastInLevel(true);
				root.moveFirst();

				// copy before page of right page to root
				root.setLowPageID(right.getLowPageID(), logged, -1);

				// move content of right page to root
				right.moveFirst();
				while (!right.isAfterLast()) {
					root.moveNext();
					root.insert(right.getKey(), right.getValue(), true, logged,
							-1);
					right.delete(true, logged, 1);
				}

				// "Reset" page properties to get the required undo information
				// in the log
				right.setPrevPageID(null, logged, -1);
				left.setPrevPageID(null, logged, -1);
				right.setLowPageID(null, logged, -1);
				left.setLowPageID(null, logged, -1);
				left.format(PageType.BRANCH, left.getRootPageID(),
						left.getKeyType(), left.getValueType(),
						left.getHeight(), left.isUnique(), left.isCompressed(),
						logged, -1);
				right.format(PageType.BRANCH, left.getRootPageID(),
						right.getKeyType(), right.getValueType(),
						right.getHeight(), right.isUnique(),
						left.isCompressed(), logged, -1);
			} else {
				// switch root page type and update pointers
				root.setLowPageID(null, logged, -1);
				root.format(PageType.LEAF, root.getPageID(), left.getKeyType(),
						left.getValueType(), left.getHeight(), left.isUnique(),
						left.isCompressed(), logged, -1);
				root.setLastInLevel(true);
				root.moveFirst();

				// move content of right page to root
				right.moveFirst();
				while (!right.isAfterLast()) {
					root.moveNext();
					root.insert(right.getKey(), right.getValue(), true, logged,
							-1);
					right.delete(true, logged, 1);
				}

				// left and right page are empty leaf pages
				// "Reset" page properties to get the required undo information
				// in the log
				right.setPrevPageID(null, logged, -1);
				left.setPrevPageID(null, logged, -1);
				left.format(PageType.LEAF, left.getRootPageID(),
						left.getKeyType(), left.getValueType(),
						left.getHeight(), left.isUnique(), left.isCompressed(),
						logged, -1);
				right.format(PageType.LEAF, left.getRootPageID(),
						right.getKeyType(), right.getValueType(),
						right.getHeight(), right.isUnique(),
						right.isCompressed(), logged, -1);
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("Finished collapse of root page %s.",
						root));
				log.trace(root.dump("Root page"));
			}

			// delete pages
			left.deletePage();
			left = null;
			right.deletePage();
			right = null;

			// left page became empty after deletion: "next higher" key is now
			// first in root page
			root.moveFirst();

			return root;
		} catch (IndexOperationException e) {
			if (right != null) {
				right.cleanup();
			}
			if (left != null) {
				left.cleanup();
			}
			root.cleanup();
			throw new IndexAccessException(e,
					"Could not log root collapse operations.");
		}
	}

	public PageContext readFromLeaf(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value) throws IndexAccessException {
		try {
			readLockEntry(tx, rootPageID, leaf, key, value);
			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e);
		}
	}
}