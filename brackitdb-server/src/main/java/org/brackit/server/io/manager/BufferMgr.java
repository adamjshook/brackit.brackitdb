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
package org.brackit.server.io.manager;

import java.util.Collection;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;

/**
 * @author Sebastian Baechle
 * 
 */
public interface BufferMgr {
	/**
	 * Returns the buffer for the given container ID.
	 */
	public Buffer getBuffer(int containerID) throws BufferException;

	/**
	 * Returns the buffer for the given page number.
	 */
	public Buffer getBuffer(PageID pageID) throws BufferException;

	/**
	 * Adds and starts a new buffer of the given file for an existing container.
	 */
	public void startBuffer(int bufferSize, int containerID,
			String containerDir) throws BufferException;

	/**
	 * Stops the buffer of the given container.
	 */
	public void stopBuffer(int containerID, boolean force)
			throws BufferException;

	/**
	 * Creates and starts a new buffer of the given size for the provided
	 * container file.
	 */
	public void createBuffer(int bufferSize, int pageSize, int containerID,
			String containerName, int iniSize, int extSize)
			throws BufferException;

	/**
	 * Start buffers for all existing containers
	 */
	public void start() throws BufferException;

	/**
	 * Shutdown all associated buffers.
	 */
	public void shutdown() throws BufferException;

	/**
	 * Returns all current buffers
	 */
	public Collection<Buffer> getBuffers();

	/**
	 * Returns the minimum redo LSN of all buffers.
	 */
	public long checkMinRedoLSN();

	/**
	 * Force all buffers to sync out metadata information.
	 */
	public void syncAll() throws BufferException;
}
