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
package org.brackit.server.node.el.index;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.el.index.page.ElKeyValuePageContext;
import org.brackit.server.node.el.index.page.ElPageContext;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusTree;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.IndexPageHelper;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.store.page.keyvalue.KeyValuePage;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxStats;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ElBPlusTree extends BPlusTree {
	private static final Logger log = Logger.getLogger(ElBPlusTree.class);

	private final ElPlaceHolderHelper placeHolderHelper;

	public ElBPlusTree(BufferMgr bufferMgr,
			ElPlaceHolderHelper placeHolderHelper) {
		super(bufferMgr);
		this.placeHolderHelper = placeHolderHelper;
	}

	@Override
	protected PageContext createKeyValuePageContext(Tx transaction,
			KeyValuePage page) {
		return new ElKeyValuePageContext(bufferMgr, transaction, page);
	}

	protected PageContext insertSpecialIntoPage(Tx transaction,
			PageID rootPageID, PageContext page, byte[] insertKey,
			byte[] insertValue, int level, boolean isStructureModification,
			boolean compact, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		page = preparePageForInsert(transaction, rootPageID, page, insertKey,
				insertValue, compact, logged);

		try {
			if (log.isTraceEnabled()) {
				log.trace(page.dump(String.format(
						"Page before special insert of (%s, %s).", page
								.getKeyType().toString(insertKey), page
								.getValueType().toString(insertValue), page
								.getPageID())));
			}

			((ElPageContext) page).insertSpecial(insertKey, insertValue, level,
					logged, undoNextLSN);

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after insert"));
			}

			if (VERIFY_ACTION) {
				(new IndexPageHelper(bufferMgr)).verifyPage(page);
			}

			transaction.getStatistics().increment(TxStats.BTREE_INSERTS);

			return page;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not log record insertion.");
		}
	}

	protected PageContext deleteSpecialFromPage(Tx transaction,
			PageID rootPageID, PageContext page, byte[] deleteKey,
			byte[] deleteValue, int level, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(page.dump(String.format(
						"Page before special delete of (%s, %s) from page %s.",
						page.getKeyType().toString(deleteKey), page
								.getValueType().toString(deleteValue), page
								.getPageID())));
			}

			((ElPageContext) page).deleteSpecial(level, logged, undoNextLSN);

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after delete"));
			}

			return handleUnderflow(transaction, rootPageID, page, deleteKey,
					deleteValue, logged);
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not log record deletion.");
		}
	}

	public PageContext insertPrefixAwareIntoLeaf(Tx transaction,
			PageID rootPageID, PageContext leaf, byte[] key, byte[] value,
			int level, boolean compact, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		boolean treeLatched = false;

		try {
			leaf = assureLeafInsert(transaction, rootPageID, leaf, key, value,
					logged);

			if (leaf.isFlagged()) {
				/*
				 * We have to assure that no tree modification is going on
				 * (point of structural consistency) because a system failure
				 * might lead to a situation where the undo of the previous
				 * deletion in this leaf might require a logical undo (our
				 * insert may consume space required for page-oriented undo) and
				 * thus requiring a consistent tree for traversal. Therefore, we
				 * acquire the tree latch to ensure that we have a point of
				 * structural consistency in the log before we insert in the
				 * same page.
				 */
				if (log.isTraceEnabled()) {
					log.trace(String.format("Leaf page %s is flagged.", leaf));
				}

				leaf = latchTreeForLeafOperation(transaction, rootPageID, leaf,
						key, value, true, true, true, true);
				treeLatched = true;
			}

			if ((!leaf.hasEnoughSpaceForInsert(key, value))
					|| ((leaf.getPreviousKey() == null) && (leaf
							.getPreviousPageID() != null))) {
				/*
				 * The leaf page will have to be splitted or the previous page
				 * must be inspected to search for a prefix of the insert key.
				 * Get the tree latch in exclusive mode or upgrade update latch.
				 */
				if (log.isTraceEnabled()) {
					String msg = String.format(
							"Leaf page %s has not enough space for insert "
									+ "or previous page must be accessed "
									+ "for prefix aware insert.", leaf);
					log.trace(msg);
				}

				if (treeLatched) {
					treeLatch.upX(rootPageID);
				} else {
					leaf = latchTreeForLeafOperation(transaction, rootPageID,
							leaf, key, value, false, true, true, true);
					treeLatched = true;
				}
			} else {
				if (treeLatched) {
					treeLatch.downS(rootPageID);
					treeLatch.unlatch(rootPageID);
					treeLatched = false;
				}
			}

			leaf.setFlagged(false);
			leaf.setSafe(true);

			/*
			 * First insert the new entry into the current page but keep it
			 * latched.
			 */
			leaf = insertSpecialIntoPage(transaction, rootPageID, leaf, key,
					value, level, false, true, logged, undoNextLSN);

			/*
			 * Now we have to check whether we have to delete a prefix entry of
			 * the inserted key in the current page or in the previous page.
			 */
			if (leaf.hasPrevious()) {
				if (leaf.getKeyType().compareAsPrefix(leaf.getKey(), key) == 0) {
					try {
						if (log.isTraceEnabled()) {
							log
									.trace("Deleting placeholder "
											+ leaf.getKeyType().toString(
													leaf.getKey()));
						}

						leaf.delete(false, true, transaction.checkPrevLSN());
						transaction.getStatistics().increment(
								TxStats.BTREE_DELETES);
					} catch (IndexOperationException e) {
						leaf.cleanup();
						throw new IndexAccessException(e,
								"Could not delete placeholder.");
					}
				} else {
					leaf.hasNext();
				}
			} else if (leaf.getPreviousPageID() != null) {
				boolean deletePlaceHolder = false;
				PageContext previous = null;

				try {
					leaf.setSafe(false);
					leaf.unlatch();
					previous = getPage(transaction, leaf.getPreviousPageID(),
							true, false);
				} finally {
					leaf.latchX();
					leaf.setSafe(true);
				}

				try {
					previous.moveLast();
					deletePlaceHolder = (previous.getKey() != null)
							&& (leaf.getKeyType().compareAsPrefix(
									previous.getKey(), key) == 0);

					if (deletePlaceHolder) {
						leaf.cleanup();
						leaf = previous;
					} else {
						previous.cleanup();
					}
				} catch (IndexOperationException e) {
					previous.cleanup();
					throw e;
				}

				if (deletePlaceHolder) {
					if (log.isTraceEnabled()) {
						log.trace("Deleting placeholder "
								+ leaf.getKeyType().toString(leaf.getKey()));
					}

					leaf = deleteFromPage(transaction, rootPageID, leaf, leaf
							.getKey(), leaf.getValue(), false, logged,
							transaction.checkPrevLSN());

					if (leaf.getKey() == null) {
						leaf = moveNext(transaction, rootPageID, leaf,
								OpenMode.UPDATE);
					}
				}
			}

			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e, "Error inserting into index %s.",
					rootPageID);
		} finally {
			if (treeLatched) {
				treeLatch.unlatch(rootPageID);
			}
		}
	}

	public PageContext deletePrefixAwareFromLeaf(Tx transaction,
			PageID rootPageID, PageContext leaf, byte[] key, byte[] value,
			int level, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		boolean treeLatched = false;
		byte[] placeHolderKey = placeHolderHelper.createPlaceHolderKey(key,
				level);
		byte[] placeHolderValue = placeHolderHelper
				.createPlaceHolderValue(value);

		try {
			leaf = assureLeafDelete(transaction, rootPageID, leaf, key, value,
					logged);

			boolean deleteLastInLeaf = false;
			boolean deleteIsBound = (leaf.getPreviousKey() != null)
					&& (leaf.getNextKey() != null);

			if (!deleteIsBound) {
				/*
				 * The delete record is not "bound" to this page. The
				 * page-oriented undo of this delete is therefore ambiguous and
				 * requires a tree traversal. Hence, we have to enforce that
				 * this delete is only performed (logged) when the index is
				 * structural consistent. If the page will become empty after
				 * the deletion, the empty leaf must be removed from the index.
				 * Therefore we will directly latch the tree exclusively to
				 * proceed with the index reorganization. Otherwise a shared
				 * tree latch is sufficient.
				 */
				PageContext targetLeaf = latchTreeForLeafOperation(transaction,
						rootPageID, leaf, key, value, true, false, false, false);
				treeLatched = true;

				if (targetLeaf != leaf) // re-check conditions
				{
					leaf = targetLeaf;
					deleteIsBound = (leaf.getPreviousKey() != null)
							&& (leaf.getNextKey() != null);
				}

				deleteLastInLeaf = (leaf.getEntryCount() == 1);

				// tree latched in update mode
				if (deleteLastInLeaf) {
					treeLatch.upX(rootPageID);
				} else if (deleteIsBound) {
					treeLatch.downS(rootPageID);
				} else {
					treeLatch.downS(rootPageID);
					treeLatch.unlatch(rootPageID);
					treeLatched = false;
				}
			}

			/*
			 * First delete the entry from the current page but keep it latched.
			 */
			leaf = deleteSpecialFromPage(transaction, rootPageID, leaf, key,
					value, level, false, logged, undoNextLSN);

			leaf = insertPlaceHolder(transaction, rootPageID, leaf,
					placeHolderKey, placeHolderValue);

			if (leaf.getKey() == null) {
				leaf = moveNext(transaction, rootPageID, leaf, OpenMode.UPDATE);
			}

			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e, "Error deleting from index %s.",
					rootPageID);
		} finally {
			if (treeLatched) {
				treeLatch.unlatch(rootPageID);
			}
		}
	}

	private PageContext insertPlaceHolder(Tx transaction, PageID rootPageID,
			PageContext leaf, byte[] placeHolderKey, byte[] placeHolderValue)
			throws IndexAccessException {
		try {
			/*
			 * First check if direct neighbors make the insertion of a
			 * placeholder gratuitous
			 */
			byte[] nextKey = leaf.getNextKey();
			if ((nextKey != null)
					&& (leaf.getKeyType().compareAsPrefix(placeHolderKey,
							nextKey) == 0)) {
				return leaf;
			}

			byte[] previousKey = leaf.getPreviousKey();
			if ((previousKey != null)
					&& (leaf.getKeyType().compareAsPrefix(placeHolderKey,
							previousKey) == 0)) {
				return leaf;
			}

			/*
			 * Now, look first in next page to check if the first key of the
			 * next page already fulfills the requirements. After the
			 * inspection, we can directly release the next page and continue.
			 */
			PageID nextPageID = leaf.getNextPageID();
			if ((nextKey == null) && (nextPageID != null)) {
				PageContext next = getPage(transaction, nextPageID, false,
						false);

				try {
					nextKey = next.getKey();

					if ((nextKey != null)
							&& (leaf.getKeyType().compareAsPrefix(
									placeHolderKey, nextKey) == 0)) {
						return leaf;
					}
				} finally {
					next.cleanup();
				}
			}

			/*
			 * Finally, look at the last key in the previous page to check if it
			 * fulfills the requirements.
			 */
			PageID previousPageID = leaf.getPreviousPageID();
			if ((previousKey == null) && (previousPageID != null)) {
				PageContext previous = null;

				try {
					leaf.setSafe(false);
					leaf.unlatch();
					previous = getPage(transaction, previousPageID, false,
							false);
					previous.moveLast();
					previousKey = previous.getKey();

					if ((previousKey != null)
							&& (leaf.getKeyType().compareAsPrefix(
									placeHolderKey, previousKey) == 0)) {
						return leaf;
					}
				} finally {
					leaf.latchX();
					leaf.setSafe(true);
					if (previous != null) {
						previous.cleanup();
					}
				}

				leaf = verifyInsertPage(transaction, rootPageID, leaf,
						placeHolderKey);
			}

			if (log.isTraceEnabled()) {
				log.trace("Inserting placeholder "
						+ leaf.getKeyType().toString(placeHolderKey));
			}

			leaf = insertIntoPage(transaction, rootPageID, leaf,
					placeHolderKey, placeHolderValue, false, true, true,
					transaction.checkPrevLSN());
			leaf.moveNext();

			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e,
					"Error inserting place holder after prefix-aware delete");
		}
	}

	private PageContext verifyInsertPage(Tx transaction, PageID rootPageID,
			PageContext leaf, byte[] placeHolderKey)
			throws IndexAccessException {
		try {
			PageID insertPageID = null;
			PageContext parent = null;
			PageID leafPageID = leaf.getPageID();

			if (rootPageID.equals(leafPageID)) {
				return leaf;
			}

			try {
				leaf.setSafe(false);
				leaf.unlatch();
				parent = descendToParent(transaction, rootPageID, rootPageID,
						placeHolderKey, leafPageID, true); // we expect to find
				// the current leaf
				insertPageID = parent.determineNextChildPageID(
						SearchMode.GREATER_OR_EQUAL, placeHolderKey);

				leaf.latchX();
				leaf.setSafe(true);
			} catch (IndexAccessException e) {
				leaf.latchS();
				leaf.setSafe(true);
				leaf.cleanup();
				throw e;
			} finally {
				if (parent != null) {
					parent.cleanup();
				}
			}

			if (!leafPageID.equals(insertPageID)) {
				PageContext insertLeaf = getPage(transaction, insertPageID,
						true, false);
				System.err.println(String.format(
						"Switching from %s to %s to insert %s", leaf
								.getPageID(), insertLeaf.getPageID(), leaf
								.getKeyType().toString(placeHolderKey)));
				// System.err.println(parentDump);
				// System.err.println(leaf.dump("leaf"));
				// System.err.println(insertLeaf.dump("insert leaf"));
				leaf.cleanup();
				leaf = insertLeaf;
				leaf.moveAfterLast();
			}

			return leaf;
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e,
					"Error finding insert position for place holder insertion.");
		}
	}
}
