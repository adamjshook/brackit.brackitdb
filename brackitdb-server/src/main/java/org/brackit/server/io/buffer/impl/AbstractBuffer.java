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
package org.brackit.server.io.buffer.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.buffer.log.AllocateLogOperation;
import org.brackit.server.io.buffer.log.CreateUnitLogOperation;
import org.brackit.server.io.buffer.log.DeferredLogOperation;
import org.brackit.server.io.buffer.log.DeallocateLogOperation;
import org.brackit.server.io.buffer.log.DropUnitLogOperation;
import org.brackit.server.io.buffer.log.PageLogOperation.PageUnitPair;
import org.brackit.server.io.file.BlockSpace;
import org.brackit.server.io.file.StoreException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.tx.PostCommitHook;
import org.brackit.server.tx.PreCommitHook;
import org.brackit.server.tx.PostRedoHook;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxID;
import org.brackit.server.tx.log.Log;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.thread.ThreadCB;
import org.brackit.server.xquery.function.bdb.statistics.InfoContributor;
import org.brackit.server.xquery.function.bdb.statistics.ListBuffer;
import org.brackit.xquery.util.log.Logger;

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

	private final class DeallocateHook implements PreCommitHook,
			PostCommitHook, PostRedoHook {

		private final List<PageUnitPair> pageList;
		private final List<Integer> unitList;

		public DeallocateHook() {
			pageList = new ArrayList<PageUnitPair>();
			unitList = new ArrayList<Integer>();
		}

		public DeallocateHook(PageUnitPair[] pages, int[] units) {
			pageList = Arrays.asList(pages);

			unitList = new ArrayList<Integer>(units.length);
			for (int unit : units) {
				unitList.add(unit);
			}
		}

		public void addPage(PageID pageID, int unitID) {
			pageList.add(new PageUnitPair(pageID, unitID));
		}

		public void addUnit(int unitID) {
			unitList.add(unitID);
		}

		@Override
		public void prepare(Tx tx) throws ServerException {
			// do not physically deallocate the pages in the metadata, but just
			// write the corresponding log records (so that this can be redone
			// in case of a crash)

			PageUnitPair[] pages = pageList.toArray(new PageUnitPair[pageList
					.size()]);
			int[] units = new int[unitList.size()];
			for (int i = 0; i < units.length; i++) {
				units[i] = unitList.get(i);
			}

			tx.logUpdate(new DeferredLogOperation(blockSpace.getId(), pages,
					units));

			// register a PostCommitHook to physically release the blocks
			tx.addPostCommitHook(this);
		}

		@Override
		public void abort(Tx tx) throws ServerException {
			// nothing to do here, since the log record produced in the prepare
			// method does not harm in case of an undo.
		}

		@Override
		public void execute(Tx tx) throws ServerException {
			// this method is executed right after commit in a separate
			// transaction -> release the blocks physically
			executeInternal(false);
		}

		@Override
		public void execute() throws ServerException {
			// this method is executed at the end of the redo recovery phase
			executeInternal(true);
		}

		private void executeInternal(boolean force) throws ServerException {

			List<PageID> failedPages = new ArrayList<PageID>();
			List<Integer> failedUnits = new ArrayList<Integer>();

			// release single pages
			for (PageUnitPair entry : pageList) {
				try {
					Frame frame = pageNoToFrame.remove(entry.pageID);
					if (frame != null) {
						// The handle of a deleted page is allowed to be fixed
						// by concurrent threads. However, the safe flag should
						// be
						// used in this case to signal them that they
						// must not use the handle anymore.
						// To avoid any change for corruption we drop the
						// handle!
						frame.drop();
						pool.remove(frame);
					}

					deallocateBlock(entry.pageID, entry.unitID, force);
				} catch (BufferException e) {
					// log the exception, but continue to deallocate the
					// remaining blocks
					log.error(String.format(
							"Error deallocating page %s from unit %s.",
							entry.pageID, entry.unitID), e);
					failedPages.add(entry.pageID);
				}
			}

			// drop units
			for (int unitID : unitList) {
				try {
					ArrayList<Frame> toDrop = new ArrayList<Frame>();

					// determine frames that belong to this unit
					for (Frame frame : pool) {
						if (frame.getUnitID() == unitID) {
							pageNoToFrame.remove(frame.getPageID());
							frame.drop();
							toDrop.add(frame);
						}
					}

					// drop frames
					for (Frame frame : toDrop) {
						pool.remove(frame);
					}

					blockSpace.dropUnit(unitID, force);
				} catch (StoreException e) {
					// log the exception, but continue to drop the
					// remaining units
					log.error(String.format("Error dropping unit %s.", unitID),
							e);
					failedUnits.add(unitID);
				}
			}

			if (!failedPages.isEmpty() || !failedUnits.isEmpty()) {
				throw new ServerException(String.format(
						"Exceptions during releasing pages %s and units %s.",
						failedPages, failedUnits));
			}
		}
	}

	private class PageReleaserImpl implements PageReleaser {

		private final PageID pageID;
		private final int unitID;
		private final boolean force;

		private PageReleaserImpl(PageID pageID, int unitID, boolean force) {
			this.pageID = pageID;
			this.unitID = unitID;
			this.force = force;
		}

		@Override
		public void release() throws BufferException {

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
			deallocateBlock(pageID, unitID, force);
		}
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

	private final String deallocateHookName;

	public AbstractBuffer(BlockSpace blockSpace, int bufferSize,
			Log transactionLog, BufferMgr bufferMgr) throws BufferException {
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
		ListBuffer.add(this);

		this.deallocateHookName = String
				.format("DEALLOC%s", blockSpace.getId());
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

	@Override
	public synchronized void redoAllocation(Tx tx, PageID pageID, int unitID,
			long LSN) throws BufferException {

		// mark block as used in the free space info
		allocateBlock(pageID, unitID, true);

		Frame requested = pageNoToFrame.get(pageID);

		if (requested == null) {
			faultCnt++;
			requested = load(pageID);
		} else {
			hitCnt++;
		}

		if (requested.getLSN() < LSN) {
			// format handle again
			requested.init(pageID, unitID);
			requested.setLSN(LSN);
			requested.setModified(true);
		}
	}

	@Override
	public synchronized void undoDeallocation(Tx tx, PageID pageID, int unitID,
			long undoNextLSN) throws BufferException {

		try {
			tx.logCLR(new AllocateLogOperation(pageID, unitID), undoNextLSN);
		} catch (TxException e) {
			throw new BufferException(
					"Could not write log for page allocation.", e);
		}

		// mark block as used in the free space info
		allocateBlock(pageID, unitID, true);
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
		List<Frame> toFlush = new ArrayList<AbstractBuffer.Frame>();
		boolean allAssigned = buildRun((Frame) handle, toFlush);
		try {
			flushRun(toFlush, !allAssigned);
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

	@Override
	public int createUnit(Tx tx) throws BufferException {
		return createUnit(tx, -1, true, -1, false);
	}

	@Override
	public synchronized int createUnit(Tx tx, int unitID, boolean logged,
			long undoNextLSN, boolean force) throws BufferException {

		if (log.isTraceEnabled()) {
			log.trace(String.format("Creating unit %s.", unitID));
		}

		try {
			unitID = blockSpace.createUnit(unitID, force);
		} catch (StoreException e) {
			throw new BufferException("Error creating unit.", e);
		}

		if (logged) {
			try {
				if (undoNextLSN == -1) {
					tx.logUpdate(new CreateUnitLogOperation(blockSpace.getId(),
							unitID));
				} else {
					tx.logCLR(new CreateUnitLogOperation(blockSpace.getId(),
							unitID), undoNextLSN);
				}
			} catch (TxException e) {
				throw new BufferException(
						"Could not write log for unit creation.", e);
			}
		}

		return unitID;
	}

	@Override
	public synchronized void dropUnit(Tx tx, int unitID, boolean logged,
			long undoNextLSN, boolean force) throws BufferException {

		if (log.isTraceEnabled()) {
			log.trace(String.format("Dropping unit %s.", unitID));
		}

		if (logged) {
			try {
				if (undoNextLSN == -1) {
					tx.logUpdate(new DropUnitLogOperation(blockSpace.getId(),
							unitID));
				} else {
					tx.logCLR(new DropUnitLogOperation(blockSpace.getId(),
							unitID), undoNextLSN);
				}
			} catch (TxException e) {
				throw new BufferException(
						"Could not write log for dropping unit.", e);
			}
		}

		ArrayList<Frame> toDrop = new ArrayList<Frame>();

		// determine frames that belong to this unit
		for (Frame frame : pool) {
			if (frame.getUnitID() == unitID) {
				pageNoToFrame.remove(frame.getPageID());
				frame.drop();
				toDrop.add(frame);
			}
		}

		// drop frames
		for (Frame frame : toDrop) {
			pool.remove(frame);
		}

		try {
			blockSpace.dropUnit(unitID, force);
		} catch (StoreException e) {
			throw new BufferException(String.format("Error dropping unit %s.",
					unitID), e);
		}
	}

	@Override
	public synchronized void dropUnitDeferred(Tx tx, int unitID) {

		if (log.isTraceEnabled()) {
			log.trace(String.format("Dropping unit %s at Commit.", unitID));
		}

		// do not release the unit right now, but at transaction commit
		// all deallocations for this container are done in a single
		// PreCommitHook
		PreCommitHook hook = tx.getPreCommitHook(deallocateHookName);
		if (hook == null) {
			hook = new DeallocateHook();
			tx.addPreCommitHook(hook, deallocateHookName);
		}
		((DeallocateHook) hook).addUnit(unitID);
	}

	@Override
	public void dropUnit(Tx tx, int unitID) throws BufferException {
		dropUnit(tx, unitID, true, -1, false);
	}

	public synchronized Handle allocatePage(Tx tx, int unitID)
			throws BufferException {
		return allocatePage(tx, unitID, null, true, -1, false);
	}

	public synchronized Handle allocatePage(Tx tx, int unitID, PageID pageID,
			boolean logged, long undoNextLSN, boolean force)
			throws BufferException {

		if (DEBUG) {
			if (unitID <= 0) {
				throw new IllegalArgumentException(
						"UnitID must be greater than Zero!");
			}
		}

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
			pageID = allocateBlock(pageID, unitID, force);

			if (logged) {
				try {
					if (undoNextLSN == -1) {
						LSN = tx.logUpdate(new AllocateLogOperation(pageID,
								unitID));
					} else {
						LSN = tx.logCLR(
								new AllocateLogOperation(pageID, unitID),
								undoNextLSN);
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
		victim.init(pageID, unitID);
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

	@Override
	public synchronized void deletePageDeferred(Tx tx, PageID pageID, int unitID)
			throws BufferException {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Deleting page %s at Commit.", pageID));
		}

		// do not release the block right now, but at transaction commit
		// all deallocations for this container are done in a single
		// PreCommitHook
		PreCommitHook hook = tx.getPreCommitHook(deallocateHookName);
		if (hook == null) {
			hook = new DeallocateHook();
			tx.addPreCommitHook(hook, deallocateHookName);
		}
		((DeallocateHook) hook).addPage(pageID, unitID);
	}

	@Override
	public PageReleaser deletePage(Tx tx, PageID pageID, int unitID)
			throws BufferException {
		return deletePage(tx, pageID, unitID, true, -1, false);
	}

	@Override
	public synchronized PageReleaser deletePage(Tx tx, PageID pageID,
			int unitID, boolean logged, long undoNextLSN, boolean force)
			throws BufferException {

		if (log.isTraceEnabled()) {
			log.trace(String.format("Deleting page %s.", pageID));
		}

		if (logged) {
			try {
				if (undoNextLSN == -1) {
					tx.logUpdate(new DeallocateLogOperation(pageID, unitID));
				} else {
					tx.logCLR(new DeallocateLogOperation(pageID, unitID),
							undoNextLSN);
				}
			} catch (TxException e) {
				throw new BufferException(
						"Could not write log for page deallocation.", e);
			}
		}

		return new PageReleaserImpl(pageID, unitID, force);
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

	private void writeBlocks(PageID pageID, byte[] buffer, int numOfBlocks,
			boolean sync) throws BufferException {
		if (log.isTraceEnabled()) {
			log.trace(String.format(
					"Writing %s blocks [%s-%s] starting with block of page %s",
					numOfBlocks, pageID.getBlockNo(), pageID.getBlockNo()
							+ numOfBlocks - 1, pageID));
		}
		try {
			blockSpace.write(pageID.getBlockNo(), writeBuffer, numOfBlocks,
					sync);
		} catch (StoreException e) {
			throw new BufferException(e,
					"Writing %s blocks [%s-%s] starting with block of page %s",
					numOfBlocks, pageID.getBlockNo(), pageID.getBlockNo()
							+ numOfBlocks - 1, pageID);
		}
	}

	private PageID allocateBlock(PageID pageID, int unitID, boolean force)
			throws BufferException {
		int blockNo = (pageID != null) ? pageID.getBlockNo() : -1;
		if (log.isTraceEnabled()) {
			log.trace(String.format("Allocating block %s of page %s", blockNo,
					pageID));
		}
		try {
			int allocatedBlockNo = blockSpace.allocate(blockNo, unitID, force);
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

	@Override
	public void releaseAfterRedo(Tx tx, PageUnitPair[] pages, int[] units) {
		tx.addPostRedoHook(new DeallocateHook(pages, units));
	}

	private void deallocateBlock(PageID pageID, int unitID, boolean force)
			throws BufferException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Releasing block %s of page %s",
					pageID.getBlockNo(), pageID));
		}
		try {
			blockSpace.release(pageID.getBlockNo(), unitID, force);
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
			frame.init(pID, 0); // unitID not relevant, since it is overwritten
								// in the next line anyway
			System.arraycopy(buffer, offset, frame.page, 0, pageSize);
			currentBlockNo++;
			offset += pageSize;
		}
	}

	private void transferToBuffer(List<Frame> frames, byte[] buffer) {
		int offset = 0;
		for (Frame frame : frames) {
			System.arraycopy(frame.page, 0, buffer, offset, pageSize);
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

	/**
	 * @param run
	 *            List of frames in which the run is collected
	 * @return true if all collected frames are assigned
	 */
	protected boolean buildRun(Frame frame, List<Frame> run) {
		boolean allAssigned = true;

		run.add(frame);
		if (frame.getAssignedTo() == null) {
			allAssigned = false;
		}

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
			if (tmp.getAssignedTo() == null) {
				allAssigned = false;
			}
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
			if (tmp.getAssignedTo() == null) {
				allAssigned = false;
			}
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
		return allAssigned;
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
		boolean allAssigned = true;

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
				if (frame.getAssignedTo() == null) {
					allAssigned = false;
				}
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
			if (!allAssigned) {
				// sync data
				syncData();
			}

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

		// sync data file
		syncData();
	}

	/**
	 * Flushes the given list of frames. WARNING: does not sync the data file on
	 * disk. If this behavior is needed, call {@link #syncData()} afterwards.
	 */
	private void flush(List<Frame> frames) throws BufferException {
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
				flushRun(run, false);
				run.clear();
				runSize = 0;
			}
			run.add(frame);
			runSize++;
			prevBlockNo = blockNo;
		}

		if (runSize > 0) {
			flushRun(run, false);
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

	private void flushRun(List<Frame> run, boolean sync) throws BufferException {
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
		writeBlocks(firstPageID, writeBuffer, run.size(), sync);

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
			out.format(
					"%s FRM=%s MAPPED=%s FIX=%s LATCH=%s LSN=%s DIRTY=%s TX=%s",
					pID, fID, mapped, fixed, latched, LSN, dirty, tx);
			out.println();
		}
	}

	@Override
	public void shutdown(boolean force) throws BufferException {
		ListBuffer.remove(this);
		flush();

		synchronized (this) {
			if (fixCnt - unfixCnt > 0) {
				if (force) {
					log.warn(String
							.format("Closing container '%s' because some pages are still fixed in the buffer.",
									blockSpace.getId()));
				} else {
					throw new BufferException(
							String.format(
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
	public synchronized void sync() throws BufferException {
		try {
			blockSpace.sync();
		} catch (StoreException e) {
			throw new BufferException(e);
		}
	}

	private synchronized void syncData() throws BufferException {
		try {
			blockSpace.syncData();
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