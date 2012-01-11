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
package org.brackit.server.store.page.slot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.store.SearchMode;
import org.brackit.server.util.Calc;
import org.brackit.server.util.ElementlessRecordGenerator;
import org.brackit.server.util.RecordGenerator;
import org.brackit.server.util.RecordGenerator.Record;
import org.brackit.xquery.node.parser.DocumentParser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SlottedPageTest {
	protected static final int BLOCK_SIZE = 4 * 2048;

	protected Random rand;

	protected SlottedPage page;

	@Test
	public void testWriteUntilFull() {
		testWriteUntilFull(false);
	}

	@Test
	public void testWriteUntilFullCompressed() {
		testWriteUntilFull(true);
	}

	private void testWriteUntilFull(boolean compressed) {
		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());

		int slotNo = 0;

		do {
			boolean success = false;
			Tuple toWrite = generateRandomTuple(6, 6, -1, 6);
			System.out.println(String.format("Writing %s to slot %s", toWrite,
					slotNo));
			success = verifiedWrite(slotNo, toWrite, compressed);

			if (success) {
				tuples.add(toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadTuple(tuples.get(i), page.read(i));
			}

			slotNo++;
		} while (!pageFull);
	}

	@Test
	public void testUpdateTuplesSameSize() {
		testUpdateTuplesSameSize(false);
	}

	@Test
	public void testUpdateTuplesSameSizeCompressed() {
		testUpdateTuplesSameSize(true);
	}

	private void testUpdateTuplesSameSize(boolean compression) {
		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());

		int slotNo = 0;

		do {
			boolean success = false;
			Tuple toWrite = generateRandomTuple(6, 6, -1, 6);
			success = verifiedWrite(slotNo, toWrite, compression);

			if (success) {
				tuples.add(toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadTuple(tuples.get(i), page.read(i));
			}

			slotNo++;
		} while (!pageFull);

		slotNo = 0;
		for (Tuple tuple : tuples) {
			Tuple shuffled = shuffeElements(tuple);
			verifiedUpdate(slotNo, tuple, shuffled, compression);
			slotNo++;
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
			Tuple toWrite = new ArrayTuple(new byte[][] {
					record.deweyID.toBytes(), record.record });
			if (!verifiedWrite(slotNo, toWrite, compressed)) {
				break;
			}

			System.out.println("Write tuple " + slotNo);
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
		Tuple first = new ArrayTuple(new byte[][] {
				(new org.brackit.server.node.XTCdeweyID(
						"4711:1.3.3.61.17.3.9.5.3")).toBytes(),
				ElRecordAccess.createRecord(25, (byte) 2,
						"flight ensue surmise shepherdess") });
		Tuple second = new ArrayTuple(
				new byte[][] {
						(new org.brackit.server.node.XTCdeweyID(
								"4711:1.3.3.61.17.3.9.7")).toBytes(),
						ElRecordAccess
								.createRecord(
										23,
										(byte) 2,
										"miserable field tear hunted blam feather swears she berowne buttons mingle hurl vainglory freezes zounds salisbury woe pause rapier manifest soldier breeding abide ever brine bias vengeance meet crouching without sun all blunt letter wives quench dirge") });

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
		testDeleteRandom(false, 6, -1, 6);
	}

	@Test
	public void testDeleteRandomCompressed() {
		testDeleteRandom(true, 6, -1, 6);
	}

	private void testDeleteRandom(boolean compressed, int numberOfElements,
			int minElementSize, int maxElementSize) {
		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());

		int slotNo = 0;

		do {
			boolean success = false;
			Tuple toWrite = generateRandomTuple(numberOfElements,
					numberOfElements, minElementSize, maxElementSize);
			success = verifiedWrite(slotNo, toWrite, compressed);

			if (success) {
				tuples.add(toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadTuple(tuples.get(i), page.read(i));
			}

			slotNo++;
		} while (!pageFull);

		for (int i = 0; i < page.getRecordCount(); i++) {
			int entryCount = page.getRecordCount();
			int deleteSlot = rand.nextInt(entryCount);
			Tuple toDelete = tuples.remove(deleteSlot);
			boolean success = verifiedDelete(deleteSlot);

			for (int j = 0; j < tuples.size(); j++) {
				checkReadTuple(tuples.get(j), page.read(j));
			}
		}
	}

	@Test
	public void testUpdateFieldCompressed() {
		testUpdateField(true);
	}

	@Test
	public void testUpdateField() {
		testUpdateField(false);
	}

	private void testUpdateField(boolean compressed) {
		page.format(page.getHandle().getPageID());

		int slotNo = 0;
		while (true) {
			Tuple toWrite = generateRandomTuple(3, 3, 3, 3);

			if (!verifiedWrite(slotNo, toWrite, compressed)) {
				break;
			}
			slotNo++;
		}

		for (int updateOfFieldNo = 0; updateOfFieldNo < 3; updateOfFieldNo++) {
			for (int lengthOfUpdate = 1; lengthOfUpdate < 4; lengthOfUpdate++) {
				for (int updateSlot = 0; updateSlot < slotNo; updateSlot++) {
					byte[] update = generateValue(lengthOfUpdate,
							lengthOfUpdate, false, (byte) 0);

					// System.out.println(String.format("Updating field %s of slot %s with value of length %s",
					// updateOfFieldNo, updateSlot, lengthOfUpdate));

					if (!verifiedFieldUpdate(updateSlot, updateOfFieldNo,
							update)) {
						Assert.fail("Update did not succeed");
					}
				}
			}
		}
	}

	@Test
	public void testDefragment() {
		ArrayList<Tuple> tuples = new ArrayList<Tuple>();
		ArrayList<Tuple> removed = new ArrayList<Tuple>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());
		int freeSpaceAtStart = page.getFreeSpace();
		int usableSpace = page.getUsableSpace();
		int slotNo = 0;

		do {
			boolean success = false;
			Tuple toWrite = generateRandomTuple(6, 6, -1, 6);
			success = verifiedWrite(slotNo, toWrite, false);

			if (success) {
				tuples.add(toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadTuple(tuples.get(i), page.read(i));
			}

			slotNo++;
		} while (!pageFull);

		int inserted = page.getRecordCount();
		for (int i = 0; i < inserted; i++) {
			int entryCount = page.getRecordCount();
			int deleteSlot = rand.nextInt(entryCount);
			Tuple toDelete = tuples.remove(deleteSlot);
			boolean success = verifiedDelete(deleteSlot);
			removed.add(toDelete);

			for (int j = 0; j < tuples.size(); j++) {
				checkReadTuple(tuples.get(j), page.read(j));
			}
		}

		int freeSpaceAfterDelete = page.getFreeSpace();
		slotNo = 0;
		for (Tuple toInsert : removed) {
			boolean success = false;
			success = verifiedWrite(slotNo, toInsert, false);

			if (success) {
				tuples.add(toInsert);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < tuples.size(); i++) {
				checkReadTuple(tuples.get(i), page.read(i));
			}

			slotNo++;
		}
	}

	protected boolean verifiedWrite(int slotNo, Tuple toWrite,
			boolean compressed) {
		boolean success;
		int required = page.requiredSpaceForInsert(slotNo, toWrite, compressed);
		int entryCount = page.getRecordCount();
		ArrayList<Tuple> beforeTuples = new ArrayList<Tuple>(entryCount);

		for (int i = 0; i < entryCount; i++) {
			beforeTuples.add(page.read(i));
		}

		int freeSpaceBefore = page.getFreeSpace();
		byte[] beforeImage = new byte[page.getHandle().page.length];
		System.arraycopy(page.getHandle().page, 0, beforeImage, 0,
				beforeImage.length);
		success = page.write(slotNo, toWrite, false, compressed);
		int freeSpaceAfter = page.getFreeSpace();

		if (success) {
			assertTrue(
					"Freespace before insert was greater or equal than predicted consumption",
					freeSpaceBefore >= required);
			assertEquals("Insert consumed space as predicted", required,
					freeSpaceBefore - freeSpaceAfter);
			assertEquals("Insert incremented entry count by one",
					entryCount + 1, page.getRecordCount());

			beforeTuples.add(slotNo, toWrite);

			for (int i = 0; i < beforeTuples.size(); i++) {
				Tuple beforeTuple = beforeTuples.get(i);
				verifyTuple(page, i, beforeTuple);
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

			for (int i = 0; i < beforeTuples.size(); i++) {
				Tuple beforeTuple = beforeTuples.get(i);
				verifyTuple(page, i, beforeTuple);
			}
		}
		return success;
	}

	protected boolean verifiedFieldUpdate(int slotNo, int fieldNo,
			byte[] newValue) {
		boolean success;
		int entryCount = page.getRecordCount();
		int freeSpaceBefore = page.getFreeSpace();
		ArrayList<Tuple> beforeTuples = new ArrayList<Tuple>(entryCount);

		for (int i = 0; i < entryCount; i++) {
			beforeTuples.add(page.read(i));
		}

		byte[] beforeImage = new byte[page.getHandle().page.length];
		System.arraycopy(page.getHandle().page, 0, beforeImage, 0,
				beforeImage.length);
		success = page.writeField(slotNo, fieldNo, newValue);
		int freeSpaceAfter = page.getFreeSpace();

		if (success) {
			assertEquals("Entry count was not modified by update", entryCount,
					page.getRecordCount());
			byte[] afterField = page.readField(slotNo, fieldNo);
			verifiyField(newValue, afterField);

			for (int i = 0; i < beforeTuples.size(); i++) {
				Tuple beforeTuple = beforeTuples.get(i);
				if (i == slotNo)
					beforeTuple.set(fieldNo, newValue);

				verifyTuple(page, i, beforeTuple);
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

	private void verifyTuple(SlottedPage page, int slotNo, Tuple expectedTuple) {
		Tuple testTuple = page.read(slotNo);
		assertNotNull("Test tuple is not null", testTuple);
		assertEquals("Test tuple has same size as expected tuple",
				expectedTuple.getSize(), testTuple.getSize());

		for (int i = 0; i < expectedTuple.getSize(); i++) {
			byte[] expectedField = expectedTuple.get(i);
			byte[] testField = page.readField(slotNo, i);
			byte[] testFieldFromTuple = testTuple.get(i);

			try {
				verifiyField(expectedField, testField);
				verifiyField(expectedField, testFieldFromTuple);
			} catch (AssertionError e) {
				System.out.println("Expected Tuple: " + expectedTuple);
				System.out.println("Test Tuple: " + testTuple);
				throw e;
			}
		}
	}

	private void verifiyField(byte[] expectedField, byte[] testField) {
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

	private boolean verifiedUpdate(int slotNo, Tuple original, Tuple update,
			boolean compression) {
		boolean success;
		int required = page.requiredSpaceForUpdate(slotNo, update, compression);
		int entryCount = page.getRecordCount();
		int freeSpaceBefore = page.getFreeSpace();
		byte[] beforeImage = new byte[page.getHandle().page.length];
		System.arraycopy(page.getHandle().page, 0, beforeImage, 0,
				beforeImage.length);
		success = page.write(slotNo, update, true, compression);
		int freeSpaceAfter = page.getFreeSpace();

		if (success) {
			assertTrue(
					"Freespace before update was greater or equal than predicted consumption",
					freeSpaceBefore >= required);
			assertEquals("Update consumed space as predicted", required,
					freeSpaceBefore - freeSpaceAfter);
			assertEquals("Entry count was not modified by update", entryCount,
					page.getRecordCount());
		} else {
			assertTrue(
					"Freespace before update was smaller than predicted consumption",
					freeSpaceBefore < required);
			assertEquals("No space was consumed during failed update",
					freeSpaceBefore, freeSpaceAfter);
			assertTrue("Physical page was not modified during failed update",
					Arrays.equals(page.getHandle().page, beforeImage));
			assertEquals("Entry count was not modified by update", entryCount,
					page.getRecordCount());
		}
		return success;
	}

	private boolean verifiedDelete(int slotNo) {
		int entryCount = page.getRecordCount();
		int freeSpaceBefore = page.getFreeSpace();
		byte[] beforeImage = new byte[page.getHandle().page.length];
		System.arraycopy(page.getHandle().page, 0, beforeImage, 0,
				beforeImage.length);

		ArrayList<Tuple> beforeTuples = new ArrayList<Tuple>(entryCount);
		for (int i = 0; i < entryCount; i++) {
			beforeTuples.add(page.read(i));
		}

		page.delete(slotNo);
		int freeSpaceAfter = page.getFreeSpace();
		assertTrue(
				"Freespace before delete was smaller than previous consumption",
				freeSpaceBefore < freeSpaceAfter);
		assertEquals("Delete decremented entry count by one", entryCount - 1,
				page.getRecordCount());

		beforeTuples.remove(slotNo);

		for (int i = 0; i < beforeTuples.size(); i++) {
			Tuple beforeTuple = beforeTuples.get(i);
			verifyTuple(page, i, beforeTuple);
		}

		return true;
	}

	private Tuple generateRandomTuple(int minNoOfElements, int maxNoOfElements,
			int minSizeOfElement, int maxSizeOfElement) {
		int noOfElements = minNoOfElements
				+ rand.nextInt(maxNoOfElements - minNoOfElements + 1);

		Tuple tuple = new ArrayTuple(noOfElements);

		for (int i = 0; i < noOfElements; i++) {
			byte[] element = generateValue(minSizeOfElement, maxSizeOfElement,
					false, (byte) -1);

			tuple.set(i, element);
		}

		return tuple;
	}

	private byte[] generateValue(int minSizeOfElement, int maxSizeOfElement,
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

	private Tuple shuffeElements(Tuple tuple) {
		byte[][] elements = new byte[tuple.getSize()][];

		for (int i = 0; i < tuple.getSize(); i++) {
			elements[i] = tuple.get(i);
		}

		Collections.shuffle(Arrays.asList(elements));

		return new ArrayTuple(elements);
	}

	@Test
	public void testWriteReadSingle() {
		Tuple written = new ArrayTuple(6);
		written.set(0, new byte[] { (byte) 128 });
		written.set(1, new byte[] { (byte) 255 });
		written.set(2, new byte[] { (byte) 255, (byte) 128 });
		written.set(3, new byte[] {});
		written.set(4, new byte[] { (byte) 255, (byte) 255 });
		written.set(5, null);
		page.format(page.getHandle().getPageID());
		page.write(0, written, false);
		Tuple read = page.read(0);

		checkReadTuple(written, read);
	}

	@Test
	public void testWriteReadSingleCompressed() {
		Tuple written = new ArrayTuple(6);
		written.set(0, new byte[] { (byte) 128 });
		written.set(1, new byte[] { (byte) 255 });
		written.set(2, new byte[] { (byte) 255, (byte) 128 });
		written.set(3, new byte[] {});
		written.set(4, new byte[] { (byte) 255, (byte) 255 });
		written.set(5, null);
		page.format(page.getHandle().getPageID());
		page.write(0, written, false, true);
		Tuple read = page.read(0);

		checkReadTuple(written, read);
	}

	@Test
	public void testWrite2inSameSlot() {
		testWrite2InSameSlot(false);
	}

	@Test
	public void testWrite2inSameSlotCompressed() {
		testWrite2InSameSlot(true);
	}

	private void testWrite2InSameSlot(boolean compressed) {
		Tuple firstWritten = new ArrayTuple(6);
		firstWritten.set(0, new byte[] { (byte) 128 });
		firstWritten.set(1, new byte[] { (byte) 255 });
		firstWritten.set(2, new byte[] { (byte) 255, (byte) 128 });
		firstWritten.set(3, new byte[] {});
		firstWritten.set(4, new byte[] { (byte) 255, (byte) 255 });
		firstWritten.set(5, null);

		Tuple secondWritten = new ArrayTuple(5);
		secondWritten.set(0, new byte[] { (byte) 128 });
		secondWritten.set(1, null);
		secondWritten.set(2, new byte[] { (byte) 255, (byte) 128 });
		secondWritten.set(3, new byte[] {});

		page.format(page.getHandle().getPageID());
		page.write(0, firstWritten, false, compressed);
		page.write(0, secondWritten, false, compressed);
		Tuple readSlot0 = page.read(0);
		Tuple readSlot1 = page.read(1);

		checkReadTuple(secondWritten, readSlot0);
		checkReadTuple(firstWritten, readSlot1);
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

	private void checkReadTuple(Tuple written, Tuple read) {
		if (written != null) {
			assertNotNull("read tuple is not null", read);
			assertEquals("read same number of elements", written.getSize(),
					read.getSize());

			for (int i = 0; i < written.getSize(); i++) {
				byte[] writtenElement = written.get(i);
				byte[] readElement = read.get(i);

				if (writtenElement == null) {
					assertNull(String.format("read element %s is null", i),
							readElement);
				} else {
					assertNotNull(String.format("read element %s is not null",
							i), readElement);
					assertEquals(
							String
									.format("size read element i is equal to size of written"),
							writtenElement.length, readElement.length);
					assertTrue(String
							.format("read element i is equal to written"),
							Arrays.equals(writtenElement, readElement));
				}
			}
		} else {
			assertNull("read tuple is null", read);
		}
	}

	public static void main(String[] args) {
		int MAX_VALUES = 30;
		byte[][] values = new byte[MAX_VALUES][];

		for (int i = 0; i < MAX_VALUES; i++) {
			values[i] = Calc.fromInt(i * 2);
		}

		for (int i = 1; i < 2 * MAX_VALUES; i++) {
			SearchMode searchMode = SearchMode.GREATER_OR_EQUAL;
			for (int j = 0; j < MAX_VALUES; j++) {
				// if (log.isTraceEnabled())
				// {
				// log.trace(String.format("%s is inside %s %s : %s",
				// Field.INTEGER.valueToString(values[j]), searchMode, i,
				// searchMode.isInside(Field.INTEGER, values[j],
				// XTCcalc.getBytes(4, i))));
				// }
			}

			// System.out.println(String.format("%4s -> %4s", i,
			// Field.INTEGER.valueToString(values[findSlot(searchMode,
			// Field.INTEGER, 1, XTCcalc.getBytes(4, i))])));
		}
	}

	@Test
	public void testInsertBeforeBug() {
		page.format(page.getHandle().getPageID());
		byte[][] elementsA = new byte[][] { Calc.fromUIntVar(2400),
				Calc.fromUIntVar(2400) };
		byte[][] elementsB = new byte[][] { Calc.fromUIntVar(2300),
				Calc.fromUIntVar(2300) };
		Tuple toWriteA = new ArrayTuple(elementsA);
		Tuple toWriteB = new ArrayTuple(elementsB);
		verifiedWrite(0, toWriteA, true);
		verifiedWrite(1, toWriteA, true);
		verifiedWrite(1, toWriteB, true);

	}

	@Test
	public void testInsertBeforeBug2() {
		page.format(page.getHandle().getPageID());
		byte[][] elements0 = new byte[][] { new byte[] { 12 },
				Calc.fromUIntVar(2400) };
		byte[][] elementsA = new byte[][] { Calc.fromUIntVar(2400),
				Calc.fromUIntVar(2400) };
		byte[][] elementsB = new byte[][] { Calc.fromUIntVar(2300),
				Calc.fromUIntVar(2300) };
		Tuple toWrite0 = new ArrayTuple(elements0);
		Tuple toWriteA = new ArrayTuple(elementsA);
		Tuple toWriteB = new ArrayTuple(elementsB);
		verifiedWrite(0, toWrite0, true);
		verifiedWrite(1, toWriteA, true);
		verifiedWrite(1, toWriteB, true);
	}

	@Before
	public void setUp() throws Exception {
		rand = new Random(123456789);
		Handle handle = new Handle(BLOCK_SIZE) {
		};
		handle.init(new PageID(3));
		page = new SlottedPage(null, handle);
	}

	@After
	public void tearDown() {

	}
}
