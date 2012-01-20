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
package org.brackit.server.store.page;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.tx.log.SizeConstants;

/**
 * Abstract base for all kinds of pages that need an entry and free space
 * management. Subclasses can define additional header fields by statically
 * reserving some space directly after the base page header.
 * 
 * <p>
 * <b>Base page header layout:</b> <br/>
 * <table border="1">
 * <tr>
 * <th>Field</th>
 * <th>Size</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>byte 0-3</td>
 * <td>int</td>
 * <td>base page no</td>
 * </tr>
 * <tr>
 * <td>byte 4-5</td>
 * <td>word</td>
 * <td>spare</td>
 * </tr>
 * <tr>
 * <td>byte 6-7</td>
 * <td>word</td>
 * <td>number of entries</td>
 * </tr>
 * <tr>
 * <td>byte 8-11</td>
 * <td>int</td>
 * <td>used space field</td>
 * </tr>
 * <tr>
 * <td>byte 11-15</td>
 * <td>int</td>
 * <td>freespace offset pointer</td>
 * </tr>
 * </table>
 * </p>
 * 
 * @author Sebastian Baechle
 * 
 */
public abstract class BasePage implements BufferedPage {
	private static final Logger log = Logger.getLogger(BasePage.class);

	public static final int BASE_PAGE_NO_OFFSET = Handle.GENERAL_HEADER_SIZE;

	public static final int SPARE_OFFSET = BASE_PAGE_NO_OFFSET
			+ PageID.getSize();

	public static final int RECORD_COUNT_OFFSET = SPARE_OFFSET
			+ SizeConstants.SHORT_SIZE;

	public static final int USED_SPACE_OFFSET = RECORD_COUNT_OFFSET
			+ SizeConstants.SHORT_SIZE;

	public static final int FREE_SPACE_POINTER_OFFSET = USED_SPACE_OFFSET
			+ SizeConstants.INT_SIZE;

	public static final int BASE_PAGE_SIZE = 2 * SizeConstants.INT_SIZE + 2
			* SizeConstants.SHORT_SIZE + PageID.getSize();

	public static final int BASE_PAGE_START_OFFSET = Handle.GENERAL_HEADER_SIZE
			+ BASE_PAGE_SIZE;

	protected final Buffer buffer;

	protected final Handle handle;

	protected final int reservedSpace;

	protected BasePage(Buffer buffer, Handle handle, int reservedSpace) {
		this.buffer = buffer;
		this.handle = handle;
		this.reservedSpace = reservedSpace;
	}

	public int getReservedOffset() {
		return BASE_PAGE_START_OFFSET;
	}

	public void cleanup() {
		try {
			if (handle.isLatchedS()) // also implies U and X
			{
				handle.unlatch();
			}
			if (buffer.isFixed(handle)) {
				buffer.unfixPage(handle);
			}
		} catch (BufferException e) {
			log.error("Unfix of page failed.", e);
		}
	}

	@Override
	public int getSize() {
		return handle.getPageSize();
	}

	public Buffer getBuffer() {
		return buffer;
	}

	public Handle getHandle() {
		return handle;
	}

	public boolean isSafe() {
		return handle.isSafe();
	}

	public void setSafe(boolean safe) {
		handle.setSafe(safe);
	}

	public PageID getPageID() {
		return handle.getPageID();
	}

	public final long getLSN() {
		return handle.getLSN();
	}

	public final void setLSN(long LSN) {
		handle.setLSN(LSN);
	}

	public void downS() {
		handle.downS();
	}

	public int getMode() {
		return handle.getMode();
	}

	public String info() {
		return handle.info();
	}

	public boolean isLatchedX() {
		return handle.isLatchedX();
	}

	public boolean isLatchedS() {
		return handle.isLatchedS();
	}

	public boolean isLatchedU() {
		return handle.isLatchedU();
	}

	public void latchX() {
		handle.latchX();
	}

	public boolean latchXC() {
		return handle.latchXC();
	}

	public void latchS() {
		handle.latchS();
	}

	public boolean latchSC() {
		return handle.latchSC();
	}

	public void latchSI() {
		handle.latchSI();
	}

	public void latchU() {
		handle.latchU();
	}

	public boolean latchUC() {
		return handle.latchUC();
	}

	public void unlatch() {
		handle.unlatch();
	}

	public void upX() {
		handle.upX();
	}

