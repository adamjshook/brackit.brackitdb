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
package org.brackit.server.store.index.aries.log;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusTree;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.PageType;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;

/**
 * @author Sebastian Baechle
 * 
 */
public class UpdateLogOperation extends BPlusIndexLogOperation {
	private static final Logger log = Logger
			.getLogger(UpdateLogOperation.class);

	public enum ActionType {
		INSERT, DELETE, UPDATE
	}

	private static final int SIZE = BASE_SIZE + (2 * SizeConstants.INT_SIZE);

	protected byte[] key;

	protected byte[] value;

	protected byte[] oldValue;

	public UpdateLogOperation(byte type, PageID pageID, PageID rootPageID,
			byte[] key, byte[] oldValue, byte[] value) {
		super(type, pageID, rootPageID);
		this.key = key;
		this.oldValue = oldValue;
		this.value = value;
	}

	@Override
	public int getSize() {
		return SIZE
				+ key.length
				+ value.length
				+ ((oldValue != null) ? SizeConstants.INT_SIZE
						+ oldValue.length : 0);
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		super.toBytes(bb);
		bb.putInt(key.length);
		bb.put(key);
		bb.putInt(value.length);
		bb.put(value);

		if (oldValue != null) {
			bb.putInt(oldValue.length);
			bb.put(oldValue);
		}
	}

