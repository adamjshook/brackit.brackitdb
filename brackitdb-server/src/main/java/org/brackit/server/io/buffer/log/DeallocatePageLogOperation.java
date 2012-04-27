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

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.xquery.util.log.Logger;

/**
 * This log record is only used for undoing page allocations. The Undo operation
 * of this log record is therefore never invoked.
 * 
 * @author Sebastian Baechle
 * 
 */
public final class DeallocatePageLogOperation extends SinglePageLogOperation {

	private final static Logger log = Logger
			.getLogger(DeallocatePageLogOperation.class.getName());

	public DeallocatePageLogOperation(PageID pageID, int unitID) {
		super(PageLogOperation.DEALLOCATE, pageID, unitID);
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		Buffer buffer = null;

		try {
			buffer = tx.getBufferManager().getBuffer(pageID);

			try {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Redeallocating page %s.", pageID));
				}
				buffer.deletePageImmediately(tx, pageID, -1, false, -1);
			} catch (BufferException e) {
				throw new LogException(e, "Could not deallocate page %s.",
						pageID);
			}
		} catch (BufferException e) {
			if (log.isDebugEnabled()) {
				log.debug(String
						.format("Page %s is already allocated.", pageID));
			}
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		throw new UnsupportedOperationException(
				"This log record can only be used as CLR. Undo is therefore impossible.");
	}

	@Override
	public String toString() {
		return String.format("%s(%s)", getClass().getSimpleName(), pageID);
	}
}