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
package org.brackit.server.node.index.cas.impl;

import static org.brackit.server.node.index.definition.IndexDefBuilder.createCASIdxDef;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElCollection;
import org.brackit.server.node.el.ElNode;
import org.brackit.server.node.el.ElStore;
import org.brackit.server.node.index.cas.CASIndex;
import org.brackit.server.node.index.definition.Cluster;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.txnode.Persistor;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.store.Field;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.util.log.Logger;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.util.path.PathException;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.Type;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author Sebastian Baechle
 * 
 */
public class CASIndexTest {
	private static final Logger log = Logger.getLogger(CASIndexTest.class
			.getName());

	private static final String DOC = "<?xml version = '1.0' encoding = 'UTF-8'?>"
			+ "<root>"
			+ "<c>no</c>"
			+ "<a>"
			+ "<b>  no  </b>"
			+ "<c> c</c>"
			+ "</a>"
			+ "<a>"
			+ "<b>no</b>"
			+ "<c>d"
			+ " </c>"
			+ "</a>"
			+ "<a>"
			+ "<b>no</b>"
			+ "<a>"
			+ "<c>a</c>"
			+ "</a>"
			+ "</a>"
			+ "<a>"
			+ "<b>no</b>"
			+ "<c>b       		</c>"
			+ "</a>"
			+ "<a>"
			+ "<c>a</c>"
			+ "</a>" + "</root>";

	private static final List<Path<QNm>> PATHS;

	private BPlusIndex index;

	private Tx t1;

	private SysMockup sm;
	
