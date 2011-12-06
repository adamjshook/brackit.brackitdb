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
package org.brackit.server.store.index.aries;

import java.io.PrintStream;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.IndexVisitor;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.store.index.aries.visitor.PageCollectorVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;

/**
 * A B+-tree index following the ARIES approach for concurrency control.
 * 
 * @author Sebastian Baechle
 */
public class BPlusIndex implements Index {
	private static final Logger log = Logger.getLogger(BPlusIndex.class);

	protected final BPlusTree tree;

	protected final BufferMgr bufferMgr;

	public BPlusIndex(BufferMgr bufferMgr) {
		this(bufferMgr, null);
	}

	public BPlusIndex(BufferMgr bufferMgr, IndexLockService lockService) {
		this(new BPlusTree(bufferMgr, lockService), bufferMgr);
	}

	protected BPlusIndex(BPlusTree tree, BufferMgr bufferMgr) {
		this.tree = tree;
		this.bufferMgr = bufferMgr;
	}

	public PageID createIndex(Tx transaction, int containerNo, Field keyType,
			Field valueType, boolean unique) throws IndexAccessException {
		return createIndex(transaction, containerNo, keyType, valueType,
				unique, true, -1);
	}

	@Override
	public PageID createIndex(Tx transaction, int containerNo, Field keyType,
			Field valueType, boolean unique, boolean compression, int unitID)
			throws IndexAccessException {
		PageContext root = null;

		try {
			root = tree.allocate(transaction, containerNo, unitID,
					PageType.INDEX_LEAF, null, keyType, valueType, unique,
					compression, true);
			PageID rootPageID = root.getPageID();
			root.cleanup();

			return rootPageID;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Could not create index root page.");
		}
	}

	@Override
	public void dropIndex(Tx transaction, PageID rootPageID)
			throws IndexAccessException {
		try {
			long undeNextLSN = transaction.checkPrevLSN();
			log.warn("Deallocation of overflow pages is not implemented yet.");
			tree.getTreeLatch().latchS(rootPageID);
			PageCollectorVisitor visitor = new PageCollectorVisitor();
			BPlusIndexWalker walker = new BPlusIndexWalker(transaction, tree,
					rootPageID, visitor);
			walker.traverse();
			Buffer buffer = bufferMgr.getBuffer(rootPageID.getContainerNo());
			for (PageID pageID : visitor.getPages()) {
				buffer.deletePage(transaction, pageID, true, -1);
			}
			transaction.logDummyCLR(undeNextLSN);
		} catch (BufferException e) {
			throw new IndexAccessException(e);
		} catch (TxException e) {
			log.error("Writing dumy CLR failed", e);
			throw new IndexAccessException(e);
		} finally {
			tree.getTreeLatch().unlatch(rootPageID);
		}
	}

	@Override
	public void traverse(Tx transaction, PageID rootPageID, IndexVisitor visitor)
			throws IndexAccessException {
		try {
			tree.getTreeLatch().latchS(rootPageID);
			BPlusIndexWalker walker = new BPlusIndexWalker(transaction, tree,
					rootPageID, visitor);
			walker.traverse();
		} finally {
			tree.getTreeLatch().unlatch(rootPageID);
		}
	}

	@Override
	public void dump(Tx transaction, PageID rootPageID, PrintStream out)
			throws IndexAccessException {
		tree.getTreeLatch().latchS(rootPageID);
		try {
			int i = 0;
			IndexIterator it = open(transaction, rootPageID, SearchMode.FIRST,
					null, null, OpenMode.READ);
			Field keyType = it.getKeyType();
			Field valueType = it.getValueType();
			out.append(String.format("Dumping content of index %s with "
					+ "key type %s and value type %s:.\n", rootPageID, keyType,
					valueType));

			out.append("----------------------------------------------\n");
			if (it.getKey() != null) {
				PageID currentPageID = null;

				if ((currentPageID == null)
						|| (!it.getCurrentPageID().equals(currentPageID))) {
					currentPageID = it.getCurrentPageID();
					out.append(String
							.format("-------------- %s --------------\n",
									currentPageID));
				}
				do {
					byte[] key = it.getKey();
					byte[] value = it.getValue();
					out.append(String.format("%7s. %s -> %s\n", ++i,
							key != null ? keyType.toString(key) : null,
							value != null ? valueType.toString(value) : null));
				} while (it.next());
			}
			out.append("----------------------------------------------\n");
			it.close();
		} finally {
			tree.getTreeLatch().unlatch(rootPageID);
		}
	}

