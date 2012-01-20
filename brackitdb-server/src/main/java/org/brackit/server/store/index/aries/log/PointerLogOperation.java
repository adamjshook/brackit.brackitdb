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
package org.brackit.server.store.index.aries.log;

import java.nio.ByteBuffer;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusTree;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.PageType;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;

/**
 * @author Sebastian Baechle
 * 
 */
public class PointerLogOperation extends BPlusIndexLogOperation {
	private static final Logger log = Logger
			.getLogger(PointerLogOperation.class);

	private enum PointerField {
		PREVIOUS, NEXT, BEFORE
	}

	private static final int SIZE = BASE_SIZE + 2 * PageID.getSize();

	private PageID oldTarget;

	private PageID target;

	public PointerLogOperation(byte type, PageID pageID, PageID rootPageID,
			PageID oldTarget, PageID target) {
		super(type, pageID, rootPageID);
		this.oldTarget = oldTarget;
		this.target = target;
	}

	@Override
	public int getSize() {
		return SIZE;
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		super.toBytes(bb);
		bb.put((oldTarget != null) ? oldTarget.getBytes() : PageID
				.noPageBytes());
		bb.put((target != null) ? target.getBytes() : PageID.noPageBytes());
	}

	private PointerField getField() throws LogException {
		switch (type) {
		case NEXT_PAGE:
			return PointerField.NEXT;
		case PREV_PAGE:
			return PointerField.PREVIOUS;
		case BEFORE_PAGE:
			return PointerField.BEFORE;
		default:
			throw new LogException("Unknown field type : %s.", type);
		}
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		try {
			redoPointerFieldUpdate(tx, getField(), LSN);
		} catch (IndexAccessException e) {
			throw new LogException(e, "Redo of format page %s failed.", pageID);
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		try {
			undoPointerFieldUpdate(tx, getField(), LSN, undoNextLSN);
		} catch (IndexAccessException e) {
			throw new LogException(e, "Redo of format page %s failed.", pageID);
		}
	}

	public void redoPointerFieldUpdate(Tx tx, PointerField pointerField,
			long LSN) throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin redo pointer update");
		}

		try {
			page = new BPlusTree(tx.getBufferManager()).getPage(tx, pageID,
					true, false);
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
				if ((page.getRootPageID() != null)
						&& (!page.getRootPageID().equals(rootPageID))) {
					page.cleanup();
					throw new IndexAccessException(
							"Redo pointer update of page %s failed because it does not belong to index %s.",
							pageID, rootPageID);
				}

				switch (pointerField) {
				case PREVIOUS:
					page.setPreviousPageID(target, false, -1);
					break;
				case NEXT:
					page.setNextPageID(target, false, -1);
					break;
				case BEFORE:
					page.setBeforePageID(target, false, -1);
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
					"Redo pointer update of page %s failed.", pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End redo pointer update");
		}
	}

	public void undoPointerFieldUpdate(Tx tx, PointerField pointerField,
			long LSN, long undoNextLSN) throws IndexAccessException {
		PageContext page = null;

		if (log.isTraceEnabled()) {
			log.trace("Begin undo pointer update");
		}

		try {
			page = new BPlusTree(tx.getBufferManager()).getPage(tx, pageID,
					true, false);

			if ((page.getLSN() != LSN)
					|| ((page.getPageType() != PageType.INDEX_LEAF) && (page
							.getPageType() != PageType.INDEX_TREE))
					|| (!page.getRootPageID().equals(rootPageID))) {
				page.cleanup();
				throw new IndexAccessException(
						"Undo pointer update of page %s failed because it does not belong to index %s.",
						pageID, rootPageID);
			}

			switch (pointerField) {
			case PREVIOUS:
				page.setPreviousPageID(target, true, undoNextLSN);
				break;
			case NEXT:
				page.setNextPageID(target, true, undoNextLSN);
				break;
			case BEFORE:
				page.setBeforePageID(target, true, undoNextLSN);
				break;
			}

			page.cleanup();
		} catch (IndexOperationException e) {
			throw new IndexAccessException(
					e,
					"Undo pointer update of page %s failed because it has been deleted. This must not happen in the ARIES protocol.",
					pageID);
		}

		if (log.isTraceEnabled()) {
			log.trace("End undo pointer update");
		}
	}

	@Override
	public String toString() {
		String typeString;
		switch (type) {
		case NEXT_PAGE:
			typeString = "NEXT";
			break;
		case PREV_PAGE:
			typeString = "PREV";
			break;
		case BEFORE_PAGE:
			typeString = "BEFORE";
		default:
			typeString = "UNKNOWN";
		}

		return String.format("%s(%s.%s->%s to %s in index %s)", getClass()
				.getSimpleName(), pageID, typeString, oldTarget, target,
				rootPageID);
	}
}
