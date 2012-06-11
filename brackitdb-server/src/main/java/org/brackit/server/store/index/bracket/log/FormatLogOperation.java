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

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.page.BPContext;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.xquery.util.log.Logger;

/**
 * @author Martin Hiller
 * 
 */
public class FormatLogOperation extends BracketIndexLogOperation {

	private static final Logger log = Logger
			.getLogger(FormatLogOperation.class);

	private static final int SIZE = BASE_SIZE + 3;

	private boolean oldLeaf;
	private boolean leaf;

	private int oldHeight;
	private int height;

	private boolean oldCompressed;
	private boolean compressed;

	public FormatLogOperation(PageID pageID, PageID rootPageID,
			boolean oldLeaf, boolean leaf, int oldHeight, int height,
			boolean oldCompressed, boolean compressed) {
		super(FORMAT, pageID, rootPageID);
		this.oldLeaf = oldLeaf;
		this.leaf = leaf;
		this.oldHeight = oldHeight;
		this.height = height;
		this.oldCompressed = oldCompressed;
		this.compressed = compressed;
	}

	@Override
	public int getSize() {
		return SIZE;
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		super.toBytes(bb);

		byte flags = (byte) (((oldLeaf ? 1 : 0) << 3) | ((leaf ? 1 : 0) << 2)
				| ((oldCompressed ? 1 : 0) << 1) | (compressed ? 1 : 0));

		bb.put(flags);
		bb.put((byte) oldHeight);
		bb.put((byte) height);
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		try {
			redoFormatPage(tx, LSN);
		} catch (IndexAccessException e) {
			throw new LogException(e, "Redo of format page %s failed.", pageID);
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		try {
			undoFormatPage(tx, LSN, undoNextLSN);
		} catch (IndexAccessException e) {
			throw new LogException(e, "Undo of format page %s failed.", pageID);
		}
	}

	public void undoFormatPage(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {
		BPContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo format page");
		}

		try {
			page = new BracketTree(tx.getBufferManager()).getPage(tx, pageID,
					true, false);

			if (!page.getRootPageID().equals(rootPageID)) {
				page.cleanup();
				throw new IndexAccessException(
						"Undo format page %s failed because "
								+ "it does not belong to index %s.", pageID,
						rootPageID);
			}

			page.format(oldLeaf, rootPageID, oldHeight, oldCompressed, true,
					undoNextLSN);
			page.cleanup();
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Undo format page %s failed because "
							+ "it has been deleted. This must not "
							+ "happen in the ARIES protocol.", pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End undo format page");
		}
	}

	public void redoFormatPage(Tx tx, long LSN) throws IndexAccessException {
		BPContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin redo format page");
		}

		try {
			page = new BracketTree(tx.getBufferManager()).getPage(tx, pageID,
					true, false);
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
				// if ((page.getRootPageID() != null)
				// && (!page.getRootPageID().equals(rootPageID))) {
				// page.cleanup();
				// throw new IndexAccessException(
				// "Redo format page %s failed because"
				// + " it does not belong to index %s.",
				// pageID, rootPageID);
				// }

				page.format(leaf, rootPageID, height, compressed, false, -1);

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
			throw new IndexAccessException(e, "Redo format page %s failed.",
					pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End redo format page");
		}
	}

	@Override
	public String toString() {
		return String.format("%s(%s %s->%s for index %s)", getClass()
				.getSimpleName(), pageID, oldLeaf ? "LEAF" : "BRANCH",
				leaf ? "LEAF" : "BRANCH", rootPageID);
	}

}
