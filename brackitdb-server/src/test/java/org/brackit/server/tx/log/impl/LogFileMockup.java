/*
 * [New BSD License]
 * Copyright (c) 2011, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
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
package org.brackit.server.tx.log.impl;

import org.brackit.server.tx.log.LogException;
import org.brackit.server.util.ByteSequence;

/**
 * @author Sebastian Baechle
 * 
 */
public final class LogFileMockup implements LogFile {
	private static final int CAPACITY = 1024;

	private final boolean checkOpen = true;

	private final String filename;

	private ByteSequence buffer;

	private ByteSequence loadSequence;

	public LogFileMockup(String filename, ByteSequence sequence) {
		this.filename = filename;
		this.loadSequence = sequence;
	}

	public LogFileMockup(String filename) {
		this(filename, new ByteSequence(CAPACITY, CAPACITY));
	}

	@Override
	public void open() throws LogException {
		if (buffer != null) {
			throw new LogException("Log file %s already openend.", toString());
		}

		if (loadSequence == null) {
			throw new LogException("Cannot open dummy file %s", filename);
		}

		buffer = loadSequence;
		loadSequence = null;
	}

	@Override
	public void close() throws LogException {
		if (checkOpen)
			checkOpen();

		loadSequence = buffer;
		buffer = null;
	}

	@Override
	public void delete() throws LogException {
		if (buffer != null) {
			throw new LogException("Cannot delete opened log file %s.",
					toString());
		}

		loadSequence = null;
	}

	@Override
	public long getLength() throws LogException {
		if (checkOpen)
			checkOpen();

		return buffer.getLength();
	}

	@Override
	public void seek(long pos) throws LogException {
		if (checkOpen)
			checkOpen();

		if ((pos < 0) || (pos > buffer.getLength())) {
			throw new LogException(
					"Could not seek to position %s in log file %s.", pos,
					toString());
		}

		buffer.position((int) pos);
	}

	@Override
	public long seekHead() throws LogException {
		if (checkOpen)
			checkOpen();

		seek(0);
		return 0;
	}

	@Override
	public int read(byte[] b) throws LogException {
		if (checkOpen)
			checkOpen();

		int read = Math.min(buffer.getLength() - buffer.position(), b.length);
		buffer.get(b);
		return read;
	}

	@Override
	public void sync() throws LogException {
		if (checkOpen)
			checkOpen();
	}

	@Override
	public void write(byte[] b) throws LogException {
		if (checkOpen)
			checkOpen();
		buffer.put(b);
	}

	private final void checkOpen() throws LogException {
		if (buffer == null) {
			throw new LogException("Log file %s is not open.", toString());
		}
	}

	@Override
	public long getFilePointer() throws LogException {
		if (checkOpen)
			checkOpen();

		return buffer.position();
	}

	@Override
	public int readInt() throws LogException {
		if (checkOpen)
			checkOpen();
		return buffer.getInt();
	}

	@Override
	public void writeInt(int i) throws LogException {
		if (checkOpen)
			checkOpen();
		buffer.putInt(i);
	}

	@Override
	public void writeLong(long l) throws LogException {
		if (checkOpen)
			checkOpen();
		buffer.putLong(l);
	}

	@Override
	public long readLong() throws LogException {
		if (checkOpen)
			checkOpen();
		return buffer.getLong();
	}

	@Override
	public long truncateTo(long pos) throws LogException {
		if (checkOpen)
			checkOpen();

		// do nothing -> plain raf files do not support truncation at all
		return 0;
	}

	@Override
	public String toString() {
		return filename;
	}

	public ByteSequence getByteSequence() {
		return buffer;
	}
}
