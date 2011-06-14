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
package org.brackit.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BitArrayTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testBitArray() {
		BitArray ba = null;
		try {
			ba = new BitArray(0);
		} catch (RuntimeException e) {
		}
		try {
			ba = new BitArray(7);
		} catch (RuntimeException e) {
		}
		try {
			ba = new BitArray(9);
		} catch (RuntimeException e) {
		}
		try {
			ba = new BitArray(-1);
		} catch (RuntimeException e) {
		}

		assertNull(ba);

		ba = new BitArray(BitArray.BITS_PER_WORD);
		assertNotNull(ba);
		ba = new BitArray(BitArray.BITS_PER_WORD * 2);
		assertNotNull(ba);
	}

	@Test
	public final void testGet() {

		int baSize;
		BitArray ba;

		baSize = BitArray.BITS_PER_WORD;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
		}

		baSize = BitArray.BITS_PER_WORD * 3;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
		}

	}

	@Test
	public final void testSet() {
		int baSize;
		BitArray ba;

		baSize = BitArray.BITS_PER_WORD;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
			ba.set(i);
			assertTrue(ba.get(i));
		}

		baSize = BitArray.BITS_PER_WORD * 3;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
			ba.set(i);
			assertTrue(ba.get(i));
		}
	}

	@Test
	public final void testClear() {
		int baSize;
		BitArray ba;

		baSize = BitArray.BITS_PER_WORD;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
			ba.set(i);
			assertTrue(ba.get(i));
			ba.clear(i);
			assertFalse(ba.get(i));
		}

		baSize = BitArray.BITS_PER_WORD * 3;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
			ba.set(i);
			assertTrue(ba.get(i));
			ba.clear(i);
			assertFalse(ba.get(i));
		}
	}

	@Test
	public final void testSize() {
		int baSize;
		BitArray ba;

		baSize = BitArray.BITS_PER_WORD;

		ba = new BitArray(baSize);

		assertEquals(baSize, ba.size());

		baSize = BitArray.BITS_PER_WORD * 3;

		ba = new BitArray(baSize);

		assertEquals(baSize, ba.size());

	}

	@Test
	public final void testNextClearBit() {
		int baSize;
		BitArray ba;

		baSize = BitArray.BITS_PER_WORD;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
			ba.set(i);
			assertTrue(ba.get(i));
			if (i < baSize - 1) {
				assertEquals(i + 1, ba.nextClearBit(0));
				assertEquals(i + 1, ba.nextClearBit(i));
				assertEquals(i + 1, ba.nextClearBit(i + 1));
			} else {
				assertEquals(-1, ba.nextClearBit(0));
				assertEquals(-1, ba.nextClearBit(i));
			}
		}
		ba.clear(0);
		assertEquals(0, ba.nextClearBit(0));
		assertEquals(-1, ba.nextClearBit(1));

		ba.clear(4);
		assertEquals(0, ba.nextClearBit(0));
		assertEquals(4, ba.nextClearBit(1));
		assertEquals(4, ba.nextClearBit(3));
		assertEquals(4, ba.nextClearBit(4));
		assertEquals(-1, ba.nextClearBit(5));
		assertEquals(-1, ba.nextClearBit(7));

		ba.clear(7);
		assertEquals(0, ba.nextClearBit(0));
		assertEquals(4, ba.nextClearBit(1));
		assertEquals(4, ba.nextClearBit(3));
		assertEquals(4, ba.nextClearBit(4));
		assertEquals(7, ba.nextClearBit(5));
		assertEquals(7, ba.nextClearBit(7));

		baSize = BitArray.BITS_PER_WORD * 2;

		ba = new BitArray(baSize);

		for (int i = 0; i < baSize; i++) {
			assertFalse(ba.get(i));
			ba.set(i);
			assertTrue(ba.get(i));
			if (i < baSize - 1) {
				assertEquals(i + 1, ba.nextClearBit(0));
				assertEquals(i + 1, ba.nextClearBit(i));
				assertEquals(i + 1, ba.nextClearBit(i + 1));
			} else {
				assertEquals(-1, ba.nextClearBit(0));
				assertEquals(-1, ba.nextClearBit(i));
			}
		}
		ba.clear(0);
		assertEquals(0, ba.nextClearBit(0));
		assertEquals(-1, ba.nextClearBit(1));

		ba.clear(4);
		assertEquals(0, ba.nextClearBit(0));
		assertEquals(4, ba.nextClearBit(1));
		assertEquals(4, ba.nextClearBit(3));
		assertEquals(4, ba.nextClearBit(4));
		assertEquals(-1, ba.nextClearBit(5));
		assertEquals(-1, ba.nextClearBit(7));
		assertEquals(-1, ba.nextClearBit(8));

		ba.clear(7);
		assertEquals(0, ba.nextClearBit(0));
		assertEquals(4, ba.nextClearBit(1));
		assertEquals(4, ba.nextClearBit(3));
		assertEquals(4, ba.nextClearBit(4));
		assertEquals(7, ba.nextClearBit(5));
		assertEquals(7, ba.nextClearBit(7));
		assertEquals(-1, ba.nextClearBit(8));

		ba.clear(10);
		assertEquals(0, ba.nextClearBit(0));
		assertEquals(4, ba.nextClearBit(1));
		assertEquals(4, ba.nextClearBit(3));
		assertEquals(4, ba.nextClearBit(4));
		assertEquals(7, ba.nextClearBit(5));
		assertEquals(7, ba.nextClearBit(7));
		assertEquals(10, ba.nextClearBit(8));
		assertEquals(10, ba.nextClearBit(9));
		assertEquals(10, ba.nextClearBit(10));
		assertEquals(-1, ba.nextClearBit(11));
	}

}
