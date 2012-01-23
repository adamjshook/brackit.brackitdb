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
package org.brackit.server.node.txnode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.brackit.xquery.QueryContext;
import org.brackit.xquery.util.log.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.NodeTest;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

public abstract class TXNodeTest<E extends TXNode<E>> extends NodeTest<E> {
	protected static final Logger log = Logger.getLogger(TXNodeTest.class
			.getName());

	protected static final String DOCUMENT = "<?xml version = '1.0' encoding = 'UTF-8'?>"
			+ "<Organization>"
			+ "<Department>"
			+ "<Member key=\"12\" employee=\"true\">"
			+ "<Firstname>Kurt</Firstname>"
			+ "<Lastname>Mayer</Lastname>"
			+ "<DateOfBirth>1.4.1963</DateOfBirth>"
			+ "<Title>Dr.-Ing.</Title>"
			+ "</Member>"
			+ "<Member key=\"40\"  employe=\"false\">"
			+ "<Firstname>Hans</Firstname>"
			+ "<Lastname>Mettmann</Lastname>"
			+ "<DateOfBirth>12.9.1974</DateOfBirth>"
			+ "<Title>Dipl.-Inf</Title>"
			+ "</Member>"
			+ "<Member>"
			+ "</Member>"
			+ "<Member>"
			+ "</Member>"
			+ "</Department>"
			+ "<Project id=\"4711\" priority=\"high\">"
			+ "<Title>XML-DB</Title>"
			+ "<Budget>10000</Budget>"
			+ "</Project>"
			+ "<Project id=\"666\" priority=\"evenhigher\">"
			+ "<Title>DISS</Title>"
			+ "<Budget>7000</Budget>"
			+ "<Abstract>Native<b>XML</b>-Databases</Abstract>"
			+ "</Project>"
			+ "</Organization>";

	protected BPlusIndex index;

	protected Tx tx;

	protected SysMockup sm;
	
	protected void checkSubtreePreOrderReduced(final E node, org.w3c.dom.Node domNode) throws Exception {
		E child = null;
		String nodeString = node.toString();

		if (domNode instanceof Element) {
			Element element = (Element) domNode;
			assertEquals(nodeString + " is of type element", Kind.ELEMENT,
					node.getKind());

			// System.out.println("Checking name of element " +
			// node.getDeweyID() + " level " + node.getDeweyID().getLevel() +
			// " is " + element.getNodeName());

			assertEquals(String.format("Name of node %s", nodeString),
					element.getNodeName(), node.getName().toString());
			compareAttributes(node, element);

			NodeList domChildNodes = element.getChildNodes();
			ArrayList<E> children = new ArrayList<E>();

			for (E c = node.getFirstChild(); c != null; c = c
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

				checkSubtreePreOrderReduced(child, domChild);
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
					.getNodeValue().trim(), node.getValue().stringValue());
		} else {
			throw new DocumentException("Unexpected dom node: %s",
					domNode.getClass());
		}
	}
	
	protected void checkSubtreePostOrderReduced(final E node, org.w3c.dom.Node domNode) throws Exception {
		E child = null;
		String nodeString = node.toString();

		if (domNode instanceof Element) {
			Element element = (Element) domNode;
			assertEquals(nodeString + " is of type element", Kind.ELEMENT,
					node.getKind());

			// System.out.println("Checking name of element " +
			// node.getDeweyID() + " level " + node.getDeweyID().getLevel() +
			// " is " + element.getNodeName());

			assertEquals(String.format("Name of node %s", nodeString),
					element.getNodeName(), node.getName().toString());
			compareAttributes(node, element);

			NodeList domChildNodes = element.getChildNodes();
			ArrayList<E> children = new ArrayList<E>();

			for (E c = node.getLastChild(); c != null; c = c
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

				checkSubtreePostOrderReduced(child, domChild);
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
					.getNodeValue().trim(), node.getValue().stringValue());
		} else {
			throw new DocumentException("Unexpected dom node: %s",
					domNode.getClass());
		}
	}
	
	protected void checkSubtreeViaChildStream(final E node, org.w3c.dom.Node domNode) throws Exception {
		E child = null;
		String nodeString = node.toString();

		if (domNode instanceof Element) {
			Element element = (Element) domNode;
			assertEquals(nodeString + " is of type element", Kind.ELEMENT,
					node.getKind());

			// System.out.println("Checking name of element " +
			// node.getDeweyID() + " level " + node.getDeweyID().getLevel() +
			// " is " + element.getNodeName());

			assertEquals(String.format("Name of node %s", nodeString),
					element.getNodeName(), node.getName().toString());
			compareAttributes(node, element);

			NodeList domChildNodes = element.getChildNodes();
			ArrayList<E> children = new ArrayList<E>();

			Stream<E> childStream = node.getChildren();
			for (E c = childStream.next(); c != null; c = childStream
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

				checkSubtreeViaChildStream(child, domChild);
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
					.getNodeValue().trim(), node.getValue().stringValue());
		} else {
			throw new DocumentException("Unexpected dom node: %s",
					domNode.getClass());
		}
	}
	
