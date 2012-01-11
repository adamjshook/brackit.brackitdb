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
package org.brackit.server.io.buffer.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.buffer.log.PageLogOperationHelper;
import org.brackit.server.io.file.BlockSpace;
import org.brackit.server.io.file.StoreException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.procedure.InfoContributor;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.server.procedure.statistics.ListBuffer;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxID;
import org.brackit.server.tx.log.Log;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.thread.ThreadCB;

/**
 * @author Sebastian Baechle
 */
public abstract class AbstractBuffer implements Buffer, InfoContributor {
	private static final Logger log = Logger.getLogger(AbstractBuffer.class);

	protected static abstract class Frame extends Handle {

		public Frame(int pageSize) {
			super(pageSize);
		}

		abstract void drop();

		abstract void prefetched();

		abstract void fix();

		abstract void unfix();

		abstract boolean isFixed();

		abstract int fixCount();
	}

	protected static Comparator<Frame> PAGEID_COMPARATOR = new Comparator<Frame>() {
		@Override
		public int compare(Frame o1, Frame o2) {
			PageID o1P = o1.getPageID();
			PageID o2P = o2.getPageID();
			if (o1P == null) {
				if (o2P == null) {
					return o1.hashCode() < o2.hashCode() ? -1
							: o1.hashCode() == o2.hashCode() ? 0 : 1;
				}
				return -1;

			} else if (o2 == null) {
				return 1;
			}
			return o1P.compareTo(o2P);
		}
	};

	protected static Comparator<Frame> REDO_COMPARATOR = new Comparator<Frame>() {
		@Override
		public int compare(Frame o1, Frame o2) {
			long o1RLSN = o1.getRedoLSN();
			long o2RLSN = o2.getRedoLSN();
			return (o1RLSN < o2RLSN) ? -1 : (o1RLSN == o2RLSN) ? 0 : 1;
		}
	};

	/**
	 * Debug switch
	 */
	private final static boolean DEBUG = false;

	/**
	 * Debug switch for page fixes
	 */
	private final static boolean FIX_DEBUG = false;

	// buffer management
	/**
	 * Contains the mapping of page number to buffer position
	 */
	private final HashMap<PageID, Frame> pageNoToFrame;

	// Initilization-dependent fields
	/**
	 * Size of a buffer page in bytes
	 */
	private int pageSize;

	/**
	 * Number of maintained buffer positions
	 */
	private final int bufferSize;

	/**
	 * {@link org.brackit.server.io.buffer.BufferMgr.BufferManager} for Logging
	 * purposes
	 */
	private final BufferMgr bufferMgr;

	/**
	 * Helper for creating log entries
	 */
	private final PageLogOperationHelper loggableHelper;

	private final Log transactionLog;

	private final BlockSpace blockSpace;

	private final int prefetchSize;

	private final byte[] prefetchBuffer;

	private final int writeSize;

	private final byte[] writeBuffer;

	private final List<Frame> pool;

	private int unfixCnt;

	private int fixCnt;

	private int hitCnt;

	private int faultCnt;

	public AbstractBuffer(BlockSpace blockSpace, int bufferSize,
			Log transactionLog, BufferMgr bufferMgr) throws BufferException {
		this.bufferMgr = bufferMgr;
		this.loggableHelper = new PageLogOperationHelper(bufferMgr);
		this.transactionLog = transactionLog;
		this.blockSpace = blockSpace;
		this.bufferSize = bufferSize;
		this.pageNoToFrame = new HashMap<PageID, Frame>();
		this.pool = new ArrayList<Frame>(bufferSize);
		// open first to determine page size
		open();
		this.prefetchSize = 10;
		this.prefetchBuffer = new byte[prefetchSize * pageSize];
		this.writeSize = 40;
		this.writeBuffer = new byte[writeSize * pageSize];
		ProcedureUtil.register(ListBuffer.class, this);
	}

	protected abstract Frame shrink();

	protected abstract Frame grow(int pageSize);

