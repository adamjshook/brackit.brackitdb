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
package org.brackit.server.store.index.bracket.log;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.page.BPContext;
import org.brackit.server.store.index.bracket.page.Branch;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;

/**
 * @author Martin Hiller
 * 
 */
public class BranchUpdateLogOperation extends BracketIndexLogOperation {
	private static final Logger log = Logger
			.getLogger(BranchUpdateLogOperation.class);

	public enum ActionType {
		INSERT(BracketIndexLogOperation.BRANCH_INSERT),
		DELETE(BracketIndexLogOperation.BRANCH_DELETE),
		UPDATE(BracketIndexLogOperation.BRANCH_UPDATE);
		
		private byte type;
		private ActionType(byte type) {
			this.type = type;
		}
		
		public byte getType() {
			return type;
		}
	}

	private static final int SIZE = BASE_SIZE + (2 * SizeConstants.INT_SIZE);

	protected byte[] key;

	protected byte[] value;

	protected byte[] oldValue;

	public BranchUpdateLogOperation(ActionType actionType, PageID pageID, PageID rootPageID,
			byte[] key, byte[] oldValue, byte[] value) {
		super(actionType.getType(), pageID, rootPageID);
		this.key = key;
		this.oldValue = oldValue;
		this.value = value;
	}
	
	public BranchUpdateLogOperation(byte type, PageID pageID, PageID rootPageID,
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
	public void redo(Tx tx, long LSN) throws LogException {
		switch (type) {
		case BRANCH_INSERT:
			try {
				redoPageContentUpdate(tx, ActionType.INSERT, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of insert into into page %s failed", pageID);
			}
		case BRANCH_DELETE:
			try {
				redoPageContentUpdate(tx, ActionType.DELETE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Redo of delete from page %s failed",
						pageID);
			}
		case BRANCH_UPDATE:
			try {
				redoPageContentUpdate(tx, ActionType.UPDATE, LSN);
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
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		switch (type) {
		case BRANCH_INSERT:
			try {
				undoPageContentUpdate(tx, ActionType.INSERT, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of insert into into page %s failed", pageID);
			}
		case BRANCH_DELETE:
			try {
				undoPageContentUpdate(tx, ActionType.DELETE, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Undo of delete from page %s failed",
						pageID);
			}
		case BRANCH_UPDATE:
			try {
				undoPageContentUpdate(tx, ActionType.UPDATE, LSN, undoNextLSN);
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
		BPContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin redo page content update");
		}

		BracketTree tree = new BracketTree(tx.getBufferManager());
		try {
			page = tree.getPage(tx, pageID, true, false);
		} catch (IndexOperationException e) {
			if (log.isDebugEnabled()) {
				log.trace(String.format("Page %s has been deleted"
						+ " and flushed to disk. " + "No redo necessary.",
						pageID));
			}

			return;
		}

		try {
			if (page.getLSN() < LSN) {
				if (!page.getRootPageID().equals(rootPageID)) {
					page.cleanup();
					throw new IndexAccessException(
							"Redo content update of page %s"
									+ " failed because it does not belong "
									+ "to index %s.", pageID, rootPageID);
				}
				
				if (page.isLeaf()) {
					page.cleanup();
					throw new IndexAccessException("LogOperation type not valid for leaf pages.");
				}
				
				Branch branch = (Branch) page;

				int searchResult = branch.search(SearchMode.GREATER_OR_EQUAL,
						key, oldValue);

				switch (actionType) {
				case INSERT:
					if (searchResult > 0) {
						page.moveNext();
					}
					branch.insert(key, value, false, -1);
					break;
				case DELETE:
					branch.delete(false, -1);
					break;
				case UPDATE:
					branch.setValue(value, false, -1);
					break;
				}
			} else {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"LSN %s of page %s is >= RedoLSN %s."
									+ " No redo necessary.", page.getLSN(),
							pageID, LSN));
				}
			}

			page.cleanup();
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Redo content update of page %s failed.", pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End redo content update");
		}
	}

	public void undoPageContentUpdate(Tx tx, ActionType actionType, long LSN,
			long undoNextLSN) throws IndexAccessException {
		BPContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo page content update");
		}
		BracketTree tree = new BracketTree(tx.getBufferManager());

		try {
			page = tree.getPage(tx, pageID, true, false);

			if (page.getEntryCount() == 0) {
				// TODO
				throw new RuntimeException("Not supported!");
//				page.cleanup();
//				page = tree.descendToPosition(tx, rootPageID,
//						SearchMode.GREATER_OR_EQUAL, key, value, true,
//						actionType == ActionType.DELETE);
			} else if ((page.getLSN() != LSN)
					|| ((page.getRootPageID() != null)
							&& (!page.getRootPageID().equals(rootPageID)))) {
				page.cleanup();
				throw new IndexAccessException(
						"Undo content update of page %s failed"
								+ " because it does not belong to index %s.",
						pageID, rootPageID);
			}
			
			if (page.isLeaf()) {
				page.cleanup();
				throw new IndexAccessException("LogOperation type not valid for leaf pages.");
			}
			
			Branch branch = (Branch) page;

			int searchResult = branch.search(SearchMode.GREATER_OR_EQUAL, key,
					value);

			switch (actionType) {
			case INSERT:
				branch.delete(true, undoNextLSN);
				break;
			case DELETE:
				if (searchResult > 0) {
					page.moveNext();
				}
				branch.insert(key, value, true, undoNextLSN);
				break;
			case UPDATE:
				branch.setValue(oldValue, true, undoNextLSN);
				break;
			}

			page.cleanup();
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Undo content update of page %s failed"
							+ " because it has been deleted. "
							+ "This must not happen in the ARIES protocol.",
					pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End undo content update");
		}
	}

	@Override
	public String toString() {
		String typeString;

		switch (type) {
		case BRANCH_INSERT:
			typeString = "SMOInsert into";
			break;
		case BRANCH_DELETE:
			typeString = "SMODelete from";
			break;
		case BRANCH_UPDATE:
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