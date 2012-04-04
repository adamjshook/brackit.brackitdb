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
package org.brackit.server.util.sort;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.brackit.server.node.el.ElNode;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.store.Field;
import org.brackit.server.util.Calc;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;
import org.junit.Test;

public abstract class SortTest {
	protected Sort sorter;

	@Test
	public void testSortedDesc() throws Exception {
		int noItems = 1000000;
		int currentKey = noItems;
		int currentValue = noItems + 256;
		long bytes = 0;

		long start = System.currentTimeMillis();
		for (int i = 0; i < noItems; i++) {
			byte[] key = Calc.fromInt(currentKey--);
			byte[] value = Calc.fromInt(currentValue--);
			sorter.add(new SortItem(key, value));
			bytes += key.length + value.length;
		}
		checkSortResult(sorter, noItems);
		long end = System.currentTimeMillis();
//		System.out.println(String.format(
//				"Sorting %s items (%.2f MB) took %s ms", noItems,
//				bytes / ((double) 1024 * 1024), end - start));
	}

	@Test
	public void testSortedAsc() throws Exception {
		int currentKey = 0;
		int currentValue = 256;
		int noItems = 1000000;
		long bytes = 0;

		long start = System.currentTimeMillis();
		for (int i = 0; i < noItems; i++) {
			byte[] key = Calc.fromInt(currentKey++);
			byte[] value = Calc.fromInt(currentValue++);
			sorter.add(new SortItem(key, value));
			bytes += key.length + value.length;
		}
		checkSortResult(sorter, noItems);
		long end = System.currentTimeMillis();
//		System.out.println(String.format(
//				"Sorting %s items (%.2f MB) took %s ms", noItems,
//				bytes / ((double) 1024 * 1024), end - start));
	}

	@Test
	public void testRandomKeysRandomValues() throws Exception {
		int noItems = 500000;
		Random rand = new Random(123456789);
		long bytes = 0;
		int minKeyLength = 1;
		int maxKeyLength = 15;

		int minValueLength = 0;
		int maxValueLength = 50;

		long start = System.currentTimeMillis();
		for (int i = 0; i < noItems; i++) {
			byte[] key = new byte[minKeyLength
					+ rand.nextInt(maxKeyLength - minKeyLength)];
			byte[] value = new byte[maxValueLength
					+ rand.nextInt(maxValueLength - minValueLength)];
			rand.nextBytes(key);
			rand.nextBytes(value);
			sorter.add(new SortItem(key, value));
			bytes += key.length + value.length;
		}
		checkSortResult(sorter, noItems);
		long end = System.currentTimeMillis();

//		System.out.println(String.format(
//				"Sorting %s items (%.2f MB) took %s ms", noItems,
//				bytes / ((double) 1024 * 1024), end - start));
	}

	private void checkSortResult(Sort sorter, int noItems) throws Exception {
		int sortedCount = 0;
		SortItem previous = null;
		SortItem current;
		Stream<? extends SortItem> sorted = sorter.sort();

		try {

			while ((current = sorted.next()) != null) {
				if (previous != null) {
					assertTrue("Sort correct.", previous.compareDeepTo(current,
							Field.BYTEARRAY, Field.BYTEARRAY) <= 0);
				}
				previous = current;
				sortedCount++;
			}
		} finally {
			sorted.close();
		}

		assertEquals("Got correct number of results", noItems, sortedCount);
	}
}

class SortTestByteEncoder implements IndexEncoder<ElNode> {
	private Field keyType;
	private Field valueType;
	private boolean sortKey;
	private boolean sortValue;

	public SortTestByteEncoder(Field keyType, Field valueType, boolean sortKey,
			boolean sortValue) {
		this.keyType = keyType;
		this.valueType = valueType;
		this.sortKey = sortKey;
		this.sortValue = sortValue;
	}

	@Override
	public Field getKeyType() {
		return keyType;
	}

	@Override
	public Field getValueType() {
		return valueType;
	}

	@Override
	public boolean sortKey() {
		return sortKey;
	}

	@Override
	public boolean sortValue() {
		return sortValue;
	}

	@Override
	public ElNode decode(byte[] key, byte[] value) throws DocumentException {
		return null;
	}

	@Override
	public byte[] encodeKey(ElNode node) throws DocumentException {
		return null;
	}

	@Override
	public byte[] encodeValue(ElNode node) throws DocumentException {
		return null;
	}

	@Override
	public int getUnitID() {
		return 0;
	}

}