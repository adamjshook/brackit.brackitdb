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
import java.util.Arrays;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;

/**
 * Blob store based on an index that breaks the blob down into a sequence of
 * (offset, chunk) pairs which are stored sequentially in an conventional index.
 * 
 * @author Sebastian Baechle
 * 
 */
public class IndexBlobStore implements BlobStore {
	private static final Logger log = Logger.getLogger(IndexBlobStore.class);

	private final Index index;

	public IndexBlobStore(BufferMgr bufferMgr) {
		this.index = new BPlusIndex(bufferMgr);
	}

	public IndexBlobStore(Index index) {
		this.index = index;
	}

	@Override
	public PageID create(Tx transaction, int containerNo, int unitID)
			throws BlobStoreAccessException {
		try {
			
			if (unitID != -1) {
				throw new UnsupportedOperationException("A BlobStore of this type can not be assigned to a unit manually!");
			}
			
			return index.createIndex(transaction, containerNo, Field.INTEGER,
					Field.BYTEARRAY, true, true);
		} catch (IndexAccessException e) {
			throw new BlobStoreAccessException(e,
					"Error creating backing index in container %s.",
					containerNo);
		}
	}

	@Override
	public void drop(Tx transaction, PageID pageID)
			throws BlobStoreAccessException {
		try {
			index.dropIndex(transaction, pageID);
		} catch (IndexAccessException e) {
			throw new BlobStoreAccessException(e,
					"Error dropping backing index %s.", pageID);
		}
	}

	@Override
	public byte[] read(Tx transaction, PageID pageID)
			throws BlobStoreAccessException {
		InputStream in = readStream(transaction, pageID);
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
			throw new BlobStoreAccessException(e, "Error reading blob %s. ",
					pageID);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				throw new BlobStoreAccessException(e,
						"Error closing blob stream %s.", pageID);
			}
		}
	}

	@Override
	public InputStream readStream(Tx transaction, PageID pageID)
			throws BlobStoreAccessException {
		try {
			IndexIterator iterator = index.open(transaction, pageID,
					SearchMode.FIRST, null, null, OpenMode.READ);
			return new IndexBlobStoreInputStream(iterator, transaction);
		} catch (IndexAccessException e) {
			throw new BlobStoreAccessException(e, "Error reading blob %s.",
					pageID);
		} catch (IOException e) {
			throw new BlobStoreAccessException(e, "Error reading blob %s.",
					pageID);
		}
	}

	@Override
	public void write(Tx transaction, PageID pageID, byte[] blob, boolean logged)
			throws BlobStoreAccessException {
		writeStream(transaction, pageID, new ByteArrayInputStream(blob), logged);
	}

	@Override
	public void writeStream(Tx transaction, PageID pageID, InputStream stream,
			boolean logged) throws BlobStoreAccessException {
		IndexIterator iterator = null;
		int position = 0;

		try {
			OpenMode openMode = (logged) ? OpenMode.BULK : OpenMode.LOAD;
			iterator = index.open(transaction, pageID, SearchMode.FIRST, null,
					null, openMode);

			// erase blob if necessary
			while (iterator.getKey() != null) {
				iterator.delete();
			}

			byte[] chunk = new byte[iterator.getMaxInlineValueSize(4)];

			for (int length = readChunk(stream, chunk); length > 0; length = readChunk(
					stream, chunk)) {
				byte[] value = new byte[length];
				System.arraycopy(chunk, 0, value, 0, length);
				byte[] key = Calc.fromInt(position);

				iterator.insert(key, value);
				iterator.next();
				position += length;
			}

			iterator.close();
		} catch (IndexAccessException e) {
			throw new BlobStoreAccessException(e,
					"An error occured while writing blob %s.", pageID);
		} catch (IOException e) {
			iterator.close();
			throw new BlobStoreAccessException(e,
					"Error reading input stream while writing blob %s.", pageID);
		}
	}

	private int readChunk(InputStream stream, byte[] chunk) throws IOException {
		int length = 0;
		int chunkSize = chunk.length;
		Arrays.fill(chunk, (byte) 0);

		for (int read = stream.read(chunk, length, chunkSize - length); read > 0; read = stream
				.read(chunk, length, chunkSize - length)) {
			length += read;
		}

		return length;
	}
}