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
package org.brackit.server.io.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 
 * A wrapper around RAF to support the BlockFile interface. Writes are synced.
 * 
 * @author Ou Yi
 * 
 */
public class RAFBlockFile implements BlockFile {

	String fileName;
	RandomAccessFile file;
	boolean autoSync;
	final int blockSize;

	// first operate on raf, later generalize to virtual files
	public RAFBlockFile(String fileName, int blockSize) {
		this.fileName = fileName;
		this.blockSize = blockSize;
	}

	@Override
	public void open(boolean autoSync) throws FileException {
		try {
			file = new RandomAccessFile(fileName,
					autoSync ? Constants.FILE_MODE_SYNC
							: Constants.FILE_MODE_UNSY);
			this.autoSync = autoSync;
		} catch (FileNotFoundException e) {
			throw new FileException(e);
		}
	}

	@Override
	public void close() throws FileException {
		try {
			if (!autoSync) {
				sync();
			}
			file.close();
		} catch (IOException e) {
			throw new FileException(e);
		}
	}

	@Override
	public void read(int blockNo, byte[] block, int numBlocks)
			throws FileException {
		try {
			seekToBlock(blockNo);
			file.read(block, 0, numBlocks * blockSize);
		} catch (IOException e) {
			throw new FileException(e);
		}
	}

	@Override
	public void write(int blockNo, byte[] block, int numBlocks)
			throws FileException {
		try {
			seekToBlock(blockNo);
			file.write(block, 0, numBlocks * blockSize);

			if (autoSync) {
				sync();
			}
		} catch (IOException e) {
			throw new FileException(e);
		}
	}

	@Override
	public void sync() throws FileException {
		try {
			file.getFD().sync();
		} catch (IOException e) {
			throw new FileException(e);
		}
	}

	private void seekToBlock(int blockNo) throws IOException {
		long blockPos = (long) blockNo * (long) blockSize;

		if (file.getFilePointer() != blockPos) {
			file.seek(blockPos);
		}
	}

	@Override
	public int getBlockCnt() throws FileException {
		try {
			return (int) (file.length() / blockSize);
		} catch (IOException e) {
			throw new FileException(e);
		}
	}
}
