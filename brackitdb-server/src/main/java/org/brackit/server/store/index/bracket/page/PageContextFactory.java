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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.page.bracket.BracketPage;
import org.brackit.server.store.page.keyvalue.CachingKeyValuePageImpl;
import org.brackit.server.store.page.keyvalue.KeyValuePage;
import org.brackit.server.store.page.keyvalue.SlottedKeyValuePage;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.thread.Latch;
import org.brackit.xquery.util.Cfg;

/**
 * @author Martin Hiller
 * 
 */
public class PageContextFactory {

	private static final Logger log = Logger
			.getLogger(PageContextFactory.class);

	public static int BRANCH_TYPE = Cfg.asInt(Index.PAGE_VERSION, 1);

	protected final BufferMgr bufferMgr;

	public PageContextFactory(BufferMgr bufferMgr) {
		this.bufferMgr = bufferMgr;
	}

	public final Branch allocateBranch(Tx tx, int containerNo, int unitID,
			PageID rootPageID, int height, boolean compression, boolean logged)
			throws IndexOperationException {
		Buffer buffer = null;
		Handle handle = null;
		Branch page = null;

		try {
			buffer = (containerNo != -1) ? bufferMgr.getBuffer(containerNo)
					: bufferMgr.getBuffer(rootPageID);

			if (unitID == -1) {
				// create new unit
				unitID = buffer.createUnit(tx);
			}

			handle = buffer.allocatePage(tx, unitID);

			page = createBranch(tx, buffer, handle, Latch.MODE_X);

			if (rootPageID == null) {
				/*
				 * Use own page number as root page no. This is required for
				 * index creation.
				 */
				rootPageID = page.getPageID();
			}

			page.format(rootPageID, height, compression, logged, -1);
		} catch (BufferException e) {
			throw new IndexOperationException(e,
					"Could not allocated new page.");
		} catch (IndexOperationException e) {
			handle.unlatch();

			try {
				buffer.unfixPage(handle);
			} catch (BufferException e1) {
				log.error("Unfix of page failed.", e1);
			}

			throw e;
		}

		if (log.isTraceEnabled()) {
			log.trace(page.dump("requested page page"));
		}

		if (log.isDebugEnabled()) {
			log.debug("Allocated page " + page);
		}

		return page;
	}

	private Branch createKeyValuePageContext(Tx tx, KeyValuePage page) {
		return new BranchBPContext(bufferMgr, tx, page);
	}

	private Branch createBranch(Tx tx, Buffer buffer, Handle handle)
			throws IndexOperationException {
		switch (BRANCH_TYPE) {
		case 1:
			return createKeyValuePageContext(tx, new SlottedKeyValuePage(
					buffer, handle, BranchBPContext.RESERVED_SIZE));
		case 2:
			return createKeyValuePageContext(tx, new CachingKeyValuePageImpl(
					buffer, handle, BranchBPContext.RESERVED_SIZE));
		default:
			throw new IndexOperationException(
					"Unsupported page context type %s.", BRANCH_TYPE);
		}
	}

	private Branch createBranch(Tx tx, Buffer buffer, Handle handle,
			int latchMode) throws IndexOperationException {
		return createBranch(tx, buffer, handle);
	}

	public final Leaf allocateLeaf(Tx tx, int containerNo, int unitID,
			PageID rootPageID, boolean logged) throws IndexOperationException {
		Buffer buffer = null;
		Handle handle = null;
		Leaf page = null;

		try {
			buffer = (containerNo != -1) ? bufferMgr.getBuffer(containerNo)
					: bufferMgr.getBuffer(rootPageID);

			if (unitID == -1) {
				// create new unit
				unitID = buffer.createUnit(tx);
			}

			handle = buffer.allocatePage(tx, unitID);

			page = createLeaf(tx, buffer, handle, Latch.MODE_X);

			if (rootPageID == null) {
				/*
				 * Use own page number as root page no. This is required for
				 * index creation.
				 */
				rootPageID = page.getPageID();
			}

			page.format(rootPageID, logged, -1);
		} catch (BufferException e) {
			throw new IndexOperationException(e,
					"Could not allocated new page.");
		} catch (IndexOperationException e) {
			handle.unlatch();

			try {
				buffer.unfixPage(handle);
			} catch (BufferException e1) {
				log.error("Unfix of page failed.", e1);
			}

			throw e;
		}

		if (log.isTraceEnabled()) {
			log.trace(page.dump("requested page page"));
		}

		if (log.isDebugEnabled()) {
			log.debug("Allocated page " + page);
		}

		return page;
	}

	private Leaf createLeaf(Tx tx, Buffer buffer, Handle handle)
			throws IndexOperationException {
		return new LeafBPContext(bufferMgr, tx, new BracketPage(buffer, handle));
	}

	private Leaf createLeaf(Tx tx, Buffer buffer, Handle handle, int latchMode)
			throws IndexOperationException {
		return createLeaf(tx, buffer, handle);
	}

	/**
	 * Fixes a page in the buffer, latches it and returns an initialized page
	 * context for it
	 * 
	 * @param tx
	 *            tx accessing the index
	 * @param forUpdate
	 *            indicates whether the page should be latched for write access
	 * @param updateLatch
	 *            indicates whether the page should be latched in update mode
	 *            first
	 * @return a page context for the requested page
	 * @throws IndexOperationException
	 */
	public final BPContext getPage(Tx tx, PageID pageID, boolean forUpdate,
			boolean updateLatch) throws IndexOperationException {
		Buffer buffer = null;
		Handle handle = null;
		BPContext page = null;

		try {
			buffer = bufferMgr.getBuffer(pageID);
			handle = buffer.fixPage(tx, pageID);

			if (forUpdate) {
				if (updateLatch) {
					handle.latchU();
				} else {
					handle.latchX();
				}
			} else {
				handle.latchS();
			}

			if (AbstractBPContext.isLeaf(handle.page)) {
				page = createLeaf(tx, buffer, handle,
						(forUpdate) ? (updateLatch) ? Latch.MODE_U
								: Latch.MODE_X : Latch.MODE_S);
			} else {
				page = createBranch(tx, buffer, handle,
						(forUpdate) ? (updateLatch) ? Latch.MODE_U
								: Latch.MODE_X : Latch.MODE_S);
			}
		} catch (BufferException e) {
			throw new IndexOperationException(e,
					"Could not fix requested page %s.", pageID);
		} catch (IndexOperationException e) {
			handle.unlatch();

			try {
				buffer.unfixPage(handle);
			} catch (BufferException e1) {
				log.error("Unfix of page failed.", e1);
			}
			throw e;
		}

		if (log.isTraceEnabled()) {
			log.trace(page.dump("requested page page"));
		}

		if (log.isDebugEnabled()) {
			log.debug("Fetched page " + page);
		}

		return page;
	}
}
