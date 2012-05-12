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
package org.brackit.server.store.blob.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.store.index.aries.visitor.SizeCounterVisitor;
import org.brackit.server.tx.TxException;
import org.junit.Test;

public class IndexBlobStoreTest extends AbstractBlobStoreTest {
	private static final Logger log = Logger.getLogger(IndexBlobStoreTest.class
			.getName());

	public IndexBlobStoreTest() {
	}

	@Test
	public void testIndexUsage() throws BlobStoreAccessException,
			BufferException, TxException, IndexAccessException,
			FileNotFoundException {
		byte[] original = new byte[1024];
		rand.nextBytes(original);
		blobStore.write(t1, blobID, original, true);

		byte[] read = blobStore.read(t1, blobID);

		assertNotNull("Read bytes are not null", read);
		assertEquals("Number of read bytes same as written bytes",
				original.length, read.length);

		// for (int j = 0; j < original.length; j++)
		// {
		// System.out.println(String.format("%4s : %4s", original[j], read[j]));
		// }

		assertTrue("Read same bytes as written", Arrays.equals(original, read));

		SizeCounterVisitor scv = new SizeCounterVisitor();
		Index index = new BPlusIndex(sm.bufferManager);
		index.traverse(t1, blobID, scv);
		long idxSize = scv.getIndexSize();
		long spare = scv.getSpareSize();
		int pageNo = scv.getIndexPageCount();
		int indexHeight = scv.getIndexHeight();
		int indexLeaveCount = scv.getIndexLeaveCount();
		long indexTuples = scv.getIndexTuples();
		long indexPointers = scv.getIndexPointers();
		System.out
				.println(String
						.format(
								"Size = %s, Spare = %s, Pages = %s, Height = %s, Leaves = %s, Entries = %s, Pointers = %s",
								idxSize, spare, pageNo, indexHeight,
								indexLeaveCount, indexTuples, indexPointers));
		PrintStream printer = new PrintStream(new FileOutputStream(
				"/media/ramdisk/blob.dot"));
		DisplayVisitor display = new DisplayVisitor(printer, true);
		index.traverse(t1, blobID, display);
		printer.close();
		// setUp();
	}

	@Override
	protected BlobStore createBlobStore(BufferMgr bufferMgr) {
		return new IndexBlobStore(new BPlusIndex(bufferMgr));
	}
}