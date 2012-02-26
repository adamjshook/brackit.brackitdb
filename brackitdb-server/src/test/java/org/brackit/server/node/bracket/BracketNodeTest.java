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
package org.brackit.server.node.bracket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import org.brackit.server.ServerException;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNodeTest;
import org.brackit.server.node.util.NavigationStatistics;
import org.brackit.server.node.util.Traverser;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.xquery.node.parser.CollectionParser;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.node.stream.ArrayStream;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * @author Martin Hiller
 * 
 */
public class BracketNodeTest extends TXNodeTest<BracketNode> {

	private BracketStore store;

	private static final File smallDocument = new File("xmark10.xml");
	private static final File mediumDocument = new File("xmark50.xml");
	private static final File bigDocument = new File("xmark100.xml");
	private static final boolean COLLECTION_CHECK = true;

	@Ignore
	@Test
	public void storeBigDocument() throws ServerException, IOException,
			DocumentException {
		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));
		BracketLocator locator = coll.getDocument().locator;
		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));

//		FileOutputStream outputFile = new FileOutputStream("leafs.txt");
//		PrintStream out = new PrintStream(outputFile, false, "UTF-8");
//		coll.store.index.dump(tx, locator.rootPageID, out);
//		outputFile.close();
	}

	@Ignore
	@Test
	public void storeCollection() throws ServerException, IOException,
			DocumentException {
		
		CollectionParser parser = new CollectionParser(new Stream<SubtreeParser>() {
			
			private final int MAX = 10;
			private int count = 0;
			
			@Override
			public SubtreeParser next() throws DocumentException {
				try {
					SubtreeParser parser = null;
					if (count < MAX) {
						parser = new DocumentParser(smallDocument);
						count++;
					}
					return parser;
				} catch (FileNotFoundException e) {
					throw new DocumentException(e);
				}
			}
			
			@Override
			public void close() {
			}
			
		});
		
		BracketCollection coll = createCollection(parser);

		// FileOutputStream outputFile = new FileOutputStream("leafs.txt");
		// PrintStream out = new PrintStream(outputFile, false, "UTF-8");
		// coll.store.index.dump(tx, new PageID(coll.getID()), out);
		// outputFile.close();
		
		// iterate over documents
		Stream<? extends BracketNode> docs = coll.getDocuments();
		BracketNode doc = null;
		while ((doc = docs.next()) != null) {
			System.out.println(doc);
			System.out.println(doc.hintPageInfo);
		}
		docs.close();
	}

	@Ignore
	@Test
	public void storeMediumDocument() throws ServerException, IOException,
			DocumentException {
		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				mediumDocument));
		BracketLocator locator = coll.getDocument().locator;
		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));

		// FileOutputStream outputFile = new FileOutputStream("leafs.txt");
		// PrintStream out = new PrintStream(outputFile, false, "UTF-8");
		// coll.store.index.dump(tx, locator.rootPageID, out);
		// outputFile.close();
	}

	public static void main(String[] args) throws Exception {
//		BracketNodeTest test = new BracketNodeTest();
//		test.setUp();
//		test.storeCollection();
		
		BracketMockup mockup = new BracketMockup();
		TXCollection<BracketNode> coll = mockup.createCollection("testCollection");
		
		coll.add(new DocumentParser(smallDocument));
		
//		for (int i = 0; i < 10; i++) {
//			coll.add(new DocumentParser(smallDocument));
//		}
	}

	@Ignore
	@Test
	public void testDeleteRollback() throws Exception {

		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));

		// FileOutputStream outputFile = new FileOutputStream("leafs_old.txt");
		// PrintStream out = new PrintStream(outputFile, false, "UTF-8");
		// coll.store.index.dump(tx, coll.getDocument().locator.rootPageID,
		// out);
		// outputFile.close();

		tx.commit();
		tx = sm.taMgr.begin(IsolationLevel.NONE, null, false);
		coll = coll.copyFor(tx);

		BracketLocator locator = coll.getDocument().locator;
		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));

		BracketNode test = root.getNode(new XTCdeweyID("2:1.9"));
		test.delete();

		tx.rollback();

		tx = sm.taMgr.begin(IsolationLevel.NONE, null, false);
		coll = coll.copyFor(tx);

		locator = coll.getDocument().locator;
		root = coll.getDocument().getNode(XTCdeweyID.newRootID(locator.docID));

		Node domRoot = createDomTree(new InputSource(
				new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		long start = System.currentTimeMillis();
		checkSubtreePreOrderReduced(root, domRoot); // check document index
		long end = System.currentTimeMillis();
		System.out.println("Preorder Traversal: " + (end - start) / 1000f);

		tx.commit();

		// outputFile = new FileOutputStream("leafs.txt");
		// out = new PrintStream(outputFile, false, "UTF-8");
		// coll.store.index.dump(tx, locator.rootPageID, out);
		// outputFile.close();
	}

	@Ignore
	@Test
	public void traverseBigDocumentInPreorder() throws Exception {
		verifyAgainstDOM(CheckType.PREORDER);
	}

	@Ignore
	@Test
	public void traverseBigDocumentViaChildStream() throws Exception {
		verifyAgainstDOM(CheckType.CHILDSTREAM);
	}

	@Ignore
	@Test
	public void scanSubtree() throws Exception {
		verifyAgainstDOM(CheckType.SUBTREE);
	}

	@Ignore
	@Test
	public void scanSubtreeSkipAttributes() throws Exception {
		verifyAgainstDOM(CheckType.SUBTREE_NOATTR);
	}

	@Ignore
	@Test
	public void traverseBigDocumentInPostorder() throws Exception {
		verifyAgainstDOM(CheckType.POSTORDER);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		tx = sm.taMgr.begin(IsolationLevel.NONE, null, false);
		store = new BracketStore(sm.bufferManager, sm.dictionary, sm.mls);
	}

	@Override
	protected TXCollection<BracketNode> createDocument(
			DocumentParser documentParser) throws DocumentException {
		StorageSpec spec = new StorageSpec("test", sm.dictionary);
		BracketCollection collection = new BracketCollection(tx, store);
		collection.create(spec, documentParser);
		return collection;
	}
	
	private BracketCollection createCollection(
			SubtreeParser parser) throws DocumentException {
		StorageSpec spec = new StorageSpec("test", sm.dictionary);
		BracketCollection collection = new BracketCollection(tx, store);
		collection.create(spec, parser);
		return collection;
	}

	private void traverse(boolean preorder, int times)
			throws DocumentException, FileNotFoundException {

		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));
		BracketLocator locator = coll.getDocument().locator;
		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));
		System.out.println("Document created!");

		Traverser trav = new Traverser(-1, false);
		NavigationStatistics navStats = trav.run(ctx, root, preorder ? times
				: 0, preorder ? 0 : times);
		System.out.println(navStats);

	}

	private void traverseViaChildStream(int times) throws DocumentException,
			FileNotFoundException {

		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));
		BracketLocator locator = coll.getDocument().locator;
		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));
		System.out.println("Document created!");

		for (int i = 0; i < times; i++) {
			traverseViaChildStreamAtomic(root);
		}
		System.out.println("Traversal finished!");
	}

	private void traverseViaChildStreamAtomic(BracketNode root)
			throws DocumentException {
		Stream<BracketNode> children = root.getChildren();
		BracketNode currentChild = null;
		while ((currentChild = children.next()) != null) {
			traverseViaChildStreamAtomic(currentChild);
		}
		children.close();
	}

	private enum CheckType {
		PREORDER {
			@Override
			public void doCheck(BracketNode root, Node domRoot,
					BracketNodeTest testInstance) throws Exception {
				testInstance.checkSubtreePreOrderReduced(root, domRoot);
			}
		},
		POSTORDER {
			@Override
			public void doCheck(BracketNode root, Node domRoot,
					BracketNodeTest testInstance) throws Exception {
				testInstance.checkSubtreePostOrderReduced(root, domRoot);
			}
		},
		CHILDSTREAM {
			@Override
			public void doCheck(BracketNode root, Node domRoot,
					BracketNodeTest testInstance) throws Exception {
				testInstance.checkSubtreeViaChildStream(root, domRoot);
			}
		},
		SUBTREE {
			@Override
			public void doCheck(BracketNode root, Node domRoot,
					BracketNodeTest testInstance) throws Exception {
				Stream<? extends BracketNode> nodes = root.getSubtree();
				BracketNode first = nodes.next();
				testInstance.checkSubtree(first, nodes, domRoot, false);
				nodes.close();
			}
		},
		SUBTREE_NOATTR {
			@Override
			public void doCheck(BracketNode root, Node domRoot,
					BracketNodeTest testInstance) throws Exception {
				Stream<? extends BracketNode> nodes = root.getSubtree();
				BracketNode first = nodes.next();
				testInstance.checkSubtree(first, nodes, domRoot, true);
				nodes.close();
			}
		};

		public abstract void doCheck(BracketNode root, Node domRoot,
				BracketNodeTest testInstance) throws Exception;
	}

	private void verifyAgainstDOM(CheckType type) throws Exception {

		long start = System.currentTimeMillis();
		BracketCollection coll = null;
		BracketNode document = null;

		if (COLLECTION_CHECK) {

			// insert one small document before and after the big document
			coll = createCollection(new CollectionParser(new SubtreeParser[] {
					new DocumentParser(smallDocument),
					new DocumentParser(bigDocument),
					new DocumentParser(smallDocument)
			}));
			
			Stream<? extends BracketNode> docs = coll.getDocuments();
			docs.next();
			document = docs.next();
			docs.close();

		} else {

			coll = (BracketCollection) createDocument(new DocumentParser(
					bigDocument));
			document = coll.getDocument();
		}

		BracketLocator locator = document.locator;
		long end = System.currentTimeMillis();
		System.out.println("Document created in: " + (end - start) / 1000f);

		BracketNode root = document
				.getNode(XTCdeweyID.newRootID(locator.docID));
		Node domRoot = null;

		domRoot = createDomTree(new InputSource(new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		start = System.currentTimeMillis();
		type.doCheck(root, domRoot, this);
		end = System.currentTimeMillis();
		System.out.println("Verification succeeded: " + (end - start) / 1000f);
	}
}
