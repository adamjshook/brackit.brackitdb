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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.store.blob.impl;

import java.io.IOException;
import java.io.InputStream;

import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.tx.Tx;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SimpleBlobInputStream extends InputStream {
	protected final Tx transaction;

	protected SimpleBlobPage page;

	protected byte buf[];

	protected int pos;

	protected int count;

	public SimpleBlobInputStream(SimpleBlobPage page, Tx transaction)
			throws IOException {
		this.page = page;
		this.transaction = transaction;
		fill();
	}

	private void fill() throws IOException {
		try {
			if (page != null) {
				byte[] content = page.getChunk();
				buf = content;
				count = buf.length;
				pos = 0;

				SimpleBlobPage next = page.getNext(transaction, false, false);
				page.cleanup();
				page = next;
			} else {
				count = 0;
				pos = 0;
			}
		} catch (BlobStoreAccessException e) {
			if (page != null) {
				page.cleanup();
			}
			page = null;
			throw new IOException(e);
		}
	}

	@Override
	public synchronized int read() throws IOException {
		if (pos >= count) {
			fill();

			if (pos >= count) {
				return -1;
			}
		}
		return getBufIfOpen()[pos++] & 0xff;
	}

	private int read1(byte[] b, int off, int len) throws IOException {
		int avail = count - pos;
		if (avail <= 0) {
			fill();
			avail = count - pos;
			if (avail <= 0)
				return -1;
		}
		int cnt = (avail < len) ? avail : len;
		System.arraycopy(getBufIfOpen(), pos, b, off, cnt);
		pos += cnt;
		return cnt;
	}

	/**
	 * @see java.io.BufferedInputStream
	 */
	@Override
	public synchronized int read(byte b[], int off, int len) throws IOException {
		getBufIfOpen(); // Check for closed stream

		if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}

		int n = 0;
		for (;;) {
			int nread = read1(b, off + n, len - n);
			if (nread <= 0)
				return (n == 0) ? nread : n;
			n += nread;
			if (n >= len)
				return n;
		}
	}

	private byte[] getBufIfOpen() throws IOException {
		if (buf == null) {
			throw new IOException("Stream closed");
		}

		return buf;
	}

	@Override
	public synchronized int available() throws IOException {
		return (count - pos);
	}

	@Override
	public synchronized void close() throws IOException {
		buf = null;

		if (page != null) {
			page.cleanup();
			page = null;
		}
	}
}