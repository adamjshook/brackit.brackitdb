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
package org.brackit.server.node.bracket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.brackit.server.ServerException;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNodeTest;
import org.brackit.server.node.util.NavigationStatistics;
import org.brackit.server.node.util.Traverser;
import org.brackit.server.store.index.bracket.BracketTree;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.server.tx.TxException;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Stream;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 * @author Martin Hiller
 * 
 */
public class BracketNodeTest extends TXNodeTest<BracketNode> {

	private BracketStore store;

	private static final File mediumDocument = new File("xmark50.xml");
	private static final File bigDocument = new File(
			"D:/Eclipse Projekte/xtc/BracketBenchmark/docs/xmark100.xml");

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

		Node domRoot = createDomTree(ctx, new InputSource(new FileReader(
				bigDocument)));
		System.out.println("DOM-Tree created!");

		long start = System.currentTimeMillis();
		checkSubtreePreOrderReduced(ctx, root, domRoot); // check document index
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

		domRoot = createDomTree(ctx, new InputSource(
				new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		start = System.currentTimeMillis();
		checkSubtreePreOrderReduced(ctx, root, domRoot); // check document index
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

		domRoot = createDomTree(ctx, new InputSource(
				new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		start = System.currentTimeMillis();
		checkSubtreeViaChildStream(ctx, root, domRoot); // check document index
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

		domRoot = createDomTree(ctx, new InputSource(
				new FileReader(bigDocument)));
		System.out.println("DOM-Tree created!");

		start = System.currentTimeMillis();
		checkSubtreePostOrderReduced(ctx, root, domRoot); // check document
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

	protected void checkSubtreeViaChildStream(QueryContext ctx,
			final BracketNode node, org.w3c.dom.Node domNode) throws Exception {
		BracketNode child = null;
		String nodeString = node.toString();

		if (domNode instanceof Element) {
			Element element = (Element) domNode;
			assertEquals(nodeString + " is of type element", Kind.ELEMENT,
					node.getKind());

			// System.out.println("Checking name of element " +
			// node.getDeweyID() + " level " + node.getDeweyID().getLevel() +
			// " is " + element.getNodeName());

			assertEquals(String.format("Name of node %s", nodeString),
					element.getNodeName(), node.getName());
			compareAttributes(ctx, node, element);

			NodeList domChildNodes = element.getChildNodes();
			ArrayList<BracketNode> children = new ArrayList<BracketNode>();

			Stream<BracketNode> childStream = node.getChildren();
			for (BracketNode c = childStream.next(); c != null; c = childStream
					.next()) {
				// System.out.println(String.format("-> Found child of %s : %s",
				// node, c));
				children.add(c);
			}
			childStream.close();

			childStream = node.getChildren();
			for (int i = 0; i < domChildNodes.getLength(); i++) {
				org.w3c.dom.Node domChild = domChildNodes.item(i);
				// System.out.println("Checking if child  " + ((domChild
				// instanceof Element) ? domChild.getNodeName() :
				// domChild.getNodeValue()) + " exists under " + node);

				child = childStream.next();

				assertNotNull(String.format("child node %s of node %s", i,
						nodeString), child);

				checkSubtreePreOrderReduced(ctx, child, domChild);
			}
			childStream.close();

			assertEquals(
					String.format("child count of element %s", nodeString),
					domChildNodes.getLength(), children.size());

		} else if (domNode instanceof Text) {
			Text text = (Text) domNode;

			assertEquals(
					nodeString + " is of type text : \"" + text.getNodeValue()
							+ "\"", Kind.TEXT, node.getKind());
			assertEquals(String.format("Text of node %s", nodeString), text
					.getNodeValue().trim(), node.getValue());
		} else {
			throw new DocumentException("Unexpected dom node: %s",
					domNode.getClass());
		}
	}

	protected void checkSubtreePreOrderReduced(QueryContext ctx,
			final BracketNode node, org.w3c.dom.Node domNode) throws Exception {
		BracketNode child = null;
		String nodeString = node.toString();

		if (domNode instanceof Element) {
			Element element = (Element) domNode;
			assertEquals(nodeString + " is of type element", Kind.ELEMENT,
					node.getKind());

			// System.out.println("Checking name of element " +
			// node.getDeweyID() + " level " + node.getDeweyID().getLevel() +
			// " is " + element.getNodeName());

			assertEquals(String.format("Name of node %s", nodeString),
					element.getNodeName(), node.getName());
			compareAttributes(ctx, node, element);

			NodeList domChildNodes = element.getChildNodes();
			ArrayList<BracketNode> children = new ArrayList<BracketNode>();

			for (BracketNode c = node.getFirstChild(); c != null; c = c
					.getNextSibling()) {
				// System.out.println(String.format("-> Found child of %s : %s",
				// node, c));
				children.add(c);
			}

			for (int i = 0; i < domChildNodes.getLength(); i++) {
				org.w3c.dom.Node domChild = domChildNodes.item(i);
				// System.out.println("Checking if child  " + ((domChild
				// instanceof Element) ? domChild.getNodeName() :
				// domChild.getNodeValue()) + " exists under " + node);

				if (child == null) {
					child = node.getFirstChild();
					// System.out.println(String.format("First child of %s is %s",
					// node, child));
				} else {
					child = child.getNextSibling();
					// System.out.println(String.format("Next sibling of %s is %s",
					// oldChild, child));
				}

				assertNotNull(String.format("child node %s of node %s", i,
						nodeString), child);

				checkSubtreePreOrderReduced(ctx, child, domChild);
			}

			assertEquals(
					String.format("child count of element %s", nodeString),
					domChildNodes.getLength(), children.size());

		} else if (domNode instanceof Text) {
			Text text = (Text) domNode;

			assertEquals(
					nodeString + " is of type text : \"" + text.getNodeValue()
							+ "\"", Kind.TEXT, node.getKind());
			assertEquals(String.format("Text of node %s", nodeString), text
					.getNodeValue().trim(), node.getValue());
		} else {
			throw new DocumentException("Unexpected dom node: %s",
					domNode.getClass());
		}
	}

	protected void checkSubtreePostOrderReduced(QueryContext ctx,
			final BracketNode node, org.w3c.dom.Node domNode) throws Exception {
		BracketNode child = null;
		String nodeString = node.toString();

		if (domNode instanceof Element) {
			Element element = (Element) domNode;
			assertEquals(nodeString + " is of type element", Kind.ELEMENT,
					node.getKind());

			// System.out.println("Checking name of element " +
			// node.getDeweyID() + " level " + node.getDeweyID().getLevel() +
			// " is " + element.getNodeName());

			assertEquals(String.format("Name of node %s", nodeString),
					element.getNodeName(), node.getName());
			compareAttributes(ctx, node, element);

			NodeList domChildNodes = element.getChildNodes();
			ArrayList<BracketNode> children = new ArrayList<BracketNode>();

			for (BracketNode c = node.getLastChild(); c != null; c = c
					.getPreviousSibling()) {
				// System.out.println(String.format("-> Found child of %s : %s",
				// node, c));
				children.add(c);
			}

			for (int i = domChildNodes.getLength() - 1; i >= 0; i--) {
				org.w3c.dom.Node domChild = domChildNodes.item(i);
				// System.out.println("Checking if child  " + ((domChild
				// instanceof Element) ? domChild.getNodeName() :
				// domChild.getNodeValue()) + " exists under " + node);

				if (child == null) {
					child = node.getLastChild();
					// System.out.println(String.format("First child of %s is %s",
					// node, child));
				} else {
					child = child.getPreviousSibling();
					// System.out.println(String.format("Next sibling of %s is %s",
					// oldChild, child));
				}

				assertNotNull(String.format("child node %s of node %s", i,
						nodeString), child);

				checkSubtreePostOrderReduced(ctx, child, domChild);
			}

			assertEquals(
					String.format("child count of element %s", nodeString),
					domChildNodes.getLength(), children.size());

		} else if (domNode instanceof Text) {
			Text text = (Text) domNode;

			assertEquals(
					nodeString + " is of type text : \"" + text.getNodeValue()
							+ "\"", Kind.TEXT, node.getKind());
			assertEquals(String.format("Text of node %s", nodeString), text
					.getNodeValue().trim(), node.getValue());
		} else {
			throw new DocumentException("Unexpected dom node: %s",
					domNode.getClass());
		}
	}

	protected void compareAttributes(QueryContext ctx, BracketNode node,
			Element element) throws Exception {
		NamedNodeMap domAttributes = element.getAttributes();
		Stream<? extends BracketNode> attributes = node.getAttributes();

		int attributesSize = 0;
		BracketNode c;
		while ((c = attributes.next()) != null) {
			attributesSize++;

			int ancestorLevel = 0;
			for (BracketNode ancestor = node; ancestor != null; ancestor = ancestor
					.getParent()) {
				if (ancestorLevel == 0) {
					try {
						assertTrue(String.format("node %s is attribute of %s",
								c, ancestor), c.isAttributeOf(ancestor));
					} catch (AssertionError e) {
						c.isAttributeOf(ancestor);
						throw e;
					}
					assertTrue(String.format("node %s is parent of %s",
							ancestor, c), ancestor.isParentOf(c));
				}
				assertTrue(
						String.format("node %s is ancestor of %s", ancestor, c),
						ancestor.isAncestorOf(c));
				ancestorLevel++;
			}
		}
		attributes.close();

		assertEquals(String.format("attribute count of element %s", node),
				domAttributes.getLength(), attributesSize);

		// check if all stored attributes really exist
		for (int i = 0; i < domAttributes.getLength(); i++) {
			Attr domAttribute = (Attr) domAttributes.item(i);
			BracketNode attribute = node.getAttribute(domAttribute.getName());
			assertNotNull(
					String.format("Attribute \"%s\" of node %s",
							domAttribute.getName(), node), attribute);
			assertEquals(attribute + " is of type attribute", Kind.ATTRIBUTE,
					attribute.getKind());
			assertEquals(String.format(
					"Value of attribute \"%s\" (%s) of node %s",
					domAttribute.getName(), attribute, node),
					domAttribute.getValue(), attribute.getValue());
		}
	}

	protected org.w3c.dom.Node createDomTree(QueryContext ctx,
			InputSource source) throws Exception {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(source);
			fix(document.getDocumentElement());
			return document.getDocumentElement();
		} catch (Exception e) {
			throw new DocumentException(
					"An error occured while creating DOM input source: %s",
					e.getMessage());
		}
	}

	private boolean fix(org.w3c.dom.Node node) {
		if (node == null) {
			return false;
		}
		if (node.getNodeType() == Node.TEXT_NODE) {
			String trimmed = node.getTextContent().trim();
			if (trimmed.isEmpty()) {
				node.getParentNode().removeChild(node);
				return true;
			} else {
				node.setNodeValue(trimmed);
			}
		} else if (node.getNodeType() == Node.ELEMENT_NODE) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (fix(children.item(i))) {
					i--; // child deleted, step one back
				}
			}
		}
		return false;
	}

}
