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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * 
 */
package org.brackit.server.io.file;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Ou Yi
 * 
 */
public class DefaultBlockSpaceTest {

	static final String STORE_ROOT = null;

	static final int BLOCK_SIZE = 4096;
	static final int INIT_SIZE = 1024;
	static final double EXT_SIZE = 0.5;

	DefaultBlockSpace bs;

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
		bs = new DefaultBlockSpace(STORE_ROOT, 0);
		try {
			bs.create(BLOCK_SIZE, INIT_SIZE, EXT_SIZE);
		} catch (StoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		drop();
	}

	void drop() {
		// String storeRoot = Cfg.getProperty(StoreProperties.STORE_ROOT);
		// File root = new File(storeRoot);
		// if (root.isDirectory()) {
		// File[] files = root.listFiles();
		// for (int i = 0; i < files.length; i++) {
		// files[i].delete();
		// }
		// }
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#DefaultBlockSpace(int, int, int, double)}
	 * .
	 */
	@Test
	public final void testDefaultBlockSpace() {
		bs = new DefaultBlockSpace(STORE_ROOT, 0);
		assertNotNull(bs);
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#allocate(int)}.
	 */
	@Test
	public final void testAllocate() {
		int lba1 = 0, lba2 = 0;
		try {
			bs.open();

			lba1 = bs.allocate(-1);

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}

		try {
			bs.open();

			lba2 = bs.allocate(-1);

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}

		assertTrue("the allocation survives an open-close cycle",
				lba1 + 1 == lba2);

		try {
			bs.open();

			for (int i = 0; i < INIT_SIZE * 2; i++) {
				bs.allocate(-1);
			}

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}

		try {
			bs.open();

			for (int i = 0; i < INIT_SIZE * 2; i++) {
				lba2 = bs.allocate(-1);
			}
			assertTrue(lba2 == 4098);

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#sizeOfHeader()}.
	 */
	@Test
	public final void testHeaderLength() {
		assertTrue(bs.sizeOfHeader() == DefaultBlockSpace.BLOCK_HEADER_LENGTH);
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#release(int)}.
	 */
	@Test
	public final void testRelease() {
		try {
			bs.open();

			byte[] blk = new byte[BLOCK_SIZE];
			int lba = bs.allocate(-1);

			// assertTrue(blk[0] == DefaultBlockSpace.BLOCK_IN_USE);

			bs.release(lba);

			// assertTrue(blk[0] == ~DefaultBlockSpace.BLOCK_IN_USE);

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}

		try {
			bs.open();

			byte[] blk = new byte[BLOCK_SIZE];
			int lba = bs.allocate(-1);

			// assertTrue(blk[0] == DefaultBlockSpace.BLOCK_IN_USE);

			bs.close();

			bs.open();

			bs.release(lba);
		} catch (StoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#close()}.
	 */
	@Test
	public final void testClose() {
		try {
			bs.close();
		} catch (StoreException e) {
			assertNotNull(e); // not opened
		}

		try {
			bs.open();
			bs.close();
		} catch (StoreException e) {
			fail("no exception expected");
		}

	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#create()}.
	 */
	@Test
	public final void testCreate() {
		// covered in setUp()
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#open()}.
	 */
	@Test
	public final void testOpen() {
		try {
			bs.open();
		} catch (StoreException e) {
			assertNull(e);
		}

		try {
			bs.open(); // reopen
		} catch (StoreException e) {
			assertNotNull(e);
		}
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#read(int, byte[], int)}
	 * .
	 */
	@Test
	public final void testRead() {

		try {
			bs.open();

			for (int i = 0; i < INIT_SIZE; i++) {
				bs.allocate(-1);
			}

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}

		try {
			bs.open();
			byte[] blk = new byte[BLOCK_SIZE];
			for (int i = 0; i < INIT_SIZE; i++) {
				bs.read(i, blk, 1);
				assertTrue(Arrays.equals(bs.iniBlock, blk));
			}

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}
		try {
			bs.open();
			byte[] blk = new byte[BLOCK_SIZE];
			bs.read(1024, blk, 1);

			bs.close();
		} catch (StoreException e) {
			assertNotNull(e);
		}
	}

	/**
	 * Test method for
	 * {@link org.brackit.server.io.file.DefaultBlockSpace#write(int, byte[], int)}
	 * .
	 */
	@Test
	public final void testWrite() {

		byte[] blk = new byte[BLOCK_SIZE];

		try {
			bs.open();

			for (int i = 0; i < blk.length; i++) {
				blk[i] = (byte) 1;
			}
			for (int i = 0; i < INIT_SIZE; i++) {
				int lba = bs.allocate(-1);
				bs.write(lba, blk, 1);
			}

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
			try {
				bs.close();
			} catch (StoreException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		try {
			bs.open();
			byte[] blk1 = new byte[BLOCK_SIZE];
			for (int i = 0; i < INIT_SIZE; i++) {
				bs.read(i + 1, blk1, 1);
				assertTrue(Arrays.equals(blk, blk1));
			}

			bs.close();
		} catch (StoreException e) {
			e.printStackTrace();
		}
		try {
			bs.open();
			bs.write(1024, blk, 1);

			bs.close();
		} catch (StoreException e) {
			assertNotNull(e);
		}
	}

}
