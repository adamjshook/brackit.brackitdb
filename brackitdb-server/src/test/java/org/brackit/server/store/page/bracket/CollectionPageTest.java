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
package org.brackit.server.store.page.bracket;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.page.bracket.BracketKey.Type;
import org.brackit.server.store.page.bracket.BracketPage.UnresolvedValue;
import org.brackit.server.store.page.bracket.XMLDoc.Record;
import org.brackit.server.store.page.bracket.navigation.NavigationResult;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.xquery.xdm.DocumentException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Martin Hiller
 * 
 */
public class CollectionPageTest {

	private static final int collectionID = 3;

	private int[] docIDs;
	private Record[] records;
	private XMLDoc document;

	private BracketPage page;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {

		Handle handle = new Handle(8192) {
		};
		handle.init(new PageID(collectionID));

		page = new BracketPage(null, handle);
		page.format(new PageID(collectionID));

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		generateDocument();

		// fill page with documents
		docIDs = new int[] { 0, 1, 3 };
		for (int docID : docIDs) {
			insertSampleDocument(docID, deweyIDBuffer);
		}
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void navigateToKeyTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);

			for (Record record : records) {

				NavigationResult navRes = page.navigateToKey(new XTCdeweyID(
						docID, record.deweyID.divisionValues), deweyIDBuffer);
				assertEquals(NavigationStatus.FOUND, navRes.status);

				UnresolvedValue value = page
						.getValueUnresolved(navRes.keyOffset);
				assertNotNull(value.value);
				assertEquals(record.value, new String(value.value));
			}
		}
	}

	@Test
	public void navigateFirstChildTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);

			for (XMLNode node : document.getNodes()) {

				// do not check attributes
				if (node.deweyID.isAttribute()) {
					continue;
				}

				NavigationResult refNode = page.navigateToKey(new XTCdeweyID(
						docID, node.deweyID.divisionValues), deweyIDBuffer);

				NavigationResult navRes = page.navigateFirstChild(
						refNode.keyOffset, deweyIDBuffer, refNode.keyType);

				XMLNode firstChild = (node.getChildren().isEmpty() ? null
						: node.getChildren().get(0));

				// check status
				if (firstChild == null) {
					assertFalse(NavigationStatus.FOUND == navRes.status);
				} else {
					assertEquals(NavigationStatus.FOUND, navRes.status);

					// check deweyID
					XTCdeweyID expected = new XTCdeweyID(docID,
							firstChild.deweyID.divisionValues);
					assertEquals(expected, deweyIDBuffer.getDeweyID());

					// check value
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					if (firstChild.value != null) {
						assertNotNull(value);
						assertEquals(firstChild.value, new String(value));
					} else {
						assertNull(value);
					}
				}
			}
		}
	}
	
	@Test
	public void navigateNextTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();
		
		// init navigation result
		NavigationResult navRes = new NavigationResult();
		navRes.status = NavigationStatus.FOUND;
		navRes.keyOffset = BracketPage.BEFORE_LOW_KEY_OFFSET;
		
		Iterator<XMLNode> iter = null;
		
		int docNumber = -1;
		
		while (true) {
			
			navRes = page.navigateNext(navRes.keyOffset, deweyIDBuffer, navRes.keyType, false);
			
			if (navRes.status != NavigationStatus.FOUND || navRes.keyType == Type.DOCUMENT) {
				// iterator has to be empty
				assertTrue(iter == null || !iter.hasNext());
				
				// check value
				if (navRes.status == NavigationStatus.FOUND) {
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					assertNull(value);
				}
				
				// reset iterator
				iter = document.getNodes().iterator();
				docNumber++;
				
				if (navRes.status != NavigationStatus.FOUND) {
					break;
				}
			} else {
				// non document node found
				assertTrue(iter.hasNext());
				XMLNode next = iter.next();
				
				// check deweyID
				XTCdeweyID expected = new XTCdeweyID(new DocID(collectionID, docIDs[docNumber]),
						next.deweyID.divisionValues);
				assertEquals(expected, deweyIDBuffer.getDeweyID());

				// check value
				byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
				if (next.value != null) {
					assertNotNull(value);
					assertEquals(next.value, new String(value));
				} else {
					assertNull(value);
				}
			}
		}
	}
	
	@Test
	public void navigateNextInDocumentTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();
		
		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);
		
			Iterator<XMLNode> iter = document.getNodes().iterator();
			
			// navigate to document key
			NavigationResult navRes = page.navigateToKey(new XTCdeweyID(docID), deweyIDBuffer);
			
			while (iter.hasNext()) {
				XMLNode next = iter.next();
				navRes = page.navigateNext(navRes.keyOffset, deweyIDBuffer, navRes.keyType, true);
				
				assertEquals(NavigationStatus.FOUND, navRes.status);
				
				// check deweyID
				XTCdeweyID expected = new XTCdeweyID(docID,
						next.deweyID.divisionValues);
				assertEquals(expected, deweyIDBuffer.getDeweyID());

				// check value
				byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
				if (next.value != null) {
					assertNotNull(value);
					assertEquals(next.value, new String(value));
				} else {
					assertNull(value);
				}
			}
			
			// iterator closed
			navRes = page.navigateNext(navRes.keyOffset, deweyIDBuffer, navRes.keyType, true);
			assertFalse(navRes.status == NavigationStatus.FOUND);
		}
	}
	
	@Test
	public void navigateNextAttributeTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);

			for (XMLNode node : document.getNodes()) {

				NavigationResult refNode = page.navigateToKey(new XTCdeweyID(
						docID, node.deweyID.divisionValues), deweyIDBuffer);

				NavigationResult navRes = page.navigateNextAttribute(
						refNode.keyOffset, deweyIDBuffer, refNode.keyType);

				XMLNode nextAttribute = null;
				if (!node.getAttributes().isEmpty()) {
					nextAttribute = node.getAttributes().get(0);
				} else if (node.deweyID.isAttribute()) {
					nextAttribute = node.getNextSibling();
				}

				// check status
				if (nextAttribute == null) {
					assertFalse(NavigationStatus.FOUND == navRes.status);
				} else {
					assertEquals(NavigationStatus.FOUND, navRes.status);

					// check deweyID
					XTCdeweyID expected = new XTCdeweyID(docID,
							nextAttribute.deweyID.divisionValues);
					assertEquals(expected, deweyIDBuffer.getDeweyID());

					// check value
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					if (nextAttribute.value != null) {
						assertNotNull(value);
						assertEquals(nextAttribute.value, new String(value));
					} else {
						assertNull(value);
					}
				}
			}
		}
	}
	
	@Test
	public void navigateParentTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);

			for (XMLNode node : document.getNodes()) {

				NavigationResult refNode = page.navigateToKey(new XTCdeweyID(
						docID, node.deweyID.divisionValues), deweyIDBuffer);

				NavigationResult navRes = page.navigateParent(deweyIDBuffer);

				XMLNode parent = node.getParent();

				// check status
				if (parent == null) {
					// DOCUMENT node is found
					assertEquals(NavigationStatus.FOUND, navRes.status);
					
					// check deweyID
					XTCdeweyID expected = new XTCdeweyID(docID);
					assertEquals(expected, deweyIDBuffer.getDeweyID());
					
					// check value
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					assertNull(value);
				} else {
					assertEquals(NavigationStatus.FOUND, navRes.status);

					// check deweyID
					XTCdeweyID expected = new XTCdeweyID(docID,
							parent.deweyID.divisionValues);
					assertEquals(expected, deweyIDBuffer.getDeweyID());

					// check value
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					if (parent.value != null) {
						assertNotNull(value);
						assertEquals(parent.value, new String(value));
					} else {
						assertNull(value);
					}
				}
			}
		}
	}
	
	@Test
	public void navigatePreviousSiblingTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);

			for (XMLNode node : document.getNodes()) {

				// do not check attributes
				if (node.deweyID.isAttribute()) {
					continue;
				}

				NavigationResult refNode = page.navigateToKey(new XTCdeweyID(
						docID, node.deweyID.divisionValues), deweyIDBuffer);

				NavigationResult navRes = page.navigatePreviousSibling(
						refNode.keyOffset, deweyIDBuffer);

				XMLNode previousSibling = node.getPreviousSibling();

				// check status
				if (previousSibling == null) {
					assertFalse(NavigationStatus.FOUND == navRes.status);
				} else {
					assertEquals(NavigationStatus.FOUND, navRes.status);

					// check deweyID
					XTCdeweyID expected = new XTCdeweyID(docID,
							previousSibling.deweyID.divisionValues);
					assertEquals(expected, deweyIDBuffer.getDeweyID());

					// check value
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					if (previousSibling.value != null) {
						assertNotNull(value);
						assertEquals(previousSibling.value, new String(value));
					} else {
						assertNull(value);
					}
				}
			}
		}
	}
	
	@Test
	public void navigateNextSiblingTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);

			for (XMLNode node : document.getNodes()) {

				// do not check attributes
				if (node.deweyID.isAttribute()) {
					continue;
				}

				NavigationResult refNode = page.navigateToKey(new XTCdeweyID(
						docID, node.deweyID.divisionValues), deweyIDBuffer);

				NavigationResult navRes = page.navigateNextSibling(
						refNode.keyOffset, deweyIDBuffer, refNode.keyType);

				XMLNode nextSibling = node.getNextSibling();

				// check status
				if (nextSibling == null) {
					assertFalse(NavigationStatus.FOUND == navRes.status);
				} else {
					assertEquals(NavigationStatus.FOUND, navRes.status);

					// check deweyID
					XTCdeweyID expected = new XTCdeweyID(docID,
							nextSibling.deweyID.divisionValues);
					assertEquals(expected, deweyIDBuffer.getDeweyID());

					// check value
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					if (nextSibling.value != null) {
						assertNotNull(value);
						assertEquals(nextSibling.value, new String(value));
					} else {
						assertNull(value);
					}
				}
			}
		}
	}

	@Test
	public void navigateLastChildTest() throws DocumentException {

		DeweyIDBuffer deweyIDBuffer = new DeweyIDBuffer();

		for (int docNumber : docIDs) {
			DocID docID = new DocID(collectionID, docNumber);

			for (XMLNode node : document.getNodes()) {

				// do not check attributes
				if (node.deweyID.isAttribute()) {
					continue;
				}

				NavigationResult refNode = page.navigateToKey(new XTCdeweyID(
						docID, node.deweyID.divisionValues), deweyIDBuffer);

				NavigationResult navRes = page.navigateLastChild(
						refNode.keyOffset, deweyIDBuffer);
				if (navRes.status == NavigationStatus.POSSIBLY_FOUND) {
					navRes.status = NavigationStatus.FOUND;
				}

				XMLNode lastChild = (node.getChildren().isEmpty() ? null
						: node.getChildren().get(node.getChildren().size() - 1));

				// check status
				if (lastChild == null) {
					assertFalse(NavigationStatus.FOUND == navRes.status);
				} else {
					assertEquals(NavigationStatus.FOUND, navRes.status);

					// check deweyID
					XTCdeweyID expected = new XTCdeweyID(docID,
							lastChild.deweyID.divisionValues);
					assertEquals(expected, deweyIDBuffer.getDeweyID());

					// check value
					byte[] value = page.getValueUnresolved(navRes.keyOffset).value;
					if (lastChild.value != null) {
						assertNotNull(value);
						assertEquals(lastChild.value, new String(value));
					} else {
						assertNull(value);
					}
				}
			}
		}
	}

	private int insertSampleDocument(int docNumber, DeweyIDBuffer deweyIDBuffer)
			throws DocumentException {

		int currentOffset = 0;

		DocID docID = new DocID(collectionID, docNumber);

		for (int i = 0; i < records.length; i++) {
			Record record = records[i];
			BracketNodeSequence sequence = BracketNodeSequence.fromNode(
					new XTCdeweyID(docID, record.deweyID.divisionValues),
					record.value.getBytes(), record.numberOfAncestors, false);

			if (i == 0) {
				currentOffset = page.insertSequence(sequence, deweyIDBuffer);
			} else {
				currentOffset = page.insertSequenceAfter(sequence,
						currentOffset, deweyIDBuffer);
			}
		}

		return currentOffset;
	}

	private void generateDocument() throws DocumentException {

		DocID docID = new DocID(-1, 0);

		records = new Record[] {
				new Record(new XTCdeweyID(docID, "1.3.1.3"), 3,
						"attribute1=value1"),
				new Record(new XTCdeweyID(docID, "1.3.1.7"), 0,
						"attribute2=value2"),
				new Record(new XTCdeweyID(docID, "1.3.5"), 0, "someText1"),
				new Record(new XTCdeweyID(docID, "1.7.1.5"), 1,
						"attribute3=value3"),
				new Record(new XTCdeweyID(docID, "1.8.3.1.3"), 1,
						"attribute4=value4"),
				new Record(new XTCdeweyID(docID, "1.8.3.1.5"), 0,
						"attribute5=value5"),
				new Record(new XTCdeweyID(docID, "1.8.3.3"), 0, "someText2"),
				new Record(new XTCdeweyID(docID, "1.9.1.3"), 1,
						"attribute6=value6") };

		document = new XMLDoc();

		for (Record record : records) {
			document.addRecord(record);
		}
	}

}
