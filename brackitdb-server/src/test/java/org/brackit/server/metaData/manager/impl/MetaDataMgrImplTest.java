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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.BlobHandle;
import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.DBItem;
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

	protected Tx tx;

	protected Tx tx2;

	protected SysMockup sm;

	protected MetaDataMgrImpl mdm;

	@Test
	public void testInstall() throws Exception {
		mdm.start(tx, true);
	}

	@Test
	public void testShutdown() throws Exception {
		mdm.start(tx, true);
		mdm.shutdown();
	}

	@Test
	public void testStart() throws Exception {
		mdm.start(tx, true);
		tx.commit();
		mdm = new MetaDataMgrImpl(sm.taMgr);
		mdm.start(tx2, false);
	}

	@Test
	public void testPutDocument() throws Exception {
		mdm.start(tx, true);
		mdm.create(tx, "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
	}

	@Test
	public void testPutCollection() throws Exception {
		mdm.start(tx, true);
		List<Node<?>> documents = new ArrayList<Node<?>>();
		int docCount = 3;
		DBCollection<?> collection = mdm.create(tx, "/test.col");

		for (int i = 0; i < docCount; i++) {
			Node<?> doc = collection.add(new DocumentParser(DOCUMENT));
			documents.add(doc);

			int j = 0;
			Stream<? extends Node<?>> docs = collection.getDocuments();
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
		mdm.start(tx, true);
		mdm.create(tx, "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
		try {
			DBCollection<?> collection2 = mdm.create(tx, "/test.xml",
					new DocumentParser(DOCUMENT));
			Assert.fail("Duplicate insertion was not detected.");
		} catch (DocumentException e) {
			// expected
		}
	}

	@Test
	public void testMkDirAndPut() throws Exception {
		mdm.start(tx, true);
		mdm.mkdir(tx, "/myDir");
		mdm.create(tx, "/myDir" + "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = mdm.lookup(tx, "/myDir" + "/test.xml");
	}

	@Test
	public void testMkDirAndPutDuplicate() throws Exception {
		mdm.start(tx, true);
		mdm.mkdir(tx, "/myDir");
		mdm.create(tx, "/myDir" + "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = mdm.lookup(tx, "/myDir" + "/test.xml");
		try {
			DBCollection<?> collection2 = mdm.create(tx, "/myDir"
					+ "/test.xml", new DocumentParser(DOCUMENT));
			Assert.fail("Duplicate insertion was not detected.");
		} catch (DocumentException e) {
			// expected
		}
	}

	@Test
	public void testMkDirAndDelete() throws Exception {
		mdm.start(tx, true);
		mdm.mkdir(tx, "/myDir");
		mdm.drop(tx, "/myDir");
	}

	@Test
	public void testMkDirPutAndDelete() throws Exception {
		mdm.start(tx, true);
		mdm.mkdir(tx, "/myDir");
		mdm.create(tx, "/myDir" + "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = mdm.lookup(tx, "/myDir" + "/test.xml");
		mdm.drop(tx, "/myDir");
		try {
			DBCollection<?> collection2 = mdm.lookup(tx, collection.getID());
			Assert
					.fail("Implicitly deleted collection could be accessed by ID.");
		} catch (DocumentException e) {
			// expected
		}
		try {
			DBCollection<?> collection2 = mdm.lookup(tx, "/myDir"
					+ "/test.xml");
			Assert
					.fail("Implicitly deleted collection could be accessed by name.");
		} catch (DocumentException e) {
			// expected
		}
	}

	@Test
	public void testDeleteDocument() throws Exception {
		mdm.start(tx, true);
		mdm.create(tx, "/test.xml", new DocumentParser(DOCUMENT));
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
		mdm.drop(tx, "/test.xml");
		tx.commit();
		try {
			collection = mdm.lookup(tx, "/test.xml");
			Assert.fail("Locator found after delete.");
		} catch (DocumentException e) {
			e.printStackTrace();
			// expected
		}
	}

	@Test
	public void testGetAliveLocatorByNameFirst() throws Exception {
		mdm.start(tx, true);
		mdm.create(tx, "/test.xml", new DocumentParser(DOCUMENT));
		(new BPlusIndex(sm.bufferManager)).dump(tx, new PageID(5), System.out);
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
		DBCollection<?> collection2 = mdm.lookup(tx, collection.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetAliveLocatorByIDFirst() throws Exception {
		mdm.start(tx, true);
		mdm.create(tx, "/test.xml", new DocumentParser(DOCUMENT));
		// (new BPlusIndex(bufferMgr)).dump(ctx, new PageID(5), System.out);
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
		DBCollection<?> collection2 = mdm.lookup(tx, collection.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetDeadLocatorByNameFirst() throws Exception {
		mdm.start(tx, true);
		mdm.create(tx, "/test.xml", new DocumentParser(DOCUMENT));
		tx.commit();
		mdm.shutdown();
		mdm.start(tx2, false);
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
		DBCollection<?> collection2 = mdm.lookup(tx, collection.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetDeadLocatorByIDFirst() throws Exception {
		mdm.start(tx, true);
		mdm.create(tx, "/test.xml", new DocumentParser(DOCUMENT));
		tx.commit();
		mdm.shutdown();
		mdm.start(tx2, false);
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
		DBCollection<?> collection2 = mdm.lookup(tx, collection.getID());
		Assert.assertSame("Got same collection object back", collection,
				collection2);
	}

	@Test
	public void testGetDeadLocatorCheckIndexes() throws Exception {
		mdm.start(tx, true);
		DBCollection<?> original = mdm.create(tx, "/test.xml",
				new DocumentParser(DOCUMENT));
		original.getIndexController().createIndex("create element index");
		tx.commit();
		mdm.shutdown();
		mdm.start(tx2, false);
		DBCollection<?> collection = mdm.lookup(tx, "/test.xml");
		System.out.println(collection.get(Indexes.class).getIndexDefs());
	}

	@Test
	public void testPutAndGetBlobAliveByPath() throws Exception, IOException {
		mdm.start(tx, true);
		byte[] blob = createBlob();
		mdm.putBlob(tx, new ByteArrayInputStream(blob), "/test.bin",
				SysMockup.CONTAINER_NO);
		BlobHandle handle = mdm.getBlob(tx, "/test.bin");
		InputStream stream = handle.read();
		checkBlob(blob, stream);
	}

	@Test
	public void testPutAndGetAliveBlobByID() throws Exception, IOException {
		mdm.start(tx, true);
		byte[] blob = createBlob();
		BlobHandle handle = mdm.putBlob(tx, new ByteArrayInputStream(blob),
				"/test.bin", SysMockup.CONTAINER_NO);
		handle = mdm.getBlob(tx, handle.getID());
		InputStream stream = handle.read();
		checkBlob(blob, stream);
	}
	
	@Test
	public void testPutAndGetBlobDeadByPath() throws Exception, IOException {
		mdm.start(tx, true);
		byte[] blob = createBlob();
		mdm.putBlob(tx, new ByteArrayInputStream(blob), "/test.bin",
				SysMockup.CONTAINER_NO);
		tx.commit();
		mdm.shutdown();
		mdm.start(tx, false);
		BlobHandle handle = mdm.getBlob(tx, "/test.bin");
		InputStream stream = handle.read();
		checkBlob(blob, stream);
	}

	@Test
	public void testPutAndGetDeadBlobByID() throws Exception, IOException {
		mdm.start(tx, true);
		byte[] blob = createBlob();
		BlobHandle handle = mdm.putBlob(tx, new ByteArrayInputStream(blob),
				"/test.bin", SysMockup.CONTAINER_NO);
		tx.commit();
		mdm.shutdown();
		mdm.start(tx, false);
		handle = mdm.getBlob(tx, handle.getID());
		InputStream stream = handle.read();
		checkBlob(blob, stream);
	}
	
	@Test
	public void testPutAndGetBlobItemAliveByPath() throws Exception, IOException {
		mdm.start(tx, true);
		byte[] blob = createBlob();
		mdm.putBlob(tx, new ByteArrayInputStream(blob), "/test.bin",
				SysMockup.CONTAINER_NO);
		DBItem<?> item = mdm.getItem(tx, "/test.bin");
		InputStream stream = ((BlobHandle) item).read();
		checkBlob(blob, stream);
	}
	
	@Test
	public void testPutAndGetBlobItemDeadByPath() throws Exception, IOException {
		mdm.start(tx, true);
		byte[] blob = createBlob();
		mdm.putBlob(tx, new ByteArrayInputStream(blob), "/test.bin",
				SysMockup.CONTAINER_NO);
		tx.commit();
		mdm.shutdown();
		mdm.start(tx, false);
		DBItem<?> item = mdm.getItem(tx, "/test.bin");
		InputStream stream = ((BlobHandle) item).read();
		checkBlob(blob, stream);
	}

	private byte[] createBlob() {
		byte[] blob = new byte[1024 * 10];
		new Random(123456789).nextBytes(blob);
		return blob;
	}
	
	private void checkBlob(byte[] bytes, InputStream stream) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		byte[] chunk = new byte[256];		
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
		sm = new SysMockup(false);
		mdm = new MetaDataMgrImpl(sm.taMgr);
		tx = sm.taMgr.begin();
		tx2 = sm.taMgr.begin();
	}
}
