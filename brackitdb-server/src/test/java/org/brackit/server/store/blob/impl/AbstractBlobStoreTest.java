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

import java.util.Arrays;
import java.util.Random;

import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public abstract class AbstractBlobStoreTest {

	protected SysMockup sm;

	protected BlobStore blobStore;

	protected PageID blobID;

	protected PageID nonuniqueRootPageID;

	protected Random rand;

	protected Tx t1;

	public AbstractBlobStoreTest() {
		super();
	}

	@Test
	public void testReadWriteLessThanPageSize()
			throws BlobStoreAccessException, BufferException, TxException {
		for (int i = 0; i <= 1024; i++) {
			// System.out.println("Size " + i);
			byte[] original = new byte[i];
			rand.nextBytes(original);
			blobStore.write(t1, blobID, original, false);

			byte[] read = blobStore.read(t1, blobID);

			assertNotNull("Read bytes are not null", read);
			assertEquals("Number of read bytes same as written bytes",
					original.length, read.length);

			// for (int j = 0; j < original.length; j++)
			// {
			// System.out.println(String.format("%4s : %4s", original[j],
			// read[j]));
			// }

			assertTrue("Read same bytes as written", Arrays.equals(original,
					read));

			// setUp();
		}
	}

	@After
	public void tearDown() throws BufferException {
	}

	@Before
	public void setUp() throws Exception {
		sm = new SysMockup();
		blobStore = createBlobStore(sm.bufferManager);

		t1 = sm.taMgr.begin();
		blobID = blobStore.create(t1, SysMockup.CONTAINER_NO, -1);

		// use same random source to get reproducable results in case of an
		// error
		rand = new Random(12345678);
	}

	protected abstract BlobStore createBlobStore(BufferMgr bufferMgr);
}