	public synchronized void open() throws BufferException {
		if (!blockSpace.isClosed()) {
			throw new BufferException("Buffer is already opened");
		}
		try {
			blockSpace.open();
			pageSize = blockSpace.sizeOfBlock();
		} catch (StoreException e) {
			throw new BufferException(e);
		}
	}

	public synchronized Handle fixPage(Tx transaction, PageID pageID)
			throws BufferException {
		Frame requested = pageNoToFrame.get(pageID);

		if (requested == null) {
			faultCnt++;
			requested = load(pageID);
		} else {
			hitCnt++;
		}

		// fix handle and update statistics
		requested.fix();
		fixCnt++;
		if (FIX_DEBUG) {
			ThreadCB.get().registerFix(pageID);
		}

		return requested;
	}

	public synchronized void unfixPage(Handle handle) throws BufferException {
		if (FIX_DEBUG) {
			ThreadCB.get().registerUnfix(handle.getPageID());
		}
		((Frame) handle).unfix();
		unfixCnt++;
	}

	public synchronized void flush() throws BufferException {
		flushInternal(null);
	}

	public synchronized void flushAssigned(Tx transaction)
			throws BufferException {
		flushInternal(transaction);
	}

	public synchronized void flush(Handle handle) throws BufferException {
		if (!handle.isModified()) {
			return;
		}
		List<Frame> toFlush = buildRun((Frame) handle);
		try {
			flushRun(toFlush);
		} finally {
			for (Frame f : toFlush) {
				if (f != handle) {
					f.unlatch();
				}
			}
		}
	}

	@Override
	public void clear() throws BufferException {
		log.warn("Warning: buffer clear is not concurrency proof yet.");

		flush();

		synchronized (this) {
			pageNoToFrame.clear();
			pool.clear();
		}
	}

	public synchronized long checkMinRedoLSN() {
		if (pool.isEmpty()) {
			return Long.MAX_VALUE;
		}
		Frame frame = Collections.min(new ArrayList<Frame>(pool),
				REDO_COMPARATOR);
		return frame.getRedoLSN();
	}

	public synchronized Handle allocatePage(Tx transaction)
			throws BufferException {
		return allocatePage(transaction, null, true, -1);
	}

	public synchronized Handle allocatePage(Tx transaction, PageID pageID,
			boolean logged, long undoNextLSN) throws BufferException {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Allocating page %s.", pageID));
		}

		if (pageNoToFrame.containsKey(pageID)) {
			throw new BufferException("Page %s is already loaded into buffer",
					pageID);
		}

		Frame victim = allocateFrames(1).get(0);
		long LSN = -1;

		try {
			evict(victim);
			pageID = allocateBlock(pageID);

			if (logged) {
				try {
					if (undoNextLSN == -1) {
						LSN = transaction.logUpdate(loggableHelper
								.createAllocateLogOp(pageID));
					} else {
						LSN = transaction.logCLR(loggableHelper
								.createAllocateLogOp(pageID), undoNextLSN);
					}
				} catch (TxException e) {
					throw new BufferException(
							"Could not write log for page allocation.", e);
				}
			}
		} catch (BufferException e) {
			// victim is free simply kick the page out
			pool.remove(victim);
			throw e;
		}

		if (victim.getAssignedTo() != null)
			throw new RuntimeException();

		victim.fix();
		victim.init(pageID);
		victim.setLSN(LSN);
		// update page mapping
		if (pageNoToFrame.put(pageID, victim) != null)
			throw new RuntimeException(pageID.toString());
		// new page is always "modified" and not in sync with external storage
		victim.setModified(true);

		fixCnt++;
		if (FIX_DEBUG) {
			ThreadCB.get().registerFix(victim.getPageID());
		}

