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
package org.brackit.server.io.buffer.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.impl.BufferMgrMockup;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BufferImplTest {

	private static final String CONTAINER_NAME = "juni.cnt";

	private static final int CONTAINER_NO = 99;

	private static final int BUFFER_SIZE = 10;

	private static final int EXTEND_SIZE = 10;

	private static final int BLOCK_SIZE = 4096;

	private static final int INITIAL_SIZE = 20;

	// 170 = 10101010
	private static final byte BYTE_PATTERN = (byte) 170;

	// -1431655766 = 10101010 10101010 10101010 10101010
	private static final int INT_PATTERN = -1431655766;

	private TxMgr taMgr;

	private Buffer buffer;
	
	private int unitID;

	private Tx t1;

	private Tx t2;

	private Tx t3;

	private BufferMgrMockup bufferManager;

	@Test
	public void testReplacement() throws ServerException {
		PageID pageNo = null;
		Handle handle = null;

		// prepare pages for the test
		PageID[] pageNumbers = prepareTestPages(buffer, 2 * BUFFER_SIZE, false);
		Handle[] pageHandles = new Handle[2 * BUFFER_SIZE];

		// fill buffer with first BUFFER_SIZE pages
		for (int i = 0; i < BUFFER_SIZE; i++) {
			pageNo = pageNumbers[i];
			handle = buffer.fixPage(t2, pageNo);
			pageHandles[i] = handle;
		}

		// make all buffer positions avaliable again
		for (int i = 0; i < BUFFER_SIZE; i++) {
			buffer.unfixPage(pageHandles[i]);
		}

		buffer.resetCounters();

		// refer first BUFFER_SIZE pages in buffer "position" times
		for (int i = 0; i < BUFFER_SIZE; i++) {
			for (int j = i; j < BUFFER_SIZE; j++) {
				handle = buffer.fixPage(t2, pageNumbers[j]);
				buffer.unfixPage(handle);
			}
		}

		assertEquals("page faults after reset", buffer.getMissCount(), 0);

		// produce buffer misses by referencing the other pages
		for (int i = BUFFER_SIZE; i < 2 * BUFFER_SIZE; i++) {
			pageNo = pageNumbers[i];
			handle = buffer.fixPage(t2, pageNo);
			pageHandles[i] = handle;
			buffer.unfixPage(handle);

			assertEquals("page faults after referencing page number " + i, i
					- BUFFER_SIZE + 1, buffer.getMissCount());
		}
	}

	@Test
	public void testUndoDeallocateWithoutFlush() throws ServerException {
		Handle handle = buffer.allocatePage(t2, unitID);
		PageID pageNo = handle.getPageID();
		handle.unlatch();
		buffer.unfixPage(handle);
		t2.commit();

		// handle = buffer.fixPage(ctx, pageNo);
		// handle.latchExclusive();
		buffer.deletePageDeferred(t1, pageNo, -1, true, -1);
		// handle.unlatch();
		// buffer.unfixPage(handle);
		t1.rollback();

		buffer.flush();

		try {
			handle = buffer.fixPage(t3, pageNo);
		} catch (BufferException e) {
			fail("Could not fix deleted page after rollback.");
		}
	}

	@Test
	public void testUndoDeallocateWithFlush() throws ServerException {
		Handle handle = buffer.allocatePage(t2, unitID);
		PageID pageNo = handle.getPageID();
		handle.unlatch();
		buffer.unfixPage(handle);
		t2.commit();

		// handle = buffer.fixPage(ctx, pageNo);
		// handle.latchExclusive();
		buffer.deletePageDeferred(t1, pageNo, -1, true, -1);
		// handle.unlatch();
		// buffer.unfixPage(handle);
		t1.rollback();

		buffer.flush();

		try {
			handle = buffer.fixPage(t3, pageNo);
		} catch (BufferException e) {
			fail("Could not fix deleted page after rollback.");
		}
	}

	@Test
	public void testUndoAllocateWithoutFlush() throws ServerException {
		Handle handle = buffer.allocatePage(t2, unitID);
		PageID pageNo = handle.getPageID();
		handle.unlatch();
		buffer.unfixPage(handle);
		t2.rollback();

		buffer.flush();

		try {
			handle = buffer.fixPage(t3, pageNo);
			fail("Could not fix deleted page after rollback.");
		} catch (BufferException e) {
			assertTrue("Could not fix deleted page after rollback.", true);
		}
	}

	@Test
	public void testUndoAllocateWithFlush() throws ServerException {
		Handle handle = buffer.allocatePage(t2, unitID);
		PageID pageNo = handle.getPageID();
		handle.unlatch();
		buffer.unfixPage(handle);
		t2.rollback();

		buffer.flush();

		try {
			handle = buffer.fixPage(t3, pageNo);
			fail("Could not fix deleted page after rollback.");
		} catch (BufferException e) {
			assertTrue("Could not fix deleted page after rollback.", true);
		}
	}

	@Test
	public void testRecoveryWithUndo() throws ServerException, TxException {
		for (int i = 0; i < 4; i++) {
			Handle handle = buffer.allocatePage(t2, unitID);
			handle.unlatch();
		}

		taMgr.getLog().flushAll();

		bufferManager.dropBuffer(CONTAINER_NO);
		bufferManager.createBuffer(BUFFER_SIZE, BLOCK_SIZE, CONTAINER_NO,
				CONTAINER_NAME, INITIAL_SIZE, EXTEND_SIZE);
		buffer = bufferManager.getBuffer(CONTAINER_NO);
		unitID = buffer.createUnit(-1);

		taMgr.recover();
	}

	@Test
	public void testDelete() throws ServerException {
		Handle handle = buffer.allocatePage(t2, unitID);
		PageID pageNo = handle.getPageID();
		handle.unlatch();
		buffer.unfixPage(handle);
		buffer.deletePageDeferred(t2, pageNo, -1, true, -1);
		try {
			Handle fetchDeleted = buffer.fixPage(t2, pageNo);
			fail("Could fix deleted page");
		} catch (BufferException e) {
			// expected
		}
	}

	@Test
	public void testNewDelete() throws ServerException {
		Handle handle = buffer.allocatePage(t2, unitID);
		PageID pageNo = handle.getPageID();
		handle.unlatch();
		buffer.deletePageDeferred(t2, pageNo, -1, true, -1);
		try {
			Handle fetchDeleted = buffer.fixPage(t2, pageNo);
			fail("Could fix deleted page");
		} catch (BufferException e) {
			// expected
		}
	}

	private PageID[] prepareTestPages(Buffer buffer, int numberOfPages,
			boolean withPattern) throws ServerException {
		Handle handle = null;
		PageID pageNo = null;
		int[] freeSpace = new int[numberOfPages];
		long[] LSN = new long[numberOfPages];
		PageID[] pageNumbers = new PageID[numberOfPages];
		byte[] pattern = null;

		int startOffset = BLOCK_SIZE - Handle.GENERAL_HEADER_SIZE;
		for (int i = 0; i < numberOfPages; i++) {
			// allocate a new page
			handle = buffer.allocatePage(t2, unitID);

			// prepare the bit pattern
			if (pattern == null) {
				int patternLength = startOffset;
				pattern = new byte[patternLength];
				Arrays.fill(pattern, BYTE_PATTERN);
			}

			pageNo = handle.getPageID();

			if (withPattern) {
				// write the pattern in the page
				System.arraycopy(pattern, 0, handle.page, startOffset,
						pattern.length);

				// verify written bytes
				byte[] readPattern = Arrays.copyOfRange(handle.page,
						startOffset, startOffset + pattern.length);
				assertTrue("comparison of written and read byte pattern",
						Arrays.equals(pattern, readPattern));
			}

			// log changes
			// TODO
			LSN[i] = handle.getLSN();

			// flush the page to disk
			buffer.flush(handle);

			// release page
			handle.unlatch();
			buffer.unfixPage(handle);

			pageNumbers[i] = pageNo;

			// System.out.println(String.format("%s. Page %s was allocated at %s",
			// i, pageID, handle));
		}

		// fix pages again and verify that they have been stored correctly
		for (int i = 0; i < numberOfPages; i++) {
			pageNo = pageNumbers[i];
			handle = buffer.fixPage(t2, pageNo);
			handle.latchS();

			// System.out.println(String.format("%s. Page %s was fixed at %s and has pageID %s",
			// i, pageID, handle, buffer.getNo(handle)));

			if (withPattern) {
				// verify restored bytes
				byte[] restoredPattern = Arrays.copyOfRange(handle.page,
						startOffset, startOffset + pattern.length);
				assertTrue("comparison of written and restored byte pattern",
						Arrays.equals(pattern, restoredPattern));
			}

			// verify page fields
			assertEquals("restored page number", pageNo, handle.getPageID());
			assertEquals("restored LSN", LSN[i], handle.getLSN());

			handle.unlatch();
			buffer.unfixPage(handle);
		}

		t2.commit();

		buffer.flush();

		return pageNumbers;
	}

	@Before
	public void setUp() throws ServerException {
		taMgr = new TaMgrMockup();
		bufferManager = (BufferMgrMockup) taMgr.getBufferManager();
		bufferManager.createBuffer(BUFFER_SIZE, BLOCK_SIZE, CONTAINER_NO,
				CONTAINER_NAME, INITIAL_SIZE, EXTEND_SIZE);
		buffer = bufferManager.getBuffer(CONTAINER_NO);
		unitID = buffer.createUnit(-1);
		t1 = taMgr.begin();
		t2 = taMgr.begin();
		t3 = taMgr.begin();
	}

	@After
	public void tearDown() throws ServerException {
	}

}
