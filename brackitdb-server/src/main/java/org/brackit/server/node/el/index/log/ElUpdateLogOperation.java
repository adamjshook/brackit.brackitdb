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
package org.brackit.server.node.el.index.log;

import java.nio.ByteBuffer;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.node.el.index.ElBPlusTree;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.PageType;
import org.brackit.server.store.index.aries.log.UpdateLogOperation;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElUpdateLogOperation extends UpdateLogOperation {
	private static final Logger log = Logger
			.getLogger(ElUpdateLogOperation.class);

	public static final byte USER_INSERT_SPECIAL = 23;

	public static final byte USER_DELETE_SPECIAL = 24;

	private int level;

	public ElUpdateLogOperation(byte type, PageID pageID, PageID rootPageID,
			byte[] key, byte[] oldValue, byte[] value, int level) {
		super(type, pageID, rootPageID, key, oldValue, value);
		this.level = level;
	}

	@Override
	public int getSize() {
		return super.getSize() + SizeConstants.BYTE_SIZE;
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		super.toBytes(bb);
		bb.put((byte) level);
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		switch (type) {
		case USER_INSERT_SPECIAL:
			try {
				redoPageContentUpdate(tx, ActionType.INSERT, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of insert into index %s failed", rootPageID);
			}
		case USER_DELETE_SPECIAL:
			try {
				redoPageContentUpdate(tx, ActionType.DELETE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of delete from index %s failed", rootPageID);
			}
		default:
			throw new LogException("Unsupported update operation type: %s.",
					type);
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		switch (type) {
		case USER_INSERT_SPECIAL:
			try {
				undoInsertSpecial(tx, pageID, rootPageID, key, value, level,
						LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of insert into index %s failed", rootPageID);
			}
		case USER_DELETE_SPECIAL:
			try {
				undoDeleteSpecial(tx, pageID, rootPageID, key, value, level,
						LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of delete from index %s failed", rootPageID);
			}
		default:
			throw new LogException("Unsupported update operation type: %s.",
					type);
		}
	}

	public void undoInsertSpecial(Tx tx, PageID pageID, PageID rootPageID,
			byte[] key, byte[] value, int level, long LSN, long undoNextLSN)
			throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo insert special");
		}

		ElBPlusTree tree = new ElBPlusTree(tx.getBufferManager(),
				new ElRecordAccess());

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
											"Page %s can not be used for direct"
													+ " undo because it was modified after "
													+ "the update and is no longer a leaf of index %s.",
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

		(tree).deletePrefixAwareFromLeaf(tx, rootPageID, page,
				key, value, level, true, undoNextLSN);

		page.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End undo insert special");
		}
	}

	public void undoDeleteSpecial(Tx tx, PageID pageID, PageID rootPageID,
			byte[] key, byte[] value, int level, long LSN, long undoNextLSN)
			throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo delete special");
		}
		ElBPlusTree tree = new ElBPlusTree(tx.getBufferManager(),
				new ElRecordAccess());

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
													+ "undo because it was modified after "
													+ "the update and is no longer a leaf of index %s.",
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

		(tree).insertPrefixAwareIntoLeaf(tx, rootPageID, page,
				key, value, level, true, true, undoNextLSN);

		page.cleanup();
		if (log.isTraceEnabled()) {
			log.trace("End undo delete special");
		}
	}
}
