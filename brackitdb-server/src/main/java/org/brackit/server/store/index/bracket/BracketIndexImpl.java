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
package org.brackit.server.store.index.bracket;

import java.io.PrintStream;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketLocator;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Martin Hiller
 * 
 */
public class BracketIndexImpl implements BracketIndex {

	private static final Logger log = Logger.getLogger(BracketIndexImpl.class);

	protected final BracketTree tree;

	protected final BufferMgr bufferMgr;

	public BracketIndexImpl(BufferMgr bufferMgr) {
		this(new BracketTree(bufferMgr), bufferMgr);
	}

	protected BracketIndexImpl(BracketTree tree, BufferMgr bufferMgr) {
		this.tree = tree;
		this.bufferMgr = bufferMgr;
	}

	@Override
	public PageID createIndex(Tx tx, int containerNo)
			throws IndexAccessException {
		return createIndex(tx, containerNo, -1);
	}

	@Override
	public void dropIndex(Tx tx, PageID rootPageID) throws IndexAccessException {
		// TODO Auto-generated method stub

	}

	@Override
	public void dump(Tx tx, PageID rootPageID, PrintStream out)
			throws IndexAccessException {
		tree.dumpLeafs(tx, rootPageID, out);
	}

	@Override
	public BracketIter open(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode) throws IndexAccessException {
		return open(tx, rootPageID, navMode, key, openMode, null);
	}

	@Override
	public PageID createIndex(Tx tx, int containerNo, int unitID)
			throws IndexAccessException {
		Leaf root = null;

		try {
			root = tree.allocateLeaf(tx, containerNo, unitID, null, true);
			PageID rootPageID = root.getPageID();
			root.cleanup();

			return rootPageID;
		} catch (IndexOperationException e) {
			throw new IndexAccessException(e,
					"Could not create index root page.");
		}
	}

	@Override
	public BracketIter open(Tx tx, PageID rootPageID, NavigationMode navMode,
			XTCdeweyID key, OpenMode openMode, HintPageInformation hintPageInfo)
			throws IndexAccessException {
		Leaf leaf = tree.openInternal(tx, rootPageID, navMode, key, openMode,
				hintPageInfo, null);
		if (leaf != null) {
			return new BracketIterImpl(tx, tree, rootPageID, leaf, openMode,
					navMode == NavigationMode.TO_INSERT_POS ? key : null);
		} else {
			return null;
		}
	}

	public String printLeafScannerStats(NavigationMode navMode)
			throws IndexAccessException {
		return tree.printLeafScannerStats(navMode);
	}

	/**
	 * @see org.brackit.server.store.index.bracket.BracketIndex#openChildStream(org.brackit.server.node.bracket.BracketLocator,
	 *      org.brackit.server.node.XTCdeweyID,
	 *      org.brackit.server.store.index.bracket.HintPageInformation)
	 */
	@Override
	public Stream<BracketNode> openChildStream(BracketLocator locator,
			XTCdeweyID parentDeweyID, HintPageInformation hintPageInfo) {
		return new ChildStream(locator, tree, parentDeweyID, hintPageInfo);
	}
}