	public byte[] read(Tx transaction, PageID rootPageID, byte[] key)
			throws IndexAccessException {
		if (log.isTraceEnabled()) {
			log.trace("Begin read");
		}

		byte[] value = null;

		PageContext leaf = tree.descendToPosition(transaction, rootPageID,
				SearchMode.GREATER_OR_EQUAL, key, null, false, false);
		leaf = tree.readFromLeaf(transaction, rootPageID, leaf, key, value);

		try {
			if ((leaf.getKey() != null)
					&& (leaf.getKeyType().compare(leaf.getKey(), key) == 0)) {
				value = leaf.getValue();
			}
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e, "Error reading index page.");
		} finally {
			leaf.cleanup();
		}

		if (log.isTraceEnabled()) {
			log.trace("End read");
		}

		return value;
	}

	public void insert(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException {
		insertInternal(transaction, rootPageID, key, value, -1);
	}

	public void insertPersistent(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException {
		long prevLSN = transaction.checkPrevLSN();
		prevLSN = (prevLSN != -1) ? prevLSN : -2;
		insertInternal(transaction, rootPageID, key, value, prevLSN);
	}

	protected void insertInternal(Tx transaction, PageID rootPageID,
			byte[] key, byte[] value, long undoNextLSN)
			throws IndexAccessException {
		if (log.isTraceEnabled()) {
			log.trace("Begin insert");
		}

		PageContext leaf = tree.descendToPosition(transaction, rootPageID,
				SearchMode.GREATER_OR_EQUAL, key, value, true, true);
		leaf = tree.insertIntoLeaf(transaction, rootPageID, leaf, key, value,
				false, true, undoNextLSN);
		leaf.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End insert");
		}
	}

	public void update(Tx transaction, PageID rootPageID, byte[] key,
			byte[] oldValue, byte[] newValue) throws IndexAccessException {
		if (log.isTraceEnabled()) {
			log.trace("Begin update");
		}

		PageContext leaf = tree.descendToPosition(transaction, rootPageID,
				SearchMode.GREATER_OR_EQUAL, key, oldValue, true, false);
		leaf = tree.updateInLeaf(transaction, rootPageID, leaf, key, newValue,
				oldValue, -1);
		leaf.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End update");
		}
	}

	public void delete(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException {
		deleteInternal(transaction, rootPageID, key, value, -1);
	}

	public void deletePersistent(Tx transaction, PageID rootPageID, byte[] key,
			byte[] value) throws IndexAccessException {
		long prevLSN = transaction.checkPrevLSN();
		prevLSN = (prevLSN != -1) ? prevLSN : -2;
		deleteInternal(transaction, rootPageID, key, value, prevLSN);
	}

	protected void deleteInternal(Tx transaction, PageID rootPageID,
			byte[] key, byte[] value, long undoNextLSN)
			throws IndexAccessException {
		if (log.isTraceEnabled()) {
			log.trace("Begin delete");
		}

		PageContext leaf = tree.descendToPosition(transaction, rootPageID,
				SearchMode.GREATER_OR_EQUAL, key, value, true, false);
		leaf = tree.deleteFromLeaf(transaction, rootPageID, leaf, key, value,
				undoNextLSN, true);
		leaf.cleanup();

		if (log.isTraceEnabled()) {
			log.trace("End delete");
		}
	}

	public IndexIterator open(Tx transaction, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode)
			throws IndexAccessException {
		return open(transaction, rootPageID, searchMode, key, value, openMode,
				null, -1);
	}

	public IndexIterator open(Tx transaction, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode,
			PageID hintPageID, long LSN) throws IndexAccessException {
		PageContext leaf = tree.openInternal(transaction, rootPageID,
				searchMode, key, value, openMode, hintPageID, LSN);
		return new BPlusIndexIterator(transaction, tree, rootPageID, leaf,
				openMode);
	}
}