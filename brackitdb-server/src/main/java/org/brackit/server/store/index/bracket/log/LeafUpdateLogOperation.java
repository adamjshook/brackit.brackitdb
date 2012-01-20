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
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.store.index.bracket.page.BPContext;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;

/**
 * @author Martin Hiller
 * 
 */
public class LeafUpdateLogOperation extends BracketIndexLogOperation {

	private static final Logger log = Logger
			.getLogger(LeafUpdateLogOperation.class);

	private XTCdeweyID key;
	private byte[] keyBytes;
	private byte[] oldValue;
	private byte[] newValue;

	private static final int SIZE = BASE_SIZE + 3 * SizeConstants.SHORT_SIZE;

	public LeafUpdateLogOperation(PageID pageID, PageID rootPageID,
			XTCdeweyID key, byte[] oldValue, byte[] newValue) {
		super(LEAF_UPDATE, pageID, rootPageID);
		this.key = key;
		this.keyBytes = Field.COLLECTIONDEWEYID.encode(key);
		this.oldValue = oldValue;
		this.newValue = newValue;
	}
	
	public LeafUpdateLogOperation(PageID pageID, PageID rootPageID,
			byte[] keyBytes, byte[] oldValue, byte[] newValue) {
		super(LEAF_UPDATE, pageID, rootPageID);
		this.key = Field.COLLECTIONDEWEYID.decode(rootPageID.value(), keyBytes);
		this.keyBytes = keyBytes;
		this.oldValue = oldValue;
		this.newValue = newValue;
	}

	@Override
	public int getSize() {
		return SIZE + keyBytes.length + oldValue.length + newValue.length;
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		super.toBytes(bb);
		bb.putShort((short) keyBytes.length);
		bb.put(keyBytes);
		bb.putShort((short) oldValue.length);
		bb.put(oldValue);
		bb.putShort((short) newValue.length);
		bb.put(newValue);
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		try {
			redoPageContentUpdate(tx, LSN);
			return;
		} catch (IndexAccessException e) {
			throw new LogException(e, "Redo of update index %s failed",
					rootPageID);
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		try {
			undoUpdate(tx, LSN, undoNextLSN);
			return;
		} catch (IndexAccessException e) {
			throw new LogException(e,
					"Undo of update index %s failed", rootPageID);
		}
	}

	public void undoUpdate(Tx tx, long LSN, long undoNextLSN)
			throws IndexAccessException {

		BPContext page = null;
		Leaf leaf = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo update");
		}
		BracketTree tree = new BracketTree(tx.getBufferManager());

		try {
			page = tree.getPage(tx, pageID, true, false);
			
			if (page.getRootPageID() == null
					|| !page.getRootPageID().equals(rootPageID)
					|| !page.isLeaf()) {
				// page not usable for direct access
				page.cleanup();
				page = null;
			}
			
			if (page != null) {
				leaf = (Leaf) page;
				page = null;
				
				if (leaf.navigateContextFree(key, NavigationMode.TO_KEY) != NavigationStatus.FOUND) {
					// wrong page
					leaf.cleanup();
					leaf = null;
				}
			}
			
		}  catch (IndexOperationException e) {
			if (log.isTraceEnabled()) {
				log.trace(String.format("Page %s could not be fixed for undo.",
						pageID));
			}
			page = null;
		}

		if (leaf == null) {
			// descend down the tree
			leaf = tree.descendToPosition(tx, rootPageID,
					NavigationMode.TO_KEY, key,
					new DeweyIDBuffer(),
					tree.getLeafScanner(NavigationMode.TO_KEY), true);
		}

		leaf = tree.updateInLeaf(tx, rootPageID, leaf, oldValue, undoNextLSN);
		leaf.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End undo update");
		}
	}

	public void redoPageContentUpdate(Tx tx, long LSN)
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

				if (leaf.navigateContextFree(key, NavigationMode.TO_KEY) != NavigationStatus.FOUND) {
					leaf.cleanup();
					throw new IndexAccessException(
							"Updated node %s not found in page %s.", key,
							pageID);
				}

				// redo update
				if (!leaf.setValue(newValue, false, -1)) {
					leaf.cleanup();
					throw new IndexAccessException(
							"Not enough space to redo the update in page %s.",
							pageID);
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

	@Override
	public String toString() {
		return String.format("%s(Update in %s of index %s): %s", getClass()
				.getSimpleName(), pageID, rootPageID, key);
	}

}
