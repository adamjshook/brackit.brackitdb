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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.brackit.server.ServerException;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNodeTest;
import org.brackit.server.node.util.NavigationStatistics;
import org.brackit.server.node.util.Traverser;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.MultiChildStreamMockup;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.d2linked.D2Node;
import org.brackit.xquery.node.d2linked.D2NodeFactory;
import org.brackit.xquery.node.parser.CollectionParser;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.node.parser.StreamSubtreeParser;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
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

		// FileOutputStream outputFile = new FileOutputStream("leafs.txt");
		// PrintStream out = new PrintStream(outputFile, false, "UTF-8");
		// coll.store.index.dump(tx, locator.rootPageID, out);
		// outputFile.close();
	}

	@Ignore
	@Test
	public void storeCollection() throws ServerException, IOException,
			DocumentException {

		CollectionParser parser = new CollectionParser(
				new Stream<SubtreeParser>() {

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
		BracketNodeTest test = new BracketNodeTest();
		test.setUp();
		test.multiChildStreamTest();
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
		verifyAgainstDOM(bigDocument, CheckType.PREORDER);
	}

	@Ignore
	@Test
	public void traverseBigDocumentViaChildStream() throws Exception {
		verifyAgainstDOM(bigDocument, CheckType.CHILDSTREAM);
	}

	@Ignore
	@Test
	public void scanSubtree() throws Exception {
		verifyAgainstDOM(bigDocument, CheckType.SUBTREE);
	}

	@Ignore
	@Test
	public void scanSubtreeSkipAttributes() throws Exception {
		verifyAgainstDOM(bigDocument, CheckType.SUBTREE_NOATTR);
	}

	@Ignore
	@Test
	public void traverseBigDocumentInPostorder() throws Exception {
		verifyAgainstDOM(bigDocument, CheckType.POSTORDER);
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

	private BracketCollection createCollection(SubtreeParser parser)
			throws DocumentException {
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
				Stream<? extends BracketNode> nodes = root
						.getDescendantOrSelf();
				BracketNode first = nodes.next();
				testInstance.checkSubtree(first, nodes, domRoot, true);
				nodes.close();
			}
		};

		public abstract void doCheck(BracketNode root, Node domRoot,
				BracketNodeTest testInstance) throws Exception;
	}

	private void verifyAgainstDOM(File docFile, CheckType... types)
			throws Exception {

		long start = System.currentTimeMillis();
		BracketCollection coll = null;
		BracketNode document = null;

		if (COLLECTION_CHECK) {

			// insert one small document before and after the big document
			coll = createCollection(new CollectionParser(new SubtreeParser[] {
					new DocumentParser(smallDocument),
					new DocumentParser(docFile),
					new DocumentParser(smallDocument) }));

			Stream<? extends BracketNode> docs = coll.getDocuments();
			docs.next();
			document = docs.next();
			docs.close();

		} else {

			coll = (BracketCollection) createDocument(new DocumentParser(
					docFile));
			document = coll.getDocument();
		}

		BracketLocator locator = document.locator;
		long end = System.currentTimeMillis();
		System.out.println("Document created in: " + (end - start) / 1000f);

		BracketNode root = document
				.getNode(XTCdeweyID.newRootID(locator.docID));

		verifyAgainstDOM(docFile, root, types);
	}

	private void verifyAgainstDOM(File docFile, BracketNode root,
			CheckType... types) throws Exception {

		Node domRoot = null;
		long start = 0;
		long end = 0;

		domRoot = createDomTree(new InputSource(new FileReader(docFile)));
		System.out.println("DOM-Tree created!");

		Throwable[] failures = new Throwable[types.length];

		for (int i = 0; i < types.length; i++) {
			try {
				start = System.currentTimeMillis();
				types[i].doCheck(root, domRoot, this);
				end = System.currentTimeMillis();
				System.out.println(String.format("%s successful in: "
						+ (end - start) / 1000f, types[i].name()));
			} catch (Throwable e) {
				failures[i] = e;
				System.out.println(String.format(
						"%s failed after: "
								+ (System.currentTimeMillis() - start) / 1000f,
						types[i].name()));
			}
		}

		System.out.println();

		boolean passed = true;
		for (int i = 0; i < failures.length; i++) {
			if (failures[i] != null) {
				// assertion error or exception
				passed = false;
				System.out.println(failures[i]);
			}
		}

		assertTrue(passed);
	}

	@Ignore
	@Test
	public void testReplace() throws DocumentException, FileNotFoundException {

		BracketCollection coll = createCollection(new DocumentParser(
				bigDocument));
		BracketNode root = coll.getDocument().getFirstChild();

		BracketCollection ref = createCollection(new StreamSubtreeParser(
				root.getSubtree()));

		BracketNode toBeReplaced = root.getNode(new XTCdeweyID(root
				.getDeweyID().docID, "1.9"));
		BracketNode replacement = ref.getDocument().getNode(
				new XTCdeweyID(root.getDeweyID().docID, "1.9"));

		toBeReplaced.replaceWith(replacement);

		indexLookup(ref.getDocument().getFirstChild(), root);
	}

	private static void indexLookup(BracketNode ref, BracketNode test)
			throws DocumentException {

		DocID docID = test.getDeweyID().getDocID();

		Stream<? extends BracketNode> stream = ref.getSubtree();
		BracketNode current = null;
		while ((current = stream.next()) != null) {

			XTCdeweyID deweyID = new XTCdeweyID(docID, current.getDeweyID()
					.getDivisionValues());

			BracketNode node = test.getNode(deweyID);
			if (node == null) {
				throw new RuntimeException("Node not found!");
			}

			QNm name1 = current.getName();
			QNm name2 = node.getName();
			assertEquals(name1, name2);

			if (current.getKind().ID != Kind.ELEMENT.ID) {
				String value1 = (current.getValue() != null ? current
						.getValue().stringValue() : null);
				String value2 = (node.getValue() != null ? node.getValue()
						.stringValue() : null);
				assertEquals(value1, value2);
			}
		}
		stream.close();
	}

	@Ignore
	@Test
	public void ultimateNavigationTest() throws Exception {
		verifyAgainstDOM(bigDocument, createConfusedDocument(bigDocument),
				CheckType.values());
	}

	@Ignore
	@Test
	public void testReplaceNavigate() throws Exception {

		BracketCollection coll = createCollection(new DocumentParser(
				bigDocument));
		BracketNode root = coll.getDocument().getFirstChild();

		BracketNode toBeReplaced = root.getNode(new XTCdeweyID(root
				.getDeweyID().docID, "1.9"));

		BracketCollection backup = createCollection(new StreamSubtreeParser(
				toBeReplaced.getSubtree()));
		BracketNode backupRoot = backup.getDocument().getFirstChild();

		toBeReplaced.replaceWith(backupRoot);

		System.out.println("Replacing finished.");

		verifyAgainstDOM(bigDocument, root, CheckType.values());
	}

	@Ignore
	@Test
	public void emptyLastPageTest() throws Exception {

		BracketCollection coll = createCollection(new DocumentParser(
				bigDocument));
		BracketNode root = coll.getDocument().getFirstChild();
		BracketNode lastChild = root.getLastChild();

		D2NodeFactory fac = new D2NodeFactory();
		D2Node backup = fac.build(new StreamSubtreeParser(lastChild
				.getSubtree()));

		lastChild.insertBefore(backup);
		lastChild.delete();

		System.out.println("Empty last page produced.");

		verifyAgainstDOM(bigDocument, root, CheckType.values());
	}

	private BracketNode createConfusedDocument(File docFile)
			throws DocumentException, FileNotFoundException {

		int avgNumberOfSubtrees = 2500;
		int maxLevel = 5;

		D2NodeFactory fac = new D2NodeFactory();
		long seed = System.currentTimeMillis();

		System.out
				.println(String.format("Seed for Random generator: %s", seed));

		Random ran = new Random(seed);

		List<int[]> toMove = new ArrayList<int[]>();
		List<Integer> prefixList = new ArrayList<Integer>();

		SubtreeParser parser = null;
		int docNumber = 0;
		if (COLLECTION_CHECK) {
			parser = new CollectionParser(new SubtreeParser[] {
					new DocumentParser(smallDocument),
					new DocumentParser(docFile),
					new DocumentParser(smallDocument) });
			docNumber = 1;
		} else {
			parser = new DocumentParser(docFile);
		}

		BracketCollection coll = createCollection(parser);
		BracketNode root = (new BracketNode(coll, docNumber)).getFirstChild();
		DocID docID = root.getDeweyID().docID;

		int numberOfRelevantNodes = 0;
		Stream<? extends BracketNode> s = root.getDescendantOrSelf();
		BracketNode current = s.next();
		while ((current = s.next()) != null) {
			if (current.getKind().ID == Kind.ELEMENT.ID
					&& current.getDeweyID().getLevel() <= maxLevel) {
				numberOfRelevantNodes++;
			}
		}
		s.close();

		int moveRate = numberOfRelevantNodes / avgNumberOfSubtrees;

		s = root.getDescendantOrSelf();
		current = s.next();
		while ((current = s.next()) != null) {
			if (current.getKind().ID == Kind.ELEMENT.ID
					&& current.getDeweyID().getLevel() <= maxLevel
					&& ran.nextInt(moveRate) == 0) {
				// remember this subtree
				int[] currentDivisions = current.getDeweyID().divisionValues;
				int prefixIndex = -1;

				int[] tempDivisions = null;

				for (int i = toMove.size() - 1; i >= 0; i--) {

					ArrayList<int[]> chain = new ArrayList<int[]>();
					int length = 0;
					for (int j = i; j >= 0; j = prefixList.get(j)) {
						int[] div = toMove.get(j);
						chain.add(div);
						length += div.length;
					}
					tempDivisions = new int[length];
					int index = 0;
					for (int j = chain.size() - 1; j >= 0; j--) {
						int[] div = chain.get(j);
						System.arraycopy(div, 0, tempDivisions, index,
								div.length);
						index += div.length;
					}

					if (tempDivisions.length >= currentDivisions.length) {
						continue;
					}

					boolean prefix = true;
					for (int j = 0; j < tempDivisions.length; j++) {
						if (tempDivisions[j] != currentDivisions[j]) {
							prefix = false;
							break;
						}
					}

					if (prefix) {
						prefixIndex = i;
						break;
					}
				}

				int[] toStore = currentDivisions;
				if (prefixIndex >= 0) {
					toStore = Arrays.copyOfRange(currentDivisions,
							tempDivisions.length, currentDivisions.length);
				}

				toMove.add(toStore);
				prefixList.add(prefixIndex);
			}
		}
		s.close();

		System.out.println(String.format("Number of subtrees to be moved: %s",
				toMove.size()));

		int[] divisions = null;
		int[] empty = new int[0];

		// subtrees to move determined
		for (int i = 0; i < toMove.size(); i++) {
			int[] d1 = empty;
			int[] d2 = toMove.get(i);
			int prefixIndex = prefixList.get(i);
			if (prefixIndex >= 0) {
				d1 = toMove.get(prefixIndex);
			}

			divisions = new int[d1.length + d2.length];
			System.arraycopy(d1, 0, divisions, 0, d1.length);
			System.arraycopy(d2, 0, divisions, d1.length, d2.length);

			XTCdeweyID deweyID = new XTCdeweyID(docID, divisions);

			// move subtree
			BracketNode node = root.getNodeInternal(deweyID);
			D2Node backup = fac
					.build(new StreamSubtreeParser(node.getSubtree()));
			BracketNode newNode = node.insertAfter(backup);
			node.delete();

			toMove.set(i, newNode.getDeweyID().divisionValues);
		}

		System.out.println("Moving finished.");

		return root;
	}

	@Ignore
	@Test
	public void multiChildStreamTest() throws DocumentException,
			FileNotFoundException {

		final int depth = 3;

		BracketNode root = createConfusedDocument(bigDocument);

		System.out.println("\nStart testing MultiChildStream...");

		int count1 = 0;
		int count2 = 0;

		Stream<? extends BracketNode> stream = root.getDescendantOrSelf();
		BracketNode node = null;
		while ((node = stream.next()) != null) {

			count1++;

			Stream<BracketNode> multiChildStream = store.index
					.openMultiChildStream(node.locator, node.getDeweyID(),
							node.hintPageInfo, new BracketFilter[depth]);

			// BracketNode current = null;
			// while ((current = multiChildStream.next()) != null) {
			// }
			// multiChildStream.close();

			Stream<BracketNode> multiChildStreamMockup = new MultiChildStreamMockup(
					node.locator, new BracketTree(store.bufferMgr),
					node.getDeweyID(), node.hintPageInfo,
					new BracketFilter[depth]);

			// compare both streams

			BracketNode current = null;
			BracketNode expected = null;
			while ((expected = multiChildStreamMockup.next()) != null) {

				count2++;
				current = multiChildStream.next();

				assertNotNull(current);
				assertEquals(expected.getDeweyID(), current.getDeweyID());
				assertEquals(expected.getKind(), current.getKind());
				if (expected.getKind() == Kind.ELEMENT) {
					assertEquals(expected.getName(), current.getName());
				} else {
					assertTrue(expected.getKind() == Kind.TEXT);
					assertEquals(expected.getValue().stringValue(), current
							.getValue().stringValue());
				}
			}
			multiChildStreamMockup.close();

			assertNull(multiChildStream.next());
			multiChildStream.close();

			// System.out.println(String.format("Test successful for node %s",
			// node));
		}
		stream.close();
		System.out
				.println(String
						.format("Test successful!\nNumber of streams: %s\nNumber of compared nodes: %s",
								count1, count2));
	}

	@Ignore
	@Test
	public void compareMultiChildStreams() throws DocumentException,
			FileNotFoundException {

		BracketNode root = createConfusedDocument(bigDocument);

		long time1 = Long.MAX_VALUE;
		long time2 = Long.MAX_VALUE;

		for (int i = 0; i < 100; i++) {

			Stream<? extends BracketNode> stream1 = store.index
					.openMultiChildStream(root.locator, root.getDeweyID(),
							root.hintPageInfo, new BracketFilter[3]);

			Stream<? extends BracketNode> stream2 = new MultiChildStreamMockup(
					root.locator, new BracketTree(store.bufferMgr),
					root.getDeweyID(), root.hintPageInfo, new BracketFilter[3]);

			long start = System.currentTimeMillis();
			while (stream1.next() != null)
				;
			stream1.close();
			long end = System.currentTimeMillis();
			time1 = Math.min(end - start, time1);

			start = System.currentTimeMillis();
			while (stream2.next() != null)
				;
			stream2.close();
			end = System.currentTimeMillis();
			time2 = Math.min(end - start, time2);
		}

		System.out.println(String.format("\nMinimum MultiChildStream: %s",
				time1));
		System.out.println(String.format("Minimum ChildStream Forking: %s",
				time2));
		System.out.println(String.format("Saving: %s %%",
				(1 - ((double) time1 / time2)) * 100));
	}
}
