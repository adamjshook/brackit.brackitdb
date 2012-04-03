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
package org.brackit.server.store.index.bracket;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.index.bracket.page.Branch;
import org.brackit.server.store.index.bracket.page.BranchBPContext;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.index.bracket.page.LeafBPContext;
import org.brackit.server.store.index.bracket.page.LeafBPContextTest;
import org.brackit.server.store.page.bracket.BracketPage;
import org.brackit.server.store.page.keyvalue.SlottedKeyValuePage;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.xquery.xdm.DocumentException;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Martin Hiller
 * 
 */
public class BracketTreeTest {

	protected static final String CONTAINER_NAME = LeafBPContextTest.class
			.getSimpleName() + ".cnt";
	protected static final int CONTAINER_NO = 0;
	protected static final int BUFFER_SIZE = 500;
	protected static final int EXTEND_SIZE = 300;
	protected static final int BLOCK_SIZE = 4096;
	protected static final int INITIAL_SIZE = 20;

	private TxMgr txMgr;
	private Tx tx;

	private BracketTree tree;
	private Branch root;
	private Leaf leaf1;
	private Leaf leaf2;
	private Leaf leaf3;

	private DocID docID;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		txMgr = new TaMgrMockup();
		BufferMgr bufferManager = txMgr.getBufferManager();
		bufferManager.createBuffer(BUFFER_SIZE, BLOCK_SIZE, CONTAINER_NO,
				CONTAINER_NAME, INITIAL_SIZE, EXTEND_SIZE);
		Buffer buffer = bufferManager.getBuffer(CONTAINER_NO);
		
		int unitID = buffer.createUnit();

		tx = txMgr.begin(IsolationLevel.SERIALIZABLE, null, false);

		docID = new DocID(99, 0);

		// create root
		root = new BranchBPContext(bufferManager, tx, new SlottedKeyValuePage(
				buffer, buffer.allocatePage(tx, unitID, new PageID(99), true, -1),
				BranchBPContext.RESERVED_SIZE));
		root.format(new PageID(99), 1, false, true, -1);
		root.setLastInLevel(true);

		// create three sample leafs
		leaf1 = new LeafBPContext(bufferManager, tx, new BracketPage(buffer,
				buffer.allocatePage(tx, unitID, new PageID(1), true, -1)));
		leaf1.format(new PageID(99), true, -1);
		leaf2 = new LeafBPContext(bufferManager, tx, new BracketPage(buffer,
				buffer.allocatePage(tx, unitID, new PageID(2), true, -1)));
		leaf2.format(new PageID(99), true, -1);
		leaf3 = new LeafBPContext(bufferManager, tx, new BracketPage(buffer,
				buffer.allocatePage(tx, unitID, new PageID(3), true, -1)));
		leaf3.format(new PageID(99), true, -1);
		
		// chain leafs
		leaf1.setNextPageID(new PageID(2), true, -1);
		leaf2.setPrevPageID(new PageID(1), true, -1);
		leaf2.setNextPageID(new PageID(3), true, -1);
		leaf3.setPrevPageID(new PageID(2), true, -1);

		fill();

		root.cleanup();
		leaf1.cleanup();
		leaf2.cleanup();
		leaf3.cleanup();
		
		// create tree
		tree = new BracketTree(bufferManager);

		tx.commit();
		tx = txMgr.begin(IsolationLevel.SERIALIZABLE, null, false);

//		Handle handle1 = buffer.fixPage(tx, new PageID(1));
//		handle1.latchX();
//		leaf1 = new LeafBPContext(bufferManager, tx, new BracketPage(buffer,
//				handle1));
//		Handle handle2 = buffer.fixPage(tx, new PageID(2));
//		handle2.latchX();
//		leaf2 = new LeafBPContext(bufferManager, tx, new BracketPage(buffer,
//				handle2));

	}

	private void fill() throws IndexOperationException, DocumentException {

		leaf1.insertRecordAfter(new XTCdeweyID(docID, "1.5.1.3"),
				"attribute1".getBytes(), 2, true, -1, true);
		leaf1.insertRecordAfter(new XTCdeweyID(docID, "1.5.3"),
				"text1".getBytes(), 0, true, -1, true);
		leaf1.insertRecordAfter(new XTCdeweyID(docID, "1.5.7"),
				"text2".getBytes(), 0, true, -1, true);
		leaf1.bulkLog(false, -1);
		
		leaf1.setHighKey(new XTCdeweyID(docID, "1.6.3"), true, -1);
		
		leaf2.insertRecordAfter(new XTCdeweyID(docID, "1.6.3.3"),
				"text3".getBytes(), 1, true, -1, true);
		leaf2.insertRecordAfter(new XTCdeweyID(docID, "1.6.3.5"),
				"text4".getBytes(), 0, true, -1, true);
		leaf2.bulkLog(false, -1);
		
		leaf2.setHighKey(new XTCdeweyID(docID, "1.7"), true, -1);
		
		leaf3.insertRecordAfter(new XTCdeweyID(docID, "1.7.1.5"),
				"attribute2".getBytes(), 1, true, -1, true);
		leaf3.insertRecordAfter(new XTCdeweyID(docID, "1.7.1.7"),
				"attribute3".getBytes(), 0, true, -1, true);
		leaf3.insertRecordAfter(new XTCdeweyID(docID, "1.7.2.3"),
				"text5".getBytes(), 0, true, -1, true);
		leaf3.insertRecordAfter(new XTCdeweyID(docID, "1.7.3"),
				"text6".getBytes(), 0, true, -1, true);
		leaf3.bulkLog(false, -1);
		
		
		root.setLowPageID(leaf1.getPageID(), true, -1);
		root.insert(new XTCdeweyID(docID, "1.6.3").toBytes(), leaf2.getPageID().getBytes(), true, -1);
		root.moveNext();
		root.insert(new XTCdeweyID(docID, "1.7").toBytes(), leaf3.getPageID().getBytes(), true, -1);

	}

	@Test
	public void testDeleteSequence() throws Exception {

		tree.deleteSequence(tx, new PageID(99), new XTCdeweyID(docID, "1.5.7"), new XTCdeweyID(docID, "1.7.1.7"), new PageID(2), true, -1);
		tx.rollback();

	}
}
