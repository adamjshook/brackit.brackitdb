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
package org.brackit.server.store.index.aries;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.IndexAccessException;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BPlusIndexDuplicateTest extends AbstractBPlusIndexTest {
	private static final Logger log = Logger
			.getLogger(BPlusIndexDuplicateTest.class);

	public BPlusIndexDuplicateTest() {
		super();
	}

	@Test
	public void testOverflowRootAndInsertDuplicatesGreaterAndSmallerAndDeleteDuplicatesAscending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(30, 0);
		LinkedList<Entry> duplicates = generateDuplicates(entries.get(15), 25);

		int i = 1;
		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID,
			// "/home/sbaechl/projects/xtc/test/" + number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.shuffle(entries, rand);
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID,
			// "/home/sbaechl/projects/xtc/test/" + number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID,
			// "/home/sbaechl/projects/xtc/test/" + number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID,
			// "/home/sbaechl/projects/xtc/test/" + number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}
	}

	@Test
	public void testOverflowRootAndInsertDuplicatesGreaterAndSmallerAndDeleteDuplicatesDescending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(30, 0);
		LinkedList<Entry> duplicates = generateDuplicates(entries.get(15), 25);

		int i = 1;
		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.shuffle(entries, rand);
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.reverse(duplicates);
		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.reverse(entries);
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}
	}

	@Test
	public void testOverflowGrandChildAndDeleteDuplicatesAscending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(30, 0);
		LinkedList<Entry> duplicates = generateDuplicates(entries.getLast(), 40);
		entries.addAll(duplicates);

		int i = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer,
				nonuniqueRootPageID);
	}

	@Test
	public void testOverflowRootAndDeleteDuplicatesAscending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(1, 0);
		entries.addAll(generateDuplicates(entries.getFirst(), 44));

		int i = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID, "/media/ramdisk/" + number(i)
			// + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID, "/media/ramdisk/" + number(i)
			// + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer,
				nonuniqueRootPageID);
	}

	@Test
	public void testOverflowLastLeafAndDeleteDuplicatesAscending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(30, 0);
		LinkedList<Entry> duplicates = generateDuplicates(entries.removeLast(),
				40);

		int i = 1;
		for (Entry entry : entries) {

			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.shuffle(entries, rand);
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer,
				nonuniqueRootPageID);
	}

	@Test
	public void testOverflowLastLeafAndDeleteDuplicatesDescending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(30, 0);
		LinkedList<Entry> duplicates = generateDuplicates(entries.getLast(), 40);

		int i = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.reverse(duplicates);
		for (Entry entry : duplicates) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.shuffle(entries, rand);
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		indexPageHelper.checkIndexConsistency(t2, sm.buffer,
				nonuniqueRootPageID);
	}

	@Test
	public void testOverflowRootAndDeleteDuplicatesDescending()
			throws IndexAccessException, IndexOperationException {
		LinkedList<Entry> entries = generateEntries(1, 0);
		entries.addAll(generateDuplicates(entries.getFirst(), 40));

		int i = 1;
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Inserting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.insert(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}

		Collections.reverse(entries);
		for (Entry entry : entries) {
			if (log.isDebugEnabled())
				log.debug(String.format("%s. Deleting (%s, %s)", i,
						Field.UINTEGER.toString(entry.key), Field.UINTEGER
								.toString(entry.value)));
			index.delete(t2, nonuniqueRootPageID, entry.key, entry.value);
			// printIndex(display, "/home/sbaechl/projects/xtc/test/" +
			// number(i) + ".dot", true);
			assertEquals("fixed pages after delete", 0, sm.buffer.getFixCount());
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					nonuniqueRootPageID);
			i++;
		}
	}

}