	static {
		PATHS = new LinkedList<Path<QNm>>();
		try {
			PATHS.add(Path.parse("//a/c"));
		} catch (PathException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testCreateSimpleCASIndex() throws DocumentException {
		ElCollection locator = createDocument(t1, new DocumentParser(DOC));
		IndexDef casIdx = createCASIdxDef(null, false, null, PATHS);
		locator.getIndexController().createIndexes(casIdx);
	}

	@Test
	public void testSplidClustering() throws IndexAccessException,
			DocumentException {
		ElCollection locator = createDocument(t1, new DocumentParser(DOC));
		IndexDef casIdx = createCASIdxDef(null, false, null, PATHS);
		locator.getIndexController().createIndexes(casIdx);
		String[] ids = new String[] { "1.9.5.3.3", "1.13.3.3", "1.11.5.3",
				"1.5.5.3", "1.7.5.3" };
		// IndexPrinter.print(t1, casIdx.getID(), System.out);
		validateIndexContent(t1, locator, casIdx.getID(), ids, Field.STRING,
				true);
	}

	@Test
	public void testSplidClusteringCollection() throws IndexAccessException,
			DocumentException {
		List<DBCollection<?>> locators = new ArrayList<DBCollection<?>>();
		ElCollection collection = createCollection(t1);
		for (int i = 0; i < 10; i++) {
			locators.add(collection.add(new DocumentParser(DOC))
					.getCollection());
		}
		IndexDef casIdx = createCASIdxDef(null, false, null, PATHS);
		collection.getIndexController().createIndexes(casIdx);
		String[] ids = new String[] { "1.9.5.3.3", "1.13.3.3", "1.11.5.3",
				"1.5.5.3", "1.7.5.3" };
		// IndexPrinter.print(t1, casIdx.getID(), System.out);
		validateIndexContent(t1, collection, casIdx.getID(), ids, Field.STRING,
				true);
	}

	@Test
	public void testPCRClustering() throws IndexAccessException,
			DocumentException {
		ElCollection locator = createDocument(t1, new DocumentParser(DOC));
		IndexDef casIdx = createCASIdxDef(Cluster.PCR, false, null, PATHS);
		locator.getIndexController().createIndexes(casIdx);
		String[] ids = new String[] { "1.13.3.3", "1.9.5.3.3", "1.11.5.3",
				"1.5.5.3", "1.7.5.3" };
		// IndexPrinter.print(t1, casIdx.getID(), System.out);
		validateIndexContent(t1, locator, casIdx.getID(), ids, Field.STRING,
				false);
	}

	@Test
	public void testSplidClusteringUpdate() throws IndexAccessException,
			DocumentException {
		ElCollection locator = createDocument(t1, new DocumentParser(DOC));
		IndexDef casIdx = createCASIdxDef(null, false, null, PATHS);
		locator.getIndexController().createIndexes(casIdx);
		String[] idsBefore = new String[] { "1.9.5.3.3", "1.13.3.3",
				"1.11.5.3", "1.5.5.3", "1.7.5.3" };
		validateIndexContent(t1, locator, casIdx.getID(), idsBefore,
				Field.STRING, true);

		ElNode root = locator.getDocument().getFirstChild();
		root.getLastChild().append(Kind.ELEMENT, new QNm("c"), null)
				.append(Kind.TEXT, null, new Una("ba"));
		root.getLastChild().insertAfter(Kind.ELEMENT, new QNm("a"), null)
				.append(Kind.ELEMENT, new QNm("a"), null)
				.append(Kind.ELEMENT, new QNm("c"), null)
				.append(Kind.TEXT, null, new Una("a"));

		String[] idsAfter = new String[] { "1.9.5.3.3", "1.13.3.3",
				"1.15.3.3.3", "1.11.5.3", "1.13.5.3", "1.5.5.3", "1.7.5.3" };
		validateIndexContent(t1, locator, casIdx.getID(), idsAfter,
				Field.STRING, true);
	}

	@Test
	public void testPCRClusteringUpdate() throws IndexAccessException,
			DocumentException {
		ElCollection locator = createDocument(t1, new DocumentParser(DOC));
		IndexDef casIdx = createCASIdxDef(Cluster.PCR, false, null, PATHS);
		locator.getIndexController().createIndexes(casIdx);
		
		String[] idsBefore = new String[] { "1.13.3.3", "1.9.5.3.3",
				"1.11.5.3", "1.5.5.3", "1.7.5.3" };
		validateIndexContent(t1, locator, casIdx.getID(), idsBefore,
				Field.STRING, false);

		ElNode root = locator.getDocument().getFirstChild();
		root.getLastChild().append(Kind.ELEMENT, new QNm("c"), null)
				.append(Kind.TEXT, null, new Una("ba"));
		root.getLastChild().insertAfter(Kind.ELEMENT, new QNm("a"), null)
				.append(Kind.ELEMENT, new QNm("a"), null)
				.append(Kind.ELEMENT, new QNm("c"), null)
				.append(Kind.TEXT, null, new Una("a"));

		root.getFirstChild().getNextSibling()
				.append(Kind.ELEMENT, new QNm("c"), null)
				.append(Kind.TEXT, null, new Una("a"));

		// SubtreePrinter.print(root, System.out);
		// IndexPrinter.print(t1, casIdx.getID(), System.out);
		String[] idsAfter = new String[] { "1.5.7.3", "1.13.3.3", "1.9.5.3.3",
				"1.15.3.3.3", "1.11.5.3", "1.13.5.3", "1.5.5.3", "1.7.5.3" };
		validateIndexContent(t1, locator, casIdx.getID(), idsAfter,
				Field.STRING, false);
	}

	private List<ElNode> getResultNodes(final Tx tx, ElCollection collection,
			final Field keyType, String[] ids, final boolean splidClustering)
			throws DocumentException {
		List<ElNode> nodes = new ArrayList<ElNode>();
		Stream<? extends ElNode> documents = collection.getDocuments();
		ElNode document;
		while ((document = documents.next()) != null) {
			for (String id : ids) {
				XTCdeweyID deweyID = new XTCdeweyID(document.getDeweyID()
						.getDocID(), id);
				ElNode node = document.getNode(deweyID);
				nodes.add(node);
			}
		}
		documents.close();
		assertTrue("more than one document in collection", nodes.size() > 0);

		Collections.sort(nodes, new Comparator<ElNode>() {
			@Override
			public int compare(ElNode o1, ElNode o2) {
				int diff;
				try {
					byte[] val1 = Calc.fromString(o1.getValue().stringValue());
					byte[] val2 = Calc.fromString(o2.getValue().stringValue());
					diff = keyType.compare(val1, val2);
				} catch (DocumentException e) {
					throw new RuntimeException(e);
				}
				if (diff != 0)
					return diff;

				if (splidClustering) {
					diff = o1.getDeweyID().compareTo(o2.getDeweyID());

					if (diff != 0)
						return diff;

					return o1.getPCR() - o2.getPCR();
				} else {
					diff = o1.getPCR() - o2.getPCR();

					if (diff != 0)
						return diff;

					return o1.getDeweyID().compareTo(o2.getDeweyID());
				}
			}

		});
		return nodes;
	}

	private void validateIndexContent(Tx tx, ElCollection collection,
			int idxNo, String[] ids, Field keyType, boolean splidClustering)
			throws DocumentException {
		CASIndex<ElNode> casIndex = new CASIndexImpl<ElNode>(sm.bufferManager);
		List<ElNode> expectedNodes = getResultNodes(tx, collection, keyType,
				ids, splidClustering);

		Stream<? extends ElNode> stream = casIndex.open(tx,
				collection.getIndexController(), idxNo, Type.STR,
				SearchMode.FIRST, null, null, true, true, null);
		int pos = -1;
		int expectedSize = expectedNodes.size();

		ElNode next;
		while ((next = stream.next()) != null) {
			assertTrue("No more index entries found than expected",
					++pos < expectedSize);
			ElNode expected = expectedNodes.get(pos);
			assertEquals("Expected index entry found", expected, next);
			System.out.println("Matched " + expected);
		}
		assertTrue("Number of index entries is correct",
				pos == expectedSize - 1);
		stream.close();
	}

	void printIndex(Tx transaction, String filename, PageID rootPageNo,
			boolean showValues) throws IndexAccessException {
		try {
			PrintStream printer = new PrintStream(new File(filename));
			index.traverse(transaction, rootPageNo, new DisplayVisitor(printer,
					showValues));
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@After
	public void tearDown() throws BufferException {
	}

	@Before
	public void setUp() throws Exception {
		sm = new SysMockup();
		index = new BPlusIndex(sm.bufferManager);
		t1 = sm.taMgr.begin();
	}

	private ElCollection createCollection(Tx tx) throws DocumentException {
		ElStore elStore = new ElStore(sm.bufferManager, sm.dictionary, sm.mls);
		StorageSpec spec = new StorageSpec("", sm.dictionary);
		ElCollection collection = new ElCollection(tx, elStore);
		collection.create(spec);
		collection.setPersistor(new Persistor() {
			@Override
			public void persist(Tx tx, Materializable materializable)
					throws DocumentException {
			}
		});
		return collection;
	}

	private ElCollection createDocument(Tx tx, DocumentParser documentParser)
			throws DocumentException {
		ElStore elStore = new ElStore(sm.bufferManager, sm.dictionary, sm.mls);
		StorageSpec spec = new StorageSpec("", sm.dictionary);
		ElCollection collection = new ElCollection(tx, elStore);
		collection.create(spec, documentParser);
		collection.setPersistor(new Persistor() {
			@Override
			public void persist(Tx tx, Materializable materializable)
					throws DocumentException {
			}
		});
		return collection;
	}
}