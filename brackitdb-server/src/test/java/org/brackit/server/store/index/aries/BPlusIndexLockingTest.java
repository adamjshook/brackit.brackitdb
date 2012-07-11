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
package org.brackit.server.store.index.aries;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.services.KVLLockService;
import org.brackit.server.util.Calc;
import org.brackit.xquery.util.Cfg;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BPlusIndexLockingTest extends AbstractBPlusIndexTest {
	private IndexLockService lockService;

	private class IntegerByteArrayLockName implements LockName {
		private final byte[] name;

		public IntegerByteArrayLockName(byte[] name) {
			this.name = name;
		}

		@Override
		public boolean equals(Object obj) {
			return Arrays.equals(name, ((IntegerByteArrayLockName) obj).name);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(name);
		}

		@Override
		public String toString() {
			return (name == null) ? "EOF" : Integer.toString(Calc
					.toUIntVar(name));
		}
	}

	private class IntegerByteArrayKVLLockService extends KVLLockService {
		public IntegerByteArrayKVLLockService(String name, int maxLocks,
				int maxTransactions) {
			super(name, maxLocks, maxTransactions);
		}

		@Override
		protected LockName createLockName(int unitID, byte[] key, byte[] value) {
			return new IntegerByteArrayLockName(key);
		}
	}

	@Test
	public void testReadInsertedValues() throws IndexAccessException,
			IndexOperationException, TxException {
		List<Entry> entries = generateEntries(10, 1);
		loadIndex(t1, entries, uniqueRootPageID);

		t2.getLockCB().setTimeout(100);
		for (Entry entry : entries) {
			try {
				byte[] readValue = index.read(t2, uniqueRootPageID, entry.key);
				fail("Could read insert of another TX");
			} catch (IndexAccessException e) {
				// expected
			}
		}

		t1.commit();

		for (Entry entry : entries) {
			index.read(t2, uniqueRootPageID, entry.key);
		}
	}

	@Test
	public void testOpenAtInsertedValues() throws IndexAccessException,
			IndexOperationException, TxException {
		List<Entry> entries = generateEntries(10, 1);

		loadIndex(t1, entries, uniqueRootPageID);

		t2.getLockCB().setTimeout(100);
		for (Entry entry : entries) {
			try {
				IndexIterator it = index.open(t2, uniqueRootPageID,
						SearchMode.GREATER_OR_EQUAL, entry.key, null,
						OpenMode.READ);
				fail("Could read insert of another TX");
			} catch (IndexAccessException e) {
				// expected
			}
		}

		t1.commit();

		for (Entry entry : entries) {
			index.read(t2, uniqueRootPageID, entry.key);
		}
	}

	@Test
	public void testScanIntoInsertedValues() throws IndexAccessException,
			IndexOperationException, TxException {
		List<Entry> entries = generateEntries(10, 1);
		loadIndex(t1, entries.subList(0, entries.size() / 2), uniqueRootPageID);
		t1.commit();
		t1 = sm.taMgr.begin();
		loadIndex(t1, entries.subList(entries.size() / 2 + 1, entries.size()),
				uniqueRootPageID);

		t2.getLockCB().setTimeout(100);
		IndexIterator it = null;
		int i = 0;

		for (Entry entry : entries) {
			if (i == 0) {
				it = index.open(t2, uniqueRootPageID,
						SearchMode.GREATER_OR_EQUAL, entries.get(0).key, null,
						OpenMode.READ);
			} else if (i < entries.size() / 2) {
				assertTrue("Next entry is readable", it.next());
			} else {
				try {
					it.next();
					fail("Could read insert of another TX");
				} catch (IndexAccessException e) {
					// expected
					break;
				}
			}
			i++;
		}

		t1.commit();

		for (Entry entry : entries) {
			index.read(t2, uniqueRootPageID, entry.key);
		}
	}

	@Test
	public void testDeleteReadValues() throws IndexAccessException,
			IndexOperationException, TxException {
		List<Entry> entries = generateEntries(10, 1);
		loadIndex(t1, entries, uniqueRootPageID);
		t1.commit();

		t1 = sm.taMgr.begin();
		for (Entry entry : entries) {
			byte[] readValue = index.read(t1, uniqueRootPageID, entry.key);
			assertNotNull("Found written value", readValue);
			assertTrue("Read correct value", Arrays.equals(entry.value,
					readValue));
		}

		t2.getLockCB().setTimeout(100);
		for (Entry entry : entries) {
			try {
				index.delete(t2, uniqueRootPageID, entry.key, entry.value);
				fail("Could delete read key of another TX");
			} catch (IndexAccessException e) {
				// expected
			}
		}

		t1.commit();

		for (Entry entry : entries) {
			index.read(t2, uniqueRootPageID, entry.key);
		}
	}

	@Test
	public void testDeleteInsertedValues() throws IndexAccessException,
			IndexOperationException, TxException {
		List<Entry> entries = generateEntries(10, 1);
		loadIndex(t1, entries, uniqueRootPageID);

		t2.getLockCB().setTimeout(100);
		for (Entry entry : entries) {
			try {
				index.delete(t2, uniqueRootPageID, entry.key, entry.value);
				fail("Could delete insert of another TX");
			} catch (IndexAccessException e) {
				// expected
			}
		}

		t1.commit();

		for (Entry entry : entries) {
			index.read(t2, uniqueRootPageID, entry.key);
		}
	}

	@Override
	@Before
	public void setUp() throws Exception {
		Cfg.set(TxMgr.LOCK_WAIT_TIMEOUT, 5000);
		super.setUp();
		lockService = new IntegerByteArrayKVLLockService("Test",
				LOAD_SIZE * 2, 10);
		index = new BPlusIndex(sm.bufferManager, lockService);
	}
}
