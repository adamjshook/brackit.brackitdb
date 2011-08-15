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

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.page.BPContext;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;

/**
 * @author Martin Hiller
 * 
 */
public class HighkeyLogOperation extends BracketIndexLogOperation {

	private static final Logger log = Logger
			.getLogger(HighkeyLogOperation.class);

	private static final int SIZE = BASE_SIZE + 2 * SizeConstants.SHORT_SIZE;

	private byte[] oldHighKey;
	private byte[] newHighKey;

	public HighkeyLogOperation(PageID pageID, PageID rootPageID,
			byte[] oldHighKey, byte[] newHighKey) {
		super(HIGHKEY_UPDATE, pageID, rootPageID);
		this.oldHighKey = oldHighKey;
		this.newHighKey = newHighKey;
	}

	@Override
	public int getSize() {
		return SIZE + (oldHighKey == null ? 0 : oldHighKey.length)
				+ (newHighKey == null ? 0 : newHighKey.length);
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		super.toBytes(bb);

		if (oldHighKey == null) {
			bb.putShort((short) 0);
		} else {
			bb.putShort((short) oldHighKey.length);
			bb.put(oldHighKey);
		}

		if (newHighKey == null) {
			bb.putShort((short) 0);
		} else {
			bb.putShort((short) newHighKey.length);
			bb.put(newHighKey);
		}
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		try {
			redoHighkeyUpdate(tx, LSN);
		} catch (IndexAccessException e) {
			throw new LogException(e, "Redo of Highkey Update in page %s failed.", pageID);
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		try {
			undoHighkeyUpdate(tx, LSN, undoNextLSN);
		} catch (IndexAccessException e) {
			throw new LogException(e, "Undo of Highkey Update in page %s failed.", pageID);
		}
	}

	public void redoHighkeyUpdate(Tx tx, long LSN) throws IndexAccessException {
		BPContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin redo highkey update");
		}

		try {
			page = new BracketTree(tx.getBufferManager()).getPage(tx, pageID,
					true, false);
		} catch (IndexOperationException e) {
			if (log.isDebugEnabled()) {
				log.trace(String.format("Page %s has been deleted "
						+ "and flushed to disk. " + "No redo necessary.",
						pageID));
			}

			return;
		}

		try {
			if (page.getLSN() < LSN) {
				if ((page.getRootPageID() != null)
						&& (!page.getRootPageID().equals(rootPageID))) {
					page.cleanup();
					throw new IndexAccessException(
							"Redo highkey update of page %s failed"
									+ " because it does not belong to index %s.",
							pageID, rootPageID);
				}

				if (!page.isLeaf()) {
					page.cleanup();
					throw new IndexAccessException(
							"LogOperation type not valid for branch pages.");
				}

				Leaf leaf = (Leaf) page;
				if (!leaf.setHighKeyBytes(newHighKey, false, -1)) {
					leaf.cleanup();
					throw new IndexAccessException(
							"Not enough space to set the new Highkey.");
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
					"Redo highkey update of page %s failed.", pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End redo pointer update");
		}
	}

	public void undoHighkeyUpdate(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {
		BPContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo highkey update");
		}

		try {
			page = new BracketTree(tx.getBufferManager()).getPage(tx, pageID,
					true, false);

			if (page.getRootPageID() == null
					|| !page.getRootPageID().equals(rootPageID)) {
				page.cleanup();
				throw new IndexAccessException(
						"Undo highkey update of page %s failed"
								+ " because it does not belong to index %s.",
						pageID, rootPageID);
			}

			if (!page.isLeaf()) {
				page.cleanup();
				throw new IndexAccessException(
						"LogOperation type not valid for branch pages.");
			}

			Leaf leaf = (Leaf) page;
			if (!leaf.setHighKeyBytes(oldHighKey, true, undoNextLSN)) {
				leaf.cleanup();
				throw new IndexAccessException(
						"Not enough space to set the old Highkey.");
			}

			page.cleanup();
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new IndexAccessException(e,
					"Undo pointer update of page %s failed"
							+ " because it has been deleted. "
							+ "This must not happen in the ARIES protocol.",
					pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End undo pointer update");
		}
	}

	@Override
	public String toString() {

		DocID docID = new DocID(rootPageID.value());
		XTCdeweyID oldDeweyID = (oldHighKey == null) ? null : new XTCdeweyID(docID, oldHighKey);
		XTCdeweyID newDeweyID = (newHighKey == null) ? null : new XTCdeweyID(docID, newHighKey);
		
		return String.format("%s(%s.%s->%s to %s in index %s)", getClass()
				.getSimpleName(), pageID, "HIGHKEY", oldDeweyID, newDeweyID,
				rootPageID);
	}

}
