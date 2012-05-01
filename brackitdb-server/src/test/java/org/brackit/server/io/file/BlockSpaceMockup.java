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

import java.util.HashMap;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BlockSpaceMockup implements BlockSpace {
	static final int BLOCK_HEADER_LENGTH = 1;

	private String filename;

	private int containerNo;

	private int maxBlockNo;

	private int blockSize;

	private HashMap<Integer, byte[]> blocks;

	private boolean closed;

	public BlockSpaceMockup(String containerFilename, int containerNo) {
		super();
		this.filename = containerFilename;
		this.containerNo = containerNo;
		this.closed = true;
	}

	@Override
	public int allocate(int blockNo, int unitID, boolean force) throws StoreException {
		if (blockNo < 0) {
			blockNo = maxBlockNo++;
		} else {
			if (blocks.containsKey(blockNo)) {
				throw new StoreException(String.format(
						"Block %s is already in use.", blockNo));
			}

			maxBlockNo = Math.max(maxBlockNo, blockNo);
		}

		blocks.put(blockNo, new byte[blockSize]);

		return blockNo;
	}

	@Override
	public void close() throws StoreException {
		closed = true;
	}

	@Override
	public void create(int blkSize, int iniSize, double extent)
			throws StoreException {
		this.blockSize = blkSize;
		this.maxBlockNo = 1; // avoid block 0
		this.blocks = new HashMap<Integer, byte[]>();
	}

	@Override
	public int getId() {
		return containerNo;
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public boolean isUsed(int blockNo) {
		byte[] myBlock = blocks.get(blockNo);

		return (myBlock != null);
	}

	@Override
	public void open() throws StoreException {
		this.closed = false;
	}

	@Override
	public int read(int blockNo, byte[] buffer, int numBlocks)
			throws StoreException {
		int offset = 0;

		for (int i = 0; i < numBlocks; i++) {
			byte[] myBlock = blocks.get(blockNo + i);
			if (myBlock == null) {
				if (i == 0) {
					throw new StoreException(String.format(
							"Block %s is unused.", blockNo));
				}
				return i;
			}
			System.arraycopy(myBlock, 0, buffer, offset, myBlock.length);
			offset += blockSize;
		}
		return numBlocks;
	}

	@Override
	public void release(int blockNo, int hintUnitID, boolean force) throws StoreException {
		byte[] myBlock = blocks.get(blockNo);

		if (myBlock == null) {
			throw new StoreException(String.format("Block %s is unused.",
					blockNo));
		}

		blocks.remove(blockNo);
	}

	@Override
	public int sizeOfBlock() throws StoreException {
		return blockSize;
	}

	@Override
	public int sizeOfHeader() {
		return BLOCK_HEADER_LENGTH;
	}

	@Override
	public void write(int blockNo, byte[] buffer, int numBlocks)
			throws StoreException {
		int offset = 0;
		for (int i = 0; i < numBlocks; i++) {
			byte[] myBlock = blocks.get(blockNo + i);

			if (myBlock == null) {
				throw new StoreException(String.format("Block %s is unused.",
						blockNo + i));
			}

			System.arraycopy(buffer, offset, myBlock, 0, myBlock.length);
			offset += blockSize;
		}
	}

	@Override
	public void sync() throws StoreException {
	}

	@Override
	public int createUnit(int unitID) throws StoreException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void dropUnit(int unitID) throws StoreException {
		// TODO Auto-generated method stub
		
	}
}
