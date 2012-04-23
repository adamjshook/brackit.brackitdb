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
package org.brackit.server.tx.log.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.brackit.server.tx.log.LogException;

/**
 * Simple random access log file.
 * 
 * @author Sebastian Baechle
 * 
 */
public class RAFLogFile implements LogFile {
	private final boolean checkOpen;

	private final String filename;

	private RandomAccessFile raf;

	public RAFLogFile(String filename) throws FileNotFoundException {
		this(filename, true);
	}

	public RAFLogFile(String filename, boolean checkOpen)
			throws FileNotFoundException {
		this.filename = filename;
		this.checkOpen = checkOpen;
	}

	@Override
	public void open() throws LogException {
		if (raf != null) {
			throw new LogException("Log file %s already openend.", toString());
		}

		try {
			raf = new RandomAccessFile(filename, Constants.LOG_FILE_MODE);
		} catch (IOException e) {
			throw new LogException(e, "Opening log file %s failed.", toString());
		}
	}

	@Override
	public void close() throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			raf.close();
		} catch (IOException e) {
			throw new LogException(e, "Could not close log file %s.",
					toString());
		} finally {
			raf = null;
		}
	}

	@Override
	public void delete() throws LogException {
		if (raf != null) {
			throw new LogException("Cannot delete opened log file %s.",
					toString());
		}

		File file = new File(filename);

		if (file.exists()) {
			file.delete();
		}
	}

	@Override
	public long getLength() throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			return raf.length();
		} catch (IOException e) {
			throw new LogException(e, "Could not read length of log file %s.",
					toString());
		}
	}

	@Override
	public void seek(long pos) throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			raf.seek(pos);
		} catch (IOException e) {
			throw new LogException(e,
					"Could not seek to position %s in log file %s.", pos,
					toString());
		}
	}

	@Override
	public long seekHead() throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			raf.seek(0);
			return 0;
		} catch (IOException e) {
			throw new LogException(e,
					"Could not seek to position %s in log file %s.", 0,
					toString());
		}
	}

	@Override
	public int read(byte[] b) throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			// System.out.println(String.format("%s: Reading %s bytes at %s.",
			// toString(), b.length, raf.getFilePointer()));
			return raf.read(b);
		} catch (IOException e) {
			throw new LogException(e, "Could not read %s bytes log file %s.",
					b.length, toString());
		}
	}

	@Override
	public void sync() throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			raf.getFD().sync();
		} catch (IOException e) {
			throw new LogException(e, "Could not sync log file %s to disk.",
					toString());
		}
	}

	@Override
	public void write(byte[] b) throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			raf.write(b);
		} catch (IOException e) {
			throw new LogException(e, "Could not write % bytes log file %s.",
					b.length, toString());
		}
	}

	private final void checkOpen() throws LogException {
		if (raf == null) {
			throw new LogException("Log file %s is not open.", toString());
		}
	}

	@Override
	public long getFilePointer() throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			return raf.getFilePointer();
		} catch (IOException e) {
			throw new LogException(e,
					"Could not read position in log file %s.", toString());
		}
	}

	@Override
	public int readInt() throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			return raf.readInt();
		} catch (IOException e) {
			throw new LogException(e, "Could not read int from log file %s.",
					toString());
		}
	}

	@Override
	public void writeInt(int i) throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			raf.writeInt(i);
		} catch (IOException e) {
			throw new LogException(e, "Could not write int to log file %s.",
					toString());
		}
	}

	@Override
	public void writeLong(long l) throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			raf.writeLong(l);
		} catch (IOException e) {
			throw new LogException(e, "Could not write long to log file %s.",
					toString());
		}
	}

	@Override
	public long readLong() throws LogException {
		if (checkOpen)
			checkOpen();

		try {
			return raf.readLong();
		} catch (IOException e) {
			throw new LogException(e, "Could not read long from log file %s.",
					toString());
		}
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
}