	protected final int getStartOffset() {
		return BASE_PAGE_START_OFFSET + reservedSpace;
	}

	public final int getFreeSpaceOffset() {
		return ((handle.page[FREE_SPACE_POINTER_OFFSET] & 255) << 24)
				| (((handle.page[FREE_SPACE_POINTER_OFFSET + 1] & 255) << 16) | (((handle.page[FREE_SPACE_POINTER_OFFSET + 2] & 255) << 8) | (handle.page[FREE_SPACE_POINTER_OFFSET + 3] & 255)));
	}

	protected final void setFreeSpaceOffset(int offset) {
		handle.page[FREE_SPACE_POINTER_OFFSET] = (byte) ((offset >> 24) & 255);
		handle.page[FREE_SPACE_POINTER_OFFSET + 1] = (byte) ((offset >> 16) & 255);
		handle.page[FREE_SPACE_POINTER_OFFSET + 2] = (byte) ((offset >> 8) & 255);
		handle.page[FREE_SPACE_POINTER_OFFSET + 3] = (byte) ((offset) & 255);

		handle.setModified(true);
	}

	public final int getRecordCount() {
		return (short) (((handle.page[RECORD_COUNT_OFFSET] & 255) << 8) | (handle.page[RECORD_COUNT_OFFSET + 1] & 255));
	}

	protected final void addRecord() {
		short value = (short) (getRecordCount() + 1);

		handle.page[RECORD_COUNT_OFFSET] = (byte) ((value >> 8) & 255);
		handle.page[RECORD_COUNT_OFFSET + 1] = (byte) ((value) & 255);

		handle.setModified(true);
	}

	protected final void removeRecord() {
		short value = (short) (getRecordCount() - 1);

		handle.page[RECORD_COUNT_OFFSET] = (byte) ((value >> 8) & 255);
		handle.page[RECORD_COUNT_OFFSET + 1] = (byte) ((value) & 255);

		handle.setModified(true);
	}

	protected final void setEntryCount(short entryCount) {
		handle.page[RECORD_COUNT_OFFSET] = (byte) ((entryCount >> 8) & 255);
		handle.page[RECORD_COUNT_OFFSET + 1] = (byte) ((entryCount) & 255);

		handle.setModified(true);
	}

	public PageID getBasePageID() {
		return PageID.fromBytes(handle.page, BASE_PAGE_NO_OFFSET);
	}

	public void setBasePageID(PageID basePage) {
		basePage.toBytes(handle.page, BASE_PAGE_NO_OFFSET);
	}

	public void clear() {
		setEntryCount((short) 0);
		setUsedSpace(0);
		setFreeSpaceOffset(getStartOffset());
	}

	public final int getUsedSpace() {
		return ((handle.page[USED_SPACE_OFFSET] & 255) << 24)
				| (((handle.page[USED_SPACE_OFFSET + 1] & 255) << 16) | (((handle.page[USED_SPACE_OFFSET + 2] & 255) << 8) | (handle.page[USED_SPACE_OFFSET + 3] & 255)));
	}

	public final int getUsableSpace() {
		return handle.getPageSize() - (BASE_PAGE_START_OFFSET + reservedSpace);
	}

	public final int getFreeSpace() {
		return (handle.getPageSize() - (BASE_PAGE_START_OFFSET + reservedSpace))
				- getUsedSpace();
	}

	protected final void allocateSpace(int space) {
		if (space <= 0) {
			throw new IllegalArgumentException();
		}

		int freeSpace = getFreeSpace();

		if (space > freeSpace) {
			throw new RuntimeException();
		}

		setUsedSpace(getUsedSpace() + space);
	}

	protected final void freeSpace(int space) {
		if (space <= 0) {
			throw new IllegalArgumentException();
		}

		if (space > getUsedSpace()) {
			throw new RuntimeException();
		}

		setUsedSpace(getUsedSpace() - space);
	}

	protected final void setUsedSpace(int used) {
		handle.page[USED_SPACE_OFFSET] = (byte) ((used >> 24) & 255);
		handle.page[USED_SPACE_OFFSET + 1] = (byte) ((used >> 16) & 255);
		handle.page[USED_SPACE_OFFSET + 2] = (byte) ((used >> 8) & 255);
		handle.page[USED_SPACE_OFFSET + 3] = (byte) ((used) & 255);

		handle.setModified(true);
	}
}