	@Override
	public void redo(Tx transaction, long LSN) throws LogException {
		switch (type) {
		case USER_INSERT:
			try {
				redoPageContentUpdate(transaction, ActionType.INSERT, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of insert into index %s failed", rootPageID);
			}
		case USER_DELETE:
			try {
				redoPageContentUpdate(transaction, ActionType.DELETE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of delete from index %s failed", rootPageID);
			}
		case USER_UPDATE:
			try {
				redoPageContentUpdate(transaction, ActionType.UPDATE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of delete from index %s failed", rootPageID);
			}
		case SMO_INSERT:
			try {
				redoPageContentUpdate(transaction, ActionType.INSERT, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of insert into into page %s failed", pageID);
			}
		case SMO_DELETE:
			try {
				redoPageContentUpdate(transaction, ActionType.DELETE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Redo of delete from page %s failed",
						pageID);
			}
		case SMO_UPDATE:
			try {
				redoPageContentUpdate(transaction, ActionType.UPDATE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Redo of update in page %s failed",
						pageID);
			}
		default:
			throw new LogException("Unsupported update operation type: %s.",
					type);
		}
	}

	@Override
	public void undo(Tx transaction, long LSN, long undoNextLSN)
			throws LogException {
		switch (type) {
		case USER_INSERT:
			try {
				undoInsert(transaction, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of insert into index %s failed", rootPageID);
			}
		case USER_DELETE:
			try {
				undoDelete(transaction, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of delete from index %s failed", rootPageID);
			}
		case USER_UPDATE:
			try {
				undoUpdate(transaction, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of delete from index %s failed", rootPageID);
			}
		case SMO_INSERT:
			try {
				undoPageContentUpdate(transaction, ActionType.INSERT, LSN,
						undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of insert into into page %s failed", pageID);
			}
		case SMO_DELETE:
			try {
				undoPageContentUpdate(transaction, ActionType.DELETE, LSN,
						undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Undo of delete from page %s failed",
						pageID);
			}
		case SMO_UPDATE:
			try {
				undoPageContentUpdate(transaction, ActionType.UPDATE, LSN,
						undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Undo of update in page %s failed",
						pageID);
			}
		default:
			throw new LogException("Unsupported update operation type: %s.",
					type);
		}
	}

	public void redoPageContentUpdate(Tx tx, ActionType actionType, long LSN)
			throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin redo page content update");
		}
		BPlusTree tree = new BPlusTree(tx.getBufferManager());
		try {
			page = tree.getPage(tx, pageID, true, false);
		} catch (IndexOperationException e) {
			if (log.isDebugEnabled()) {
				log
						.trace(String
								.format(
										"Page %s has been deleted and flushed to disk. No redo necessary.",
										pageID));
			}

			return;
		}

		try {
			if (page.getLSN() < LSN) {
				if (!page.getRootPageID().equals(rootPageID)) {
					page.cleanup();
					throw new IndexAccessException(
							"Redo content update of page %s failed because it does not belong to index %s.",
							pageID, rootPageID);
				}

				int searchResult = page.search(SearchMode.GREATER_OR_EQUAL,
						key, oldValue);

				switch (actionType) {
				case INSERT:
					if (searchResult > 0) {
						page.moveNext();
					}
					page.insert(key, value, true, false, -1);
					break;
				case DELETE:
					page.delete(true, false, -1);
					break;
				case UPDATE:
					page.setValue(value, true, false, -1);
					break;
				}
			} else {
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"LSN %s of page %s is >= RedoLSN %s. No redo necessary.",
											page.getLSN(), pageID, LSN));
				}
			}

			page.cleanup();
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Redo content update of page %s failed.", pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End redo content update");
		}
	}

	public void undoPageContentUpdate(Tx tx, ActionType actionType, long LSN,
			long undoNextLSN) throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo page content update");
		}

		BPlusTree tree = new BPlusTree(tx.getBufferManager());
		try {
			while (true) {
				page = tree.getPage(tx, pageID, true, false);

				if (page.isSafe()) {
					break;
				}

				page.cleanup();
				tree.getTreeLatch().latchSI(rootPageID);
			}

			if ((page.getLSN() != LSN)
					|| ((page.getPageType() != PageType.INDEX_LEAF) && (page
							.getPageType() != PageType.INDEX_TREE))
					|| (!page.getRootPageID().equals(rootPageID))) {
				page.cleanup();
				throw new IndexAccessException(
						"Undo content update of page %s failed because it does not belong to index %s.",
						pageID, rootPageID);
			}

			int searchResult = page.search(SearchMode.GREATER_OR_EQUAL, key,
					value);

			switch (actionType) {
			case INSERT:
				page.delete(true, true, undoNextLSN);
				break;
			case DELETE:
				if (searchResult > 0) {
					page.moveNext();
				}
				page.insert(key, value, true, true, undoNextLSN);
				break;
			case UPDATE:
				page.setValue(oldValue, true, true, undoNextLSN);
				break;
			}

			page.cleanup();
		} catch (IndexOperationException e) {
			throw new IndexAccessException(
					e,
					"Undo content update of page %s failed because it has been deleted. This must not happen in the ARIES protocol.",
					pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End undo content update");
		}
	}

	public void undoInsert(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo insert");
		}
		BPlusTree tree = new BPlusTree(tx.getBufferManager());

		try {
			while (true) {
				page = tree.getPage(tx, pageID, true, false);

				if (page.isSafe()) {
					break;
				}

				page.cleanup();
				tree.getTreeLatch().latchSI(rootPageID);
			}

			if (page.getLSN() == LSN) {
				page.search(SearchMode.GREATER_OR_EQUAL, key, value);
			} else if ((page.getPageType() != PageType.INDEX_LEAF)
					|| (!page.getRootPageID().equals(rootPageID))) {
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Page %s can not be used for direct undo because it was modified after the update and is no longer a leaf of index %s.",
											pageID, rootPageID));
				}

				page.cleanup();

