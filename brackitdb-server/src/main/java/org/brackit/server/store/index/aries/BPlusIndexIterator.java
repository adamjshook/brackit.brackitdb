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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.external.IndexStatistics;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxStats;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BPlusIndexIterator implements IndexIterator {
	private static Logger log = Logger.getLogger(BPlusIndexIterator.class
			.getName());

	protected final Tx transaction;

	protected final BPlusTree tree;

	protected final PageID rootPageID;

	protected final OpenMode openMode;

	protected final Field keyType;

	protected final Field valueType;

	protected PageContext page;

	protected byte[] key;

	protected byte[] value;

	private final int pageSize;

	private long rememberedLSN;

	private PageID rememberedPageID;

	private boolean on;

	public BPlusIndexIterator(Tx transaction, BPlusTree tree,
			PageID rootPageID, PageContext page, OpenMode openMode)
			throws IndexAccessException {
		super();
		try {
			this.transaction = transaction;
			this.tree = tree;
			this.rootPageID = rootPageID;
			this.page = page;
			this.openMode = openMode;
			this.keyType = page.getKeyType();
			this.valueType = page.getValueType();
			this.key = page.getKey();
			this.value = page.getValue();
			this.pageSize = page.getSize();
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e, "Error initializing iterator");
		}
		off();
	}

	public Field getKeyType() throws IndexAccessException {
		return keyType;
	}

	public Field getValueType() throws IndexAccessException {
		return valueType;
	}

	public void close() {
		if (page != null) {
			// if ((!on) && (openMode != OpenMode.LOAD))
			// {
			// page.latchShared();
			// }

			page.cleanup();
			page = null;
		}
	}

	public void insert(byte[] insertKey, byte[] insertValue)
			throws IndexAccessException {
		insertInternal(insertKey, insertValue, -1);
	}

	public void insertPersistent(byte[] insertKey, byte[] insertValue)
			throws IndexAccessException {
		insertInternal(insertKey, insertValue, transaction.checkPrevLSN());
	}

	protected void insertInternal(byte[] insertKey, byte[] insertValue,
			long undoNextLSN) throws IndexAccessException {
		on();
		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		try {
			if (openMode != OpenMode.LOAD) {
				checkInsertPosition(insertKey, insertValue);
			}

			page = tree.insertIntoLeaf(transaction, rootPageID, page,
					insertKey, insertValue, openMode.compact(), openMode
							.doLog(), undoNextLSN);
			key = insertKey;
			value = insertValue;
		} catch (IndexAccessException e) {
			page = null;
			throw e;
		}
		off();
	}

	private void checkInsertPosition(byte[] insertKey, byte[] insertValue)
			throws IndexAccessException {
		try {
			byte[] previousKey = page.getPreviousKey();
			byte[] previousValue = page.getPreviousValue();

			if ((key != null)
					&& ((keyType.compare(insertKey, key) > 0) || ((keyType
							.compare(insertKey, key) == 0) && (page.isUnique() || (valueType
							.compare(insertValue, value) >= 0))))) {
				close();
				throw new IndexAccessException(
						"Insert of (%s, %s) at current position violates index integrity because it is greater than the current record (%s, %s) or a duplicate.",
						(insertKey != null) ? keyType.toString(insertKey)
								: null, (insertValue != null) ? valueType
								.toString(insertValue) : null,
						(key != null) ? keyType.toString(key) : null,
						(value != null) ? valueType.toString(value) : null);
			}

			if ((previousKey != null)
					&& ((keyType.compare(previousKey, insertKey) > 0) || ((keyType
							.compare(previousKey, insertKey) == 0) && (page
							.isUnique() || (valueType.compare(previousValue,
							insertValue) >= 0))))) {
				close();
				throw new IndexAccessException(
						"Insert of (%s, %s) at current position violates index integrity because it is smaller than the previous record (%s, %s) or a duplicate.",
						(insertKey != null) ? keyType.toString(insertKey)
								: null, (insertValue != null) ? valueType
								.toString(insertValue) : null,
						(previousKey != null) ? keyType.toString(previousKey)
								: null, (previousValue != null) ? valueType
								.toString(previousValue) : null);
			}

			if ((previousKey == null)
					|| ((key == null) && (page.getNextPageID() != null))) {
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Restart with a tree traversal to insert (%s, %s) because insert position is ambigious.",
											page,
											(insertKey != null) ? keyType
													.toString(insertKey) : null,
											(insertValue != null) ? valueType
													.toString(insertValue)
													: null));
				}

				page.cleanup();
				page = tree.descendToPosition(transaction, rootPageID,
						SearchMode.GREATER_OR_EQUAL, insertKey, insertValue,
						openMode.forUpdate(), true);
			}
		} catch (IndexOperationException e) {
			page = null;
			throw new IndexAccessException(e,
					"Error validating insert position");
		}
	}

	public void update(byte[] newValue) throws IndexAccessException {
		updateInternal(newValue, -1);
	}

	public void updatePersistent(byte[] newValue) throws IndexAccessException {
		updateInternal(newValue, transaction.checkPrevLSN());
	}

	protected void updateInternal(byte[] newValue, long undoNextLSN)
			throws IndexAccessException {
		on();
		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		try {
			page = tree.updateInLeaf(transaction, rootPageID, page, key,
					newValue, value, undoNextLSN);
			value = newValue;
		} catch (IndexAccessException e) {
			page = null;
			throw e;
		}
		off();
	}

	public void delete() throws IndexAccessException {
		deleteInternal(-1);
	}

	public void deletePersistent() throws IndexAccessException {
		deleteInternal(transaction.checkPrevLSN());
	}

	protected void deleteInternal(long undoNextLSN) throws IndexAccessException {
		on();
		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		try {
			page = tree.deleteFromLeaf(transaction, rootPageID, page, key,
					value, undoNextLSN, openMode.doLog());
			key = page.getKey();
			value = page.getValue();
		} catch (IndexAccessException e) {
			page = null;
			throw e;
		} catch (IndexOperationException e) {
			page = null;
			throw new IndexAccessException(e);
		}
		off();
	}

	public byte[] getKey() throws IndexAccessException {
		return key;
	}

	public byte[] getValue() throws IndexAccessException {
		return value;
	}

	public boolean next() throws IndexAccessException {
		try {
			on();
			page = tree.moveNext(transaction, rootPageID, page, openMode);
			boolean hasNext = !page.isAfterLast();

			key = page.getKey();
			value = page.getValue();

			off();
			return hasNext;
		} catch (IndexAccessException e) {
			page = null;
			throw e;
		} catch (IndexOperationException e) {
			page = null;
			throw new IndexAccessException(e, "Error moving to next record.");
		}

	}

	public boolean previous() throws IndexAccessException {
		try {
			on();
			// TODO: Check for previous() is a bit clumsy compared to next()
			int currentPosition = page.getPosition();
			PageContext previous = tree.movePrevious(transaction, rootPageID,
					page, openMode);
			boolean hasPrevious = ((currentPosition > 1) || (previous != page));

			page = previous;
			key = page.getKey();
			value = page.getValue();

			off();
			return hasPrevious;
		} catch (IndexOperationException e) {
			page = null;
			throw new IndexAccessException(e,
					"Error moving to previous record.");
		} catch (IndexAccessException e) {
			page = null;
			throw e;
		}
	}

	public PageID getCurrentPageID() throws IndexAccessException {
		return rememberedPageID;
	}

	public long getCurrentLSN() throws IndexAccessException {
		return rememberedLSN;
	}

	@Override
	public int getMaxInlineValueSize(int maxKeySize)
			throws IndexAccessException {
		on();
		int maxInlineValueSize = page.calcMaxInlineValueSize(maxKeySize);
		off();

		return maxInlineValueSize;
	}

	@Override
	public int getMaxKeySize() throws IndexAccessException {
		on();
		int maxKeySize = page.calcMaxKeySize();
		off();

		return maxKeySize;
	}

	protected void on() throws IndexAccessException {
		if (true)
			return;
		if (openMode == OpenMode.LOAD) {
			on = true;
			return;
		}

		try {
			while (true) {
				if (!openMode.forUpdate()) {
					page.latchS();
				} else {
					page.latchX();
				}

				if (page.isSafe()) {
					break;
				}

				page.unlatch();
				tree.getTreeLatch().latchSI(rootPageID);
			}

			if (page.getLSN() != rememberedLSN) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Repositioning cursor for index %s at (%s, %s)",
							rootPageID, (key != null) ? keyType.toString(key)
									: null, (value != null) ? valueType
									.toString(value) : null));
				}

				// Reposition the iterator with a new traversal
				page.cleanup();
				PageContext newPage = null;
				page = null;

				if (key != null) {
					// descend down the index tree to the last seen record again
					newPage = tree.descendToPosition(transaction, rootPageID,
							SearchMode.GREATER_OR_EQUAL, key, value, openMode
									.forUpdate(), false);
				} else {
					// descend down the index tree to the end again
					newPage = tree.descendToPosition(transaction, rootPageID,
							SearchMode.LAST, null, null, openMode.forUpdate(),
							false);
					newPage.moveNext();
				}

				page = newPage;
				rememberedLSN = page.getLSN();
				rememberedPageID = page.getPageID();
				key = page.getKey();
				value = page.getValue();
			}

			on = true;
		} catch (IndexOperationException e) {
			e = null;
			throw new IndexAccessException(e, "Error switching iterator on.");
		}
	}

	protected void off() throws IndexAccessException {
		if (true)
			return;
		if (openMode == OpenMode.LOAD) {
			on = false;
			return;
		}

		rememberedLSN = page.getLSN();
		rememberedPageID = page.getPageID();
		page.unlatch();
		on = false;
	}

	@Override
	public IndexStatistics getStatistics() {
		IndexStatistics is = new IndexStatistics();
		TxStats statistics = transaction.getStatistics();
		is.setIndexHeight(statistics.get(TxStats.BTREE_ROOT_SPLITS) + 1);
		is
				.setIndexLeaveCount(statistics
						.get(TxStats.BTREE_LEAF_ALLOCATIONS) + 1);
		is.setIndexPointers(statistics.get(TxStats.BTREE_LEAF_ALLOCATIONS));
		is.setIndexTuples(statistics.get(TxStats.BTREE_INSERTS));
		int pageCount = 1 + statistics.get(TxStats.BTREE_LEAF_ALLOCATIONS)
				- statistics.get(TxStats.BTREE_LEAF_DEALLOCATIONS)
				+ statistics.get(TxStats.BTREE_BRANCH_ALLOCATE_COUNT)
				- statistics.get(TxStats.BTREE_BRANCH_DEALLOCATE_COUNT);
		is.setPageCount(pageCount);
		is.setIdxSize(pageCount * pageSize);
		return is;
	}

	@Override
	public void triggerStatistics() {
		transaction.getStatistics().reset();
	}
}