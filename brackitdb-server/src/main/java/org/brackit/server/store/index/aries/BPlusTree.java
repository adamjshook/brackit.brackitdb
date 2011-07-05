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
package org.brackit.server.store.index.aries;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.store.index.aries.page.PageContextFactory;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxStats;
import org.brackit.server.tx.thread.ThreadCB;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BPlusTree extends PageContextFactory {
	private static final Logger log = Logger.getLogger(BPlusTree.class);

	protected static boolean VERIFY_ACTION = false;

	/**
	 * Globally shared tree latch container for all B* indexes.
	 */
	protected static final TreeLatch treeLatch = new TreeLatch();

	protected static final RewindException REWIND_EXCEPTION = new RewindException();

	protected final IndexLockService lockService;

	protected int rootSplits = 0;

	/**
	 * Exception used to signal the need to rewind pointer chasing because of
	 * concurrent modification actions in the tree.
	 * 
	 * {@link Exception#fillInStackTrace()} is overridden to minimize the
	 * creation overhead.
	 * 
	 * @author Sebastian Baechle
	 */
	private static final class RewindException extends Exception {
		@Override
		public synchronized Throwable fillInStackTrace() {
			return null;
		}
	}

	public BPlusTree(BufferMgr bufferMgr) {
		this(bufferMgr, null);
	}

	public BPlusTree(BufferMgr bufferMgr, IndexLockService lockService) {
		super(bufferMgr);
		this.lockService = lockService;
	}

	public TreeLatch getTreeLatch() {
		return treeLatch;
	}

	protected void logDummyCLR(Tx tx, long undoNextLSN)
			throws IndexOperationException {
		try {
			long lsn = tx.logDummyCLR(undoNextLSN);
		} catch (TxException e) {
			throw new IndexOperationException(e,
					"Could not write dummy CLR to log.");
		}
	}

	protected void undo(Tx tx, long rememberedLSN) {
		try {
			tx.undo(rememberedLSN);
		} catch (TxException e) {
			log.error(String.format(
					"Could not undo changes of %s back to LSN %s.", tx,
					rememberedLSN), e);
		}
	}

	public PageContext descendToPosition(Tx tx, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, boolean forUpdate,
			boolean forInsert) throws IndexAccessException {
		while (true) {
			try {
				PageContext leaf = descend(tx, rootPageID, null, rootPageID,
						searchMode, key, forUpdate);
				return scan(tx, rootPageID, leaf, searchMode, key, value,
						forUpdate, forInsert);
			} catch (RewindException e) {
				// retry
			}
		}
	}

	private PageContext scan(Tx tx, PageID rootPageID, PageContext leaf,
			SearchMode searchMode, byte[] key, byte[] value, boolean forUpdate,
			boolean forInsert) throws IndexAccessException, RewindException {
		try {
			while (true) {
				Field keyType = leaf.getKeyType();
				Field valueType = leaf.getValueType();
				int result = leaf.search(searchMode, key, value);

				if (result == 0) {
					return leaf;
				} else if (result > 0) {
					if ((searchMode.isRandom()) || (!leaf.hasNextPageID())) {
						if (searchMode.moveAfterLast()) {
							leaf.moveAfterLast();
						}

						if (log.isTraceEnabled()) {
							log
									.trace(String
											.format(
													"Stopping scan for (%s,%s) in mode=%s"
															+ " in page %s at (%s,%s) after (%s,%s)",
													keyType.toString(key),
													valueType.toString(value),
													searchMode,
													leaf,
													keyType.toString(leaf
															.getKey()),
													valueType.toString(leaf
															.getValue()),
													keyType.toString(leaf
															.getPreviousKey()),
													valueType
															.toString(leaf
																	.getPreviousValue())));
						}

						return leaf;
					} else {
						// reached end of current page -> glimpse at the next
						// page to check if we can stop in current page or if we
						// must continue in next page
						PageContext next = getNextPage(tx, rootPageID, leaf,
								forUpdate);
						result = checkNextPageLowKey(tx, rootPageID, leaf,
								next, searchMode, key, value, forUpdate,
								forInsert, keyType, valueType);

						if (result < 0) {
							next.cleanup();
							return leaf;
						} else if (result == 0) {
							leaf.cleanup();
							return next;
						} else {
							leaf.cleanup();
							leaf = next;
						}
					}
				} else {
					if ((searchMode.isRandom())
							|| (leaf.getPreviousPageID() == null)) {
						if (log.isTraceEnabled()) {
							log.trace(String.format(
									"Stopping scan for (%s,%s)  in mode=%s "
											+ "in page %s at (%s,%s)", keyType
											.toString(key), valueType
											.toString(value), searchMode, leaf,
									keyType.toString(leaf.getKey()), valueType
											.toString(leaf.getValue())));
						}

						return leaf;
					} else {
						// reached begin of current page -> glimpse at the
						// previous page to check if we can stop in current page
						// or if we must continue in previous page
						PageContext previous = getPreviousPage(tx, rootPageID,
								leaf, forUpdate);
						result = checkPreviousPageHighKey(tx, rootPageID, leaf,
								previous, searchMode, key, value, forUpdate,
								keyType, valueType);

						if (result > 0) {
							previous.cleanup();
							return leaf;
						} else if (result == 0) {
							leaf.cleanup();
							return previous;
						} else {
							leaf.cleanup();
							leaf = previous;
						}
					}
				}
			}
		} catch (IndexOperationException e) {
			leaf.cleanup();
			throw new IndexAccessException(e, "Error during leaf scan");
		}
	}

	private PageContext descend(Tx tx, PageID rootPageID, PageContext parent,
			PageID pageID, SearchMode searchMode, byte[] key, boolean forUpdate)
			throws IndexAccessException, RewindException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace(String.format("Descending to page %s.", pageID));
		}

		try {
			page = getPage(tx, pageID, forUpdate, forUpdate);
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e, "Error fetching index page %s.",
					pageID);
		} finally {
			if (parent != null) // we do not need a stable parent page anymore
			{
				parent.cleanup();
			}
		}

		try {
			if (!page.isSafe()) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Rewind because page %s is involved"
									+ " in structure modification.", page));
				}

				// release page to avoid deadlock and
				// wait for end of structure modification
				if (forUpdate) {
					page.downS();
				}
				page.cleanup();
				treeLatch.latchSI(rootPageID);
				throw REWIND_EXCEPTION;
			}

			int pageType = page.getPageType();

			if (pageType == PageType.INDEX_LEAF) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Reached leaf page %s searching for %s %s.", page,
							searchMode, page.getKeyType().toString(key)));
					log.trace(page.dump("leaf page"));
				}

				if (forUpdate) {
					page.upX();
				}
				return page;
			} else if (pageType == PageType.INDEX_TREE) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Passing tree page %s searching for %s %s.", page,
							searchMode, page.getKeyType().toString(key)));
					log.trace(page.dump("tree page"));
				}

				if (forUpdate) {
					page.downS();
				}
				PageID childPageID = page.determineNextChildPageID(searchMode,
						key);
				return descendToChild(tx, rootPageID, page, childPageID,
						searchMode, key, forUpdate);
			} else {
				log.error(page.dump(String.format("Page with invalid type %s",
						pageType)));
				if (forUpdate) {
					page.downS();
				}
				page.cleanup();
				throw new IndexAccessException(String.format(
						"Page %s has an invalid index page type '%s'.", pageID,
						pageType));
			}
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Error inspecting index page %s",
					pageID);
		}
	}

	private PageContext descendToChild(Tx tx, PageID rootPageID,
			PageContext page, PageID childPageID, SearchMode searchMode,
			byte[] key, boolean forUpdate) throws IndexAccessException,
			RewindException {
		while (true) {
			PageID pageID = page.getPageID();
			long beforeLSN = page.getLSN();

			try {
				return descend(tx, rootPageID, page, childPageID, searchMode,
						key, forUpdate);
			} catch (RewindException e) {
				// child was part of split -> fix current page again after wait,
				// check LSN and retry if possible
				try {
					page = getPage(tx, pageID, false, false);
				} catch (IndexOperationException e1) {
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Continue rewind because page %s"
										+ " was deleted in between.", pageID));
					}
					throw REWIND_EXCEPTION;
				}

				long afterLSN = page.getLSN();

				if (afterLSN != beforeLSN) {
					// page was modified in between -> rewind
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Continue rewind because page %s"
										+ " was modified in between.", pageID));
					}
					page.cleanup();
					throw REWIND_EXCEPTION;
				} else {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Stop rewind because page %s "
								+ "was not modified in between "
								+ "(previousLSN=%s currentLSN=%s).", pageID,
								beforeLSN, afterLSN));
					}
				}
			}
		}
	}

	private int checkPreviousPageHighKey(Tx tx, PageID rootPageID,
			PageContext page, PageContext previous, SearchMode searchMode,
			byte[] searchKey, byte[] searchValue, boolean forUpdate,
			Field keyType, Field valueType) throws IndexOperationException,
			IndexAccessException {
		previous.moveLast();
		byte[] currentKey = previous.getKey();
		byte[] currentValue = (searchValue != null) ? previous.getValue()
				: null;
		boolean currentKeyIsInside = searchMode.isInside(keyType, currentKey,
				searchKey);
		boolean currentValueIsInside = (searchValue != null) ? searchMode
				.isInside(valueType, currentValue, searchValue)
				: currentKeyIsInside;

		if (searchMode.findGreatestInside()) {
			if ((currentKeyIsInside) && (currentValueIsInside)) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Stopping scan for (%s,%s) in mode=%s "
									+ "in previous %s at last record (%s,%s).",
							keyType.toString(searchKey), valueType
									.toString(searchValue), searchMode,
							previous, keyType.toString(previous.getKey()),
							valueType.toString(previous.getValue())));
				}
				return 0;
			}
		} else if ((!currentKeyIsInside) || (!currentValueIsInside)) {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Stopping scan for (%s,%s) in mode=%s "
						+ "in current page %s at first record (%s,%s).",
						keyType.toString(searchKey), valueType
								.toString(searchValue), searchMode, page,
						keyType.toString(page.getKey()), valueType
								.toString(page.getValue())));
			}
			return 1;
		}

		if (log.isTraceEnabled()) {
			log.trace(String.format("Reached beginning of page %s"
					+ " (type=%s, lowKey=%s). " + "Proceed with scan for "
					+ "(key=%s,  mode=%s) in previous page %s", page, page
					.getPageType(), keyType.toString(page.getKey()), keyType
					.toString(searchKey), searchMode, previous));
			log.trace(page.dump("Current page"));
			log.trace(previous.dump("Previous page"));
		}

		return -1;
	}

	private int checkNextPageLowKey(Tx tx, PageID rootPageID, PageContext page,
			PageContext next, SearchMode searchMode, byte[] searchKey,
			byte[] searchValue, boolean forUpdate, boolean forInsert,
			Field keyType, Field valueType) throws IndexOperationException {
		byte[] currentKey = next.getKey();
		byte[] currentValue = (searchValue != null) ? next.getValue() : null;
		boolean currentKeyIsInside = searchMode.isInside(keyType, currentKey,
				searchKey);
		boolean currentValueIsInside = (searchValue != null) ? searchMode
				.isInside(valueType, currentValue, searchValue)
				: currentKeyIsInside;

		if (!searchMode.findGreatestInside()) {
			if ((currentKeyIsInside) && (currentValueIsInside)) {
				if ((forInsert) && (keyType.compare(currentKey, searchKey) > 0)) {
					// step after last record of current page before return
					page.moveAfterLast();
					if (log.isTraceEnabled()) {
						log.trace(String.format("Stopping scan for (%s,%s)"
								+ " in mode=%s in current page "
								+ "%s after last record (%s,%s).", keyType
								.toString(searchKey), valueType
								.toString(searchValue), searchMode, page,
								keyType.toString(page.getPreviousKey()),
								valueType.toString(page.getPreviousValue())));
					}
					return -1;
				} else {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Stopping scan for (%s,%s)"
								+ " in mode=%s" + " in next %s at "
								+ "first record (%s,%s).", keyType
								.toString(searchKey), valueType
								.toString(searchValue), searchMode, next,
								keyType.toString(next.getKey()), valueType
										.toString(next.getValue())));
					}
					return 0;
				}
			}
		} else if ((!currentKeyIsInside) || (!currentValueIsInside)) {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Stopping scan for (%s,%s) in mode=%s "
						+ "in current page %s at last record (%s,%s).", keyType
						.toString(searchKey), valueType.toString(searchValue),
						searchMode, page, keyType.toString(page.getKey()),
						valueType.toString(page.getValue())));
			}
			return -1;
		}

		if (log.isTraceEnabled()) {
			log.trace(String.format("Reached end of page %s "
					+ "(type=%s, highKey=%s). " + "Proceed with scan for "
					+ "(key=%s,  mode=%s) in next page %s", page, page
					.getPageType(), keyType.toString(page.getKey()), keyType
					.toString(searchKey), searchMode, next));
			log.trace(page.dump("Current page"));
			log.trace(next.dump("Next page"));
		}

		return 1;
	}

	private PageContext getPreviousPage(Tx tx, PageID rootPageID,
			PageContext page, boolean forUpdate)
			throws IndexOperationException, RewindException {
		while (true) {
			PageContext previous = null;

			try {
				PageID previousPageID = page.getPreviousPageID();

				// unlatch context page to avoid deadlock
				long beforeLSN = page.getLSN();
				page.unlatch();

				try {
					previous = getPage(tx, previousPageID, forUpdate, false);
				} finally {
					if (forUpdate) {
						page.latchX();
					} else {
						page.latchS();
					}
				}

				long afterLSN = page.getLSN();

				if (beforeLSN != afterLSN) {
					// page was modified in between -> rewind
					if (log.isTraceEnabled()) {
						log.trace(String.format("Rewind pointer chasing "
								+ "because context page was "
								+ "modified while acquiring"
								+ " latch for previous page of %s.", page));
					}
					previous.cleanup();
					page.cleanup();
					throw REWIND_EXCEPTION;
				}

				if (previous.isSafe()) {
					return previous;
				}
			} catch (IndexOperationException e) {
				if (previous != null) {
					previous.cleanup();
				}
				throw e;
			}

			// previous page is part of ongoing structure modification -> pause
			if (log.isTraceEnabled()) {
				log.trace(String.format("Pausing pointer chasing because "
						+ "previous page %s is involved in "
						+ "a structure modification.", previous));
			}

			previous.cleanup();
			previous = null;

			// unlatch context page to avoid deadlock
			long beforeLSN = page.getLSN();
			page.unlatch();

			// wait for end of structure modification
			treeLatch.latchSI(rootPageID);

			if (forUpdate) {
				page.latchX();
			} else {
				page.latchS();
			}

			long afterLSN = page.getLSN();

			if (beforeLSN != afterLSN) {
				// page was modified in between -> rewind
				if (log.isTraceEnabled()) {
					log.trace(String.format("Rewind pointer chasing because "
							+ "context page was modified while acquiring "
							+ "latch for previous page of %s.", page));
				}
				page.cleanup();
				throw REWIND_EXCEPTION;
			}
		}
	}

	private PageContext getNextPage(Tx tx, PageID rootPageID, PageContext page,
			boolean forUpdate) throws IndexOperationException, RewindException {
		while (true) {
			PageContext next = null;

			try {
				PageID nextPageID = page.getNextPageID();
				next = getPage(tx, nextPageID, forUpdate, false);

				if (next.isSafe()) {
					return next;
				}
			} catch (IndexOperationException e) {
				if (next != null) {
					next.cleanup();
				}
				throw e;
			}

			// next page is part of ongoing structure modification -> pause
			if (log.isTraceEnabled()) {
				log.trace(String.format("Pausing pointer chasing because "
						+ "next page %s is involved in a "
						+ "structure modification.", next));
			}

			long beforeLSN = page.getLSN();

			next.cleanup();

			// unlatch context page and next page to avoid deadlock
			page.unlatch();

			// wait for end of structure modification
			treeLatch.latchSI(rootPageID);

			if (forUpdate) {
				page.latchX();
			} else {
				page.latchS();
			}

			long afterLSN = page.getLSN();

			if (beforeLSN != afterLSN) // page was modified in between -> rewind
			{
				if (log.isTraceEnabled()) {
					log.trace(String.format("Rewind pointer chasing because "
							+ "context page was modified while "
							+ "acquiring latch for next page of %s.", page));
				}
				page.cleanup();
				throw REWIND_EXCEPTION;
			}
		}
	}

	protected PageContext moveNext(Tx tx, PageID rootPageID, PageContext page,
			OpenMode openMode) throws IndexAccessException {
		byte[] currentKey = null;
		byte[] currentValue = null;

		try {
			if ((lockService != null) && (openMode != OpenMode.LOAD)) {
				if (openMode.forUpdate()) {
					lockService.downgradeLock(tx, page.getUnitID(), rootPageID,
							currentKey, currentValue);
				}
			}

			if (page.moveNext()) {
				if ((lockService != null) && (openMode != OpenMode.LOAD)) {
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey(), page
								.getValue());
					}
				}
				return page;
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("Reached end of current page %s. "
						+ "Attempting to proceed to next page %s.", page, page
						.getNextPageID()));
			}

			if (!page.hasNextPageID()) {
				if (log.isTraceEnabled()) {
					log.trace("Reached end of index.");
				}

				if ((lockService != null) && (openMode != OpenMode.LOAD)) {
					// lock EOF
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey(), page
								.getValue());
					}
				}

				return page;
			}

			PageContext next = getNextPage(tx, rootPageID, page, openMode
					.forUpdate());

			if (log.isTraceEnabled()) {
				log.trace(String.format("Switching to next page %s.", next));
			}
			page.cleanup();
			next.moveFirst();

			// lock key or EOF
			if ((lockService != null) && (openMode != OpenMode.LOAD)) {
				if (openMode.forUpdate()) {
					updateLockEntry(tx, rootPageID, page, page.getKey(), page
							.getValue());
				} else {
					readLockEntry(tx, rootPageID, page, page.getKey(), page
							.getValue());
				}
			}

			return next;
		} catch (RewindException e) {
			page = descendToPosition(tx, rootPageID,
					SearchMode.GREATER_OR_EQUAL, currentKey, currentValue,
					openMode.forUpdate(), false);
			return moveNext(tx, rootPageID, page, openMode);
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not move to next entry");
		}
	}

	protected PageContext movePrevious(Tx tx, PageID rootPageID,
			PageContext page, OpenMode openMode) throws IndexAccessException {
		byte[] currentKey = null;
		byte[] currentValue = null;

		try {
			currentKey = page.getKey();
			currentValue = page.getValue();

			if ((lockService != null) && (openMode != OpenMode.LOAD)) {
				if (openMode.forUpdate()) {
					lockService.downgradeLock(tx, page.getUnitID(), rootPageID,
							currentKey, currentValue);
				}
			}

			if (page.hasPrevious()) {
				if ((lockService != null) && (openMode != OpenMode.LOAD)) {
					if (openMode.forUpdate()) {
						updateLockEntry(tx, rootPageID, page, page.getKey(),
								page.getValue());
					} else {
						readLockEntry(tx, rootPageID, page, page.getKey(), page
								.getValue());
					}
				}

				return page;
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format(
						"Reached beginning of current page %s. "
								+ "Attempting to proceed to previous page %s.",
						page, page.getPreviousPageID()));
			}

			if (page.getPreviousPageID() == null) {
				if (log.isTraceEnabled()) {
					log.trace("Reached beginning of index.");
				}

				return page;
			}

			PageContext previous = getPreviousPage(tx, rootPageID, page,
					openMode.forUpdate());

			if (log.isTraceEnabled()) {
				log.trace(String.format("Switching to previous page %s.",
						previous));
			}

			page.cleanup();
			previous.moveLast();

			if ((lockService != null) && (openMode != OpenMode.LOAD)) {
				if (openMode.forUpdate()) {
					updateLockEntry(tx, rootPageID, page, page.getKey(), page
							.getValue());
				} else {
					readLockEntry(tx, rootPageID, page, page.getKey(), page
							.getValue());
				}
			}

			return previous;
		} catch (RewindException e) {
			page = descendToPosition(tx, rootPageID,
					SearchMode.GREATER_OR_EQUAL, currentKey, currentValue,
					openMode.forUpdate(), false);
			return movePrevious(tx, rootPageID, page, openMode);
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Could not move to previous entry");
		}
	}

	public PageContext insertIntoLeaf(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean compact,
			boolean logged, long undoNextLSN) throws IndexAccessException {
		boolean treeLatched = false;

		try {
			leaf = assureLeafInsert(tx, rootPageID, leaf, key, value, logged);

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

				leaf = latchTreeForLeafOperation(tx, rootPageID, leaf, key,
						value, true, true, true, true);
				treeLatched = true;
			}

			/*
			 * Optimistically try to insert record in page saving the
			 * computation of required space. If this fails we will have to
			 * ensure that we hold the treelatch exclusively to perform a split
			 */
			if (leaf.insert(key, value, false, logged, undoNextLSN)) {
				if (treeLatched) {
					treeLatch.downS(rootPageID);
					treeLatch.unlatch(rootPageID);
					treeLatched = false;
				}

				if (leaf.isFlagged()) {
					leaf.setFlagged(false);
				}

				leaf.setSafe(true);
				tx.getStatistics().increment(TxStats.BTREE_INSERTS);

				return leaf;
			}

			/*
			 * The leaf page will have to be splitted. Get the tree latch in
			 * exclusive mode or upgrade update latch.
			 */
			if (log.isTraceEnabled()) {
				log.trace(String.format(
						"Leaf page %s has not enough space for insert.", leaf));
			}

			if (treeLatched) {
				treeLatch.upX(rootPageID);
			} else {
				leaf = latchTreeForLeafOperation(tx, rootPageID, leaf, key,
						value, false, true, true, true);
				treeLatched = true;
			}

			if (leaf.isFlagged()) {
				leaf.setFlagged(false);
			}

			leaf.setSafe(true);

			return insertIntoPage(tx, rootPageID, leaf, key, value, false,
					compact, logged, undoNextLSN);
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

	protected PageContext assureLeafInsert(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean logged)
			throws IndexAccessException {
		while (true) {
			try {
				if ((!leaf.isAfterLast()) || (!leaf.hasNextPageID())) {
					assureNextKeyForInsert(tx, rootPageID, leaf, key, value,
							logged);
					return leaf;
				} else {
					PageContext next = getNextPage(tx, rootPageID, leaf, false);

					long beforeLSN = leaf.getLSN();
					leaf.unlatch();

					try {
						assureNextKeyForInsert(tx, rootPageID, next, key,
								value, logged);
						next.cleanup();
					} catch (RewindException e) {
						leaf.latchS();
						leaf.cleanup();
						throw e;
					} catch (IndexOperationException e) {
						leaf.latchS();
						next.cleanup();
						throw e;
					}

					leaf.latchX();
					while (!leaf.isSafe()) {
						leaf.unlatch();
						treeLatch.latchSI(rootPageID);
						leaf.latchX();
					}

					long afterLSN = leaf.getLSN();

					if (beforeLSN == afterLSN) {
						return leaf;
					}

					leaf.init(); // necessary to update cached content in page
					// context
					if (leaf.search(SearchMode.GREATER_OR_EQUAL, key, value) != 0) {
						leaf.cleanup();
						leaf = descendToPosition(tx, rootPageID,
								SearchMode.GREATER_OR_EQUAL, key, value, true,
								true);
					}
				}
			} catch (RewindException e) {
				leaf = descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, true);
			} catch (IndexOperationException e) {
				leaf.cleanup();
				throw new IndexAccessException(e, "Error assuring leaf insert");
			}
		}
	}

	private void assureNextKeyForInsert(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean logged)
			throws IndexOperationException, RewindException {
		Field keyType = leaf.getKeyType();
		Field valueType = leaf.getValueType();
		byte[] nextKey = leaf.getKey();
		byte[] nextValue = (!leaf.isUnique()) ? leaf.getValue() : null;

		// check for duplicates
		if ((nextKey != null) && (keyType.compare(key, nextKey) == 0)) {
			if ((leaf.isUnique())
					|| ((valueType.compare(value, nextValue) == 0))) {
				if ((lockService != null) && (logged)) {
					readLockEntry(tx, rootPageID, leaf, nextKey, nextValue);
				}

				if (log.isTraceEnabled()) {
					log.trace(leaf.dump("Duplicate violation page"));
				}

				throw new IndexOperationException(
						"%s index %s already contains an entry (%s, %s).",
						(leaf.isUnique()) ? "Unique" : "Non-unique",
						rootPageID, keyType.toString(key), valueType
								.toString(value));
			}
		}

		if ((lockService != null) && (logged)) {
			insertLockEntry(tx, rootPageID, leaf, key, value, nextKey,
					nextValue);
		}
	}

	private void insertLockEntry(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value, byte[] nextKey, byte[] nextValue)
			throws IndexOperationException, RewindException {
		int unitID = leaf.getUnitID();
		if (!lockService.lockInsert(tx, unitID, rootPageID, key, value,
				nextKey, nextValue, true)) {
			long beforeLSN = leaf.getLSN();
			leaf.unlatch();

			try {
				lockService.lockInsert(tx, unitID, rootPageID, key, value,
						nextKey, nextValue, false);
			} finally {
				leaf.latchX();
			}

			while (!leaf.isSafe()) {
				leaf.unlatch();
				treeLatch.latchSI(rootPageID);
				leaf.latchX();
			}

			long afterLSN = leaf.getLSN();

			if (beforeLSN != afterLSN) {
				leaf.cleanup();
				throw REWIND_EXCEPTION;
			}
		}
	}

	private void readLockEntry(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value) throws IndexOperationException,
			RewindException {
		int unitID = leaf.getUnitID();
		if (!lockService.lockRead(tx, unitID, rootPageID, key, value, true)) {
			long beforeLSN = leaf.getLSN();
			leaf.unlatch();

			try {
				lockService.lockRead(tx, unitID, rootPageID, key, value, false);
			} finally {
				leaf.latchX();
			}

			while (!leaf.isSafe()) {
				leaf.unlatch();
				treeLatch.latchSI(rootPageID);
				leaf.latchX();
			}

			long afterLSN = leaf.getLSN();

			if (beforeLSN != afterLSN) {
				leaf.cleanup();
				throw REWIND_EXCEPTION;
			}
		}
	}

	private void updateLockEntry(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value) throws IndexOperationException,
			RewindException {
		int unitID = leaf.getUnitID();
		if (!lockService.lockUpdate(tx, unitID, rootPageID, key, value, true)) {
			long beforeLSN = leaf.getLSN();
			leaf.unlatch();

			try {
				lockService.lockUpdate(tx, unitID, rootPageID, key, value,
						false);
			} finally {
				leaf.latchX();
			}

			while (!leaf.isSafe()) {
				leaf.unlatch();
				treeLatch.latchSI(rootPageID);
				leaf.latchX();
			}

			long afterLSN = leaf.getLSN();

			if (beforeLSN != afterLSN) {
				leaf.cleanup();
				throw REWIND_EXCEPTION;
			}
		}
	}

	protected PageContext latchTreeForLeafOperation(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean updateOnly,
			boolean leftBound, boolean rightBound, boolean forInsert)
			throws IndexOperationException, IndexAccessException {
		boolean conditionalLatchSuccessFull = (updateOnly) ? treeLatch
				.latchUC(rootPageID) : treeLatch.latchXC(rootPageID);

		if (conditionalLatchSuccessFull) {
			return leaf;
		}

		if (log.isTraceEnabled()) {
			log.trace(String.format(
					"Establishing a POSC with an unconditional %s tree latch.",
					(updateOnly) ? "update" : "exclusive"));
		}

		long beforeLSN = leaf.getLSN();
		leaf.unlatch();

		if (updateOnly) {
			treeLatch.latchU(rootPageID);
		} else {
			treeLatch.latchX(rootPageID);
		}

		leaf.latchX();
		long afterLSN = leaf.getLSN();

		try {
			/*
			 * Now, the structure modification has completed and the log has as
			 * a point of structural consistency. If the current page was not
			 * modified and the current position is unambiguous, we can simply
			 * proceed with the current leaf. Otherwise, we have to traverse the
			 * index again to get the correct position.
			 */
			if ((beforeLSN != afterLSN)
					|| ((leftBound) && ((leaf.getPreviousKey() == null) && (leaf
							.hasPreviousPageID())))
					|| ((rightBound) && ((leaf.getKey() == null)) && (leaf
							.hasNextPageID()))) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Established a POSC with an unconditional "
									+ "%s tree latch but page was modified "
									+ "or position in %s is ambigous bound.",
							(updateOnly) ? "update" : "exclusive", leaf));
				}

				/*
				 * The tree traversal will not encounter an inconsistent tree
				 * with unsafe pages because we still hold the tree latch.
				 */
				leaf.cleanup();
				return descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true,
						forInsert);
			}

			return leaf;
		} catch (IndexOperationException e) {
			if (updateOnly) {
				treeLatch.downS(rootPageID);
			}
			treeLatch.unlatch(rootPageID);
			throw e;
		} catch (IndexAccessException e) {
			if (updateOnly) {
				treeLatch.downS(rootPageID);
			}
			treeLatch.unlatch(rootPageID);
			throw e;
		}
	}

	public PageContext deleteFromLeaf(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, long undoNextLSN,
			boolean logged) throws IndexAccessException {
		boolean treeLatched = false;

		try {
			if (value == null) {
				value = leaf.getValue();
			}

			leaf = assureLeafDelete(tx, rootPageID, leaf, key, value, logged);

			int position = leaf.getPosition();
			int entryCount = leaf.getEntryCount();
			boolean deleteLastInLeaf = false;
			boolean deleteIsBound = (position > 1) && (position < entryCount);

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
				PageContext targetLeaf = latchTreeForLeafOperation(tx,
						rootPageID, leaf, key, value, true, false, false, false);
				treeLatched = true;

				if (targetLeaf != leaf) // re-check conditions
				{
					leaf = targetLeaf;
					position = leaf.getPosition();
					entryCount = leaf.getEntryCount();
					deleteIsBound = (position > 1) && (position < entryCount);
				}

				deleteLastInLeaf = (entryCount == 1);

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

			if (!deleteLastInLeaf) {
				// record is bound to page and no page deletion will be
				// necessary
				leaf = deleteFromPage(tx, rootPageID, leaf, key, value, false,
						logged, undoNextLSN);

				if (undoNextLSN == -1) {
					/*
					 * We are in forward processing and have to signal the
					 * deletion because we might need to perform an undo of the
					 * deletion.
					 */
					leaf.setFlagged(true);
				}
			} else {
				/*
				 * Deletions leading to a page deletion are always processed as
				 * forward operations. In the undo case, however, we will write
				 * a CLR after the completed deletion.
				 */
				leaf = deleteFromPage(tx, rootPageID, leaf, key, value, false,
						true, -1);

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
		} finally {
			if (treeLatched) {
				treeLatch.unlatch(rootPageID);
			}
		}
	}

	protected PageContext assureLeafDelete(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value, boolean logged)
			throws IndexAccessException {
		while (true) {
			try {
				int unitID = leaf.getUnitID();
				Field keyType = leaf.getKeyType();
				Field valueType = leaf.getValueType();

				// check if delete key is present
				if ((leaf.getKey() == null)
						|| (keyType.compare(key, leaf.getKey()) != 0)
						|| ((value != null) && (valueType.compare(value, leaf
								.getValue()) != 0))) {
					if ((lockService != null) && (logged)) {
						if (!lockService.lockRead(tx, unitID, rootPageID, leaf
								.getKey(), leaf.getValue(), true)) {
							long beforeLSN = leaf.getLSN();
							leaf.unlatch();

							try {
								lockService.lockRead(tx, unitID, rootPageID,
										leaf.getKey(), leaf.getValue(), false);
							} finally {
								leaf.latchX();
							}

							leaf.latchX();
							while (!leaf.isSafe()) {
								leaf.unlatch();
								treeLatch.latchSI(rootPageID);
								leaf.latchX();
							}

							long afterLSN = leaf.getLSN();

							if (beforeLSN != afterLSN) {
								leaf.cleanup();
								throw REWIND_EXCEPTION;
							}
						}
					}
					throw new IndexOperationException(
							"Index %s does not contain an entry (%s, %s).",
							rootPageID, keyType.toString(key), valueType
									.toString(value));
				}

				if ((!leaf.isAfterLast()) || (!leaf.hasNextPageID())) {
					leaf.moveNext();
					assureNextKeyForDelete(tx, rootPageID, leaf, key, value);
					leaf.hasPrevious();
					return leaf;
				} else {
					PageContext next = getNextPage(tx, rootPageID, leaf, false);

					long beforeLSN = leaf.getLSN();
					leaf.unlatch();

					try {
						assureNextKeyForDelete(tx, rootPageID, next, key, value);
						next.cleanup();
					} catch (RewindException e) {
						leaf.latchS();
						leaf.cleanup();
						throw e;
					} catch (IndexOperationException e) {
						leaf.latchS();
						next.cleanup();
						throw e;
					}

					leaf.latchX();
					while (!leaf.isSafe()) {
						leaf.unlatch();
						treeLatch.latchSI(rootPageID);
						leaf.latchX();
					}

					long afterLSN = leaf.getLSN();

					if (beforeLSN == afterLSN) {
						return leaf;
					}

					leaf.init(); // necessary to update cached content in page
					// context
					if (leaf.search(SearchMode.GREATER_OR_EQUAL, key, value) != 0) {
						leaf.cleanup();
						leaf = descendToPosition(tx, rootPageID,
								SearchMode.GREATER_OR_EQUAL, key, value, true,
								true);
					}
				}
			} catch (RewindException e) {
				leaf = descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, true);
			} catch (IndexOperationException e) {
				leaf.cleanup();
				throw new IndexAccessException(e, "Error assuring leaf insert");
			}
		}
	}

	private void assureNextKeyForDelete(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] value)
			throws IndexOperationException, RewindException {
		Field keyType = leaf.getKeyType();
		Field valueType = leaf.getValueType();
		byte[] nextKey = leaf.getKey();
		byte[] nextValue = (leaf.isUnique()) ? leaf.getValue() : null;

		if (lockService != null) {
			deleteLockEntry(tx, rootPageID, leaf, key, value, nextKey,
					nextValue);
		}
	}

	private void deleteLockEntry(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value, byte[] nextKey, byte[] nextValue)
			throws IndexOperationException, RewindException {
		int unitID = leaf.getUnitID();
		if (!lockService.lockDelete(tx, unitID, rootPageID, key, value,
				nextKey, nextValue, true)) {
			long beforeLSN = leaf.getLSN();
			leaf.unlatch();

			try {
				lockService.lockDelete(tx, unitID, rootPageID, key, value,
						nextKey, nextValue, false);
			} finally {
				leaf.latchX();
			}

			while (!leaf.isSafe()) {
				leaf.unlatch();
				treeLatch.latchSI(rootPageID);
				leaf.latchX();
			}

			long afterLSN = leaf.getLSN();

			if (beforeLSN != afterLSN) {
				leaf.cleanup();
				throw REWIND_EXCEPTION;
			}
		}
	}

	public PageContext openInternal(Tx tx, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode,
			PageID hintPageID, long LSN) throws IndexAccessException {
		PageContext leaf = null;

		if (hintPageID != null) {
			try {
				PageContext hintPage = getPage(tx, hintPageID, openMode
						.forUpdate(), openMode.forUpdate());

				if ((hintPage.getLSN() != LSN)
						|| (!hintPage.getRootPageID().equals(rootPageID))) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Page %s can not be used"
								+ " for direct access because "
								+ "it was modified after the update "
								+ "or it is no longer part of index %s.",
								hintPageID, rootPageID));
					}

					hintPage.cleanup();
				} else {
					hintPage.search(searchMode, key, value);

					if ((hintPage.getPosition() == 1)
							|| (hintPage.getPosition() == hintPage
									.getEntryCount())) {
						if (log.isTraceEnabled()) {
							log.trace(String.format("Page %s can not be used "
									+ "for direct access because "
									+ "the search key is not bound "
									+ "to this page.", hintPageID));
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

		while (true) {
			try {
				leaf = descendToPosition(tx, rootPageID, searchMode, key,
						value, openMode.forUpdate(), false);

				if (lockService != null) {
					readLockEntry(tx, rootPageID, leaf, leaf.getKey(), leaf
							.getValue());
				}

				if (!openMode.doLog()) {
					tx.addFlushHook(rootPageID.getContainerNo());
				}

				return leaf;

			} catch (RewindException e) {
				// continue
			} catch (IndexOperationException e) {
				leaf.cleanup();
				throw new IndexAccessException(e);
			}
		}
	}

	protected PageContext insertIntoPage(Tx tx, PageID rootPageID,
			PageContext page, byte[] insertKey, byte[] insertValue,
			boolean isStructureModification, boolean compact, boolean logged,
			long undoNextLSN) throws IndexAccessException {
		// page = preparePageForInsert(tx, rootPageID, page, insertKey,
		// insertValue, compact, logged);

		try {
			Field keyType = page.getKeyType();
			Field valueType = page.getValueType();

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page before insert"));
			}

			while (!page.insert(insertKey, insertValue,
					isStructureModification, logged, undoNextLSN)) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Splitting page %s for insert of (%s, %s).", page,
							keyType.toString(insertKey), valueType
									.toString(insertValue)));
				}

				page = split(tx, rootPageID, page, insertKey, insertValue,
						compact, logged);

				if (log.isTraceEnabled()) {
					log.trace(page.dump("Splitted page before insert"));
				}
			}

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after insert"));
			}

			if (VERIFY_ACTION) {
				(new IndexPageHelper(bufferMgr)).verifyPage(page);
			}

			int insertType = (page.getPageType() == PageType.INDEX_LEAF) ? TxStats.BTREE_INSERTS
					: TxStats.BTREE_BRANCH_INSERTS;
			tx.getStatistics().increment(insertType);

			return page;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not log record insertion.");
		}
	}

	protected PageContext preparePageForInsert(Tx tx, PageID rootPageID,
			PageContext page, byte[] insertKey, byte[] insertValue,
			boolean compact, boolean logged) throws IndexAccessException {
		try {
			Field keyType = page.getKeyType();
			Field valueType = page.getValueType();

			while (!page.hasEnoughSpaceForInsert(insertKey, insertValue)) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Splitting page %s for insert of (%s, %s).", page,
							keyType.toString(insertKey), valueType
									.toString(insertValue)));
				}

				page = split(tx, rootPageID, page, insertKey, insertValue,
						compact, logged);
			}
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Error preparing page for insert.");
		}

		return page;
	}

	private PageContext split(Tx tx, PageID rootPageID, PageContext page,
			byte[] insertKey, byte[] insertValue, boolean compact,
			boolean logged) throws IndexAccessException {
		// Remember LSN of previously logged action.
		long rememberedLSN = tx.checkPrevLSN();

		try {
			// Split and propagate if necessary.
			if (page.getPageID().equals(rootPageID)) {
				page = splitRoot(tx, rootPageID, page, insertKey, insertValue,
						compact, logged);
			} else {
				page = splitNonRoot(tx, rootPageID, page, insertKey,
						insertValue, compact, logged);
			}

			// Write dummy CLR to skip page split in undo processing.
			logDummyCLR(tx, rememberedLSN);
		} catch (IndexAccessException e) {
			log.error(e);
			undo(tx, rememberedLSN);
			throw e;
		} catch (IndexOperationException e) {
			log.error(e);
			undo(tx, rememberedLSN);
			page.cleanup();
			throw new IndexAccessException(e,
					"Could not log dummy CLR after page split.");
		}
		return page;
	}

	public PageContext updateInLeaf(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] newValue, byte[] oldValue, long undoNextLSN)
			throws IndexAccessException {
		boolean logged = true;
		boolean treeLatched = false;

		try {
			if (!leaf.isUnique()) {
				leaf.cleanup();
				throw new IndexAccessException(
						"Non-unique %s does not support updates.", rootPageID);
			}

			leaf = assureLeafUpdate(tx, rootPageID, leaf, key, newValue,
					oldValue, logged);

			if (leaf.isFlagged()) {
				/*
				 * We have to assure that no tree modification is going on
				 * (point of structural consistency) because a system failure
				 * might lead to a situation where the undo of the previous
				 * deletion in this leaf might require a logical undo (our
				 * update may consume space required for page-oriented undo) and
				 * thus requiring a consistent tree for traversal. Therefore, we
				 * acquire the tree latch to ensure that we have a point of
				 * structural consistency in the log before we update in the
				 * same page.
				 */
				if (log.isTraceEnabled()) {
					log.trace(String.format("Leaf page %s is flagged.", leaf));
				}

				leaf = latchTreeForLeafOperation(tx, rootPageID, leaf, key,
						oldValue, true, false, false, false);
				treeLatched = true;
			}

			if (!leaf.hasEnoughSpaceForUpdate(key, newValue)) {
				/*
				 * The leaf page will have to be splitted. Get the tree latch in
				 * exclusive mode or upgrade update latch.
				 */
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Leaf page %s has not enough space for update.",
							leaf));
				}

				if (treeLatched) {
					treeLatch.upX(rootPageID);
				} else {
					leaf = latchTreeForLeafOperation(tx, rootPageID, leaf, key,
							oldValue, false, false, false, false);
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

			return updateInPage(tx, rootPageID, leaf, key, newValue, oldValue,
					false, logged, undoNextLSN);
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

	protected PageContext assureLeafUpdate(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] newValue, byte[] oldValue,
			boolean logged) throws IndexAccessException {
		while (true) {
			try {
				if ((!leaf.isAfterLast()) || (!leaf.hasNextPageID())) {
					leaf.moveNext();
					assureNextKeyForUpdate(tx, rootPageID, leaf, key, oldValue,
							newValue, logged);
					leaf.hasPrevious();
					return leaf;
				} else {
					PageContext next = getNextPage(tx, rootPageID, leaf, false);

					long beforeLSN = leaf.getLSN();
					leaf.unlatch();

					try {
						assureNextKeyForUpdate(tx, rootPageID, leaf, key,
								oldValue, newValue, logged);
						next.cleanup();
					} catch (RewindException e) {
						leaf.latchS();
						leaf.cleanup();
						throw e;
					} catch (IndexOperationException e) {
						leaf.latchS();
						next.cleanup();
						throw e;
					}

					leaf.latchX();
					while (!leaf.isSafe()) {
						leaf.unlatch();
						treeLatch.latchSI(rootPageID);
						leaf.latchX();
					}

					long afterLSN = leaf.getLSN();

					if (beforeLSN == afterLSN) {
						return leaf;
					}

					// necessary to update cached
					// content in page context
					leaf.init();
					if (leaf.search(SearchMode.GREATER_OR_EQUAL, key, oldValue) != 0) {
						leaf.cleanup();
						leaf = descendToPosition(tx, rootPageID,
								SearchMode.GREATER_OR_EQUAL, key, oldValue,
								true, false);
					}
				}
			} catch (RewindException e) {
				leaf = descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, oldValue, true, false);
			} catch (IndexOperationException e) {
				leaf.cleanup();
				throw new IndexAccessException(e, "Error assuring leaf update");
			}
		}
	}

	private void assureNextKeyForUpdate(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] key, byte[] oldValue, byte[] newValue,
			boolean logged) throws IndexOperationException, RewindException {
		// TODO
	}

	protected PageContext updateInPage(Tx tx, PageID rootPageID,
			PageContext page, byte[] key, byte[] newValue, byte[] oldValue,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		try {
			page = preparePageForUpdate(tx, rootPageID, page, key, newValue,
					oldValue, logged);

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page before update"));
			}

			page.setValue(newValue, isStructureModification, logged,
					undoNextLSN);

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after update"));
			}

			if (VERIFY_ACTION) {
				(new IndexPageHelper(bufferMgr)).verifyPage(page);
			}

			return page;
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e, "Could not log record update.");
		}
	}

	protected PageContext preparePageForUpdate(Tx tx, PageID rootPageID,
			PageContext page, byte[] key, byte[] newValue, byte[] oldValue,
			boolean logged) throws IndexAccessException {
		try {
			Field keyType = page.getKeyType();
			Field valueType = page.getValueType();

			while (!page.hasEnoughSpaceForInsert(key, oldValue)) {
				if (log.isTraceEnabled()) {
					log.trace(String
							.format("Splitting page %s for update"
									+ " of (%s, %s) to (%s, %s).", page,
									keyType.toString(key), valueType
											.toString(oldValue), keyType
											.toString(key), valueType
											.toString(newValue)));
				}

				page = split(tx, rootPageID, page, key, oldValue, false, logged);
			}
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Error preparing page for update.");
		}

		return page;
	}

	protected PageContext splitNonRoot(Tx tx, PageID rootPageID,
			PageContext left, byte[] insertKey, byte[] insertValue,
			boolean compact, boolean logged) throws IndexAccessException {
		PageContext right = null;
		PageContext parent = null;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Splitting non-root page %s.", left
						.getPageID()));
				log.trace(left.dump("Split Page"));
			}

			// find out where to split
			int insertPosition = left.getPosition();
			int splitPosition = chooseSplitPosition(left, insertPosition,
					insertKey, insertValue, compact);
			left.moveTo(splitPosition - 1);
			byte[] separatorKey = ((insertPosition == splitPosition) && (insertPosition <= left
					.getEntryCount())) ? insertKey : left.getKey();
			left.moveNext();

			int leftPageType = left.getPageType();

			// allocate and format new right page
			Field keyType = left.getKeyType();
			Field valueType = left.getValueType();
			right = allocate(tx, -1, left.getUnitID(), leftPageType,
					rootPageID, keyType, valueType, left.isUnique(), left
							.isCompressed(), logged);

			if (leftPageType == PageType.INDEX_TREE) {
				// skip current record and set before page of right page
				right.setBeforePageID(left.getAfterPageID(), logged, -1);
				separatorKey = left.getKey();
				left.delete(true, logged, -1);

				if (insertPosition >= splitPosition) {
					splitPosition++;
				}
			}

			// shift second half to right page
			while (!left.isAfterLast()) {
				right.insert(left.getKey(), left.getValue(), true, logged, -1);
				right.moveNext();
				left.delete(true, logged, -1);
			}

			if (leftPageType == PageType.INDEX_LEAF) {
				// update next pointer if it exists
				PageContext next = getPage(tx, left.getNextPageID(), true,
						false);

				// chain left page with right page
				left.setNextPageID(right.getPageID(), logged, -1);
				right.setPreviousPageID(left.getPageID(), logged, -1);

				if (next != null) {
					try {
						right.setNextPageID(next.getPageID(), logged, -1);
						next.setPreviousPageID(right.getPageID(), logged, -1);
					} finally {
						next.cleanup();
					}
				}
			}

			// mark pages unsafe to signal other threads the structure
			// modification
			left.setSafe(false);
			right.setSafe(false);
			right.unlatch();
			left.unlatch();

			try {
				// find insert position for separator entry in parent page
				parent = descendToParent(tx, rootPageID, rootPageID,
						separatorKey, left.getPageID(), true);

				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Insert separator (%s, %s) in parent page %s.",
							keyType.toString(separatorKey), right.getPageID(),
							parent));
				}

				parent = insertIntoPage(tx, rootPageID, parent, separatorKey,
						right.getPageID().getBytes(), true, compact, logged, -1);
			} finally {
				// mark pages save to signal other threads the end of the
				// structure modification
				left.latchX();
				right.latchX();
				left.setSafe(true);
				right.setSafe(true);
			}

			// split at this level is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of non-root page %s completed.",
						left));
				log.trace(parent.dump("Parent page"));
				log.trace(left.dump("Left page (splitted)"));
				log.trace(right.dump("Right page (new)"));
			}

			if (VERIFY_ACTION) {
				(new IndexPageHelper(bufferMgr)).verifySplit(left, right,
						parent, separatorKey, keyType, valueType);
			}

			int splitType = (leftPageType == PageType.INDEX_LEAF) ? TxStats.BTREE_LEAF_ALLOCATIONS
					: TxStats.BTREE_BRANCH_ALLOCATE_COUNT;
			tx.getStatistics().increment(splitType);

			// Free unneeded split page
			if (insertPosition < splitPosition) {
				// unlatch and unfix right split page
				left.moveTo(insertPosition);
				parent.cleanup();
				right.cleanup();
				return left;
			} else {
				// unlatch and unfix right left split page
				right.moveTo(insertPosition - splitPosition + 1);
				parent.cleanup();
				left.cleanup();
				return right;
			}
		} catch (IndexOperationException e) {
			left.cleanup();
			if (right != null)
				right.cleanup();
			if (parent != null)
				parent.cleanup();
			throw new IndexAccessException(e);
		}
	}

	protected int chooseSplitPosition(PageContext splitPage,
			int insertPosition, byte[] insertKey, byte[] insertValue,
			boolean compact) throws IndexOperationException {
		int entryCount = splitPage.getEntryCount();
		int pageType = splitPage.getPageType();
		Field keyType = splitPage.getKeyType();
		Field valueType = splitPage.getValueType();
		byte[] separatorKey = null;

		if (entryCount < 3) {
			log.error(String.format("Cannot split page %s because"
					+ " it contains less than three records.", splitPage
					.getPageID()));
			log.error(splitPage.dump("Page to split"));
			throw new IndexOperationException("Cannot split page %s because"
					+ " it contains less than three records.", splitPage
					.getPageID());
		}

		if (compact) {
			if (insertPosition == entryCount + 1) {
				return (pageType == PageType.INDEX_LEAF) ? entryCount + 1
						: entryCount;
			} else {
				return (pageType == PageType.INDEX_LEAF) ? entryCount
						: entryCount - 1;
			}
		} else {
			return (entryCount / 2) + 1;
		}
	}

	protected PageContext splitRoot(Tx tx, PageID rootPageID, PageContext root,
			byte[] insertKey, byte[] insertValue, boolean compact,
			boolean logged) throws IndexAccessException {
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
					keyType, valueType, root.isUnique(), root.isCompressed(),
					logged);
			right = allocate(tx, -1, root.getUnitID(), rootPageType,
					rootPageID, keyType, valueType, root.isUnique(), root
							.isCompressed(), logged);

			// find out where to split
			int insertPosition = root.getPosition();
			int splitPosition = chooseSplitPosition(root, insertPosition,
					insertKey, insertValue, compact);
			root.moveTo(splitPosition - 1);
			byte[] separatorKey = ((insertPosition == splitPosition) && (insertPosition <= root
					.getEntryCount())) ? insertKey : root.getKey();
			root.moveNext();

			if (rootPageType == PageType.INDEX_TREE) {
				// skip current record and set before pages
				right.setBeforePageID(root.getAfterPageID(), logged, -1);
				left.setBeforePageID(root.getBeforePageID(), logged, -1);
				separatorKey = root.getKey();
				root.delete(true, logged, -1);
				if (insertPosition >= splitPosition) {
					splitPosition++;
				}
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

			if (rootPageType == PageType.INDEX_LEAF) {
				// chain left page with right page
				left.setNextPageID(right.getPageID(), logged, -1);
				right.setPreviousPageID(left.getPageID(), logged, -1);

				// convert root from leaf into tree page
				root.format(root.getUnitID(), PageType.INDEX_TREE, rootPageID,
						keyType, Field.PAGEID, root.isUnique(), root
								.isCompressed(), logged, -1);
				// reposition context in converted root page
				root.moveFirst();
			}

			// insert separator in converted root page
			root.insert(separatorKey, right.getPageID().getBytes(), true,
					logged, -1);
			root.setBeforePageID(left.getPageID(), logged, -1);

			// root split is complete
			if (log.isTraceEnabled()) {
				log.trace(String.format("Split of root page %s completed.",
						root));
				log.trace(root.dump("Root page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			// statistic accounting for root page splits -> index height
			tx.getStatistics().increment(TxStats.BTREE_ROOT_SPLITS);

			if (VERIFY_ACTION) {
				(new IndexPageHelper(bufferMgr)).verifyRootSplit(root, left,
						right, keyType, valueType, separatorKey);
			}

			// Free unneeded split page and return
			if (insertPosition < splitPosition) {
				// unlatch and unfix right split page
				left.moveTo(insertPosition);
				right.cleanup();
				root.cleanup();
				return left;
			} else {
				// unlatch and unfix right left split page
				right.moveTo(insertPosition - splitPosition + 1);
				left.cleanup();
				root.cleanup();
				return right;
			}
		} catch (IndexOperationException e) {
			left.cleanup();
			right.cleanup();
			root.cleanup();
			throw new IndexAccessException(e, "Error during root split.");
		}
	}

	protected PageContext descendToParent(Tx tx, PageID rootPageID,
			PageID pageID, byte[] separatorKey, PageID targetPageID,
			boolean forUpdate) throws IndexAccessException {
		PageContext page = null;
		PageContext parentPage = null;

		if (log.isTraceEnabled()) {
			log.trace(String.format(
					"Descending to page %s looking for parent of page %s).",
					pageID, targetPageID));
		}

		try {
			page = getPage(tx, pageID, forUpdate, forUpdate);
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"An error occured while accessing an index page.");
		}

		try {
			int pageType = page.getPageType();
			long pageLSN = page.getLSN();

			if (pageType == PageType.INDEX_LEAF) {
				if (log.isTraceEnabled()) {
					log.trace(String.format("Backtracking search for "
							+ "parent of leaf page %s in leaf page %s",
							targetPageID, pageID));
				}

				if (forUpdate) {
					page.downS();
				}

				page.cleanup();
				return null;
			}

			if (pageType != PageType.INDEX_TREE) {
				log.error(page.dump(String.format("Search for parent page "
						+ "ended in page with invalid type %s", pageType)));

				if (forUpdate) {
					page.downS();
				}
				page.cleanup();
				throw new IndexAccessException(String.format(
						"Page %s has an invalid index page type '%s'.", pageID,
						pageType));
			}

			if (log.isTraceEnabled()) {
				log
						.trace(String.format("Inspecting tree page %s "
								+ "using key %s searching for parent of %s.",
								page, page.getKeyType().toString(separatorKey),
								targetPageID));
				log.trace(page.dump("tree page"));
			}

			Field keyType = page.getKeyType();
			Field valueType = page.getValueType();

			// first check if this page is the parent we are looking for
			if (page.search(SearchMode.GREATER_OR_EQUAL, separatorKey, null) <= 0) {
				if (page.getPreviousAfterPageID().equals(targetPageID)) {
					parentPage = page;
				}
			}

			if (parentPage == null) {
				do {
					if (page.getAfterPageID().equals(targetPageID)) {
						page.moveNext();
						parentPage = page;
						break;
					}
				} while ((page.hasNext())
						&& (keyType.compare(separatorKey, page.getKey()) <= 0));
			}

			if (parentPage != null) {
				if (log.isTraceEnabled()) {
					log.trace(String.format("Found parent page %s of %s.", page
							.getPageID(), targetPageID));
				}

				if (forUpdate) {
					page.upX();
				}

				return page;
			} else {
				return findParentInChildren(tx, rootPageID, page, separatorKey,
						targetPageID, forUpdate, keyType);
			}
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"An error occured while accessing an index page.");
		}
	}

	private PageContext findParentInChildren(Tx tx, PageID rootPageID,
			PageContext page, byte[] separatorKey, PageID targetPageID,
			boolean forUpdate, Field keyType) throws IndexAccessException {
		try {
			// Recursively look for the parent node in eligible children
			if (forUpdate) {
				page.downS();
			}

			PageContext parentPage = null;

			if (page.search(SearchMode.GREATER_OR_EQUAL, separatorKey, null) <= 0) {
				parentPage = descendToParent(tx, rootPageID, page
						.getPreviousAfterPageID(), separatorKey, targetPageID,
						forUpdate);
			}

			if (parentPage == null) {
				do {
					parentPage = descendToParent(tx, rootPageID, page
							.getAfterPageID(), separatorKey, targetPageID,
							forUpdate);
				} while ((parentPage == null) && (page.hasNext())
						&& (keyType.compare(separatorKey, page.getKey()) <= 0));
			}

			return parentPage;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Error while inspecting child pages.");
		} finally {
			page.cleanup();
		}
	}

	protected PageContext deleteFromPage(Tx tx, PageID rootPageID,
			PageContext page, byte[] deleteKey, byte[] deleteValue,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexAccessException {
		try {
			Field keyType = page.getKeyType();
			Field valueType = page.getValueType();

			if (deleteValue == null) {
				deleteValue = page.getValue();
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("Deleting (%s, %s) from page %s.",
						keyType.toString(deleteKey), valueType
								.toString(deleteValue), page.getPageID()));
			}

			page.delete(isStructureModification, logged, undoNextLSN);

			if (log.isTraceEnabled()) {
				log.trace(page.dump("Page after delete"));
			}

			int deleteType = (page.getPageType() == PageType.INDEX_LEAF) ? TxStats.BTREE_DELETES
					: TxStats.BTREE_BRANCH_DELETES;
			tx.getStatistics().increment(deleteType);

			return handleUnderflow(tx, rootPageID, page, deleteKey,
					deleteValue, logged);
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Could not perform record deletion.");
		}
	}

	protected PageContext handleUnderflow(Tx tx, PageID rootPageID,
			PageContext page, byte[] deleteKey, byte[] deleteValue,
			boolean logged) throws IndexAccessException,
			IndexOperationException {
		boolean deletedFromRoot = (page.getPageID().equals(rootPageID));
		boolean underFlow = (page.getEntryCount() == 0);

		if ((!deletedFromRoot && underFlow)) {
			if (page.getPageType() == PageType.INDEX_TREE) {
				// non-root tree page underflowed after deletion
				page = reorganize(tx, rootPageID, page, deleteKey, logged);
			} else {
				long rememberedLSN = tx.checkPrevLSN();

				try {
					// delete empty leaf page
					page = deleteLeaf(tx, rootPageID, page, deleteKey,
							deleteValue, logged);

					logDummyCLR(tx, rememberedLSN);
				} catch (IndexAccessException e) {
					log.error(e);
					undo(tx, rememberedLSN);
					throw e;
				} catch (IndexOperationException e) {
					log.error(e);
					undo(tx, rememberedLSN);
					page.cleanup();
					throw new IndexAccessException(e,
							"Could not log dummy CLR after leaf deletion.");
				}
			}
		}

		return page;
	}

	private PageContext reorganize(Tx tx, PageID rootPageID, PageContext page,
			byte[] deletedKey, boolean logged) throws IndexAccessException {
		PageContext parent = null;
		PageContext left = null;
		PageContext right = null;
		boolean doMerge = false;

		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Starting reorganization of "
						+ "index after underflow of tree page %s", page));
				log.trace(page.dump("Underflow page"));
			}

			// set transient flag of context page to signal other threads the
			// structure modification
			page.setSafe(false);
			page.unlatch();

			try {
				parent = descendToParent(tx, rootPageID, rootPageID,
						deletedKey, page.getPageID(), true);
			} finally {
				page.latchX();
				page.setSafe(true);
			}

			if (log.isTraceEnabled()) {
				log.trace(parent.dump("Parent page"));
			}

			if (!parent.isAfterLast()) {
				left = page;
				right = getPage(tx, parent.getAfterPageID(), true, false);
			} else {
				parent.hasPrevious();
				left = getPage(tx, parent.getPreviousAfterPageID(), true, false);
				right = page;
			}

			if (left.mergeable(right, parent.getKey())) {
				if ((parent.getPageID().equals(rootPageID))
						&& (parent.getEntryCount() == 1)) {
					// Collapse root when it has only one separator entry
					// separating left and right page.
					return collapseRoot(tx, parent, left, right, deletedKey,
							null, true, logged);
				} else if (right.getEntryCount() <= left.getEntryCount()) {
					return mergeRight(tx, rootPageID, parent, left, right,
							logged);
				} else {
					return mergeLeft(tx, rootPageID, parent, left, right,
							logged);
				}
			} else {
				return balance(tx, parent, left, right, page == left, logged);
			}
		} catch (IndexOperationException e) {
			page.cleanup();
			if (parent != null) {
				parent.cleanup();
			}
			throw new IndexAccessException(e,
					"Error during reorganization of index.");
		}
	}

	private PageContext deleteLeaf(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] deletedKey, byte[] deletedValue, boolean logged)
			throws IndexAccessException {
		PageContext previous = null;
		PageContext next = null;
		PageContext parent = null;
		PageContext newLeaf = null;

		try {

			if (log.isTraceEnabled()) {
				log.trace(String.format("Deleting empty leaf page %s.", leaf));
			}

			// mark page unsafe to signal other threads the structure
			// modification
			PageID previousPageID = leaf.getPreviousPageID();
			PageID nextPageID = leaf.getNextPageID();
			leaf.setSafe(false);
			PageID nextNextPageID = null;
			PageID previousPreviousPageID = null;

			// unchain leaf
			next = getPage(tx, nextPageID, true, false);

			if (next != null) {
				next.setPreviousPageID(previousPageID, logged, -1);
				nextNextPageID = next.getNextPageID();
			}

			leaf.unlatch();
			previous = getPage(tx, previousPageID, true, false);
			leaf.latchX();
			leaf.setSafe(true);

			if (previous != null) {
				previous.setNextPageID(nextPageID, logged, -1);
				previousPreviousPageID = previous.getPreviousPageID();
			}

			if ((nextPageID != null) && (previousPageID != null)) {
				try {
					previous.cleanup();
					next.setSafe(false); // protected page from modifications
					// while we delete the leaf
					next.unlatch();
					propagateDeleteLeaf(tx, rootPageID, leaf, deletedKey,
							logged);
					next.latchX();
					next.setSafe(true);
					next.moveFirst();
					return next;
				} catch (IndexAccessException e) {
					next.latchS();
					next.cleanup();
					throw e;
				}
			} else if (nextPageID != null) {
				if (nextNextPageID == null) {
					next.setSafe(false); // protected page from modifications
					// while we delete the leaf
					leaf.unlatch();
					next.unlatch();
					parent = descendToParent(tx, rootPageID, rootPageID,
							deletedKey, leaf.getPageID(), true);
					leaf.latchX();
					next.latchX();
					return collapseRoot(tx, parent, leaf, next, deletedKey,
							deletedValue, true, logged);
				} else {
					try {
						next.setSafe(false); // protected page from
						// modifications while we delete
						// the leaf
						next.unlatch();
						propagateDeleteLeaf(tx, rootPageID, leaf, deletedKey,
								logged);
						next.latchX();
						next.setSafe(true);
						next.moveFirst();
						return next;
					} catch (IndexAccessException e) {
						next.latchS();
						next.cleanup();
						throw e;
					}
				}
			} else {
				if (previousPreviousPageID == null) {
					previous.setSafe(false);
					leaf.unlatch();
					previous.unlatch();
					parent = descendToParent(tx, rootPageID, rootPageID,
							deletedKey, leaf.getPageID(), true);
					leaf.latchX();
					previous.latchX();
					return collapseRoot(tx, parent, previous, leaf, deletedKey,
							deletedValue, false, logged);
				} else {
					try {
						previous.setSafe(false); // protected page from
						// modifications while we
						// delete the leaf
						previous.unlatch();
						propagateDeleteLeaf(tx, rootPageID, leaf, deletedKey,
								logged);
						previous.latchX();
						previous.setSafe(true);
						previous.moveAfterLast();
						leaf.moveAfterLast();
						return previous;
					} catch (IndexAccessException e) {
						previous.latchS();
						previous.cleanup();
						throw e;
					}
				}
			}
		} catch (IndexOperationException e) {
			leaf.cleanup();
			next.cleanup();
			previous.cleanup();
			throw new IndexAccessException(e,
					"Could not log page deletion operations.");
		} catch (IndexAccessException e) {
			leaf.cleanup();
			throw new IndexAccessException(e,
					"Error during deletion of leaf page.");
		}
	}

	protected void propagateDeleteLeaf(Tx tx, PageID rootPageID,
			PageContext leaf, byte[] deletedKey, boolean logged)
			throws IndexAccessException {
		PageContext parent = null;

		try {
			leaf.setSafe(false);
			leaf.unlatch();

			try {
				parent = descendToParent(tx, rootPageID, rootPageID,
						deletedKey, leaf.getPageID(), true);
			} finally {
				leaf.latchX();
				leaf.setSafe(true);
			}

			if (parent.hasPrevious()) {
				// simply delete separator to context page from parent
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Deleting separator to leaf page %s from parent %s",
											leaf, parent));
				}

				parent = deleteFromPage(tx, rootPageID, parent,
						parent.getKey(), parent.getValue(), true, logged, -1);
			} else {
				// make next page new before page in parent
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Deleting separator to new before leaf page %s (current value = %s) in parent %s.",
											leaf, parent.getAfterPageID(),
											parent));
				}

				parent.setBeforePageID(parent.getAfterPageID(), logged, -1);
				parent = deleteFromPage(tx, rootPageID, parent,
						parent.getKey(), parent.getValue(), true, logged, -1);
			}

			// "Reset" page properties to get the required undo information in
			// the log
			leaf.setNextPageID(null, logged, -1);
			leaf.setPreviousPageID(null, logged, -1);
			leaf.format(leaf.getUnitID(), PageType.INDEX_LEAF, rootPageID, leaf
					.getKeyType(), leaf.getValueType(), leaf.isUnique(), leaf
					.isCompressed(), logged, -1);
			leaf.deletePage();

			parent.cleanup();
			tx.getStatistics().increment(TxStats.BTREE_LEAF_DEALLOCATIONS);
		} catch (IndexOperationException e) {
			leaf.cleanup();
			parent.cleanup();
			throw new IndexAccessException(e,
					"Could not log leaf deletion operations.");
		}
	}

	protected PageContext balance(Tx tx, PageContext parent, PageContext left,
			PageContext right, boolean targetIsLeftPage, boolean logged)
			throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace("Start rebalancing of sibling pages.");
				log.trace(parent.dump("Parent page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			int position = (targetIsLeftPage) ? left.getPosition() : right
					.getPosition();
			int leftCount = left.getEntryCount();
			int rightCount = right.getEntryCount();
			boolean returnLeftPage = targetIsLeftPage;
			int returnPosition = position;

			if (leftCount < rightCount) {
				// rotate right before page over parent to left page
				left.moveAfterLast();
				right.moveFirst();
				left.insert(parent.getKey(),
						right.getBeforePageID().getBytes(), true, logged, -1);

				// now shift some records from right page to left page until
				// pages are balanced
				left.moveAfterLast();
				int shifted = 1;
				for (; leftCount + shifted < rightCount; shifted++) {
					left.insert(right.getKey(), right.getValue(), true, logged,
							-1);
					left.moveNext();
					right.delete(true, logged, -1);
				}

				// update separator in parent
				parent.delete(true, logged, -1);
				parent.insert(right.getKey(), right.getPageID().getBytes(),
						true, logged, -1);

				// update right before page by deleting first separator key
				right.setBeforePageID(right.getAfterPageID(), logged, -1);
				right.delete(true, logged, -1);

				if (!targetIsLeftPage) {
					if (shifted < position) {
						returnPosition = position - shifted;
					} else {
						returnLeftPage = true;
						returnPosition = leftCount + position;
					}
				}
			} else {
				// convert right before page to entry in right page with
				// separator from parent if necessary
				left.moveLast();
				right.moveFirst();
				right.insert(parent.getKey(), right.getBeforePageID()
						.getBytes(), true, logged, -1);

				// now shift some records from left page to right page until
				// pages are balanced
				right.moveFirst();
				int shifted = 1;
				for (; rightCount + shifted < leftCount; shifted++) {
					right.insert(left.getKey(), left.getValue(), true, logged,
							-1);
					left.delete(true, logged, -1);
					left.moveLast();
				}

				// update separator key in parent
				parent.delete(true, logged, -1);
				parent.insert(left.getKey(), right.getPageID().getBytes(),
						true, logged, -1);

				// update right before page by deleting last separator key in
				// left
				right.moveFirst();
				right.setBeforePageID(left.getAfterPageID(), logged, -1);
				left.delete(true, logged, -1);

				if ((targetIsLeftPage) && (shifted >= position)) {
					returnLeftPage = false;
					returnPosition = shifted - position;
				}
			}

			if (log.isTraceEnabled()) {
				log.trace("Finished rebalancing of sibling pages.");
				log.trace(parent.dump("Parent page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			// balance complete
			if (returnLeftPage) {
				left.moveTo(returnPosition);
				parent.cleanup();
				right.cleanup();
				return left;
			} else {
				// reset transient flag
				right.moveTo(returnPosition);
				parent.cleanup();
				left.cleanup();
				return right;
			}
		} catch (IndexOperationException e) {
			right.cleanup();
			left.cleanup();
			parent.cleanup();
			throw new IndexAccessException(e, "Error during rebalancing index.");
		}
	}

	private PageContext mergeRight(Tx tx, PageID rootPageID,
			PageContext parent, PageContext left, PageContext right,
			boolean logged) throws IndexAccessException {
		try {
			// save separator before delete
			byte[] separatorKey = parent.getKey();

			if (log.isTraceEnabled()) {
				log.trace("Starting merge of right page to left sibling.");
				log.trace(parent.dump("Parent page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			// append separator for before page in left page
			left.moveAfterLast();
			PageID beforePageID = right.getBeforePageID();
			left
					.insert(separatorKey, beforePageID.getBytes(), true,
							logged, -1);

			// append right page to left page
			if (right.moveFirst()) {
				do {
					left.moveAfterLast();
					left.insert(right.getKey(), right.getValue(), true, logged,
							-1);
				} while (right.hasNext());
			}

			parent = deleteFromPage(tx, rootPageID, parent, parent.getKey(),
					parent.getValue(), true, logged, -1);

			/*
			 * "Reset" page properties to get the required undo information in
			 * the log
			 */
			right.setBeforePageID(null, logged, -1);
			right.format(right.getUnitID(), PageType.INDEX_TREE, rootPageID,
					right.getKeyType(), right.getValueType(), right.isUnique(),
					right.isCompressed(), logged, -1);
			right.deletePage();

			if (log.isTraceEnabled()) {
				log.trace("Finished merge of right page to left sibling.");
				log.trace(parent.dump("Parent page"));
				log.trace(left.dump("Left page"));
			}

			tx.getStatistics().increment(TxStats.BTREE_BRANCH_DEALLOCATE_COUNT);

			// merge complete
			parent.cleanup();
			return left;
		} catch (IndexOperationException e) {
			left.cleanup();
			right.cleanup();
			parent.cleanup();
			throw new IndexAccessException(e, "Could not log merge operations.");
		}
	}

	private PageContext mergeLeft(Tx tx, PageID rootPageID, PageContext parent,
			PageContext left, PageContext right, boolean logged)
			throws IndexAccessException {
		try {
			// save separator before delete
			byte[] separatorKey = parent.getKey();

			if (log.isTraceEnabled()) {
				log.trace("Starting merge of left page to right sibling.");
				log.trace(parent.dump("Parent page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			// prepend separator for before page in right page
			right.moveFirst();
			PageID beforePageID = right.getBeforePageID();
			right.insert(separatorKey, beforePageID.getBytes(), true, logged,
					-1);

			// prepend content of left page in right page
			if (left.moveFirst()) {
				do {
					right.insert(left.getKey(), left.getValue(), true, logged,
							-1);
					right.moveNext();
				} while (left.hasNext());
			}

			// append separator for before page in left page
			right.setBeforePageID(left.getBeforePageID(), logged, -1);
			// update old separator to left page to point to right page
			if (parent.getPreviousKey() != null) {
				parent.hasPrevious();
				parent.setAfterPageID(right.getPageID(), logged, -1);
				parent.moveNext();
			} else {
				parent.setBeforePageID(right.getPageID(), logged, -1);
			}

			parent = deleteFromPage(tx, rootPageID, parent, parent.getKey(),
					parent.getValue(), true, logged, -1);

			// "Reset" page properties to get the required undo information in
			// the log
			left.setBeforePageID(null, logged, -1);
			left.format(left.getUnitID(), PageType.INDEX_TREE, rootPageID, left
					.getKeyType(), left.getValueType(), left.isUnique(), left
					.isCompressed(), logged, -1);
			left.deletePage();

			if (log.isTraceEnabled()) {
				log.trace("Finished merge of left page to right sibling.");
				log.trace(parent.dump("Parent page"));
				log.trace(right.dump("Right page"));
			}

			tx.getStatistics().increment(TxStats.BTREE_BRANCH_DEALLOCATE_COUNT);

			// merge complete
			parent.cleanup();
			return right;
		} catch (IndexOperationException e) {
			left.cleanup();
			right.cleanup();
			parent.cleanup();
			throw new IndexAccessException(e, "Could not log merge operations.");
		}
	}

	private PageContext collapseRoot(Tx tx, PageContext root, PageContext left,
			PageContext right, byte[] deletedKey, byte[] deletedValue,
			boolean targetIsLeftPage, boolean logged)
			throws IndexAccessException {
		try {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Starting collapse of root page %s.",
						root));
				log.trace(root.dump("Root page"));
				log.trace(left.dump("Left page"));
				log.trace(right.dump("Right page"));
			}

			root.hasPrevious();

			if (left.getPageType() == PageType.INDEX_TREE) {
				// save before page of left page
				root.setBeforePageID(left.getBeforePageID(), logged, -1);

				// change after page of current record to before page of right
				// page
				root.setAfterPageID(right.getBeforePageID(), logged, -1);

				root.moveFirst();

				// copy left page to root page
				if (left.moveFirst()) {
					do {
						root.insert(left.getKey(), left.getValue(), true,
								logged, -1);
						root.moveNext();
					} while (left.hasNext());
				}

				// copy right page to root page
				if (right.moveFirst()) {
					// skip old separator
					root.moveNext();
					do {
						root.insert(right.getKey(), right.getValue(), true,
								logged, -1);
						root.moveNext();
					} while (right.hasNext());
				}

				// "Reset" page properties to get the required undo information
				// in the log
				right.setBeforePageID(null, logged, -1);
				left.setBeforePageID(null, logged, -1);
				left.format(left.getUnitID(), PageType.INDEX_TREE, left
						.getRootPageID(), left.getKeyType(), left
						.getValueType(), left.isUnique(), left.isCompressed(),
						logged, -1);
				right.format(right.getUnitID(), PageType.INDEX_TREE, left
						.getRootPageID(), right.getKeyType(), right
						.getValueType(), right.isUnique(), left.isCompressed(),
						logged, -1);
			} else {
				// delete separator from root page
				root.delete(true, logged, -1);

				// switch root page type and update pointers
				root.setBeforePageID(null, logged, -1);
				root.format(left.getUnitID(), PageType.INDEX_LEAF, root
						.getPageID(), left.getKeyType(), left.getValueType(),
						left.isUnique(), left.isCompressed(), logged, -1);
				root.moveFirst();
				root.setPreviousPageID(null, logged, -1);
				root.setNextPageID(null, logged, -1);

				// copy left page to root page
				if (left.moveFirst()) {
					do {
						root.insert(left.getKey(), left.getValue(), true,
								logged, -1);
						root.moveNext();
					} while (left.hasNext());
				}

				// copy right page to root page
				if (right.moveFirst()) {
					do {
						root.insert(right.getKey(), right.getValue(), true,
								logged, -1);
						root.moveNext();
					} while (right.hasNext());
				}

				// "Reset" page properties to get the required undo information
				// in the log
				right.setNextPageID(null, logged, -1);
				left.setNextPageID(null, logged, -1);
				right.setPreviousPageID(null, logged, -1);
				left.setPreviousPageID(null, logged, -1);
				left.format(left.getUnitID(), PageType.INDEX_LEAF, left
						.getRootPageID(), left.getKeyType(), left
						.getValueType(), left.isUnique(), left.isCompressed(),
						logged, -1);
				right.format(right.getUnitID(), PageType.INDEX_LEAF, left
						.getRootPageID(), right.getKeyType(), right
						.getValueType(), right.isUnique(),
						right.isCompressed(), logged, -1);
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("Finished collapse of root page %s.",
						root));
				log.trace(root.dump("Root page"));
			}

			left.setSafe(true);
			right.setSafe(true);

			// delete pages
			left.deletePage();
			right.deletePage();

			if (root.search(SearchMode.GREATER_OR_EQUAL, deletedKey,
					deletedValue) > 0) {
				root.moveNext();
			}

			return root;
		} catch (IndexOperationException e) {
			right.cleanup();
			left.cleanup();
			right.cleanup();
			throw new IndexAccessException(e,
					"Could not log root collapse operations.");
		}
	}

	public PageContext readFromLeaf(Tx tx, PageID rootPageID, PageContext leaf,
			byte[] key, byte[] value) throws IndexAccessException {
		while (true) {
			try {
				if (lockService != null) {
					readLockEntry(tx, rootPageID, leaf, key, value);
				}

				return leaf;
			} catch (IndexOperationException e) {
				leaf.cleanup();
				throw new IndexAccessException(e);
			} catch (RewindException e) {
				// continue
			}
		}
	}
}