		return victim;
	}

	public synchronized void deletePage(Tx transaction, PageID pageID,
			boolean logged, long undoNextLSN) throws BufferException {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Deleting page %s.", pageID));
		}

		if (logged) {
			try {
				if (undoNextLSN == -1) {
					transaction.logUpdate(loggableHelper
							.createDeallocateLogOp(pageID));
				} else {
					transaction.logCLR(loggableHelper
							.createDeallocateLogOp(pageID), undoNextLSN);
				}
			} catch (TxException e) {
				throw new BufferException(
						"Could not write log for page deallocation.", e);
			}
		}

		Frame frame = pageNoToFrame.remove(pageID);
		if (frame != null) {
			// The handle of a deleted page is allowed to be fixed
			// by concurrent threads. However, the safe flag should be
			// used in this case to signal them that they
			// must not use the handle anymore.
			// To avoid any change for corruption we drop the handle!
			frame.drop();
			pool.remove(frame);
		}
		deallocateBlock(pageID);
	}

	private int readBlocks(PageID pageID, byte[] buffer, int numOfBlocks)
			throws BufferException {
		if (log.isTraceEnabled()) {
			log.trace(String.format(
					"Reading run of size %s starting with block %s of page %s",
					numOfBlocks, pageID.getBlockNo(), pageID));
		}
		try {
			return blockSpace.read(pageID.getBlockNo(), buffer, numOfBlocks);
		} catch (StoreException e) {
			throw new BufferException(
					e,
					"Reading run of size %s starting with block %s of page %s failed",
					numOfBlocks, pageID.getBlockNo(), pageID);
		}
	}

	private void writeBlocks(PageID pageID, byte[] buffer, int numOfBlocks)
			throws BufferException {
		if (log.isTraceEnabled()) {
			log.trace(String.format(
					"Writing %s blocks [%s-%s] starting with block of page %s",
					numOfBlocks, pageID.getBlockNo(), pageID.getBlockNo()
							+ numOfBlocks - 1, pageID));
		}
		try {
			blockSpace.write(pageID.getBlockNo(), writeBuffer, numOfBlocks);
		} catch (StoreException e) {
			throw new BufferException(e,
					"Writing %s blocks [%s-%s] starting with block of page %s",
					numOfBlocks, pageID.getBlockNo(), pageID.getBlockNo()
							+ numOfBlocks - 1, pageID);
		}
	}

	private PageID allocateBlock(PageID pageID) throws BufferException {
		int blockNo = (pageID != null) ? pageID.getBlockNo() : -1;
		if (log.isTraceEnabled()) {
			log.trace(String.format("Allocating block %s of page %s", blockNo,
					pageID));
		}
		try {
			int allocatedBlockNo = blockSpace.allocate(blockNo);
			PageID allocatedPageID = new PageID(getContainerNo(),
					allocatedBlockNo);
			if ((pageID == null) && log.isTraceEnabled()) {
				log.trace(String.format("Allocated block %s of page %s",
						allocatedBlockNo, allocatedPageID));
			}
			return allocatedPageID;
		} catch (StoreException e) {
			throw new BufferException(e,
					"Allocating block %s of page %s failed", blockNo, pageID);
		}
	}

	private void deallocateBlock(PageID pageID) throws BufferException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Releasing block %s of page %s", pageID
					.getBlockNo(), pageID));
		}
		try {
			blockSpace.release(pageID.getBlockNo());
		} catch (StoreException e) {
			throw new BufferException(e,
					"Releasing block %s of page %s failed",
					pageID.getBlockNo(), pageID);
		}
	}

	private void transferToFrames(PageID firstPageID, byte[] buffer,
			List<Frame> frames) {
		int containerNo = firstPageID.getContainerNo();
		int currentBlockNo = firstPageID.getBlockNo();
		int offset = 0;

		for (Frame frame : frames) {
			// TODO remove
			if (frame.getAssignedTo() != null)
				throw new RuntimeException();

			PageID pID = new PageID(containerNo, currentBlockNo);
			frame.init(pID);
			System.arraycopy(buffer, offset, frame.page, 0, pageSize);
			currentBlockNo++;
			offset += pageSize;
		}
	}

	private void transferToBuffer(List<Frame> frames, byte[] buffer) {
		int offset = 0;
		for (Frame frame : frames) {
			System.arraycopy(frame.page, 0, buffer, offset, pageSize);
			// mark block as used
			buffer[offset] = 1;
			offset += pageSize;
		}
	}

	private List<Frame> allocateFrames(int noOfFrames) throws BufferException {
		List<Frame> frames = new ArrayList<Frame>(noOfFrames);
		int allocated = 0;
		Frame frame;

		while ((allocated < noOfFrames) && (pool.size() < bufferSize)) {
			frame = grow(pageSize);
			pool.add(frame);
			allocated++;
			// handle is not fixed and calling method must be synchronized ->
			// deadlock cannot occur
			frame.latchX();
			frames.add(frame);
		}

		while ((allocated < noOfFrames) && ((frame = shrink()) != null)) {
			allocated++;
			// handle is not fixed and calling method must be synchronized ->
			// deadlock cannot occur
			frame.latchX();
			frames.add(frame);
		}

		if (allocated == 0) {
			// no page found -> all pages fixed
			throw new BufferException("No free buffer position avaliable.");
		}

		// TODO remove
		HashSet<Frame> s = new HashSet<Frame>();
		for (Frame f : frames) {
			if (!s.add(f))
				throw new RuntimeException();
			if (f.isFixed())
				throw new RuntimeException();
		}

		if (log.isTraceEnabled()) {
			StringBuilder buf = new StringBuilder();
			buf.append("Allocated " + frames.size() + " frames:");
			for (Frame f : frames) {
				buf.append(" " + f.getPageID());
			}
			log.trace(buf);
		}

		return frames;
	}

	protected List<Frame> buildRun(Frame frame) {
		List<Frame> run = new ArrayList<Frame>();
		run.add(frame);
		PageID start = frame.getPageID();
		PageID current = new PageID(start.value() + 1);
		Frame tmp;
		while ((run.size() < writeSize)
				&& ((tmp = pageNoToFrame.get(current)) != null)
				&& (tmp.latchSC())) {
			if (!tmp.isModified()) {
				tmp.unlatch();
				break;
			}
			run.add(tmp);
			current = new PageID(current.value() + 1);
		}
		tmp = null;
		current = new PageID(start.value() - 1);
		while ((run.size() < writeSize)
				&& ((tmp = pageNoToFrame.get(current)) != null)
				&& (tmp.latchSC())) {
			if (!tmp.isModified()) {
				tmp.unlatch();
				break;
			}
			run.add(0, tmp);
			current = new PageID(current.value() - 1);
			start = current;
		}

		if (log.isTraceEnabled()) {
			StringBuilder buf = new StringBuilder();
			buf.append("Build run of " + run.size() + " frames:");
			for (Frame f : run) {
				buf.append(" " + f.getPageID());
			}
			log.trace(buf);
		}
		return run;
	}

	private Frame load(PageID pageID) throws BufferException {
		// read chunk from disk into read buffer
		int containerNo = pageID.getContainerNo();
		int prefetchBlockNo = pageID.getBlockNo() + 1;
		int maxFetchSize = 1;
		while ((maxFetchSize < prefetchSize)
				&& (!pageNoToFrame.containsKey(new PageID(containerNo,
						prefetchBlockNo++))))
			maxFetchSize++;
		int fetched = readBlocks(pageID, prefetchBuffer, maxFetchSize);

		// allocate frames and load buffer into them
		List<Frame> frames = allocateFrames(fetched);
		try {
			evict(frames);
			transferToFrames(pageID, prefetchBuffer, frames);
		} catch (BufferException e) {
			// frames are free: simply kick all out
			for (Frame frame : frames) {
				pool.remove(frame);
			}
			throw e;
		}

		// map loaded frames in buffer and
		// mark as prefetched
		Frame requested = null;
		for (Frame frame : frames) {
			if (requested == null) {
				requested = frame;
			}
			if (pageNoToFrame.put(frame.getPageID(), frame) != null)
				throw new RuntimeException(frame.getPageID().toString());
			frame.prefetched();
			frame.unlatch();
		}
		return requested;
	}

	private void evict(Frame victim) throws BufferException {
		PageID oldPageID = victim.getPageID();

		if (oldPageID != null) {
			if (victim.isModified()) {
				flush(victim);
			}
			// unmap clean pages directly
			pageNoToFrame.remove(oldPageID);
		}
	}

	private void evict(List<Frame> frames) throws BufferException {
		List<Frame> toFlush = null;
		for (Frame frame : frames) {
			if (frame.getPageID() == null) {
				// TODO remove
				if (frame.isModified())
					throw new RuntimeException();
				continue;
			}

			if (frame.isModified()) {
				if (toFlush == null) {
					toFlush = new ArrayList<Frame>();
				}
				toFlush.add(frame);
			} else {
				// TODO remove
				if (frame.getAssignedTo() != null) {
					System.out.println(frame.isFixed());
					throw new RuntimeException();
				}

				// unmap clean page directly
				pageNoToFrame.remove(frame.getPageID());
			}
		}

		if (toFlush != null) {
			flush(toFlush);

			// remove now also the flushed pages
			// from the mapping and
			// unlink them from transactions
			for (Frame flushed : toFlush) {
				pageNoToFrame.remove(flushed.getPageID());
			}
		}
	}

	private void flushInternal(Tx tx) throws BufferException {
		List<Frame> frames = new ArrayList<Frame>(pool);
		List<Frame> toFlush = new ArrayList<Frame>();

		while (!frames.isEmpty()) {
			toFlush.clear();
			int pos = 0;
			int size = frames.size();
			while (pos < size) {
				Frame frame = frames.get(pos);
				if (toFlush.isEmpty()) {
					// latch first page unconditionally to avoid starvation
					frame.latchS();
				} else if (!frame.latchSC()) {
					// we did not get the latch:
					// skip page to avoid a deadlock and retry later
					pos++;
					continue;
				}

				if (frame.isModified()) {
					if (tx == null) {
						toFlush.add(frame);
					} else if (frame.isAssignedTo(tx)) {
						toFlush.add(frame);
						frame.setAssignedTo(null);
					} else {
						frame.unlatch();
					}
				} else {
					if ((tx != null) && (frame.isAssignedTo(tx))) {
						frame.setAssignedTo(null);
					}
					frame.unlatch();
				}
				frames.remove(pos);
				size--;
			}
			try {
				flush(toFlush);
			} finally {
				for (Frame frame : toFlush) {
					frame.unlatch();
				}
			}
		}
	}

	private void flush(List<Frame> frames) throws BufferException {
		if (frames.isEmpty()) {
			return;
		}
		Collections.sort(frames, PAGEID_COMPARATOR);

		if (log.isTraceEnabled()) {
			StringBuilder buf = new StringBuilder();
			buf.append("Flushing " + frames.size() + " frames:");
			for (Frame f : frames) {
				buf.append(" " + f.getPageID() + (f.isModified() ? "*" : ""));
			}
			log.trace(buf);
		}

		flushLog(frames);

		ArrayList<Frame> run = new ArrayList<Frame>();

		int i = 0;
		int runSize = 0;
		int prevBlockNo = -1;
		for (Frame frame : frames) {
			int blockNo = frame.getPageID().getBlockNo();

			if ((runSize > 0)
					&& ((runSize == writeSize) || (prevBlockNo + 1 != blockNo))) {
				flushRun(run);
				run.clear();
				runSize = 0;
			}
			run.add(frame);
			runSize++;
			prevBlockNo = blockNo;
		}

		if (runSize > 0) {
			flushRun(run);
		}
	}

	private long flushLog(List<Frame> frames) throws BufferException {
		// flush log once for maximum LSN of all frames
		long maxLSN = Long.MIN_VALUE;
		for (Frame frame : frames) {
			maxLSN = Math.max(maxLSN, frame.getLSN());
		}
		try {
			transactionLog.flush(maxLSN);
			return maxLSN;
		} catch (LogException e) {
			throw new BufferException(e, "Flushing log failed.");
		}
	}

	private void flushRun(List<Frame> run) throws BufferException {
		PageID firstPageID = run.get(0).getPageID();

		if (log.isTraceEnabled()) {
			StringBuilder buf = new StringBuilder();
			buf.append("Flushing run of " + run.size() + " frames starting at "
					+ firstPageID + ":");
			for (Frame f : run) {
				buf.append(" " + f.getPageID() + (f.isModified() ? "*" : ""));
			}
			log.trace(buf);
		}

		// System.err.println("Before: ");
		// checkBuffer();

		transferToBuffer(run, writeBuffer);
		writeBlocks(firstPageID, writeBuffer, run.size());

		for (Frame frame : run) {
			// unlink clean pages from a transaction
			frame.setAssignedTo(null);
			frame.setModified(false);
		}

		// System.err.println("After: ");
		// checkBuffer();
	}

	private void checkBuffer() {
		for (Frame frame : pool) {
			System.err.print("Page " + frame.getPageID()
					+ (frame.isModified() ? "* " : " "));
			try {
				byte[] buffer = new byte[pageSize];
				blockSpace.read(frame.getPageID().getBlockNo(), buffer, 1);
				boolean OK = true;
				for (int i = 1; i < pageSize; i++) {
					if (buffer[i] != frame.page[i]) {
						System.err
								.println("is NOT in sync and differs in byte "
										+ i + " with image on disk");
						OK = false;
						break;
					}
				}
				if (OK)
					System.err.println("is in sync");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public int getFixCount() {
		return (fixCnt - unfixCnt);
	}

	public int getHitCount() {
		return hitCnt;
	}

	public synchronized int getMissCount() {
		return faultCnt;
	}

	public synchronized void resetCounters() {
		hitCnt = 0;
		faultCnt = 0;
		fixCnt = 0;
		unfixCnt = 0;
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public int getContainerNo() {
		return blockSpace.getId();
	}

	public synchronized void printStatus(PrintStream out) {
		out.format("Status of buffer : %s", getContainerNo());
		out.println();

		List<Frame> frames = new ArrayList<Frame>(pool);
		Collections.sort(frames, PAGEID_COMPARATOR);
		for (Frame frame : frames) {
			PageID pID = frame.getPageID();
			boolean mapped = pageNoToFrame.containsKey(pID);
			int fID = frame.hashCode();
			long LSN = frame.getLSN();
			boolean dirty = frame.isModified();
			boolean latched = frame.isLatchedS();
			int fixed = frame.fixCount();
			TxID tx = (frame.getAssignedTo() != null) ? frame.getAssignedTo()
					.getID() : null;
			out
					.format(
							"%s FRM=%s MAPPED=%s FIX=%s LATCH=%s LSN=%s DIRTY=%s TX=%s",
							pID, fID, mapped, fixed, latched, LSN, dirty, tx);
			out.println();
		}
	}

	@Override
	public void shutdown(boolean force) throws BufferException {
		flush();

		synchronized (this) {
			if (fixCnt - unfixCnt > 0) {
				if (force) {
					log
							.warn(String
									.format(
											"Closing container '%s' because some pages are still fixed in the buffer.",
											blockSpace.getId()));
				} else {
					throw new BufferException(
							String
									.format(
											"Cannot close container '%s' because some pages are still fixed in the buffer.",
											blockSpace.getId()));
				}
			}

			try {
				blockSpace.close();
			} catch (StoreException e) {
				throw new BufferException(e,
						"Error while closing container file.");
			}
		}
	}

	@Override
	public void sync() throws BufferException {
		try {
			blockSpace.sync();
		} catch (StoreException e) {
			throw new BufferException(e);
		}
	}

	@Override
	public int getPageSize() {
		return pageSize;
	}

	@Override
	public boolean isFixed(Handle handle) {
		return ((Frame) handle).isFixed();
	}

	@Override
	public String getInfo() {
		StringBuilder out = new StringBuilder();
		out.append("#" + getContainerNo());
		out.append(", " + getBufferSize() + " pages");
		out.append(" with " + getPageSize() + "B");
		out.append(", buffer hit ratio " + getHitCount());
		out.append(", fault ratio " + getMissCount());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PrintStream pw = new PrintStream(os);
		printStatus(pw);
		pw.flush();
		BufferedReader rd = new BufferedReader(new InputStreamReader(
				new ByteArrayInputStream(os.toByteArray())));
		try {
			for (String s = rd.readLine(); s != null; s = rd.readLine()) {
				out.append(s);
				out.append("\n");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		out.append("\n");
		return out.toString();
	}

	@Override
	public int getInfoID() {
		return getContainerNo();
	}
}