				// descend down the index tree
				page = tree.descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, false);
			} else if (page.search(SearchMode.GREATER_OR_EQUAL, key, value) != 0) {
				page.cleanup();
				page = tree.descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, false);
			}
		} catch (IndexOperationException e) {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Page %s could not be fixed for undo.",
						pageID));
			}

			// descend down the index tree
			page = tree.descendToPosition(tx, rootPageID,
					SearchMode.GREATER_OR_EQUAL, key, value, true, false);
		}

		page = tree.deleteFromLeaf(tx, rootPageID, page, key, value,
				undoNextLSN, true);
		page.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End undo insert");
		}
	}

	public void undoUpdate(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo update");
		}
		BPlusTree tree = new BPlusTree(tx.getBufferManager());

		try {
			while (true) {
				page = tree.getPage(tx, pageID, true, false);

				if (page.isSafe()) {
					break;
				}

				page.cleanup();
				tree.getTreeLatch().latchSI(rootPageID);
			}

			if (page.getLSN() == LSN) {
				page.search(SearchMode.GREATER_OR_EQUAL, key, value);
			} else if ((page.getPageType() != PageType.INDEX_LEAF)
					|| (!page.getRootPageID().equals(rootPageID))) {
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Page %s can not be used for direct "
													+ "undo because it was modified after the update "
													+ "and is no longer a leaf of index %s.",
											pageID, rootPageID));
				}

				page.cleanup();
				page = tree.descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, false);
			} else if (page.search(SearchMode.GREATER_OR_EQUAL, key, value) != 0) {
				page.cleanup();
				page = tree.descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, false);
			}
		} catch (IndexOperationException e) {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Page %s could not be fixed for undo.",
						pageID));
			}

			page = tree.descendToPosition(tx, rootPageID,
					SearchMode.GREATER_OR_EQUAL, key, value, true, false);
		}

		page = tree.updateInLeaf(tx, rootPageID, page, key, oldValue, value,
				undoNextLSN);
		page.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End undo delete");
		}
	}

	public void undoDelete(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo delete");
		}
		BPlusTree tree = new BPlusTree(tx.getBufferManager());

		try {
			while (true) {
				page = tree.getPage(tx, pageID, true, false);

				if (page.isSafe()) {
					break;
				}

				page.cleanup();
				tree.getTreeLatch().latchSI(rootPageID);
			}

			if (page.getLSN() == LSN) {
				page.search(SearchMode.GREATER_OR_EQUAL, key, value);
			} else if ((page.getPageType() != PageType.INDEX_LEAF)
					|| (!page.getRootPageID().equals(rootPageID))) {
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Page %s can not be used for direct "
													+ "undo because it was modified after the update "
													+ "and is no longer a leaf of index %s.",
											pageID, rootPageID));
				}

				page.cleanup();
				page = tree.descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, true);
			} else if (page.search(SearchMode.GREATER_OR_EQUAL, key, value) != 0) {
				page.cleanup();
				page = tree.descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, true);
			}

			if ((page.getPreviousKey() == null) || (page.getNextKey() == null)) {
				if (log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Page %s can not be used for direct "
													+ "undo because the insert position is ambigous.",
											pageID));
				}

				page.cleanup();
				page = tree.descendToPosition(tx, rootPageID,
						SearchMode.GREATER_OR_EQUAL, key, value, true, true);
			}
		} catch (IndexOperationException e) {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Page %s could not be fixed for undo.",
						pageID));
			}

			page = tree.descendToPosition(tx, rootPageID,
					SearchMode.GREATER_OR_EQUAL, key, value, true, true);
		}

		page = tree.insertIntoLeaf(tx, rootPageID, page, key, value, false,
				true, undoNextLSN);
		page.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End undo delete");
		}
	}

	@Override
	public String toString() {
		String typeString;

		switch (type) {
		case USER_INSERT:
			typeString = "Insert into";
			break;
		case USER_DELETE:
			typeString = "Delete from";
			break;
		case USER_UPDATE:
			typeString = "Update in";
			break;
		case SMO_INSERT:
			typeString = "SMOInsert into";
			break;
		case SMO_DELETE:
			typeString = "SMODelete from";
			break;
		case SMO_UPDATE:
			typeString = "SMOUpdate in";
			break;
		default:
			typeString = "Unkown in";
		}

		return String.format("%s(%s %s of index %s): %s", getClass()
				.getSimpleName(), typeString, pageID, rootPageID,
				(key != null) ? Arrays.toString(key) : null);
	}
}