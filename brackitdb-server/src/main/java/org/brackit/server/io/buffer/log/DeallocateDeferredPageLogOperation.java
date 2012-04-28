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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.SizeConstants;
import org.brackit.xquery.util.log.Logger;

/**
 * This LogOperation logs the deferred deallocation of single pages and whole
 * units at once.
 * 
 * @author Sebastian Baechle
 * 
 */
public final class DeallocateDeferredPageLogOperation extends PageLogOperation {

	private final static Logger log = Logger
			.getLogger(DeallocateDeferredPageLogOperation.class.getName());

	private final int containerID;
	private final PageUnitPair[] pages;
	private final int[] units;

	public DeallocateDeferredPageLogOperation(int containerID,
			PageUnitPair[] pages, int[] units) {
		super(PageLogOperation.DEALLOCATE_DEFERRED);
		this.containerID = containerID;
		this.pages = pages;
		this.units = units;
	}

	@Override
	public int getSize() {
		return 2 * SizeConstants.INT_SIZE + pages.length
				* (PageID.getSize() + SizeConstants.INT_SIZE) + units.length
				* SizeConstants.INT_SIZE;
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		bb.putInt(containerID);
		bb.putInt(pages.length);
		for (PageUnitPair page : pages) {
			bb.put(page.pageID.getBytes());
			bb.putInt(page.unitID);
		}
		bb.putInt(units.length);
		for (int unitID : units) {
			bb.putInt(unitID);
		}
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		Buffer buffer = null;

		try {
			buffer = tx.getBufferManager().getBuffer(containerID);
		} catch (BufferException e) {
			throw new LogException(e);
		}

		if (log.isDebugEnabled()) {
			log.debug("Adding PostRedoHook to transaction.");
		}

		buffer.releaseAfterRedo(tx, pages, units);
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {

		// Tx crashed between logging this record (as PreCommitHook) and Commit
		// -> don't do anything, since the PostRedoHook created by the redo
		// method will not be executed
	}

	@Override
	public String toString() {
		return String.format("%s(Pages: %s, Units: %s)", getClass()
				.getSimpleName(), Arrays.toString(pages), Arrays
				.toString(units));
	}
}