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
package org.brackit.server.io.buffer.log;

import java.nio.ByteBuffer;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;

/**
 * @author Sebastian Baechle
 * 
 */
public final class AllocatePageLogOperation extends PageLogOperation {
	private static final int SIZE = PageID.getSize() + SizeConstants.INT_SIZE;

	private final static Logger log = Logger
			.getLogger(AllocatePageLogOperation.class.getName());
	
	private final PageID pageID;
	private final int unitID;

	public AllocatePageLogOperation(PageID pageID, int unitID) {
		super(PageLogOperation.ALLOCATE);
		this.pageID = pageID;
		this.unitID = unitID;
	}

	@Override
	public int getSize() {
		return SIZE;
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		bb.put(pageID.getBytes());
		bb.putInt(unitID);
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		Handle handle = null;
		Buffer buffer = null;

		try {
			buffer = tx.getBufferManager().getBuffer(pageID);
			handle = buffer.fixPage(tx, pageID);

			if (log.isDebugEnabled()) {
				log.debug(String
						.format("Page %s is already allocated.", pageID));
			}

			try {
				buffer.unfixPage(handle);
			} catch (BufferException e) {
				throw new LogException(e, "Unfix of page %s failed.", pageID);
			}
		} catch (BufferException e) {
			// page does not exist -> redo
			try {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Reallocating page %s.", pageID));
				}

				handle = buffer.allocatePage(tx, unitID, pageID, false, -1);
				handle.setLSN(LSN);
				handle.unlatch();

				try {
					buffer.unfixPage(handle);
				} catch (BufferException e2) {
					throw new LogException(e2, "Unfix of page %s failed.",
							pageID);
				}
			} catch (BufferException e1) {
				throw new LogException(e1, "Reallocation of page %s failed.",
						pageID);
			}
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		Buffer buffer = null;

		try {
			buffer = tx.getBufferManager().getBuffer(pageID);
		} catch (BufferException e) {
			/*
			 * This must not happen because a page allocation/deletion is only
			 * allowed during an SMO and therefore a the page must not have been
			 * deleted by a concurrent transaction.
			 */
			log.error(String.format("Could not fix page %s.", pageID), e);
			throw new LogException(e, "Could not fix page %s for deletion.",
					pageID);
		}

		try {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Deallocating page %s.", pageID));
			}
			buffer.deletePage(tx, pageID, -1, true, undoNextLSN);
		} catch (BufferException e) {
			throw new LogException(e, "Could not deallocate page %s.", pageID);
		}
	}

	@Override
	public String toString() {
		return String.format("%s(%s)", getClass().getSimpleName(), pageID);
	}
}