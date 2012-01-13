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
import java.io.FileReader;
import java.io.IOException;

import org.brackit.server.ServerException;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNodeTest;
import org.brackit.server.node.util.NavigationStatistics;
import org.brackit.server.node.util.Traverser;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.xquery.node.parser.DocumentParser;
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

	private static final File mediumDocument = new File("xmark50.xml");
	private static final File bigDocument = new File("xmark100.xml");

	@Ignore
	@Test
	public void storeBigDocument() throws ServerException, IOException,
			DocumentException {
		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));
		BracketLocator locator = coll.getDocument().locator;
		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));

		// FileOutputStream outputFile = new FileOutputStream("leafs.txt");
		// PrintStream out = new PrintStream(outputFile, false, "UTF-8");
		// coll.store.index.dump(tx, locator.rootPageID, out);
		// outputFile.close();
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
		BracketNodeTest test = new BracketNodeTest();
		test.setUp();
		test.traverse(true, 1);
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

		long start = System.currentTimeMillis();
		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));
		BracketLocator locator = coll.getDocument().locator;
		long end = System.currentTimeMillis();
		System.out.println("Document created in: " + (end - start) / 1000f);

		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));
		Node domRoot = null;

		domRoot = createDomTree(new InputSource(new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		start = System.currentTimeMillis();
		checkSubtreePreOrderReduced(root, domRoot); // check document index
		end = System.currentTimeMillis();
		System.out.println("Preorder Traversal: " + (end - start) / 1000f);

		if (BracketTree.COLLECT_STATS) {
			System.out
					.println("\nLeafScannerStats for NextAttribute:\n\n"
							+ locator.collection.store.index
									.printLeafScannerStats(NavigationMode.NEXT_ATTRIBUTE));
			System.out.println("\nLeafScannerStats for FirstChild:\n\n"
					+ locator.collection.store.index
							.printLeafScannerStats(NavigationMode.FIRST_CHILD));
			System.out
					.println("\nLeafScannerStats for NextSibling:\n\n"
							+ locator.collection.store.index
									.printLeafScannerStats(NavigationMode.NEXT_SIBLING));
		}
	}

	@Ignore
	@Test
	public void traverseBigDocumentViaChildStream() throws Exception {

		long start = System.currentTimeMillis();
		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));
		BracketLocator locator = coll.getDocument().locator;
		long end = System.currentTimeMillis();
		System.out.println("Document created in: " + (end - start) / 1000f);

		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));
		Node domRoot = null;

		domRoot = createDomTree(new InputSource(new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		start = System.currentTimeMillis();
		checkSubtreeViaChildStream(root, domRoot); // check document index
		end = System.currentTimeMillis();
		System.out.println("Preorder Traversal: " + (end - start) / 1000f);

		if (BracketTree.COLLECT_STATS) {
			System.out
					.println("\nLeafScannerStats for NextAttribute:\n\n"
							+ locator.collection.store.index
									.printLeafScannerStats(NavigationMode.NEXT_ATTRIBUTE));
			System.out.println("\nLeafScannerStats for FirstChild:\n\n"
					+ locator.collection.store.index
							.printLeafScannerStats(NavigationMode.FIRST_CHILD));
			System.out
					.println("\nLeafScannerStats for NextSibling:\n\n"
							+ locator.collection.store.index
									.printLeafScannerStats(NavigationMode.NEXT_SIBLING));
		}
	}

	@Ignore
	@Test
	public void traverseBigDocumentInPostorder() throws Exception {

		long start = System.currentTimeMillis();
		BracketCollection coll = (BracketCollection) createDocument(new DocumentParser(
				bigDocument));
		BracketLocator locator = coll.getDocument().locator;
		long end = System.currentTimeMillis();
		System.out.println("Document created in: " + (end - start) / 1000f);

		BracketNode root = coll.getDocument().getNode(
				XTCdeweyID.newRootID(locator.docID));
		Node domRoot = null;

		domRoot = createDomTree(new InputSource(new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		start = System.currentTimeMillis();
		checkSubtreePostOrderReduced(root, domRoot); // check document
		// index
		end = System.currentTimeMillis();
		System.out.println("Postorder Traversal: " + (end - start) / 1000f);

		if (BracketTree.COLLECT_STATS) {
			System.out
					.println("\nLeafScannerStats for NextAttribute:\n\n"
							+ locator.collection.store.index
									.printLeafScannerStats(NavigationMode.NEXT_ATTRIBUTE));
			System.out.println("\nLeafScannerStats for LastChild:\n\n"
					+ locator.collection.store.index
							.printLeafScannerStats(NavigationMode.LAST_CHILD));
			System.out
					.println("\nLeafScannerStats for PreviousSibling:\n\n"
							+ locator.collection.store.index
									.printLeafScannerStats(NavigationMode.PREVIOUS_SIBLING));
		}
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
}
