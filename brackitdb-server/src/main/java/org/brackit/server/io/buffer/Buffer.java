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
package org.brackit.server.io.buffer;

import java.io.PrintStream;

import org.brackit.server.tx.Tx;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public interface Buffer {

	public int getBufferSize();

	public int getContainerNo();

	public Handle allocatePage(Tx tx) throws BufferException;

	public Handle allocatePage(Tx tx, PageID pageID, boolean logged,
			long undoNextLSN) throws BufferException;

	public void deletePage(Tx tx, PageID pageID, boolean logged,
			long undoNextLSN) throws BufferException;

	public Handle fixPage(Tx tx, PageID pageID) throws BufferException;

	public void unfixPage(Handle handle) throws BufferException;

	public void clear() throws BufferException;

	public void flush() throws BufferException;

	public void flush(Handle handle) throws BufferException;

	public void flushAssigned(Tx tx) throws BufferException;

	public long checkMinRedoLSN();

	public void sync() throws BufferException;

	public int getFixCount();

	public int getHitCount();

	public int getMissCount();

	public void resetCounters();

	public void printStatus(PrintStream out);

	public void shutdown(boolean force) throws BufferException;

	public int getPageSize();

	public boolean isFixed(Handle handle);
}