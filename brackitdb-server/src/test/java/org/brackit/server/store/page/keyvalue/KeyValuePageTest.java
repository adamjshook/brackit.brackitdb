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
package org.brackit.server.store.page.keyvalue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.util.ElementlessRecordGenerator;
import org.brackit.server.util.RecordGenerator;
import org.brackit.server.util.RecordGenerator.Record;
import org.brackit.xquery.node.parser.DocumentParser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public abstract class KeyValuePageTest {

	protected static final int BLOCK_SIZE = 2048;

	protected Random rand;

	protected KeyValuePageImpl page;

	protected class Entry {
		private byte[] key;
		private byte[] value;

		Entry(byte[] key, byte[] value) {
			this.key = key;
			this.value = value;
		}

		byte[] getKey() {
			return key;
		}

		byte[] getValue() {
			return value;
		}

		void setKey(byte[] key) {
			this.key = key;
		}

		void setValue(byte[] value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return String.format("{%s}->{%s}", Arrays.toString(key),
					(value != null) ? Arrays.toString(value) : null);
		}
	}

	@Test
	public void testAppendUntilFull() {
		testWriteUntilFull(false, false, false);
	}

	@Test
	public void testAppendUntilFullCompressed() {
		testWriteUntilFull(true, false, false);
	}

	@Test
	public void testPrependUntilFull() {
		testWriteUntilFull(false, true, false);
	}

	@Test
	public void testPrependUntilFullCompressed() {
		testWriteUntilFull(true, true, false);
	}

	@Test
	public void testInsertRandomUntilFull() {
		testWriteUntilFull(false, false, true);
	}

	@Test
	public void testInsertRandomUntilFullCompressed() {
		testWriteUntilFull(true, false, true);
	}

	protected void testWriteUntilFull(boolean compressed, boolean prepend,
			boolean random) {
		ArrayList<Entry> entries = new ArrayList<Entry>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());

		int no = 0;

		do {
			boolean success = false;
			Entry toWrite = generateRandomEntry(1, 6);
			int writeToPos = (random) ? (rand.nextInt(no + 1)) : ((prepend) ? 0
					: no);
			System.out.println(String.format("%3s: Writing %s to pos %s", no,
					toWrite, writeToPos));
			success = verifiedWrite(writeToPos, toWrite, compressed);

			if (success) {
				entries.add(writeToPos, toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < entries.size(); i++) {
				checkReadEntry(entries.get(i), new Entry(page.getKey(i), page
						.getValue(i)));
			}

			no++;
		} while (!pageFull);
	}

	@Ignore
	@Test
	public void testUpdateEntrysSameSize() {
		ArrayList<Entry> tuples = new ArrayList<Entry>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());

		int pos = 0;

		do {
			boolean success = false;
			Entry toWrite = generateRandomEntry(0, 6);
			success = verifiedWrite(pos, toWrite, false);

			if (success) {
				tuples.add(toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadEntry(tuples.get(i), new Entry(page.getKey(i), page
						.getValue(i)));
			}

			pos++;
		} while (!pageFull);

		pos = 0;
		for (Entry tuple : tuples) {
			Entry shuffled = shuffeElements(tuple);

			verifiedUpdate(pos, tuple, shuffled, false);
			pos++;
		}
	}

	@Test
	public void test() throws Exception {
		boolean compressed = true;
		page.format(page.getHandle().getPageID());
		RecordGenerator generator = new ElementlessRecordGenerator();
		new DocumentParser(new File(getClass()
				.getResource("/xmark/auction.xml").getFile())).parse(generator);
		List<Record> records = generator.getRecords();
		int slotNo = 0;

		for (Record record : records) {
			Entry toWrite = new Entry(record.deweyID.toBytes(), record.record);
			if (!verifiedWrite(slotNo, toWrite, compressed)) {
				break;
			}

			System.out.println("Write tuple " + slotNo + " with deweyID "
					+ record.deweyID);
			slotNo++;
		}

		// page.dump(new PrintWriter(System.out));

		for (int i = 0; i < slotNo; i++) {
			System.out.println(i + " Deleting tuple " + 0);
			verifiedDelete(0);
		}
	}

	@Test
	public void test2() throws Exception {
		page.format(page.getHandle().getPageID());
		Entry first = new Entry((new org.brackit.server.node.XTCdeweyID(
				"4711:1.3.3.61.17.3.9.5.3")).toBytes(), ElRecordAccess
				.createRecord(25, (byte) 2, "flight ensue surmise shepherdess"));
		Entry second = new Entry(
				(new org.brackit.server.node.XTCdeweyID(
						"4711:1.3.3.61.17.3.9.7")).toBytes(),
				ElRecordAccess
						.createRecord(
								23,
								(byte) 2,
								"miserable field tear hunted blam feather swears she berowne buttons mingle hurl vainglory freezes zounds salisbury woe pause rapier manifest soldier breeding abide ever brine bias vengeance meet crouching without sun all blunt letter wives quench dirge"));

		verifiedWrite(0, first, true);
		verifiedWrite(1, second, true);
	}

	@Test
	public void testDeleteRandomLarge() {
		testDeleteRandom(false, 2, 255, 255);
	}

	@Test
	public void testDeleteRandomLargeCompressed() {
		testDeleteRandom(true, 2, 255, 255);
	}

	@Test
	public void testDeleteRandom() {
		testDeleteRandom(false, 6, 0, 6);
	}

	@Test
	public void testDeleteRandomCompressed() {
		testDeleteRandom(true, 6, 0, 6);
	}

	@Test
	public void testDeleteAscending() {
		testDelete(false, 6, 0, 6, true);
	}

	@Test
	public void testDeleteAscendingCompressed() {
		testDelete(true, 6, 0, 6, true);
	}

	@Test
	public void testDeleteDescending() {
		testDelete(false, 6, 0, 6, false);
	}

	@Test
	public void testDeleteDescendingCompressed() {
		testDelete(true, 6, 0, 6, false);
	}

	protected void testDeleteRandom(boolean compressed, int numberOfElements,
			int minElementSize, int maxElementSize) {
		ArrayList<Entry> tuples = new ArrayList<Entry>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());

		int slotNo = 0;

		do {
			boolean success = false;
			Entry toWrite = generateRandomEntry(minElementSize, maxElementSize);
			success = verifiedWrite(slotNo, toWrite, compressed);

			if (success) {
				tuples.add(toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadEntry(tuples.get(i), new Entry(page.getKey(i), page
						.getValue(i)));
			}

			slotNo++;
		} while (!pageFull);

		for (int i = 0; i < slotNo - 1; i++) {
			int entryCount = page.getRecordCount();
			int deleteSlot = rand.nextInt(entryCount);
			Entry toDelete = tuples.remove(deleteSlot);
			System.out.println(i + " Deleting pos " + deleteSlot);
			boolean success = verifiedDelete(deleteSlot);

			for (int j = 0; j < tuples.size(); j++) {
				checkReadEntry(tuples.get(j), new Entry(page.getKey(j), page
						.getValue(j)));
			}
		}
	}

	protected void testDelete(boolean compressed, int numberOfElements,
			int minElementSize, int maxElementSize, boolean ascending) {
		ArrayList<Entry> tuples = new ArrayList<Entry>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());

		int slotNo = 0;

		do {
			boolean success = false;
			Entry toWrite = generateRandomEntry(minElementSize, maxElementSize);
			success = verifiedWrite(slotNo, toWrite, compressed);

			if (success) {
				tuples.add(toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadEntry(tuples.get(i), new Entry(page.getKey(i), page
						.getValue(i)));
			}

			slotNo++;
		} while (!pageFull);

		for (int i = 0; i < slotNo - 1; i++) {
			int deleteSlot = (ascending) ? 0 : page.getRecordCount() - 1;
			Entry toDelete = tuples.remove(deleteSlot);
			System.out.println(i + " Deleting pos " + deleteSlot);
			boolean success = verifiedDelete(deleteSlot);

			for (int j = 0; j < tuples.size(); j++) {
				checkReadEntry(tuples.get(j), new Entry(page.getKey(j), page
						.getValue(j)));
			}
		}
	}

	@Test
	public void testUpdateValueCompressed() {
		testUpdateValue(true);
	}

	@Test
	public void testUpdateValue() {
		testUpdateValue(false);
	}

	protected void testUpdateValue(boolean compressed) {
		ArrayList<Entry> written = new ArrayList<Entry>();
		page.format(page.getHandle().getPageID());

		int slotNo = 0;
		while (true) {
			Entry toWrite = generateRandomEntry(3, 3);

			if (!verifiedWrite(slotNo, toWrite, compressed)) {
				break;
			}

			written.add(toWrite);
			slotNo++;
		}

		// Logger.getLogger(KeyValuePage.class).setLevel(org.apache.log4j.Level.TRACE);
		for (int lengthOfUpdate = 1; lengthOfUpdate < 4; lengthOfUpdate++) {
			for (int updatePos = 0; updatePos < slotNo; updatePos++) {
				Entry entry = written.get(updatePos);
				byte[] update = generateValue(lengthOfUpdate, lengthOfUpdate,
						false, (byte) 0);

				Entry updateEntry = new Entry(entry.getKey(), update);
				System.out.println(String.format(
						"Updating value of pos %s with value of length %s",
						updatePos, lengthOfUpdate));

				if (!verifiedUpdate(updatePos, entry, updateEntry, compressed)) {
					Assert.fail("Update did not succeed");
				}
				entry.setValue(update);
			}
		}
	}

	public void testEmptyKeyValue() {
		testEmptyKeyValue(false);
	}

	@Test
	public void testEmptyKeyValueComressed() {
		testEmptyKeyValue(true);
	}

	protected void testEmptyKeyValue(boolean compressed) {
		page.format(page.getHandle().getPageID());
		if (!verifiedWrite(0, new Entry(new byte[0], new byte[0]), compressed)) {
			Assert.fail();
		}
	}

	protected boolean verifiedWrite(int slotNo, Entry toWrite,
			boolean compressed) {
		boolean success;
		int required = page.requiredSpaceForInsert(slotNo, toWrite.getKey(),
				toWrite.getValue(), compressed);
		int entryCount = page.getRecordCount();
		ArrayList<Entry> beforeEntrys = new ArrayList<Entry>(entryCount);

		for (int i = 0; i < entryCount; i++) {
			beforeEntrys.add(new Entry(page.getKey(i), page.getValue(i)));
		}

		int freeSpaceBefore = page.getFreeSpace();
		byte[] beforeImage = new byte[page.getHandle().page.length];
		System.arraycopy(page.getHandle().page, 0, beforeImage, 0,
				beforeImage.length);
		success = page.insert(slotNo, toWrite.getKey(), toWrite.getValue(),
				compressed);
		int freeSpaceAfter = page.getFreeSpace();

		if (success) {
			// System.out.println(String.format("Insert consumed space: predicted=%s, used=%s",
			// required, freeSpaceBefore - freeSpaceAfter));
			assertTrue(
					"Freespace before insert was greater or equal than predicted consumption",
					freeSpaceBefore >= required);
			assertEquals("Insert consumed space as predicted", required,
					freeSpaceBefore - freeSpaceAfter);
			assertEquals("Insert incremented entry count by one",
					entryCount + 1, page.getRecordCount());

			beforeEntrys.add(slotNo, toWrite);

			for (int i = 0; i < beforeEntrys.size(); i++) {
				Entry beforeEntry = beforeEntrys.get(i);
				verifyEntry(page, i, beforeEntry);
			}
		} else {
			assertTrue(
					"Freespace before insert was smaller than predicted consumption",
					freeSpaceBefore < required);
			assertEquals("No space was consumed during failed insert",
					freeSpaceBefore, freeSpaceAfter);
			assertTrue("Physical page was not modified during failed insert",
					Arrays.equals(page.getHandle().page, beforeImage));
			assertEquals("Entry count was not modified by failed insert",
					entryCount, page.getRecordCount());

			for (int i = 0; i < beforeEntrys.size(); i++) {
				Entry beforeEntry = beforeEntrys.get(i);
				verifyEntry(page, i, beforeEntry);
			}
		}
		return success;
	}

	protected boolean verifiedUpdate(int pos, Entry original, Entry update,
			boolean compressed) {
		boolean success;
		int required = page.requiredSpaceForUpdate(pos, update.getKey(), update
				.getValue(), compressed);
		int entryCount = page.getRecordCount();
		int freeSpaceBefore = page.getFreeSpace();
		ArrayList<Entry> beforeEntrys = new ArrayList<Entry>(entryCount);

		for (int i = 0; i < entryCount; i++) {
			Entry beforeEntry = new Entry(page.getKey(i), page.getValue(i));

			if (i == pos) {
				verifyEntry(page, i, original);
			}

			beforeEntrys.add(beforeEntry);
		}

		byte[] beforeImage = new byte[page.getHandle().page.length];
		System.arraycopy(page.getHandle().page, 0, beforeImage, 0,
				beforeImage.length);

		success = page.setValue(pos, update.getValue());
		int freeSpaceAfter = page.getFreeSpace();

		if (success) {
			assertTrue(
					"Freespace before update was greater or equal than predicted consumption",
					freeSpaceBefore >= required);
			assertEquals("Update consumed space as predicted", required,
					freeSpaceBefore - freeSpaceAfter);
			assertEquals("Entry count was not modified by update", entryCount,
					page.getRecordCount());

			for (int i = 0; i < beforeEntrys.size(); i++) {
				Entry beforeEntry = beforeEntrys.get(i);

				if (i == pos) {
					beforeEntry = update;
				}

				verifyEntry(page, i, beforeEntry);
			}
		} else {
			assertEquals("No space was consumed during failed update",
					freeSpaceBefore, freeSpaceAfter);
			assertTrue("Physical page was not modified during failed update",
					Arrays.equals(page.getHandle().page, beforeImage));
			assertEquals("Entry count was not modified by update", entryCount,
					page.getRecordCount());
		}
		return success;
	}

	protected void verifyEntry(KeyValuePageImpl page, int pos,
			Entry expectedEntry) {
		Entry testEntry = new Entry(page.getKey(pos), page.getValue(pos));
		assertNotNull("Test tuple is not null", testEntry);

		byte[] testKey = testEntry.getKey();
		byte[] testValue = testEntry.getValue();

		try {
			verifiyField(expectedEntry.getKey(), testKey);
			verifiyField(expectedEntry.getValue(), testValue);
		} catch (AssertionError e) {
			System.err.println("Error verifying pos " + pos);
			System.err.println("Expected Entry: " + expectedEntry);
			System.err.println("Test Entry: " + testEntry);
			throw e;
		}
	}

	protected void verifiyField(byte[] expectedField, byte[] testField) {
		if (expectedField == null) {
			assertNull("Test field is null", testField);
		} else {
			assertNotNull("Test field is not null", testField);
			assertEquals("Test field has same size as expected field",
					expectedField.length, testField.length);
			assertTrue("Test field has same content as expected field", Arrays
					.equals(expectedField, testField));
		}
	}

	protected boolean verifiedDelete(int pos) {
		int entryCount = page.getRecordCount();
		int freeSpaceBefore = page.getFreeSpace();
		byte[] beforeImage = new byte[page.getHandle().page.length];
		System.arraycopy(page.getHandle().page, 0, beforeImage, 0,
				beforeImage.length);

		ArrayList<Entry> beforeEntrys = new ArrayList<Entry>(entryCount);
		for (int i = 0; i < entryCount; i++) {
			beforeEntrys.add(new Entry(page.getKey(i), page.getValue(i)));
		}

		page.delete(pos);
		int freeSpaceAfter = page.getFreeSpace();
		// System.out.println(String.format("Delete freed space: used=%s",
		// freeSpaceAfter - freeSpaceBefore));
		assertTrue(
				"Freespace before delete was smaller than previous consumption",
				freeSpaceBefore < freeSpaceAfter);
		assertEquals("Delete decremented entry count by one", entryCount - 1,
				page.getRecordCount());

		beforeEntrys.remove(pos);

		for (int i = 0; i < beforeEntrys.size(); i++) {
			Entry beforeEntry = beforeEntrys.get(i);
			verifyEntry(page, i, beforeEntry);
		}

		return true;
	}

	protected Entry generateRandomEntry(int minSizeOfElement,
			int maxSizeOfElement) {
		byte[] key = generateValue(minSizeOfElement, maxSizeOfElement, false,
				(byte) -1);
		byte[] value = generateValue(minSizeOfElement, maxSizeOfElement, false,
				(byte) -1);
		Entry entry = new Entry(key, value);

		return entry;
	}

	protected byte[] generateValue(int minSizeOfElement, int maxSizeOfElement,
			boolean applyMask, byte mask) {
		int size = minSizeOfElement
				+ rand.nextInt(maxSizeOfElement - minSizeOfElement + 1);
		byte[] element = null;

		if (size >= 0) {
			element = new byte[size];
			rand.nextBytes(element);
		}

		if (applyMask) {
			for (int i = 0; i < element.length; i++) {
				element[i] &= mask;
			}
		}
		return element;
	}

	protected Entry shuffeElements(Entry entry) {
		byte[] key = entry.getKey();
		byte[] value = entry.getValue();
		entry.setKey(value);
		entry.setValue(key);

		return entry;
	}

	// @Test
	public void testCompression() throws Exception {
		byte[] previousValue = null;

		byte[][] v = new byte[2000000][];

		for (int i = 0; i < v.length; i++) {
			v[i] = generateValue(5, 30, true, (byte) 64);
		}
		Arrays.sort(v, new Comparator<byte[]>() {
			public int compare(byte[] value1, byte[] value2) {
				int length1 = value1.length;
				int length2 = value2.length;
				int length = ((length1 <= length2) ? length1 : length2);

				int pos = -1;
				while (++pos < length) {
					byte v2 = value2[pos];
					byte v1 = value1[pos];

					if (v1 != v2) {
						return v1 - v2;
					}
				}

				return length1 - length2;
			}
		});

		int maxPrefixLength = 0;
		int sumPrefixLength = 0;
		int prefixLengthGreaterZero = 0;
		for (int i = 1; i < v.length; i++) {
			// System.out.println(Arrays.toString(v[i]));
			int prefixLength = 0;
			int l = Math.min(v[i - 1].length, v[i].length);
			while (prefixLength < l) {
				if (v[i - 1][prefixLength] != v[i][prefixLength])
					break;
				prefixLength++;
			}

			maxPrefixLength = Math.max(maxPrefixLength, prefixLength);
			sumPrefixLength += prefixLength;
			if (prefixLength > 3)
				prefixLengthGreaterZero++;
		}
		double avgPrefixLength = (double) sumPrefixLength / (double) v.length;
		System.out
				.println(String
						.format(
								"MaxPrefixLength=%s #PrefixLengthGreaterZero=%s AvgPrefixLength=%s",
								maxPrefixLength, prefixLengthGreaterZero,
								avgPrefixLength));

		long start = System.currentTimeMillis();
		for (byte[] value : v) {
			Assert.fail("FIXME");
			// compress and decompress
			// assertTrue(String.format("decompressed value i is equal to original"),
			// Arrays.equals(decompressed, value));
			previousValue = value;
		}
		long end = System.currentTimeMillis();
		System.out.println(v.length + " encode/decode cycles: " + (end - start)
				+ " ms");
	}

	protected void checkReadEntry(Entry written, Entry read) {
		if (written != null) {
			assertNotNull("read entry is not null", read);

			byte[] writtenKey = written.getKey();
			byte[] readKey = read.getKey();
			byte[] writtenValue = written.getValue();
			byte[] readValue = read.getValue();

			assertNotNull(String.format("read key is not null"), readKey);
			assertEquals(String
					.format("size read key i is equal to size of written"),
					writtenKey.length, readKey.length);
			assertTrue(String.format("read key is equal to written"), Arrays
					.equals(writtenKey, readKey));

			assertNotNull(String.format("read value is not null"), readValue);
			assertEquals(String
					.format("size read value i is equal to size of written"),
					writtenValue.length, readValue.length);
			assertTrue(String.format("read value is equal to written"), Arrays
					.equals(writtenValue, readValue));
		}
	}

	@Before
	public void setUp() throws Exception {
		rand = new Random(123456789);
		page = createPage();
	}

	protected abstract KeyValuePageImpl createPage() throws BufferException;

	@After
	public void tearDown() {

	}
}
