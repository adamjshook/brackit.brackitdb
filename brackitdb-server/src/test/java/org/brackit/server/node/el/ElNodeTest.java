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
package org.brackit.server.node.el;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNodeTest;
import org.brackit.server.tx.TxException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.SubtreePrinter;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.node.parser.FragmentHelper;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElNodeTest extends TXNodeTest<ElNode> {
	protected ElStore elStore;

	@Test
	public void testFromBytes() throws Exception {
		Collection<ElNode> coll = createDocument(new DocumentParser(DOCUMENT));
		ElNode root = coll.getDocument();
		XTCdeweyID deweyID = new XTCdeweyID(root.getID(), new int[]{1,3});
		ElNode department = root.getNode(deweyID);
		check(department, deweyID, Kind.ELEMENT, "Department", "KurtMayer1.4.1963Dr.-Ing.HansMettmann12.9.1974Dipl.-Inf");
		XTCdeweyID deweyID2 = new XTCdeweyID(root.getID(), new int[]{1,3,3});
		ElNode member = root.getNode(deweyID2);
		check(member, deweyID2, Kind.ELEMENT, "Member", "KurtMayer1.4.1963Dr.-Ing.");
		XTCdeweyID deweyID3 = new XTCdeweyID(root.getID(), new int[]{1,3,3,3});
		ElNode firstname = root.getNode(deweyID3);
		check(firstname, deweyID3, Kind.ELEMENT, "Firstname", "Kurt");
		XTCdeweyID deweyID4 = new XTCdeweyID(root.getID(), new int[]{1,3,3,3,3});
		ElNode firstnameT = root.getNode(deweyID4);
		check(firstnameT, deweyID4, Kind.TEXT, "", "Kurt");
	}
	
	private void check(ElNode node, XTCdeweyID deweyID, Kind kind, 
			String name, String value) throws DocumentException {
		assertEquals("DeweyID is correct", deweyID, node.getDeweyID());
		assertEquals("Kind is correct", kind, node.getKind());
		assertEquals("Name is correct", name, node.getName());
		assertEquals("Value is correct", value, node.getValue());
	}
	
	
	@Test
	public void testEmptyElementUnderRollback1() throws Exception {
		TXCollection<ElNode> locator = createDocument(new DocumentParser(
				ROOT_ONLY_DOCUMENT));
		ElNode root = locator.getDocument().getFirstChild();
		
		tx.commit();
		tx = sm.taMgr.begin();
		root = root.copyFor(tx);

		printIndex(tx, "/media/ramdisk/testEmptyElementUnderRollback1_1.dot",
				locator.getID(), true);

		root.setAttribute(new QNm("att"), new Una("test"));

		printIndex(tx, "/media/ramdisk/testEmptyElementUnderRollback1_2.dot",
				locator.getID(), true);

		root.insertRecord(root.getDeweyID().getNewChildID(), Kind.ELEMENT,
				new QNm("child"), null);

		printIndex(tx, "/media/ramdisk/testEmptyElementUnderRollback1_3.dot",
				locator.getID(), true);

		tx.rollback();

		printIndex(tx, "/media/ramdisk/testEmptyElementUnderRollback1_4.dot",
				locator.getID(), true);
	}

	@Ignore
	@Test
	public void traverseBigDocumentInPreOrder() throws Exception,
			FileNotFoundException {
		TXCollection<ElNode> locator = createDocument(new DocumentParser(
				new File("/home/sbaechl/projects/xtc/docs/xmark8.xml")));
		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(org.apache.log4j.Level.TRACE);
		ElNode root = locator.getDocument().getFirstChild().getNode(
				XTCdeweyID.newRootID(locator.getID()));
		Node domRoot = createDomTree(new InputSource(new FileReader(
				"/home/sbaechl/projects/xtc/docs/xmark8.xml")));
		long start = System.currentTimeMillis();
		checkSubtreePreOrder(root, domRoot); // check document index
		long end = System.currentTimeMillis();
		System.out.println(end - start);
	}

	@Ignore
	@Test
	public void testReplace() throws TxException, DocumentException {
		TXCollection<ElNode> locator = createDocument(new DocumentParser(
				"<xtc><users></users><dir><doc id=\"2\" name=\"_master.xml\" pathSynopsis=\"3\"><indexes></indexes></doc><doc id=\"6\" name=\"/sample.xml\" pathSynopsis=\"7\"><indexes></indexes></doc><doc id=\"8\" name=\"/index.html\" pathSynopsis=\"9\"><indexes></indexes></doc></dir></xtc>"));
		ElNode root = locator.getDocument().getFirstChild().getNode(
				XTCdeweyID.newRootID(locator.getID()));
		root.getFirstChild();

		FragmentHelper helper = new FragmentHelper();
		helper.openElement("doc");
		helper.attribute("id", "2");
		helper.attribute("name", "_master.xml");
		helper.attribute("pathSynopsis", "3");
		helper.openElement("indexes");
		helper.closeElement();
		helper.openElement("statistics");
		helper.attribute("type", "DOCUMENT");
		helper.attribute("size", "139");
		helper.attribute("card", "13");
		helper.attribute("height", "1");
		helper.closeElement();
		helper.openElement("documentStatistics");
		helper.attribute("summaryPage", "10");
		helper.closeElement();
		helper.closeElement();
		SubtreePrinter.print(helper.getRoot(), System.out);

		tx.checkPrevLSN();

		ElNode doc = root.getLastChild().getFirstChild();

		System.out.println("Doc before");
		SubtreePrinter.print(root, System.out);
		doc.replaceWith(helper.getRoot());
		System.out.println("Doc after");
		SubtreePrinter.print(root, System.out);
		ElNode checkRoot = locator.getDocument().getFirstChild().getNode(
				root.getDeweyID());
		assertNotNull("Root still exists", checkRoot);
	}

	@Ignore
	@Test
	public void traverseBigDocumentInPostOrder() throws Exception {
		TXCollection<ElNode> locator = createDocument(new DocumentParser(
				new File("/docs/xmark8.xml")));
		// Logger.getLogger(SlottedPageContext.class.getName()).setLevel(Level.TRACE);
		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);
		ElNode root = locator.getDocument().getFirstChild().getNode(
				XTCdeweyID.newRootID(locator.getID()));
		Node domRoot = createDomTree(new InputSource(new FileReader(
				"/docs/xmark8.xml")));

		checkSubtreePostOrder(root, domRoot); // check document index
	}

	@Test
	public void testDeleteRecord() throws Exception {
		TXCollection<ElNode> locator = createDocument(new DocumentParser(
				DOCUMENT));
		ElNode root = locator.getDocument().getFirstChild().getNode(
				XTCdeweyID.newRootID(locator.getID()));
		Node domRoot = createDomTree(new InputSource(new StringReader(
				DOCUMENT)));

		ElNode department = root.getFirstChild();

		long savepointLSN = tx.checkPrevLSN();

		for (ElNode child = department.getFirstChild(); child != null; child = department
				.getFirstChild()) {
			child.delete();
			ElNode checkDepartment = locator.getDocument().getFirstChild()
					.getNode(department.getDeweyID());
			assertNotNull("Department still exists", checkDepartment);
			try {
				ElNode node = locator.getDocument().getFirstChild().getNode(
						child.getDeweyID());
				Assert.fail("Deleted child does not exist anymore");
			} catch (DocumentException e) {
				// expected
			}
		}

		tx.undo(savepointLSN);

		checkSubtreePreOrder(root, domRoot); // check document index
	}

	@Test
	public void testScan() throws TxException, DocumentException {
		TXCollection<ElNode> locator = createDocument(new DocumentParser(
				DOCUMENT));
		SubtreePrinter.print(locator.getDocument().getFirstChild(), System.out);
	}

	@Test
	public void testDeleteAttribute() throws TxException, DocumentException {
		TXCollection<ElNode> locator = createDocument(new DocumentParser(
				"<element test=\"aha\"/>"));
		ElNode root = locator.getDocument().getFirstChild().getNode(
				XTCdeweyID.newRootID(locator.getID()));
		root.getFirstChild();

		tx.checkPrevLSN();

		ElNode attribute = root.getAttribute(new QNm("test"));
		attribute.delete();
		ElNode checkRoot = locator.getDocument().getFirstChild().getNode(
				root.getDeweyID());
		assertNotNull("Root still exists", checkRoot);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		elStore = new ElStore(sm.bufferManager, sm.dictionary, sm.mls);
	}

	@Override
	protected TXCollection<ElNode> createDocument(DocumentParser documentParser)
			throws DocumentException {
		StorageSpec spec = new StorageSpec("test", sm.dictionary);
		ElCollection collection = new ElCollection(tx, elStore);
		collection.create(spec, documentParser);
		return collection;
	}
}
