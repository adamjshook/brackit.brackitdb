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
package org.brackit.server.store.index.bracket.log;

import java.nio.ByteBuffer;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.page.BPContext;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.page.bracket.BracketNodeSequence;
import org.brackit.server.store.page.bracket.DeleteSequenceInfo;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;

/**
 * @author Martin Hiller
 * 
 */
public class NodeSequenceLogOperation extends BracketIndexLogOperation {

	private static final Logger log = Logger
			.getLogger(NodeSequenceLogOperation.class);

	public enum ActionType {
		INSERT(BracketIndexLogOperation.LEAF_INSERT), DELETE(
				BracketIndexLogOperation.LEAF_DELETE), SMO_INSERT(
				BracketIndexLogOperation.LEAF_SMO_INSERT), SMO_DELETE(
				BracketIndexLogOperation.LEAF_SMO_DELETE);

		private byte type;

		private ActionType(byte type) {
			this.type = type;
		}

		public byte getType() {
			return type;
		}
	}

	private BracketNodeSequence nodes;

	private static final int SIZE = BASE_SIZE + SizeConstants.SHORT_SIZE;

	public NodeSequenceLogOperation(ActionType actionType, PageID pageID,
			PageID rootPageID, BracketNodeSequence nodes) {
		super(actionType.getType(), pageID, rootPageID);
		this.nodes = nodes;
	}

	public NodeSequenceLogOperation(byte type, PageID pageID,
			PageID rootPageID, BracketNodeSequence nodes) {
		super(type, pageID, rootPageID);
		this.nodes = nodes;
	}

	@Override
	public int getSize() {
		return SIZE + nodes.getLength();
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		super.toBytes(bb);
		bb.putShort((short) nodes.getLength());
		nodes.write(bb);
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		switch (type) {
		case LEAF_INSERT:
			try {
				redoPageContentUpdate(tx, ActionType.INSERT, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of insert into index %s failed", rootPageID);
			}
		case LEAF_DELETE:
			try {
				redoPageContentUpdate(tx, ActionType.DELETE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of delete from index %s failed", rootPageID);
			}
		case LEAF_SMO_INSERT:
			try {
				redoPageContentUpdate(tx, ActionType.SMO_INSERT, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Redo of insert into into page %s failed", pageID);
			}
		case LEAF_SMO_DELETE:
			try {
				redoPageContentUpdate(tx, ActionType.SMO_DELETE, LSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Redo of delete from page %s failed",
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
		case LEAF_INSERT:
			try {
				undoInsert(tx, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of insert into index %s failed", rootPageID);
			}
		case LEAF_DELETE:
			try {
				undoDelete(tx, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of delete from index %s failed", rootPageID);
			}
		case LEAF_SMO_INSERT:
			try {
				undoPageContentUpdate(tx, ActionType.SMO_INSERT, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e,
						"Undo of insert into into page %s failed", pageID);
			}
		case LEAF_SMO_DELETE:
			try {
				undoPageContentUpdate(tx, ActionType.SMO_DELETE, LSN, undoNextLSN);
				return;
			} catch (IndexAccessException e) {
				throw new LogException(e, "Undo of delete from page %s failed",
						pageID);
			}
		default:
			throw new LogException("Unsupported update operation type: %s.",
					type);
		}
	}

	public void undoInsert(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {

		if (log.isTraceEnabled()) {
			log.trace("Begin undo insert");
		}

		BracketTree tree = new BracketTree(tx.getBufferManager());
		tree.deleteSequence(tx, rootPageID, nodes.getLowKey(),
				nodes.getHighKey(), pageID, true, undoNextLSN);

		if (log.isTraceEnabled()) {
			log.trace("End undo insert");
		}
	}

	public void undoDelete(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {

		if (log.isTraceEnabled()) {
			log.trace("Begin undo delete");
		}

		BracketTree tree = new BracketTree(tx.getBufferManager());
		tree.insertSequence(tx, rootPageID, nodes, pageID, true, undoNextLSN);

		if (log.isTraceEnabled()) {
			log.trace("End undo delete");
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

				if (!page.isLeaf()) {
					page.cleanup();
					throw new IndexAccessException(
							"LogOperation type not valid for branch pages.");
				}

				Leaf leaf = (Leaf) page;

				switch (actionType) {
				case INSERT:
				case SMO_INSERT:
					if (!leaf.insertSequence(nodes, true, false, -1)) {
						leaf.cleanup();
						throw new IndexAccessException(
								"Not enough space to redo the insert in page %s.",
								pageID);
					}
					break;
				case DELETE:
				case SMO_DELETE:
					DeleteSequenceInfo delInfo = leaf.deleteSequence(
							nodes.getLowKey(), nodes.getHighKey(), true, false,
							-1);
					if (delInfo.checkLeftNeighbor || delInfo.checkRightNeighbor) {
						leaf.cleanup();
						throw new IndexAccessException(
								"Deletion redo in page %s spans across several leaf pages.",
								pageID);
					}
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

			if (page.getRootPageID() == null
					|| !page.getRootPageID().equals(rootPageID)) {
				page.cleanup();
				throw new IndexAccessException(
						"Undo content update of page %s failed"
								+ " because it does not belong to index %s.",
						pageID, rootPageID);
			}

			if (!page.isLeaf()) {
				page.cleanup();
				throw new IndexAccessException(
						"LogOperation type not valid for branch pages.");
			}

			Leaf leaf = (Leaf) page;

			switch (actionType) {
			case SMO_INSERT:
				DeleteSequenceInfo delInfo = leaf.deleteSequence(
						nodes.getLowKey(), nodes.getHighKey(), true, true,
						undoNextLSN);
				if (delInfo.checkLeftNeighbor || delInfo.checkRightNeighbor) {
					leaf.cleanup();
					throw new IndexAccessException(
							"Insertion undo in page %s spans across several leaf pages.",
							pageID);
				} else if (delInfo.producesEmptyLeaf
						&& leaf.getNextPageID() != null) {
					leaf.cleanup();
					throw new IndexAccessException(
							"Insertion undo in page %s would result in an empty page.",
							pageID);
				}
				break;
			case SMO_DELETE:
				if (!leaf.insertSequence(nodes, true, true, undoNextLSN)) {
					leaf.cleanup();
					throw new IndexAccessException(
							"Not enough space to undo the deletion in page %s.",
							pageID);
				}
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
		case LEAF_INSERT:
			typeString = "Insert into";
			break;
		case LEAF_DELETE:
			typeString = "Delete from";
			break;
		case LEAF_SMO_INSERT:
			typeString = "SMO Insert into";
			break;
		case LEAF_SMO_DELETE:
			typeString = "SMO Delete from";
			break;
		default:
			typeString = "Unkown in";
		}

		return String.format("%s(%s %s of index %s): %s", getClass()
				.getSimpleName(), typeString, pageID, rootPageID, nodes
				.getLowKey());
	}

}
