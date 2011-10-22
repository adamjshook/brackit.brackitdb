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
package org.brackit.server.io.manager.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.buffer.impl.TQBuffer;
import org.brackit.server.io.file.BlockSpace;
import org.brackit.server.io.file.BlockSpaceMockup;
import org.brackit.server.io.file.StoreException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.tx.log.Log;

/**
 * @author Sebastian Baechle
 * 
 */
public class BufferMgrMockup implements BufferMgr {
	private static final Logger log = Logger.getLogger(BufferMgrMockup.class
			.getName());

	/**
	 * Bit mask for the container number = 11111111 00000000 00000000 00000000
	 */
	private static final int CONTAINER_MASK = -16777216;

	/**
	 * Maps a containerID to a specific buffer manager
	 */
	private HashMap<Integer, Buffer> bufferMapping;

	private Log transactionLog;

	public BufferMgrMockup(Log transactionLog) {
		this.bufferMapping = new HashMap<Integer, Buffer>();
		this.transactionLog = transactionLog;
	}

	@Override
	public void createBuffer(int bufferSize, int pageSize, int containerID,
			String containerName, int initialContainerSize,
			int extendContainerSize) throws BufferException {
		synchronized (this) {
			if (bufferMapping.containsKey(containerID)) {
				throw new BufferException(
						"A container with ID %s already exists.", containerID);
			} else {
				// reserve containerID
				bufferMapping.put(containerID, null);
			}
		}

		Buffer buffer = null;

		BlockSpace blockSpace = new BlockSpaceMockup(containerName, containerID);
		double extRatio = (double) extendContainerSize
				/ (double) initialContainerSize;
		try {
			blockSpace.create(pageSize, initialContainerSize, extRatio);
		} catch (StoreException e) {
			throw new BufferException(e);
		}
		buffer = new TQBuffer(blockSpace, bufferSize, transactionLog, this);// new
		// SlimBuffer(blockSpace,
		// bufferSize,
		// txLog,
		// this);

		synchronized (this) {
			bufferMapping.put(containerID, buffer);
		}

		log.info(String.format(
				"Buffer for container '%s' successfully initialized",
				containerName));
	}

	@Override
	public Buffer getBuffer(PageID pageID) throws BufferException {
		int containerID = (pageID != null) ? pageID.getContainerNo() : 0;

		synchronized (this) {
			Buffer buffer = bufferMapping.get(containerID);

			if (buffer == null) {
				throw new BufferException(
						"No buffer associated with page number %s.", pageID);
			}

			return buffer;
		}
	}

	@Override
	public Buffer getBuffer(int containerID) throws BufferException {
		synchronized (this) {
			Buffer buffer = bufferMapping.get(containerID);

			if (buffer == null) {
				throw new BufferException(
						"No buffer associated with container ID %s.",
						containerID);
			}

			return buffer;
		}
	}

	@Override
	public synchronized Collection<Buffer> getBuffers() {
		return bufferMapping.values();
	}

	@Override
	public void stopBuffer(int containerID, boolean force)
			throws BufferException {
		synchronized (this) {
			Buffer buffer = bufferMapping.get(containerID);

			if (buffer == null) {
				throw new BufferException(
						"A container with ID %s does not exist.", containerID);
			} else {
				shutdownBuffer(containerID, buffer, force);
				bufferMapping.remove(containerID);
			}
		}
	}

	@Override
	public void shutdown() throws BufferException {
		synchronized (this) {
			for (Entry<Integer, Buffer> entry : bufferMapping.entrySet()) {
				Integer containerID = entry.getKey();
				Buffer buffer = entry.getValue();
				shutdownBuffer(containerID, buffer, true);
			}
		}
	}

	private synchronized void shutdownBuffer(Integer containerID,
			Buffer buffer, boolean force) throws BufferException {
		log.info(String.format("Initiated shutdown of container '%s'.",
				containerID));

		buffer.shutdown(force);

		log.info(String.format("Container '%s' shut down.", containerID));
	}

	@Override
	public void startBuffer(int bufferSize, int containerID,
			String containerDir) throws BufferException {
		throw new BufferException("Not implemented.");
	}

	public synchronized void dropBuffer(int containerID) {
		log.info(String
				.format("Initiated drop of container '%s'.", containerID));

		bufferMapping.remove(containerID);

		log.info(String.format("Container '%s' shut down.", containerID));
	}

	@Override
	public synchronized long checkMinRedoLSN() {
		long minRedoLSN = Long.MAX_VALUE;

		for (Buffer buffer : getBuffers()) {
			minRedoLSN = Math.min(minRedoLSN, buffer.checkMinRedoLSN());
		}

		return minRedoLSN;
	}

	@Override
	public synchronized void syncAll() throws BufferException {
		for (Buffer buffer : bufferMapping.values()) {
			buffer.sync();
		}
	}

	@Override
	public void start() throws BufferException {
	}
}
