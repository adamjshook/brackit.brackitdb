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
package org.brackit.server.io.buffer;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.brackit.server.tx.Tx;
import org.brackit.server.tx.thread.SyncLatch;
import org.brackit.server.util.Calc;

/**
 * <p>
 * Representation of a page in the {@link Buffer}.
 * </p>
 * <p>
 * WARNING: Reading and modifying any field of a page handle is only valid if it
 * is correctly fixed in the buffer and latched in a suitable mode!
 * </p>
 * 
 * @author Sebastian Baechle
 * 
 */
public class Handle extends SyncLatch {
	/**
	 * Size of the page header in bytes.
	 * <ul>
	 * <li>byte 0-3 (byte) - unitID</li>
	 * <li>byte 4 (byte) - page type</li>
	 * <li>byte 5-12 (long) - page LSN</li>
	 * <li>byte 13-15 spare for future use</li>
	 */
	public static final int GENERAL_HEADER_SIZE = 16;

	public static final int LSN_OFFSET = 5;

	private volatile long redoLSN = Long.MAX_VALUE;

	private volatile Tx assignedTo;

	private boolean modified;

	private PageID pageID;

	private boolean safe;

	private final AtomicReference<Object> cache;

	public final byte[] page;

	protected Handle(int pageSize) {
		this.cache = new AtomicReference<Object>();
		this.page = new byte[pageSize];
		this.safe = true;
	}

	public Tx getAssignedTo() {
		return assignedTo;
	}

	public void setAssignedTo(Tx assignedTo) {
		this.assignedTo = assignedTo;
	}

	public boolean isAssignedTo(Tx transaction) {
		Tx tx = assignedTo; // volatile read
		return ((tx != null) && (tx.equals(transaction)));
	}

	public synchronized void resetFlags() {
		setModified(false);
	}

	public synchronized boolean isModified() {
		return modified;
	}

	public synchronized void setModified(boolean modified) {
		this.modified = modified;
		this.redoLSN = Long.MAX_VALUE;
	}

	public void setRedoLSN(long LSN) {
		this.redoLSN = LSN;
	}

	public long getRedoLSN() {
		return redoLSN;
	}

	public boolean isSafe() {
		return safe;
	}

	public void setSafe(boolean safe) {
		this.safe = safe;
	}

	public PageID getPageID() {
		return pageID;
	}
	
	public int getUnitID() {
		return Calc.toInt(page, 0);
	}

	public void init(PageID pageID, int unitID, boolean format) {
		this.pageID = pageID;
		cache.set(null);
		if (format) {
			Arrays.fill(page, 0, page.length, (byte) 0);
		}
		setRedoLSN(Long.MAX_VALUE);
		resetFlags();
		// set unitID
		Calc.fromInt(unitID, page, 0);
	}

	public int getPageSize() {
		return page.length;
	}

	public long getLSN() {
		return ((((long) page[LSN_OFFSET + 0] & 0xff) << 56)
				| (((long) page[LSN_OFFSET + 1] & 0xff) << 48)
				| (((long) page[LSN_OFFSET + 2] & 0xff) << 40)
				| (((long) page[LSN_OFFSET + 3] & 0xff) << 32)
				| (((long) page[LSN_OFFSET + 4] & 0xff) << 24)
				| (((long) page[LSN_OFFSET + 5] & 0xff) << 16)
				| (((long) page[LSN_OFFSET + 6] & 0xff) << 8) | (((long) page[LSN_OFFSET + 7] & 0xff) << 0));
	}

	public void setLSN(long LSN) {
		page[LSN_OFFSET] = (byte) ((LSN >> 56) & 255);
		page[LSN_OFFSET + 1] = (byte) ((LSN >> 48) & 255);
		page[LSN_OFFSET + 2] = (byte) ((LSN >> 40) & 255);
		page[LSN_OFFSET + 3] = (byte) ((LSN >> 32) & 255);
		page[LSN_OFFSET + 4] = (byte) ((LSN >> 24) & 255);
		page[LSN_OFFSET + 5] = (byte) ((LSN >> 16) & 255);
		page[LSN_OFFSET + 6] = (byte) ((LSN >> 8) & 255);
		page[LSN_OFFSET + 7] = (byte) ((LSN) & 255);

		setModified(true);

		if (redoLSN == Long.MAX_VALUE) {
			redoLSN = LSN;
		}
	}

	@Override
	public String toString() {
		return String.format("%s (%s)", pageID, super.hashCode());
	}

	public Object getCache() {
		return cache.get();
	}

	public void setCache(Object cache) {
		this.cache.compareAndSet(null, cache);
	}
}
