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
package org.brackit.server.metaData.manager.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.BlobHandle;
import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.manager.impl.MetaDataMgrImpl;
import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MetaDataMgrImplTest {
	protected static final Logger log = Logger
			.getLogger(MetaDataMgrImplTest.class.getName());

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

	protected static final String ROOT_ONLY_DOCUMENT = "<?xml version = '1.0' encoding = 'UTF-8'?><root/>";

	protected Tx ctx;

	protected Tx ctx2;

	protected SysMockup sm;

	protected MetaDataMgrImpl metaDataMgr;

	@Test
	public void testInstall() throws Exception {
		metaDataMgr.start(ctx, true);
	}

	@Test
	public void testShutdown() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.shutdown();
	}

	@Test
	public void testStart() throws Exception {
		metaDataMgr.start(ctx, true);
		ctx.commit();
		metaDataMgr = new MetaDataMgrImpl(sm.taMgr);
		metaDataMgr.start(ctx2, false);
	}

	@Test
	public void testPutDocument() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.create(ctx, "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
	}

	@Test
	public void testPutCollection() throws Exception {
		metaDataMgr.start(ctx, true);
		List<Node<?>> documents = new ArrayList<Node<?>>();
		int docCount = 3;
		DBCollection<?> collection = metaDataMgr.create(ctx, "/test.col");

		for (int i = 0; i < docCount; i++) {
			collection.add(new DocumentParser(DOCUMENT));
			documents.add(collection.getDocument());

			int j = 0;
			Stream<? extends Node<?>> docs = collection.getDocuments();
			Node<?> doc;
			while ((doc = docs.next()) != null) {
				assertEquals("documents are equal", documents.get(j), doc);
				j++;
			}
			docs.close();

			assertEquals("seen correct amount of documents", i + 1, j);
		}
	}

	@Test
	public void testPutDocumentDuplicate() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.create(ctx, "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
		try {
			DBCollection<?> collection2 = metaDataMgr.create(ctx, "/test.xml",
					new DocumentParser(DOCUMENT));
			Assert.fail("Duplicate insertion was not detected.");
		} catch (DocumentException e) {
			// expected
		}
	}

	@Test
	public void testMkDirAndPut() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.mkdir(ctx, "/myDir");
		metaDataMgr.create(ctx, "/myDir" + "/test.xml", new DocumentParser(
				DOCUMENT));
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/myDir"
				+ "/test.xml");
	}

	@Test
	public void testMkDirAndPutDuplicate() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.mkdir(ctx, "/myDir");
		metaDataMgr.create(ctx, "/myDir" + "/test.xml", new DocumentParser(
				DOCUMENT));
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/myDir"
				+ "/test.xml");
		try {
			DBCollection<?> collection2 = metaDataMgr.create(ctx, "/myDir"
					+ "/test.xml", new DocumentParser(DOCUMENT));
			Assert.fail("Duplicate insertion was not detected.");
		} catch (DocumentException e) {
			// expected
		}
	}

	@Test
	public void testMkDirAndDelete() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.mkdir(ctx, "/myDir");
		metaDataMgr.drop(ctx, "/myDir");
	}

	@Test
	public void testMkDirPutAndDelete() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.mkdir(ctx, "/myDir");
		metaDataMgr.create(ctx, "/myDir" + "/test.xml", new DocumentParser(
				DOCUMENT));
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/myDir"
				+ "/test.xml");
		metaDataMgr.drop(ctx, "/myDir");
		try {
			DBCollection<?> collection2 = metaDataMgr.lookup(ctx, collection
					.getID());
			Assert
					.fail("Implicitly deleted collection could be accessed by ID.");
		} catch (DocumentException e) {
			// expected
		}
		try {
			DBCollection<?> collection2 = metaDataMgr.lookup(ctx, "/myDir"
					+ "/test.xml");
			Assert
					.fail("Implicitly deleted collection could be accessed by name.");
		} catch (DocumentException e) {
			// expected
		}
	}

	@Test
	public void testDeleteDocument() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.create(ctx, "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
		metaDataMgr.drop(ctx, "/test.xml");
		ctx.commit();
		try {
			collection = metaDataMgr.lookup(ctx, "/test.xml");
			Assert.fail("Locator found after delete.");
		} catch (DocumentException e) {
			e.printStackTrace();
			// expected
		}
	}

	@Test
	public void testGetAliveLocatorByNameFirst() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.create(ctx, "/test.xml", new DocumentParser(DOCUMENT));
		(new BPlusIndex(sm.bufferManager)).dump(ctx, new PageID(5), System.out);
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
		DBCollection<?> collection2 = metaDataMgr.lookup(ctx, collection
				.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetAliveLocatorByIDFirst() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.create(ctx, "/test.xml", new DocumentParser(DOCUMENT));
		// (new BPlusIndex(bufferMgr)).dump(ctx, new PageID(5), System.out);
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
		DBCollection<?> collection2 = metaDataMgr.lookup(ctx, collection
				.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetDeadLocatorByNameFirst() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.create(ctx, "/test.xml", new DocumentParser(DOCUMENT));
		ctx.commit();
		metaDataMgr.shutdown();
		metaDataMgr.start(ctx2, false);
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
		DBCollection<?> collection2 = metaDataMgr.lookup(ctx, collection
				.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetDeadLocatorByIDFirst() throws Exception {
		metaDataMgr.start(ctx, true);
		metaDataMgr.create(ctx, "/test.xml", new DocumentParser(DOCUMENT));
		ctx.commit();
		metaDataMgr.shutdown();
		metaDataMgr.start(ctx2, false);
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
		DBCollection<?> collection2 = metaDataMgr.lookup(ctx, collection
				.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetDeadLocatorCheckIndexes() throws Exception {
		metaDataMgr.start(ctx, true);
		DBCollection<?> original = metaDataMgr.create(ctx, "/test.xml",
				new DocumentParser(DOCUMENT));
		original.getIndexController().createIndex("create element index");
		ctx.commit();
		metaDataMgr.shutdown();
		metaDataMgr.start(ctx2, false);
		DBCollection<?> collection = metaDataMgr.lookup(ctx, "/test.xml");
		System.out.println(collection.get(Indexes.class).getIndexDefs());
	}

	@Test
	public void testPutAndGetBlob() throws Exception, IOException {
		metaDataMgr.start(ctx, true);
		byte[] bytes = new byte[1024 * 10];
		new Random(123456789).nextBytes(bytes);
		metaDataMgr.putBlob(ctx, new ByteArrayInputStream(bytes), "/test.bin",
				SysMockup.CONTAINER_NO);
		BlobHandle handle = metaDataMgr.getBlob(ctx, "/test.bin");

		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		byte[] chunk = new byte[256];
		InputStream stream = handle.read();

		for (int read = stream.read(chunk); read > 0; read = stream.read(chunk)) {
			buffer.put(chunk, 0, read);
		}

		assertEquals("Read same amount of bytes", bytes.length, buffer
				.position());
		assertTrue("Read same content", Arrays.equals(bytes, buffer.array()));
	}

	@After
	public void tearDown() throws DocumentException, BufferException {
	}

	@Before
	public void setUp() throws Exception {
		sm = new SysMockup();
		metaDataMgr = new MetaDataMgrImpl(sm.taMgr);
		ctx = sm.taMgr.begin();
		ctx2 = sm.taMgr.begin();
	}
}
