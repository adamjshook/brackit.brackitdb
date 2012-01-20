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
package org.brackit.server.node.el.index;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.el.Elndex;
import org.brackit.server.node.el.ElIndexIterator;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.tx.Tx;

/**
 * Special B+-tree version with support of special undo insert/delete semantics
 * required for elementless storage.
 * 
 * @author Sebastian Baechle
 * 
 */
public class ElBPlusIndex extends BPlusIndex implements Elndex {
	private static final Logger log = Logger.getLogger(ElBPlusIndex.class);

	public ElBPlusIndex(BufferMgr bufferMgr,
			ElPlaceHolderHelper placeHolderHelper) {
		super(new ElBPlusTree(bufferMgr, placeHolderHelper), bufferMgr);
	}

	@Override
	public ElIndexIterator open(Tx transaction, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode)
			throws IndexAccessException {
		return open(transaction, rootPageID, searchMode, key, value, openMode,
				null, -1);
	}

	@Override
	public ElIndexIterator open(Tx transaction, PageID rootPageID,
			SearchMode searchMode, byte[] key, byte[] value, OpenMode openMode,
			PageID hintPageID, long LSN) throws IndexAccessException {
		PageContext leaf = tree.openInternal(transaction, rootPageID,
				searchMode, key, value, openMode, hintPageID, LSN);
		return new ElBPlusIndexIteratorImpl(transaction, (ElBPlusTree) tree,
				rootPageID, leaf, openMode);
	}
}
