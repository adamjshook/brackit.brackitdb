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
package org.brackit.server.node.txnode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.node.DocID;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.QueryContext;
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
import org.xml.sax.InputSource;

public abstract class TXNodeTest<E extends TXNode<E>> {
	protected static final Logger log = Logger.getLogger(TXNodeTest.class
			.getName());

	protected BPlusIndex index;

	protected Tx tx;

	protected SysMockup sm;
	
	protected QueryContext ctx;

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
	
	@Test
	public void testStoreDocument() throws DocumentException,
			IndexAccessException {
		TXCollection<E> locator = createDocument(new DocumentParser(DOCUMENT));

		printIndex(((TXQueryContext) ctx).getTX(),
				"/media/ramdisk/document.dot", locator.getID(), true);
	}

	protected void printIndex(Tx transaction, String filename, DocID docID,
			boolean showValues) throws IndexAccessException {
		try {
			PrintStream printer = new PrintStream(new File(filename));
			index.traverse(transaction, new PageID(docID.value()),
					new DisplayVisitor(printer, showValues));
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@After
	public void tearDown() throws DocumentException, BufferException {
	}

	@Before
	public void setUp() throws Exception {
		ctx = new QueryContext(null);
		sm = new SysMockup();
		index = new BPlusIndex(sm.bufferManager);
		tx = sm.taMgr.begin();
		ctx = new TXQueryContext(tx, null);
	}

	protected abstract TXCollection<E> createDocument(
			DocumentParser documentParser) throws DocumentException;
	
	protected org.w3c.dom.Node createDomTree(QueryContext ctx,
			InputSource source) throws Exception {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(source);
			return document.getDocumentElement();
		} catch (Exception e) {
			throw new DocumentException(
					"An error occured while creating DOM input source: %s", e
							.getMessage());
		}
	}
	
	protected void compareAttributes(QueryContext ctx, E node, Element element)
	throws Exception {
		NamedNodeMap domAttributes = element.getAttributes();
		Stream<? extends E> attributes = node.getAttributes();
		
		int attributesSize = 0;
		E c;
		while ((c = attributes.next()) != null) {
			attributesSize++;
		
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
		}
		attributes.close();
		
		assertEquals(String.format("attribute count of element %s", node),
				domAttributes.getLength(), attributesSize);
		
		// check if all stored attributes really exist
		for (int i = 0; i < domAttributes.getLength(); i++) {
			Attr domAttribute = (Attr) domAttributes.item(i);
			E attribute = node.getAttribute(domAttribute.getName());
			assertNotNull(String.format("Attribute \"%s\" of node %s",
					domAttribute.getName(), node), attribute);
			assertEquals(attribute + " is of type attribute", Kind.ATTRIBUTE,
					attribute.getKind());
			assertEquals(String.format(
					"Value of attribute \"%s\" (%s) of node %s", domAttribute
							.getName(), attribute, node), domAttribute
					.getValue(), attribute.getValue());
		}
	}
}
