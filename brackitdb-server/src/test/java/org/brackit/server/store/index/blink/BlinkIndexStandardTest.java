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
package org.brackit.server.store.index.blink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.tx.TxException;
import org.brackit.server.util.Calc;
import org.junit.Ignore;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BlinkIndexStandardTest extends AbstractBlinkIndexTest {
	protected static final Logger log = Logger
			.getLogger(BlinkIndexStandardTest.class.getName());

	public BlinkIndexStandardTest() {
		super();
	}

	@Test
	public void testInsertUniqueIndexAscendingKeys()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);

		int i = 0;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, uniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, uniqueRootPageID, "/media/ramdisk/" + number(i +
			// 1) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key found", (readValue != null));
			if (log.isDebugEnabled())
				log.debug(String.format(
						"key=%s written value=%s read value=%s", Field.UINTEGER
								.toString(entry.key), Field.UINTEGER
								.toString(readValue), Field.UINTEGER
								.toString(readValue)));
			assertTrue("read value is same as written value", (Field.UINTEGER
					.compare(readValue, entry.value) == 0));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// uniqueRootPageID);
			i++;
		}

		// indexPageHelper.checkIndexConsistency(t2, sm.buffer,
		// uniqueRootPageID);
	}

	@Test
	public void testInsertUniqueIndexDescendingKeys()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		Collections.reverse(entries);
		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);
		int i = 0;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, uniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" + number(i
			// + 1) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key found", (readValue != null));
			if (log.isDebugEnabled())
				log.debug(String.format(
						"key=%s written value=%s read value=%s", Field.UINTEGER
								.toString(entry.key), Field.UINTEGER
								.toString(readValue), Field.UINTEGER
								.toString(readValue)));
			assertTrue("read value is same as written value", (Field.UINTEGER
					.compare(readValue, entry.value) == 0));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// uniqueRootPageID);
			i++;
		}

		// indexPageHelper.checkIndexConsistency(t2, sm.buffer,
		// uniqueRootPageID);
	}

	@Test
	public void testInsertUniqueIndexRandomKeys() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		Collections.shuffle(entries, rand);

		int i = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, uniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key found", (readValue != null));
			if (log.isDebugEnabled())
				log.debug(String.format(
						"key=%s written value=%s read value=%s", Field.UINTEGER
								.toString(entry.key), Field.UINTEGER
								.toString(entry.value), Field.UINTEGER
								.toString(readValue)));
			assertTrue("read value is same as written value", (Field.UINTEGER
					.compare(readValue, entry.value) == 0));
			// indexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// uniqueRootPageID);
			i++;
		}
		// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
		// uniqueRootPageID);
	}

	@Test
	public void testDeleteUniqueIndexDescendingKeys()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		Collections.reverse(entries);

		int j = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", j,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, uniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(j) + ".dot", false);
			j++;
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);

			if (readValue != null) {
				System.out.println(Field.UINTEGER.toString(entry.key));
				System.out.println(Field.UINTEGER.toString(entry.value));
				System.out.println(Field.UINTEGER.toString(readValue));
				index.read(t2, uniqueRootPageID, entry.key);
			}

			assertTrue("value for deleted key not found", (readValue == null));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID,
			// null, idxDescription);
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testDeleteUniqueIndexAscendingKey()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		int j = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", j,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, uniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(j) + ".dot", false);
			j++;
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for deleted key not found", (readValue == null));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID,
			// null, idxDescription);
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testDeleteUniqueIndexRandomKeys() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		Collections.shuffle(entries, rand);

		int i = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, uniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", false);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for deleted key not found", (readValue == null));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID,
			// null, idxDescription);
			i++;
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testUpdateUniqueIndexAscendingKeys()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		int j = 1;

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for key update found", (readValue != null));
			byte[] updateValue = Calc
					.fromUIntVar(Calc.toUIntVar(entry.value) + 1);
			index.update(t2, uniqueRootPageID, entry.key, entry.value,
					updateValue);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(j) + ".dot", false);
			j++;
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for updated key found", (readValue != null));
			if (log.isDebugEnabled())
				log.debug(String.format(
						"key=%s written value=%s read value=%s", Field.UINTEGER
								.toString(entry.key), Field.UINTEGER
								.toString(readValue), Field.UINTEGER
								.toString(readValue)));
			assertTrue("read value is same as updated value", (Field.UINTEGER
					.compare(readValue, updateValue) == 0));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// uniqueRootPageID, null);
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testUpdateUniqueIndexDescendingKeys()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		int j = 1;
		Collections.reverse(entries);

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for key update found", (readValue != null));
			byte[] updateValue = Calc
					.fromUIntVar(Calc.toUIntVar(entry.value) + 1);
			index.update(t2, uniqueRootPageID, entry.key, entry.value,
					updateValue);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(j) + ".dot", false);
			j++;
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for updated key found", (readValue != null));
			if (log.isDebugEnabled())
				log.debug(String.format(
						"key=%s written value=%s read value=%s", Field.UINTEGER
								.toString(entry.key), Field.UINTEGER
								.toString(readValue), Field.UINTEGER
								.toString(readValue)));
			assertTrue("read value is same as updated value", (Field.UINTEGER
					.compare(readValue, updateValue) == 0));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// uniqueRootPageID, null);
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testUpdateUniqueIndexRandomKeys() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		int j = 1;
		Collections.shuffle(entries, rand);

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for key update found", (readValue != null));
			byte[] updateValue = Calc
					.fromUIntVar(Calc.toUIntVar(entry.value) + 1);
			index.update(t2, uniqueRootPageID, entry.key, entry.value,
					updateValue);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(j) + ".dot", false);
			j++;
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for updated key found", (readValue != null));
			if (log.isDebugEnabled())
				log.debug(String.format(
						"key=%s written value=%s read value=%s", Field.UINTEGER
								.toString(entry.key), Field.UINTEGER
								.toString(readValue), Field.UINTEGER
								.toString(readValue)));
			assertTrue("read value is same as updated value", (Field.UINTEGER
					.compare(readValue, updateValue) == 0));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// uniqueRootPageID, null);
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testDeleteNonUniqueIndexRandomKeys()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ NUMBER_OF_DUPLICATES, 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ NUMBER_OF_DUPLICATES, i));
		}
		loadIndex(t2, entries, nonuniqueRootPageID);

		Collections.shuffle(entries, rand);

		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);
		int i = 0;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			byte[] readValue2 = index.read(t2, nonuniqueRootPageID, entry.key);
			assertTrue("value for delete key found", (readValue2 != null));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			// printIndex(t2, nonuniqueRootPageID, "/media/ramdisk/" + number(i)
			// + ".dot", false);
			byte[] readValue = index.read(t2, nonuniqueRootPageID, entry.key);
			assertTrue("value for deleted key not found", (readValue == null)
					|| (Field.UINTEGER.compare(entry.value, readValue) != 0));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// nonuniqueRootPageID, null);
			i++;
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer,
				nonuniqueRootPageID);
	}

	@Test
	public void testInsertNonUniqueIndexRandomKeys()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ NUMBER_OF_DUPLICATES, 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ NUMBER_OF_DUPLICATES, i));
		}

		Collections.shuffle(entries, rand);

		int i = 0;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));

			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID, "/media/ramdisk/" + number(i)
			// + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			// byte[] readValue = index.read(t2, idxDescription, entry.key);
			// assertTrue("value for inserted key not found", (readValue !=
			// null) && (ValueType.UINTEGER.compare(entry.value, readValue) ==
			// 0));
			// IndexPageHelper.checkIndexConsistency(t2, sm.buffer,
			// nonuniqueRootPageID);
			i++;
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer,
				nonuniqueRootPageID);
	}

	@Test
	public void testInsertUniqueIndexKeyViolation()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);

		int j = 1;
		for (Entry entry : entries) {
			try {
				if (log.isDebugEnabled())
					log.debug(String.format("%s. Trying to insert (%s, %s)", j,
							Field.UINTEGER.toString(entry.key), Field.UINTEGER
									.toString(entry.value)));
				index.insert(t2, uniqueRootPageID, entry.key, entry.value);
				fail("Duplicate insertion was not detected.");
			} catch (IndexAccessException e) {
				// expected
				assertEquals("fixed pages after error", 0, sm.buffer
						.getFixCount());
			}
		}
	}

	@Test
	public void testInsertNonUniqueIndexDuplicateViolation()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ NUMBER_OF_DUPLICATES, 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ NUMBER_OF_DUPLICATES, i));
		}
		loadIndex(t2, entries, nonuniqueRootPageID);
		// printIndex(t2, nonuniqueRootPageID,
		// "/media/ramdisk/testInsertNonUniqueIndexDuplicateViolation.dot",
		// true);

		int j = 1;
		for (Entry entry : entries) {
			try {
				if (log.isDebugEnabled())
					log.debug(String.format("%s. Trying to insert (%s, %s)",
							j++, Field.UINTEGER.toString(entry.key),
							Field.UINTEGER.toString(entry.value)));
				// if (j++ == 1804)
				// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);
				//				
				index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
				fail("Duplicate insertion was not detected.");
			} catch (IndexAccessException e) {
				// expected
				assertEquals("fixed pages after error", 0, sm.buffer
						.getFixCount());
			}
		}
	}

	@Ignore
	@Test
	public void testUpdateNonUniqueIndexDuplicateViolation()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ NUMBER_OF_DUPLICATES, 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ NUMBER_OF_DUPLICATES, i));
		}
		loadIndex(t2, entries, nonuniqueRootPageID);
		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);
		int j = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Trying to insert (%s, %s)", j,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			for (int i = 0; i <= NUMBER_OF_DUPLICATES; i++) {
				byte[] updateValue = Calc.fromUIntVar(Calc.toUIntVar(entry.key)
						+ i);

				if (Field.UINTEGER.compare(entry.value, updateValue) != 0) {
					try {
						index.update(t2, nonuniqueRootPageID, entry.key,
								entry.value, updateValue);

						// printIndex(t2, nonuniqueRootPageID,
						// "/media/ramdisk/error.dot", true);

						fail(String
								.format(
										"Duplicate update (%s, %s) -> (%s, %s) was not detected.",
										Field.UINTEGER.toString(entry.key),
										Field.UINTEGER.toString(entry.value),
										Field.UINTEGER.toString(entry.key),
										Field.UINTEGER.toString(updateValue)));
					} catch (IndexAccessException e) {
						// expected
						assertEquals("fixed pages after error", 0, sm.buffer
								.getFixCount());
					}
				}
			}
		}
	}

	@Ignore
	@Test
	public void testUpdateNonUniqueIndexAscending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ NUMBER_OF_DUPLICATES, 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ NUMBER_OF_DUPLICATES, i));
		}
		loadIndex(t2, entries, nonuniqueRootPageID);

		int j = 1;
		for (Entry entry : entries) {
			byte[] updateValue = Calc.fromUIntVar(Calc.toUIntVar(entry.value)
					+ NUMBER_OF_DUPLICATES + 1);
			index.update(t2, nonuniqueRootPageID, entry.key, entry.value,
					updateValue);
		}
	}

	@Ignore
	@Test
	public void testUpdateNonUniqueIndexDescending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ NUMBER_OF_DUPLICATES, 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ NUMBER_OF_DUPLICATES, i));
		}
		loadIndex(t2, entries, nonuniqueRootPageID);

		Collections.reverse(entries);
		int j = 1;
		for (Entry entry : entries) {
			byte[] updateValue = Calc.fromUIntVar(Calc.toUIntVar(entry.value)
					+ NUMBER_OF_DUPLICATES + 1);
			index.update(t2, nonuniqueRootPageID, entry.key, entry.value,
					updateValue);
		}
	}

	@Ignore
	@Test
	public void testUpdateNonUniqueIndexRandom() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ NUMBER_OF_DUPLICATES, 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ NUMBER_OF_DUPLICATES, 2 * i));
		}
		loadIndex(t2, entries, nonuniqueRootPageID);

		Collections.shuffle(entries, rand);
		int j = 1;
		for (Entry entry : entries) {
			byte[] updateValue = Calc.fromUIntVar(Calc.toUIntVar(entry.value));
			index.update(t2, nonuniqueRootPageID, entry.key, entry.value,
					updateValue);
		}
	}

	@Test
	public void testScanDeleteUniqueIndexForward() throws IndexAccessException,
			IndexOperationException, BufferException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		sm.buffer.clear();

		int i = 0;
		IndexIterator it = index.open(t2, uniqueRootPageID, SearchMode.FIRST,
				null, null, OpenMode.UPDATE);
		while (!entries.isEmpty()) {
			Entry entry = entries.pop();
			if (log.isDebugEnabled())
				log.debug(String.format("%s, Read (%s, %s).", i, Field.UINTEGER
						.toString(it.getKey()), Field.UINTEGER.toString(it
						.getValue())));
			assertTrue("Current key is not equal to expected", (Field.UINTEGER
					.compare(it.getKey(), entry.key) == 0));
			assertTrue("Current value is not equal to expected",
					(Field.UINTEGER.compare(it.getValue(), entry.value) == 0));
			it.delete();
			i++;
		}

		it.close();
	}

	@Test
	public void testScanUniqueIndexForward() throws IndexAccessException,
			IndexOperationException, BufferException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		sm.buffer.clear();

		int i = 0;
		IndexIterator it = index.open(t2, uniqueRootPageID, SearchMode.FIRST,
				null, null, OpenMode.READ);
		do {
			Entry entry = entries.pop();
			if (log.isDebugEnabled())
				log.debug(String.format("Read (%s, %s).", Field.UINTEGER
						.toString(it.getKey()), Field.UINTEGER.toString(it
						.getValue())));
			assertTrue("Current key is not equal to expected", (Field.UINTEGER
					.compare(it.getKey(), entry.key) == 0));
			assertTrue("Current value is not equal to expected",
					(Field.UINTEGER.compare(it.getValue(), entry.value) == 0));
		} while (it.next());
		it.close();
	}

	@Test
	public void testScanUniqueIndexBackward() throws IndexAccessException,
			IndexOperationException, BufferException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		Collections.reverse(entries);

		sm.buffer.clear();

		int i = 0;
		IndexIterator it = index.open(t2, uniqueRootPageID, SearchMode.LAST,
				null, null, OpenMode.READ);
		do {
			Entry entry = entries.pop();
			if (log.isDebugEnabled())
				log.debug(String.format("Read (%s, %s).", Field.UINTEGER
						.toString(it.getKey()), Field.UINTEGER.toString(it
						.getValue())));
			assertTrue("Current key is not equal to expected", (Field.UINTEGER
					.compare(it.getKey(), entry.key) == 0));
			assertTrue("Current value is not equal to expected",
					(Field.UINTEGER.compare(it.getValue(), entry.value) == 0));
		} while (it.previous());
		it.close();
	}

	@Test
	public void testScanDeleteUniqueIndexBackward()
			throws IndexAccessException, IndexOperationException,
			BufferException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		sm.buffer.clear();

		int i = 0;
		Collections.reverse(entries);
		IndexIterator it = index.open(t2, uniqueRootPageID, SearchMode.LAST,
				null, null, OpenMode.UPDATE);
		while (!entries.isEmpty()) {
			Entry entry = entries.pop();
			if (log.isDebugEnabled())
				log.debug(String.format("%s, Read (%s, %s).", i, Field.UINTEGER
						.toString(it.getKey()), Field.UINTEGER.toString(it
						.getValue())));
			assertTrue("Current key is not equal to expected", (Field.UINTEGER
					.compare(it.getKey(), entry.key) == 0));
			assertTrue("Current value is not equal to expected",
					(Field.UINTEGER.compare(it.getValue(), entry.value) == 0));
			it.delete();
			assertNull("Current key after last", it.getKey());
			it.previous();
			i++;
		}

		it.close();
	}

	@Test
	public void testInsertIntoOpenedUniqueIndexInsertViolation()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE / 10, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		for (Entry entry : entries) {
			for (Entry insertEntry : entries) {
				IndexIterator it = index.open(t2, uniqueRootPageID,
						SearchMode.GREATER_OR_EQUAL, entry.key, null,
						OpenMode.UPDATE);
				try {
					it.insert(insertEntry.key, insertEntry.value);
					fail("Illegal insert was not detected.");
				} catch (IndexAccessException e) {
					// expected
					assertEquals("fixed pages after error", 0, sm.buffer
							.getFixCount());
				}
			}
		}
	}

	@Test
	public void testInsertIntoOpenedNonUniqueIndexInsertViolation()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE
				/ (10 * NUMBER_OF_DUPLICATES), 0);
		for (int i = 1; i <= NUMBER_OF_DUPLICATES; i++) {
			entries.addAll(generateEntries(INDEX_LOAD_SIZE
					/ (10 * NUMBER_OF_DUPLICATES), i));
		}
		loadIndex(t2, entries, nonuniqueRootPageID);
		int i = 0;
		for (Entry entry : entries) {
			for (Entry insertEntry : entries) {
				IndexIterator it = index.open(t2, nonuniqueRootPageID,
						SearchMode.GREATER_OR_EQUAL, entry.key, null,
						OpenMode.UPDATE);
				try {
					i++;
					it.insert(insertEntry.key, insertEntry.value);
					System.out.println(i);
					fail("Illegal insert was not detected.");
				} catch (IndexAccessException e) {
					// expected
					assertEquals("fixed pages after error", 0, sm.buffer
							.getFixCount());
				}
			}
		}
	}

	@Test
	public void testFillEmptyOpenedUniqueIndex() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(500, 0);
		IndexIterator it = index.open(t2, uniqueRootPageID, SearchMode.FIRST,
				null, null, OpenMode.UPDATE);
		int i = 0;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("Inserting (%s, %s).", Field.UINTEGER
						.toString(entry.key), Field.UINTEGER
						.toString(entry.value)));
			it.insert(entry.key, entry.value);
			if (log.isDebugEnabled())
				log.debug(String.format(
						"Current record after insert (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			it.next();
			if (log.isDebugEnabled())
				log.debug(String.format("Current record after next (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			i++;
		}
		it.close();
		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testLoadEmptyOpenedUniqueIndex() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(17, 0);
		IndexIterator it = index.open(t2, uniqueRootPageID, SearchMode.FIRST,
				null, null, OpenMode.BULK);
		int i = 0;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("Inserting (%s, %s).", Field.UINTEGER
						.toString(entry.key), Field.UINTEGER
						.toString(entry.value)));
			it.insert(entry.key, entry.value);
			if (log.isDebugEnabled())
				log.debug(String.format(
						"Current record after insert (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			it.next();
			if (log.isDebugEnabled())
				log.debug(String.format("Current record after next (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			i++;
		}
		it.close();
		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testLoadEmptyOpenedNonUniqueIndex()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(1, 0);
		entries.addAll(generateDuplicates(entries.get(0), INDEX_LOAD_SIZE));

		IndexIterator it = index.open(t2, nonuniqueRootPageID,
				SearchMode.FIRST, null, null, OpenMode.BULK);
		int i = 0;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("Inserting (%s, %s).", Field.UINTEGER
						.toString(entry.key), Field.UINTEGER
						.toString(entry.value)));
			it.insert(entry.key, entry.value);
			if (log.isDebugEnabled())
				log.debug(String.format(
						"Current record after insert (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			it.next();
			if (log.isDebugEnabled())
				log.debug(String.format("Current record after next (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			i++;
		}
		it.close();
		indexPageHelper.checkIndexConsistency(t2, sm.buffer,
				nonuniqueRootPageID);
	}

	@Test
	public void testInsertInOpenedUniqueIndex() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		LinkedList<Entry> loadEntries = new LinkedList<Entry>();
		loadEntries.addAll(entries.subList(0, INDEX_LOAD_SIZE / 3));
		loadEntries.addAll(entries.subList(2 * INDEX_LOAD_SIZE / 3,
				INDEX_LOAD_SIZE));
		loadIndex(t2, loadEntries, uniqueRootPageID);

		List<Entry> insertEntries = entries.subList(INDEX_LOAD_SIZE / 3,
				2 * INDEX_LOAD_SIZE / 3);
		Entry first = insertEntries.get(0);
		IndexIterator it = index.open(t2, uniqueRootPageID,
				SearchMode.GREATER_OR_EQUAL, first.key, null, OpenMode.UPDATE);
		int i = 0;
		for (Entry entry : insertEntries) {
			if (log.isDebugEnabled())
				log.debug(String.format("Inserting (%s, %s).", Field.UINTEGER
						.toString(entry.key), Field.UINTEGER
						.toString(entry.value)));
			it.insert(entry.key, entry.value);
			if (log.isDebugEnabled())
				log.debug(String.format(
						"Current record after insert (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			it.next();
			if (log.isDebugEnabled())
				log.debug(String.format("Current record after next (%s, %s).",
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getKey()) : null,
						(it.getKey() != null) ? Field.UINTEGER.toString(it
								.getValue()) : null));
			i++;
		}
		it.close();
		indexPageHelper.checkIndexConsistency(t2, sm.buffer, uniqueRootPageID);
	}

	@Test
	public void testOpenUniqueIndexAtLast() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(40, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		IndexIterator it = index.open(t2, uniqueRootPageID, SearchMode.LAST,
				null, null, OpenMode.READ);
		assertTrue("Not at last record", it.getKey() != null);
		it.close();
	}

	@Test
	public void testRollbackInsertUniqueIndexAscendingKeys()
			throws IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);

		int i = 0;
		for (Entry entry : entries) {
			index.insert(t1, uniqueRootPageID, entry.key, entry.value);
			i++;
		}

		t1.rollback();

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for restored key not found", (readValue == null));
		}
	}

	@Test
	public void testRollbackInsertUniqueIndexDescendingKeys()
			throws IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		Collections.reverse(entries);

		int i = 0;
		for (Entry entry : entries) {
			index.insert(t1, uniqueRootPageID, entry.key, entry.value);
			i++;
		}

		t1.rollback();

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for restored key not found", (readValue == null));
		}
	}

	@Test
	public void testRollbackInsertUniqueIndexRandomKeys()
			throws IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		Collections.shuffle(entries, rand);

		int i = 0;
		for (Entry entry : entries) {
			index.insert(t1, uniqueRootPageID, entry.key, entry.value);
			i++;
		}

		t1.rollback();

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for restored key found", (readValue == null));
		}
	}

	@Test
	public void testRollbackDeleteUniqueIndexAscendingKeys()
			throws IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);

		int i = 0;
		for (Entry entry : entries) {
			index.delete(t1, uniqueRootPageID, entry.key, entry.value);
			i++;
		}

		t1.rollback();

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for restored key found", (readValue != null));
			assertTrue("value for restored key is correct", Field.UINTEGER
					.compare(readValue, entry.value) == 0);
		}
	}

	@Test
	public void testRollbackDeleteUniqueIndexDescendingKeys()
			throws IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);

		loadIndex(t2, entries, uniqueRootPageID);
		Collections.reverse(entries);

		int i = 0;
		for (Entry entry : entries) {
			index.delete(t1, uniqueRootPageID, entry.key, entry.value);
			i++;
		}
		t1.rollback();

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for restored key found", (readValue != null));
			assertTrue("value for restored key is correct", Field.UINTEGER
					.compare(readValue, entry.value) == 0);
		}
	}

	@Test
	public void testRollbackDeleteUniqueIndexRandomKeys()
			throws IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);

		loadIndex(t2, entries, uniqueRootPageID);
		Collections.shuffle(entries, rand);

		int i = 0;
		for (Entry entry : entries) {
			index.delete(t1, uniqueRootPageID, entry.key, entry.value);
			i++;
		}

		t1.rollback();

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for restored key found", (readValue != null));
			assertTrue("value for restored key is correct", Field.UINTEGER
					.compare(readValue, entry.value) == 0);
		}
	}

	@Test
	public void testTraverseUniqueIndex() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(40, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		// Logger.getLogger(BPlusIndexIterator.class.getName()).setLevel(Level.TRACE);
		// printIndex(t2, uniqueRootPageID, "/home/sbaechl/traverser.dot",
		// false);
		// (new BPlusIndexWalker(t2, sm.buffer, uniqueRootPageID)).traverse();
		assertEquals("fixed pages after traversal", 0, sm.buffer.getFixCount());
	}

	@Test
	public void testTraverseNonUniqueIndex() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(30, 0);
		Entry firstDuplicate = entries.get(11);
		Entry secondDuplicate = entries.get(20);
		Entry lastDuplicate = entries.removeLast();
		entries.addAll(generateDuplicates(firstDuplicate, 15));
		entries.addAll(generateDuplicates(secondDuplicate, 10));
		entries.addAll(generateDuplicates(lastDuplicate, 20));

		int i = 1;
		for (Entry entry : entries) {
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
		}
		// Logger.getLogger(BPlusIndexIterator.class.getName()).setLevel(Level.TRACE);
		// printIndex(t2, nonuniqueRootPageID, "/home/sbaechl/traverser.dot",
		// false);
		// (new BPlusIndexWalker(t2, sm.buffer,
		// nonuniqueRootPageID)).traverse();
		assertEquals("fixed pages after traversal", 0, sm.buffer.getFixCount());
	}

	@Test
	public void testRecoveryInsertCommitted() throws BufferException,
			IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		t2.commit();

		sm.buffer = sm.recreateBuffer();
		sm.taMgr.recover();

		int i = 0;
		for (Entry entry : entries) {
			byte[] readValue = index.read(t1, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key found", (readValue != null));
			assertTrue("read value is same as written value", (Field.UINTEGER
					.compare(readValue, entry.value) == 0));
			i++;
		}
	}

	@Test
	public void testRecoveryInsertFailed() throws BufferException,
			IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		t2.commit();
		t2 = sm.taMgr.begin();
		loadIndex(t2, entries, uniqueRootPageID);

		sm.buffer = sm.recreateBuffer();
		sm.taMgr.recover();

		int i = 0;
		for (Entry entry : entries) {
			byte[] readValue = index.read(t1, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key not found", (readValue == null));
			i++;
		}
	}

	@Test
	public void testRecoveryInsertRollback() throws BufferException,
			IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		t2.commit();
		t2 = sm.taMgr.begin();
		loadIndex(t2, entries, uniqueRootPageID);
		t2.rollback();

		sm.buffer = sm.recreateBuffer();
		sm.taMgr.recover();

		int i = 0;
		for (Entry entry : entries) {
			byte[] readValue = index.read(t1, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key not found", (readValue == null));
			i++;
		}
	}

	@Test
	public void testRecoveryDeleteCommitted() throws BufferException,
			IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		t2.commit();

		t2 = sm.taMgr.begin();
		for (Entry entry : entries) {
			index.delete(t2, uniqueRootPageID, entry.key, entry.key);
		}
		t2.commit();

		sm.buffer = sm.recreateBuffer();
		sm.taMgr.recover();

		int i = 0;
		for (Entry entry : entries) {
			byte[] readValue = index.read(t1, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key found", (readValue == null));
			i++;
		}
	}

	@Test
	public void testRecoveryDeleteFailed() throws BufferException,
			IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		t2.commit();

		// printIndex(t2, uniqueRootPageID, "/media/ramdisk/loaded.dot", true);

		t2 = sm.taMgr.begin();
		for (Entry entry : entries) {
			index.delete(t2, uniqueRootPageID, entry.key, entry.key);
		}

		// printIndex(t2, uniqueRootPageID, "/media/ramdisk/cleared.dot", true);

		// sm.taMgr.getLog().flushAll();

		sm.buffer = sm.recreateBuffer();
		sm.taMgr.recover();

		t1 = sm.taMgr.begin();

		// printIndex(ctx, uniqueRootPageID, "/media/ramdisk/recovered.dot",
		// true);

		int i = 0;
		for (Entry entry : entries) {
			byte[] readValue = index.read(t1, uniqueRootPageID, entry.key);

			if (readValue == null) {
				System.out.println("Did not find "
						+ Field.UINTEGER.toString(entry.key));
			}

			assertTrue("value for inserted key found", (readValue != null));
			assertTrue("read value is same as written value", (Field.UINTEGER
					.compare(readValue, entry.value) == 0));
			i++;
		}
	}

	@Test
	public void testRecoveryDeleteRollback() throws BufferException,
			IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		t2.commit();

		t2 = sm.taMgr.begin();
		for (Entry entry : entries) {
			index.delete(t2, uniqueRootPageID, entry.key, entry.key);
		}
		t2.rollback();

		sm.buffer = sm.recreateBuffer();
		sm.taMgr.recover();

		int i = 0;
		for (Entry entry : entries) {
			byte[] readValue = index.read(t1, uniqueRootPageID, entry.key);
			assertTrue("value for inserted key found", (readValue != null));
			assertTrue("read value is same as written value", (Field.UINTEGER
					.compare(readValue, entry.value) == 0));
			i++;
		}
	}

	@Test
	public void testRollbackInsertPersistentUniqueIndexRandomKeys()
			throws IndexAccessException, IndexOperationException, TxException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);

		int i = 0;
		for (Entry entry : entries) {
			index
					.insertPersistent(t1, uniqueRootPageID, entry.key,
							entry.value);

			// printIndex(ctx, uniqueRootPageID, "/media/ramdisk/" + number(i) +
			// ".dot", false);
			i++;
		}

		t1.rollback();

		for (Entry entry : entries) {
			byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
			assertTrue("value for persistent inserted key found",
					(readValue != null));
			assertTrue("value for persistent inserted key is correct",
					Field.UINTEGER.compare(readValue, entry.value) == 0);
		}
	}

	@Test
	public void testOpenUniqueIndexRandomThread() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);
		// Logger.getLogger(BufferImpl.class.getName()).setLevel(Level.TRACE);

		HashSet<Entry> uniqueReadKeys = new HashSet<Entry>();
		ArrayList<Entry> hits = new ArrayList<Entry>();

		for (int i = 0; i < entries.size(); i++) {
			IndexIterator iterator = index.open(t2, uniqueRootPageID,
					SearchMode.RANDOM_THREAD, null, null, OpenMode.READ);
			Entry entry = new Entry(iterator.getKey(), iterator.getValue());
			if (uniqueReadKeys.add(entry)) {
				hits.add(entry);
			}
			iterator.close();
		}

		try {
			PrintStream printer = new PrintStream(new File(
					"/media/ramdisk/randomIndex.dot"));
			DisplayVisitor visitor = new DisplayVisitor(printer, true);
			for (Entry entry : hits) {
				visitor.highlightEntry(entry.key, entry.value);
			}
			index.traverse(t2, uniqueRootPageID, visitor);
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println(entries.size());
		System.out.println(uniqueReadKeys.size());
		System.out
				.println("Collission ratio: "
						+ ((double) 1 - ((double) uniqueReadKeys.size() / (double) entries
								.size())));
	}

	@Test
	public void testOpenUniqueIndexRandomSystem() throws IndexAccessException,
			IndexOperationException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		// Logger.getLogger(BPlusIndex.class.getName()).setLevel(Level.TRACE);
		// Logger.getLogger(BufferImpl.class.getName()).setLevel(Level.TRACE);

		HashSet<Entry> uniqueReadKeys = new HashSet<Entry>();
		ArrayList<Entry> hits = new ArrayList<Entry>();

		for (int i = 0; i < entries.size(); i++) {
			IndexIterator iterator = index.open(t2, uniqueRootPageID,
					SearchMode.RANDOM_SYSTEM, null, null, OpenMode.READ);
			Entry entry = new Entry(iterator.getKey(), iterator.getValue());
			if (uniqueReadKeys.add(entry)) {
				hits.add(entry);
			}
			iterator.close();
		}

		try {
			PrintStream printer = new PrintStream(new File(
					"/media/ramdisk/randomIndex.dot"));
			DisplayVisitor visitor = new DisplayVisitor(printer, true);
			for (Entry entry : hits) {
				visitor.highlightEntry(entry.key, entry.value);
			}
			index.traverse(t2, uniqueRootPageID, visitor);
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println(entries.size());
		System.out.println(uniqueReadKeys.size());
		System.out
				.println("Collission ratio: "
						+ ((double) 1 - ((double) uniqueReadKeys.size() / (double) entries
								.size())));
	}

	@Test
	public void testCreateIndex() throws BufferException, IndexAccessException {
		PageID rootPage = index.createIndex(t2, SysMockup.CONTAINER_NO,
				Field.UINTEGER, Field.UINTEGER, true);
		Handle rootHandle = sm.buffer.fixPage(t2, rootPage);
		sm.buffer.unfixPage(rootHandle);
	}

	@Test
	public void testInsertOverflowValues() throws BufferException,
			IndexAccessException {
		uniqueRootPageID = index.createIndex(t2, SysMockup.CONTAINER_NO,
				Field.UINTEGER, Field.BYTEARRAY, true, true, 2);

		for (int i = 0; i < 10; i++) {
			byte[] key = Calc.fromUIntVar(i);
			byte[] value = new byte[SysMockup.BLOCK_SIZE];
			rand.nextBytes(value);

			index.insert(t1, uniqueRootPageID, key, value);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());

			// index.dump(ctx, uniqueRootPageID, System.out);
			// printIndex(t2, uniqueRootPageID,
			// "/media/ramdisk/insertOverflowValues" + number(i + 1) + ".dot",
			// true);

			byte[] readValue = index.read(t2, uniqueRootPageID, key);
			assertTrue("value for inserted key found", (readValue != null));
			assertTrue("read value is same as written value", Field.BYTEARRAY
					.compare(value, readValue) == 0);
		}
	}

}