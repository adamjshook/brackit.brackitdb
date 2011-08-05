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

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.buffer.impl.TQBuffer;
import org.brackit.server.io.file.BlockSpace;
import org.brackit.server.io.file.DefaultBlockSpace;
import org.brackit.server.io.file.StoreException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.procedure.InfoContributor;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.server.procedure.statistics.ListBuffers;
import org.brackit.server.tx.log.Log;
import org.brackit.xquery.util.Cfg;

/**
 * @author Sebastian Baechle
 * 
 */
public class SlimBufferMgr implements BufferMgr, InfoContributor {
	private static final Logger log = Logger.getLogger(SlimBufferMgr.class
			.getName());

	private static final String STORAGE_ROOT = "org.brackit.server.io.root";

	/**
	 * Maps a containerID to a specific buffer manager
	 */
	private Buffer[] bufferMapping;

	private Log transactionLog;

	private final String storeDir = Cfg.asString(STORAGE_ROOT, "container");

	public SlimBufferMgr(Log transactionLog) {
		this.bufferMapping = new Buffer[256];
		this.transactionLog = transactionLog;
		ProcedureUtil.register(ListBuffers.class, this);
	}

	@Override
	public synchronized void createBuffer(int bufferSize, int pageSize,
			int containerID, String containerFile, int initialContainerSize,
			int extendContainerSize) throws BufferException {
		try {
			if (bufferMapping[containerID] != null) {
				throw new BufferException(
						"A container with ID %s already exists.", containerID);
			}

			File file = new File(containerFile);
			if (!file.isAbsolute()) {
				file = new File(storeDir + File.separator + containerFile);
			}
			BlockSpace blockSpace = new DefaultBlockSpace(file.toString(),
					containerID);
			double extRatio = (double) extendContainerSize
					/ (double) initialContainerSize;
			blockSpace.create(pageSize, initialContainerSize, extRatio);
			Buffer buffer = new TQBuffer(blockSpace, bufferSize,
					transactionLog, this);
			bufferMapping[containerID] = buffer;

			Container cnt = new Container(file, containerID, bufferSize,
					pageSize, initialContainerSize, extendContainerSize);
			cnt.write();

			log.info(String.format(
					"Buffer for container '%s' successfully initialized",
					containerFile));
		} catch (StoreException e) {
			log.error(String.format("Error creating container file '%s'.",
					containerFile), e);
			throw new BufferException(e,
					"Could not create container file '%s'.", containerFile);
		}
	}

	@Override
	public Buffer getBuffer(PageID pageID) throws BufferException {
		// TODO fix container selection
		int containerID = (pageID != null) ? pageID.getContainerNo() : 0;

		Buffer buffer = bufferMapping[containerID];

		if (buffer == null) {
			throw new BufferException(
					"No buffer associated with page number %s.", pageID);
		}

		return buffer;
	}

	@Override
	public Buffer getBuffer(int containerID) throws BufferException {
		Buffer buffer = bufferMapping[containerID];

		if (buffer == null) {
			throw new BufferException(
					"No buffer associated with container ID %s.", containerID);
		}

		return buffer;
	}

	@Override
	public synchronized Collection<Buffer> getBuffers() {
		ArrayList<Buffer> buffers = new ArrayList<Buffer>();
		for (Buffer buffer : bufferMapping) {
			if (buffer != null) {
				buffers.add(buffer);
			}
		}
		return buffers;
	}

	@Override
	public synchronized void stopBuffer(int containerID, boolean force)
			throws BufferException {
		Buffer buffer = bufferMapping[containerID];

		if (buffer == null) {
			throw new BufferException("A container with ID %s does not exist.",
					containerID);
		} else {
			bufferMapping[containerID] = null;
			shutdownBuffer(containerID, buffer, force);
		}
	}

	@Override
	public synchronized void shutdown() throws BufferException {
		ProcedureUtil.deregister(ListBuffers.class, this);
		for (int containerID = 0; containerID < bufferMapping.length; containerID++) {
			Buffer buffer = bufferMapping[containerID];
			if (buffer != null) {
				bufferMapping[containerID] = null;
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
	public synchronized void startBuffer(int bufferSize, int containerID,
			String containerFile) throws BufferException {
		if (bufferMapping[containerID] != null) {
			throw new BufferException("A container with ID %s already exists.",
					containerID);
		}

		BlockSpace blockSpace = new DefaultBlockSpace(containerFile,
				containerID);
		Buffer buffer = new TQBuffer(blockSpace, bufferSize, transactionLog,
				this);

		bufferMapping[containerID] = buffer;

		log.info(String.format(
				"Buffer for container '%s' successfully initialized",
				containerFile));
	}

	@Override
	public synchronized long checkMinRedoLSN() {
		long minRedoLSN = Long.MAX_VALUE;

		for (int containerID = 0; containerID < bufferMapping.length; containerID++) {
			Buffer buffer = bufferMapping[containerID];
			if (buffer != null) {
				minRedoLSN = Math.min(minRedoLSN, buffer.checkMinRedoLSN());
			}
		}

		return minRedoLSN;
	}

	@Override
	public synchronized void syncAll() throws BufferException {
		for (int containerID = 0; containerID < bufferMapping.length; containerID++) {
			Buffer buffer = bufferMapping[containerID];
			if (buffer != null) {
				buffer.sync();
			}
		}
	}

	@Override
	public synchronized String getInfo() {
		StringBuilder out = new StringBuilder();

		for (int containerID = 0; containerID < bufferMapping.length; containerID++) {
			Buffer buffer = bufferMapping[containerID];
			if (buffer != null) {
				if (containerID < 10)
					out.append(" ");
				out.append("#" + containerID);
				out.append(", " + buffer.getBufferSize() + " pages");
				out.append(" with " + buffer.getPageSize() + "B");
				out.append(", buffer hit ratio " + buffer.getHitCount());
				out.append(", fault ratio " + buffer.getMissCount());
				out.append("\n");
			}
		}
		return out.toString();
	}

	@Override
	public synchronized void start() throws BufferException {
		File dir = new File(storeDir);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new BufferException(
						"Could not create store root directory '%s'", dir);
			}
			return;
		}
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return ((dir.isDirectory()) && (name.endsWith(".cnt")));
			}
		};
		for (File cntDir : new File(storeDir).listFiles(filter)) {
			try {
				Container cnt = new Container(cntDir);
				startBuffer(cnt.getBufSize(), cnt.getCntID(), cnt.getDir()
						.toString());
			} catch (BufferException e) {
				log.error("Could not start container", e);
				throw e;
			}
		}
	}

	@Override
	public int getInfoID() {
		return InfoContributor.NO_ID;
	}
}