	protected void checkSubtree(E node, Stream<? extends E> nodes, org.w3c.dom.Node domNode, boolean skipAttributes) throws Exception {
		E child = null;
		String nodeString = node.toString();

		if (domNode instanceof Element) {
			Element element = (Element) domNode;
			assertEquals(nodeString + " is of type element", Kind.ELEMENT,
					node.getKind());

			// System.out.println("Checking name of element " +
			// node.getDeweyID() + " level " + node.getDeweyID().getLevel() +
			// " is " + element.getNodeName());

			assertEquals(String.format("Name of node %s", nodeString),
					element.getNodeName(), node.getName().toString());
			
			if (!skipAttributes) {
				compareAttributes(node, nodes, element);
			}

			NodeList domChildNodes = element.getChildNodes();

			for (int i = 0; i < domChildNodes.getLength(); i++) {
				org.w3c.dom.Node domChild = domChildNodes.item(i);
				// System.out.println("Checking if child  " + ((domChild
				// instanceof Element) ? domChild.getNodeName() :
				// domChild.getNodeValue()) + " exists under " + node);

				child = nodes.next();

				assertNotNull(String.format("child node %s of node %s", i,
						nodeString), child);

				checkSubtree(child, nodes, domChild, skipAttributes);
			}

		} else if (domNode instanceof Text) {
			Text text = (Text) domNode;

			assertEquals(
					nodeString + " is of type text : \"" + text.getNodeValue()
							+ "\"", Kind.TEXT, node.getKind());
			assertEquals(String.format("Text of node %s", nodeString), text
					.getNodeValue().trim(), node.getValue().stringValue());
		} else {
			throw new DocumentException("Unexpected dom node: %s",
					domNode.getClass());
		}
	}
	
	protected void compareAttributes(E node, Stream<? extends E> nodes, Element element) throws Exception {
		NamedNodeMap domAttributes = element.getAttributes();
		int count = domAttributes.getLength();
		HashMap<QNm, E> map = new HashMap<QNm, E>();

		E c;
		for (int i = 0; i < count; i++) {
			c = nodes.next();
			
			int ancestorLevel = 0;
			for (E ancestor = node; ancestor != null; ancestor = ancestor
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
				assertTrue(String.format("node %s is ancestor of %s", ancestor,
						c), ancestor.isAncestorOf(c));
				ancestorLevel++;
			}
			
			map.put(c.getName(), c);
		}

		// check if all stored attributes really exist
		for (int i = 0; i < count; i++) {
			Attr domAttribute = (Attr) domAttributes.item(i);
			E attribute = map.get(new QNm(domAttribute.getName()));
			assertNotNull(String.format("Attribute \"%s\" of node %s",
					domAttribute.getName(), node), attribute);
			assertEquals(attribute + " is of type attribute", Kind.ATTRIBUTE,
					attribute.getKind());
			assertEquals(String.format(
					"Value of attribute \"%s\" (%s) of node %s", domAttribute
							.getName(), attribute, node), domAttribute
					.getValue(), attribute.getValue().stringValue());
		}
	}
	
	protected org.w3c.dom.Node createDomTree(InputSource source)
			throws Exception {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(source);
			fix(document.getDocumentElement());
			return document.getDocumentElement();
		} catch (Exception e) {
			throw new DocumentException(
					"An error occured while creating DOM input source: %s", e
							.getMessage());
		}
	}

	private boolean fix(org.w3c.dom.Node node) {
		if (node == null) {
			return false;
		}
		if (node.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
			String trimmed = node.getTextContent().trim();
			if (trimmed.isEmpty()) {
				node.getParentNode().removeChild(node);
				return true;
			} else {
				node.setNodeValue(trimmed);
			}
		} else if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (fix(children.item(i))) {
					i--; // child deleted, step one back
				}
			}
		}
		return false;
	}

	@Override
	@Test
	public void testStoreDocument() throws DocumentException,
			IndexAccessException {
		TXCollection<E> locator = createDocument(new DocumentParser(DOCUMENT));

//		printIndex(((TXQueryContext) ctx).getTX(),
//				"/media/ramdisk/document.dot", locator.getID(), true);
	}

	protected void printIndex(Tx transaction, String filename, DocID docID,
			boolean showValues) throws IndexAccessException {
		try {
			PrintStream printer = new PrintStream(new File(filename));
			index.traverse(transaction, new PageID(docID.getCollectionID()),
					new DisplayVisitor(printer, showValues));
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	@After
	public void tearDown() throws DocumentException, BufferException {
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		sm = new SysMockup();
		index = new BPlusIndex(sm.bufferManager);
		tx = sm.taMgr.begin();
		ctx = new TXQueryContext(tx, null);
	}

	@Override
	protected abstract TXCollection<E> createDocument(
			DocumentParser documentParser) throws DocumentException;
}
