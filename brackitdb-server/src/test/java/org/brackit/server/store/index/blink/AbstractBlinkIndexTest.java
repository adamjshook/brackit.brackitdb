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
package org.brackit.server.store.index.blink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.thread.IDAssigner;
import org.brackit.server.util.Calc;
import org.junit.After;
import org.junit.Before;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class AbstractBlinkIndexTest {
	private static final Logger log = Logger
			.getLogger(AbstractBlinkIndexTest.class.getName());

	protected static final int INDEX_LOAD_SIZE = 20000;

	protected static final int NUMBER_OF_DUPLICATES = 10;

	protected BlinkIndex index;

	protected PageID uniqueRootPageID;

	protected PageID nonuniqueRootPageID;

	protected Random rand;

	protected Tx t1;

	protected Tx t2;

	protected IndexPageHelper indexPageHelper;

	protected SysMockup sm;

	protected final class Entry {
		final byte[] key;

		final byte[] value;

		Entry(byte[] key, byte[] value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			return ((obj instanceof Entry)
					&& (Field.UINTEGER.compare(((Entry) obj).key, key) == 0) && (Field.UINTEGER
					.compare(((Entry) obj).value, value) == 0));
		}

		@Override
		public int hashCode() {
			return Field.UINTEGER.toString(key).hashCode();
		}
	}

	public AbstractBlinkIndexTest() {
		super();
	}

	protected String number(int i) {
		if (i < 10)
			return "00" + i;
		else if (i < 100)
			return "0" + i;
		else
			return "" + i;
	}

	protected void loadIndex(Tx transaction, List<Entry> entries,
			PageID rootPageID) throws IndexAccessException,
			IndexOperationException {
		int i = 0;
		for (Entry entry : entries) {
			index.insert(transaction, rootPageID, entry.key, entry.value);
			// printIndex(t2, nonuniqueRootPageID, "/media/ramdisk/" +
			// number(i++) + ".dot", true);
			// IndexPageHelper.checkIndexConsistency(transaction, buffer,
			// rootPageID);
			assertEquals("fixed pages after insert", 0, sm.buffer.getFixCount());
			byte[] readValue = index.read(transaction, rootPageID, entry.key);
			assertTrue("value for inserted key found", (readValue != null));
			// assertTrue("read value is same as written value",
			// (Field.UINTEGER.compare(readValue, entry.value) == 0));
		}
		// printIndex(t2, nonuniqueRootPageID, "/media/ramdisk/loaded.dot",
		// true);
		indexPageHelper.checkIndexConsistency(transaction, sm.buffer,
				rootPageID);
	}

	protected LinkedList<Entry> generateEntries(int loadSize, int valueAddon) {
		LinkedList<Entry> entries = new LinkedList<Entry>();

		for (int i = 1; i <= loadSize; i++) {
			int intKey = i * 100;
			byte[] key = Calc.fromUIntVar(intKey);
			int intValue = i * 100 + valueAddon;
			byte[] value = Calc.fromUIntVar(intValue);
			entries.add(new Entry(key, value));
		}

		return entries;
	}

	protected LinkedList<Entry> generateDuplicates(Entry entry,
			int numberOfDuplicates) {
		LinkedList<Entry> entries = new LinkedList<Entry>();

		int intValue = Calc.toUIntVar(entry.value);

		for (int i = 1; i <= numberOfDuplicates; i++) {
			intValue++;
			byte[] value = Calc.fromUIntVar(intValue);
			entries.add(new Entry(entry.key, value));
		}

		return entries;
	}

	protected void printIndex(Tx transaction, PageID rootPageID,
			String filename, boolean showValues) throws IndexAccessException {
		try {
			PrintStream printer = new PrintStream(new File(filename));
			index.traverse(transaction, rootPageID, new DisplayVisitor(printer,
					showValues));
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@After
	public void tearDown() throws BufferException {
	}

	@Before
	public void setUp() throws Exception {

		sm = new SysMockup();
		index = new BlinkIndex(sm.bufferManager);
		indexPageHelper = new IndexPageHelper(sm.bufferManager);

		t1 = sm.taMgr.begin();
		t2 = sm.taMgr.begin();
		uniqueRootPageID = index.createIndex(t2, SysMockup.CONTAINER_NO,
				Field.UINTEGER, Field.UINTEGER, true, true);
		nonuniqueRootPageID = index.createIndex(t2, SysMockup.CONTAINER_NO,
				Field.UINTEGER, Field.UINTEGER, false, true);

		// use same random source to get reproducable results in case of an
		// error
		rand = new Random(12345678);
		IDAssigner.counter.set(0);
	}

}