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
package org.brackit.server.store.index.bracket.page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.blob.impl.SimpleBlobStore;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.log.FormatLogOperation;
import org.brackit.server.store.index.bracket.log.PointerLogOperation;
import org.brackit.server.store.index.bracket.log.PointerLogOperation.PointerField;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.bracket.BracketPage;
import org.brackit.server.store.page.keyvalue.CachingKeyValuePageImpl;
import org.brackit.server.store.page.keyvalue.SlottedKeyValuePage;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.xquery.util.log.Logger;

/**
 * @author Sebastian Baechle
 * @author Martin Hiller
 * 
 */
public abstract class AbstractBPContext extends SimpleBlobStore implements
		BPContext {

	private static final Logger log = Logger.getLogger(AbstractBPContext.class);

	public static final int LEAF_FLAG_FIELD_NO = 0;
	public static final int PREV_PAGE_FIELD_NO = LEAF_FLAG_FIELD_NO + 1;
	public static final int RESERVED_SIZE = PREV_PAGE_FIELD_NO
			+ PageID.getSize();

	protected final BasePage page;

	protected final Tx tx;

	public AbstractBPContext(BufferMgr bufferMgr, Tx tx, BasePage page) {
		super(bufferMgr);
		this.tx = tx;
		this.page = page;
	}

	@Override
	public abstract int getEntryCount();

	@Override
	public int getUnitID() {
		return page.getUnitID();
	}

	private void setLeafFlag(boolean leaf) {
		byte[] buffer = page.getHandle().page;
		int offset = BasePage.BASE_PAGE_START_OFFSET + LEAF_FLAG_FIELD_NO;
		buffer[offset] = leaf ? (byte) 1 : (byte) 0;
	}

	@Override
	public abstract int getHeight();

	protected abstract void setHeight(int height);

	@Override
	public abstract boolean isLastInLevel();

	@Override
	public PageID getPrevPageID() {
		byte[] value = page.getHandle().page;
		return PageID.fromBytes(value, BasePage.BASE_PAGE_START_OFFSET
				+ PREV_PAGE_FIELD_NO);
	}

	@Override
	public void setPrevPageID(PageID prevPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = new PointerLogOperation(PointerField.PREVIOUS,
					getPageID(), getRootPageID(), getPrevPageID(), prevPageID);
		}

		byte[] value = page.getHandle().page;
		if (prevPageID != null) {
			prevPageID.toBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ PREV_PAGE_FIELD_NO);
		} else {
			PageID.noPageToBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ PREV_PAGE_FIELD_NO);
		}

		page.getHandle().setModified(true); // not covered by pageID to bytes

		if (logged) {
			log(tx, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(tx);
		}
	}

	@Override
	public boolean externalizeValue(byte[] value) {
		return (value.length > page.getUsableSpace() / 6);
	}

	@Override
	public byte[] externalize(byte[] value) throws IndexOperationException {
		try {
			PageID blobPageID = create(tx, page.getPageID().getContainerNo(),
					page.getUnitID());
			write(tx, blobPageID, value, false);
			value = blobPageID.getBytes();
		} catch (BlobStoreAccessException e) {
			throw new IndexOperationException(e,
					"Error writing large value to overflow blob");
		}
		return value;
	}

	protected void deleteExternalized(byte[] oldValue)
			throws IndexOperationException {
		List<PageID> blobPageID = new ArrayList<PageID>(1);
		blobPageID.add(PageID.fromBytes(oldValue));
		deleteExternalized(blobPageID);
	}

	protected void deleteExternalized(PageID pageID)
			throws IndexOperationException {
		List<PageID> blobPageID = new ArrayList<PageID>(1);
		blobPageID.add(pageID);
		deleteExternalized(blobPageID);
	}

	protected void deleteExternalized(List<PageID> externalPageIDs)
			throws IndexOperationException {
		
		List<PageID> exceptionPageIDs = null;
		for (PageID pageID : externalPageIDs) {				
			try {
				drop(tx, pageID);
			} catch (BlobStoreAccessException e) {
				if (exceptionPageIDs == null) {
					exceptionPageIDs = new ArrayList<PageID>();
				}
				exceptionPageIDs.add(pageID);
			}
		}
		if (exceptionPageIDs != null) {
			throw new IndexOperationException(String.format(
					"Error deleting overflow values %s.", exceptionPageIDs));
		}
	}

	protected void deleteExternalizedInstantly(byte[] oldValue)
			throws IndexOperationException {
		try {
			PageID blobPageID = PageID.fromBytes(oldValue);
			drop(tx, blobPageID);
		} catch (BlobStoreAccessException e) {
			throw new IndexOperationException(e,
					"Error deleting overflow value.");
		}
	}

	@Override
	public BPContext format(boolean leaf, PageID rootPageID, int height,
			boolean compressed, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = new FormatLogOperation(getPageID(), rootPageID,
					isLeaf(), leaf, getHeight(), height, isCompressed(),
					compressed);
		}

		/*
		 * Change page type, init free space info management, and reset entry
		 * counter.
		 */
		page.clear();

		// delete Branch/Leaf specific header information
		byte[] pageBytes = page.getHandle().page;
		Arrays.fill(
				pageBytes,
				BasePage.BASE_PAGE_START_OFFSET,
				BasePage.BASE_PAGE_START_OFFSET
						+ Math.max(BranchBPContext.RESERVED_SIZE,
								LeafBPContext.RESERVED_SIZE), (byte) 0);

		// create new page context
		AbstractBPContext newContext = this;
		if (leaf) {

			if (this.isLeaf()) {
				((LeafBPContext) this).page.clearData(false);
			} else {
				BracketPage bracketPage = new BracketPage(page.getBuffer(),
						page.getHandle());
				bracketPage.clearData(false);
				newContext = new LeafBPContext(bufferMgr, tx, bracketPage);
			}

		} else if (!leaf && this.isLeaf()) {
			switch (PageContextFactory.BRANCH_TYPE) {
			case 1:
				newContext = new BranchBPContext(bufferMgr, tx,
						new SlottedKeyValuePage(page.getBuffer(), page
								.getHandle(), BranchBPContext.RESERVED_SIZE));
				break;
			case 2:
				newContext = new BranchBPContext(bufferMgr, tx,
						new CachingKeyValuePageImpl(page.getBuffer(), page
								.getHandle(), BranchBPContext.RESERVED_SIZE));
				break;
			default:
				throw new IndexOperationException(
						"Unsupported page context type %s.",
						PageContextFactory.BRANCH_TYPE);
			}
			page.clear();
		}

		/*
		 * Set index specific fields.
		 */
		// byte[] key = new byte[0];
		// byte[] value = new byte[4 + ((pageType == PageType.INDEX_LEAF) ? 2 :
		// 1)* PageID.getSize()];
		// page.insert(0, key, value, compressed);

		newContext.setLeafFlag(leaf);
		newContext.setHeight(height);
		newContext.page.setBasePageID(rootPageID);
		newContext.setCompressed(compressed);

		if (logged) {
			log(tx, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(tx);
		}

		return newContext;
	}

	public abstract void setCompressed(boolean compressed);

	@Override
	public PageID getRootPageID() {
		return page.getBasePageID();
	}

	@Override
	public abstract boolean hasNext();

	@Override
	public abstract boolean moveNext();

	@Override
	public abstract boolean hasPrevious();

	@Override
	public abstract boolean moveFirst();

	@Override
	public abstract boolean moveLast();

	@Override
	public abstract String dump(String pageTitle);

	protected void log(Tx tx, LogOperation operation, long undoNextLSN)
			throws IndexOperationException {
		try {
			long lsn = (undoNextLSN == -1) ? tx.logUpdate(operation) : tx
					.logCLR(operation, undoNextLSN);
			page.setLSN(lsn);
		} catch (TxException e) {
			throw new IndexOperationException(e,
					"Could not write changes to log.");
		}
	}

	@Override
	public abstract BPContext createClone() throws IndexOperationException;

	@Override
	public void cleanup() {
		page.cleanup();
	}

	@Override
	public void deletePage() throws IndexOperationException {
		try {
			PageID pageID = page.getPageID();
			int unitID = page.getUnitID();
			page.cleanup();
			page.getBuffer().deletePageDeferred(tx, pageID, unitID);
		} catch (BufferException e) {
			throw new IndexOperationException(e, "Error deleting page");
		}
	}

	@Override
	public int getSize() {
		return page.getSize();
	}

	@Override
	public int getFreeSpace() {
		return page.getFreeSpace();
	}

	@Override
	public long getLSN() {
		return page.getLSN();
	}

	@Override
	public PageID getPageID() {
		return page.getPageID();
	}

	@Override
	public int getUsedSpace() {
		return page.getUsedSpace();
	}

	@Override
	public int getMode() {
		return page.getMode();
	}

	@Override
	public String info() {
		return page.info();
	}

	@Override
	public String toString() {
		return page.getHandle().toString();
	}

	@Override
	public boolean isLeaf() {
		return isLeaf(page.getHandle().page);
	}

	@Override
	public BasePage getPage() {
		return page;
	}

	public static boolean isLeaf(byte[] page) {

		int offset = BasePage.BASE_PAGE_START_OFFSET + LEAF_FLAG_FIELD_NO;
		int value = page[offset] & 255;

		if (value == 0) {
			return false;
		} else if (value == 1) {
			return true;
		} else {
			throw new RuntimeException(String.format(
					"Invalid content %s in the leaf flag field!", value));
		}

	}

	@Override
	public void latchS() {
		page.latchS();
	}

	@Override
	public void latchSI() {
		page.latchSI();
	}

	@Override
	public void latchX() {
		page.latchX();
	}

	@Override
	public void latchU() {
		page.latchU();
	}

	@Override
	public void downS() {
		page.downS();
	}

	@Override
	public void upX() {
		page.upX();
	}

	@Override
	public void unlatch() {
		page.unlatch();
	}

	@Override
	public boolean isLatchedS() {
		return page.isLatchedS();
	}

	@Override
	public boolean isLatchedU() {
		return page.isLatchedU();
	}

	@Override
	public boolean isLatchedX() {
		return page.isLatchedX();
	}

	@Override
	public boolean latchXC() {
		return page.latchXC();
	}

	@Override
	public boolean latchSC() {
		return page.latchSC();
	}

	@Override
	public boolean latchUC() {
		return page.latchUC();
	}
}
