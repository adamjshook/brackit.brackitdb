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
package org.brackit.server.store.blob.impl;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.BufferedPage;
import org.brackit.server.tx.Tx;

/**
 * Provides basic access primitves for large records exceedings (page) size
 * limits (overflows).
 * 
 * @author Sebastian Baechle
 * 
 */
public class SimpleBlobPage extends BasePage implements BufferedPage {
	private final static Logger log = Logger.getLogger(SimpleBlobPage.class);

	private final Buffer buffer;

	protected SimpleBlobPage(Buffer buffer, Handle handle) {
		super(buffer, handle, PageID.getSize());
		this.buffer = buffer;
	}

	private PageID getNextPageID() {
		return PageID.fromBytes(handle.page, BASE_PAGE_START_OFFSET);
	}

	private void setNextPageID(PageID pageID) {
		if (pageID == null) {
			PageID.noPageToBytes(handle.page, BASE_PAGE_START_OFFSET);
		} else {
			pageID.toBytes(handle.page, BASE_PAGE_START_OFFSET);
		}
	}

	void deleteTail(Tx tx) throws BlobStoreAccessException {
		for (SimpleBlobPage p = getNext(tx, true, false); p != null; p = p
				.getNext(tx, true, false)) {
			try {
				buffer.deletePage(tx, p.getPageID(), p.getUnitID());
				p.cleanup();
			} catch (BufferException e) {
				throw new BlobStoreAccessException(e,
						"Error deleting overflow page.");
			}
		}
	}

	SimpleBlobPage getNext(Tx tx, boolean write, boolean appendNew)
			throws BlobStoreAccessException {
		try {
			PageID nextPageID = getNextPageID();

			if (!write) {
				if (nextPageID != null) {
					Handle nextOverflowHandle = buffer.fixPage(tx, nextPageID);
					nextOverflowHandle.latchS();

					return new SimpleBlobPage(buffer, nextOverflowHandle);
				}
			} else {
				if (nextPageID == null) {
					if (appendNew) {
						Handle nextOverflowHandle = buffer.allocatePage(tx, getUnitID());
						nextOverflowHandle.setAssignedTo(tx);
						SimpleBlobPage next = new SimpleBlobPage(buffer,
								nextOverflowHandle);
						setNextPageID(nextOverflowHandle.getPageID());
						next.setBasePageID(getBasePageID());
						return next;
					}
				} else {
					Handle nextOverflowHandle = buffer.fixPage(tx, nextPageID);
					nextOverflowHandle.latchX();
					nextOverflowHandle.setAssignedTo(tx);
					return new SimpleBlobPage(buffer, nextOverflowHandle);
				}
			}

			return null;
		} catch (BufferException e) {
			throw new BlobStoreAccessException(e,
					"Error getting next overflow page.");
		}
	}

	byte[] getChunk() {
		int usedSpace = getUsedSpace();
		byte[] content = new byte[usedSpace];
		System.arraycopy(handle.page, getStartOffset(), content, 0, usedSpace);
		// System.out.println(String.format("Read %s bytes from %s",
		// content.length, handle));
		return content;
	}

	void setChunk(byte[] content, int length) {
		System.arraycopy(content, 0, handle.page, getStartOffset(), length);
		setUsedSpace(length);
		// System.out.println(String.format("Wrote %s bytes to %s", length,
		// handle));
	}
}
