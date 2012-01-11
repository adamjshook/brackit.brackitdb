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
package org.brackit.server.io.file;

/**
 * Supports block-oriented access, using LBA's (logical block address, derived
 * from page number), to the storage system. A BlockSpace is identified by its
 * id and characterized by its blkSize.
 * 
 * @author Ou Yi
 * 
 */
public interface BlockSpace {

	void create(int blkSize, int iniSize, double extent) throws StoreException;

	void open() throws StoreException;

	void close() throws StoreException;

	/**
	 * allocates a block: marking a free block as used
	 * 
	 * @param lba
	 *            The requested logical block number. If lba < 0, the BlockSpace
	 *            implementation may choose the to-be-allocated block freely
	 *            (Normally the next free block from the beginning of the
	 *            BlockSpace).
	 * @return the logical block address (lba) of the newly allocated block
	 */
	int allocate(int lba) throws StoreException;

	/**
	 * the opposite of allocate: marking the block identified by the lba as
	 * free.
	 * 
	 * @param lba
	 */
	void release(int lba) throws StoreException;

	int read(int lba, byte[] buffer, int numBlocks) throws StoreException;

	void write(int lba, byte[] buffer, int numBlocks) throws StoreException;

	/**
	 * Returns the length of the block header in bytes
	 * 
	 * @return
	 */
	int sizeOfHeader();

	int sizeOfBlock() throws StoreException;

	int getId();

	boolean isUsed(int lba);

	boolean isClosed();

	void sync() throws StoreException;

}
