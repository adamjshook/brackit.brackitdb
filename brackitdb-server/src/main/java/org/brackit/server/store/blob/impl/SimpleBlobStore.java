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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.tx.Tx;

/**
 * Simple blob implementation using chained pages.
 * 
 * @author Sebastian Baechle
 * 
 */
public class SimpleBlobStore implements BlobStore {
	protected final BufferMgr bufferMgr;

	public SimpleBlobStore(BufferMgr bufferMgr) {
		super();
		this.bufferMgr = bufferMgr;
	}

	@Override
	public PageID create(Tx tx, int containerNo, int unitID)
			throws BlobStoreAccessException {
		try {
			Buffer buffer = bufferMgr.getBuffer(containerNo);
			
			if (unitID == -1) {
				// create new unit
				unitID = buffer.createUnit(-1);
			}
			
			Handle nextOverflowHandle = buffer.allocatePage(tx, unitID);
			nextOverflowHandle.setAssignedTo(tx);
			SimpleBlobPage page = new SimpleBlobPage(buffer, nextOverflowHandle);

			PageID blobPageID = page.getPageID();
			page.setBasePageID(blobPageID);
			page.cleanup();

			return blobPageID;
		} catch (BufferException e) {
			throw new BlobStoreAccessException(e,
					"Could not allocate blob page");
		}
	}

	@Override
	public void drop(Tx tx, PageID pageID) throws BlobStoreAccessException {
		try {
			Buffer buffer = bufferMgr.getBuffer(pageID);
			Handle nextOverflowHandle = buffer.fixPage(tx, pageID);
			nextOverflowHandle.latchX();
			SimpleBlobPage page = new SimpleBlobPage(buffer, nextOverflowHandle);

			page.deleteTail(tx);
			buffer.deletePage(tx, nextOverflowHandle.getPageID(), nextOverflowHandle.getUnitID(), true, tx
					.checkPrevLSN());
			page.cleanup();
		} catch (BufferException e) {
			throw new BlobStoreAccessException(e, "Could not delete blob page");
		}
	}

	@Override
	public void write(Tx tx, PageID pageID, byte[] blob, boolean logged)
			throws BlobStoreAccessException {
		writeStream(tx, pageID, new ByteArrayInputStream(blob), logged);
	}

	@Override
	public void writeStream(Tx tx, PageID pageID, InputStream stream,
			boolean logged) throws BlobStoreAccessException {
		if (logged) {
			throw new BlobStoreAccessException(
					"This blob implementation does not support logged changes");
		}

		PageID basePageID = pageID;

		SimpleBlobPage page = null;
		int position = 0;

		try {
			Buffer buffer = bufferMgr.getBuffer(pageID);
			Handle nextOverflowHandle = buffer.fixPage(tx, pageID);
			nextOverflowHandle.setAssignedTo(tx);
			nextOverflowHandle.latchX();

			page = new SimpleBlobPage(buffer, nextOverflowHandle);

			byte[] chunk = new byte[page.getUsableSpace()];
			boolean first = true;

			for (int length = readChunk(chunk, stream); length > 0; length = readChunk(
					chunk, stream)) {
				if (!first) {
					SimpleBlobPage next = page.getNext(tx, true, true);
					page.cleanup();
					page = next;
				}

				page.setChunk(chunk, length);
				first = false;
			}

			page.deleteTail(tx);
			page.cleanup();
		} catch (BufferException e) {
			if (page != null) {
				page.cleanup();
			}
			throw new BlobStoreAccessException(e,
					"An error occured while writing overflow %s.", pageID);
		} catch (IOException e) {
			page.cleanup();
			throw new BlobStoreAccessException(e,
					"Error reading input stream while writing overflow %s.",
					pageID);
		}
	}

	private int readChunk(byte[] chunk, InputStream stream) throws IOException {
		int length = 0;
		int chunkSize = chunk.length;

		for (int read = stream.read(chunk, length, chunkSize - length); read > 0; read = stream
				.read(chunk, length, chunkSize - length)) {
			length += read;
		}

		return length;
	}

	@Override
	public InputStream readStream(Tx tx, PageID pageID)
			throws BlobStoreAccessException {
		try {
			Buffer buffer = bufferMgr.getBuffer(pageID);
			Handle overflowHandle = buffer.fixPage(tx, pageID);
			overflowHandle.latchS();
			SimpleBlobPage page = new SimpleBlobPage(buffer, overflowHandle);
			return new SimpleBlobInputStream(page, tx);
		} catch (BufferException e) {
			throw new BlobStoreAccessException(e, "Error reading overflow %s.",
					pageID);
		} catch (IOException e) {
			throw new BlobStoreAccessException(e, "Error reading overflow %s.",
					pageID);
		}
	}

	public byte[] read(Tx tx, PageID pageID) throws BlobStoreAccessException {
		InputStream in = readStream(tx, pageID);
		int position = 0;

		try {
			int chunkSize = in.available();

			byte[] chunk = new byte[chunkSize];
			byte[] buf = new byte[chunkSize];

			for (int n = in.read(chunk); n > 0; n = in.read(chunk)) {
				if (n > buf.length - position) {
					// resize buffer
					byte[] resized = new byte[position + 2 * chunkSize];
					System.arraycopy(buf, 0, resized, 0, position);
					buf = resized;
				}

				System.arraycopy(chunk, 0, buf, position, n);
				position += n;
			}

			byte[] blob = new byte[position];
			System.arraycopy(buf, 0, blob, 0, position);

			return blob;
		} catch (IOException e) {
			throw new BlobStoreAccessException(e,
					"Error reading overflow %s. ", pageID);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				throw new BlobStoreAccessException(e,
						"Error closing overflow stream %s.", pageID);
			}
		}
	}
}
