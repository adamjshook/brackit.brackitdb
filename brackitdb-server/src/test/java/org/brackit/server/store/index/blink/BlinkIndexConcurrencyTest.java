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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.LinkedList;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndexTest;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BlinkIndexConcurrencyTest extends AbstractBlinkIndexTest {
	private static final Logger log = Logger
			.getLogger(BlinkIndexConcurrencyTest.class);

	protected int CONCURRENT_USER_SIZE = 100;

	protected static boolean stop = false;

	protected class InsertUser implements Runnable {
		private PageID rootPageID;
		private int number;
		private LinkedList<Entry> entries;
		private boolean rollback;

		protected InsertUser(PageID rootPageID, int number,
				LinkedList<Entry> entries, boolean rollback) {
			super();
			this.rootPageID = rootPageID;
			this.number = number;
			this.entries = entries;
			this.rollback = rollback;
		}

		public void run() {
			try {
				Tx tx = sm.taMgr.begin();
				// log.debug("Starting");
				long start = System.currentTimeMillis();
				for (Entry entry : entries) {
					synchronized (BPlusIndexTest.class) {
						if (stop) {
							log.debug("Emergency stop");
							break;
						}
					}

					byte[] value = Calc.fromUIntVar(Calc.toUIntVar(entry.value)
							+ number);
					if (log.isDebugEnabled())
						log.debug(String.format("BEGIN INSERT (%s, %s)",
								Field.UINTEGER.toString(entry.key),
								Field.UINTEGER.toString(value)));
					index.insert(tx, rootPageID, entry.key, value);

					byte[] readValue = index.read(tx, rootPageID, entry.key);
					assertTrue("found value for inserted key "
							+ Calc.toUIntVar(entry.key), (readValue != null));
					assertTrue("read value is same as written value",
							(Field.UINTEGER.compare(readValue, Calc
									.fromUIntVar(Calc.toUIntVar(entry.value)
											+ number)) == 0));

					if (log.isDebugEnabled())
						log.debug(String.format("END INSERT (%s, %s)",
								Field.UINTEGER.toString(entry.key),
								Field.UINTEGER.toString(value)));
				}

				if (rollback) {
					tx.rollback();
				}

				for (Entry entry : entries) {
					synchronized (BPlusIndexTest.class) {
						if (stop) {
							log.debug("Emergency stop");
							break;
						}
					}

					byte[] readValue = index.read(tx, rootPageID, entry.key);

					if (rollback) {
						assertTrue("removed inserted key "
								+ Calc.toUIntVar(entry.key),
								(readValue == null));
					} else {
						assertTrue("found value for inserted key "
								+ Calc.toUIntVar(entry.key),
								(readValue != null));
						assertTrue("read value is same as written value",
								(Field.UINTEGER.compare(readValue, Calc
										.fromUIntVar(Calc
												.toUIntVar(entry.value)
												+ number)) == 0));
					}
				}
				long end = System.currentTimeMillis();

				if (!rollback) {
					tx.commit();
				}
			} catch (Throwable e) {
				synchronized (BPlusIndexTest.class) {
					if (stop == false) {
						log.error("First error.", e);
					}
					stop = true;
				}
				log.error(e);
			}
		}
	}

	protected class DeleteUser implements Runnable {
		private PageID rootPageID;
		private int number;
		private LinkedList<Entry> entries;
		private boolean rollback;

		protected DeleteUser(PageID rootPageID, int number,
				LinkedList<Entry> entries, boolean rollback) {
			super();
			this.rootPageID = rootPageID;
			this.number = number;
			this.entries = entries;
			this.rollback = rollback;
		}

		public void run() {
			try {
				Tx tx = sm.taMgr.begin();
				// log.debug("Starting");
				long start = System.currentTimeMillis();
				for (Entry entry : entries) {
					synchronized (BPlusIndexTest.class) {
						if (stop) {
							log.debug("Emergency stop");
							break;
						}
					}

					if (log.isDebugEnabled())
						log.debug(String.format("BEGIN DELETE (%s, %s)",
								Field.UINTEGER.toString(entry.key),
								Field.UINTEGER.toString(entry.value)));
					index.delete(tx, rootPageID, entry.key, entry.value);
					if (log.isDebugEnabled())
						log.debug(String.format("END DELETE (%s, %s)",
								Field.UINTEGER.toString(entry.key),
								Field.UINTEGER.toString(entry.value)));
				}

				if (rollback) {
					tx.rollback();
				}

				for (Entry entry : entries) {
					synchronized (BPlusIndexTest.class) {
						if (stop) {
							log.debug("Emergency stop");
							break;
						}
					}

					byte[] readValue = index.read(tx, rootPageID, entry.key);

					if (rollback) {
						assertTrue("restored deleted key "
								+ Calc.toUIntVar(entry.key),
								(readValue != null));
						assertTrue("restored correct value for deleted key "
								+ Calc.toUIntVar(entry.key), Field.UINTEGER
								.compare(entry.value, readValue) == 0);
					} else {
						assertTrue("found value for deleted key "
								+ Calc.toUIntVar(entry.key),
								(readValue == null));
					}
				}
				long end = System.currentTimeMillis();

				if (!rollback) {
					tx.commit();
				}
			} catch (Throwable e) {
				synchronized (BPlusIndexTest.class) {
					if (stop == false) {
						log.error("First error.", e);
					}
					stop = true;
				}
				log.error(e);
			}
		}
	}

	public BlinkIndexConcurrencyTest() {
		super();
	}

	@Test
	public void testInsertUniqueIndexRandomKeysConcurrent()
			throws IndexAccessException, IndexOperationException,
			InterruptedException {
		Thread[] users = new Thread[CONCURRENT_USER_SIZE];
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		Collections.shuffle(entries, rand);

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			LinkedList<Entry> userEntries = new LinkedList<Entry>();
			for (int j = 0; j < INDEX_LOAD_SIZE / CONCURRENT_USER_SIZE
					&& !entries.isEmpty(); j++) {
				Entry entry = entries.removeFirst();
				userEntries.add(entry);
			}
			users[i - 1] = new Thread(new InsertUser(uniqueRootPageID, i,
					userEntries, false), "User" + i);
			users[i - 1].setDaemon(true);
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].start();
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].join();
		}

		// indexPageHelper.checkIndexConsistency(t2, buffer, uniqueRootPageID);
		//		
		// System.out.println("LEAF DUMP");
		// byte[] prevHighKey = null;
		// PageID prevPageID = null;
		// PageContext p = index.tree.descendToPosition(ctx, uniqueRootPageID,
		// SearchMode.FIRST, null, null, false, false);
		// while (true)
		// {
		// System.out.println(p.dump(p.getPageID() + ""));
		//			
		// if (prevHighKey != null)
		// {
		// if (p.getKeyType().compare(prevHighKey, p.getKey()) >= 0)
		// {
		// System.out.println("ERROR: " +
		// p.getKeyType().valueToString(prevHighKey) + " >= " +
		// p.getKeyType().valueToString(p.getKey()));
		// PageContext parent = index.tree.descendToParent(ctx,
		// uniqueRootPageID,
		// uniqueRootPageID, p.getKey(), p.getPageID(), 1);
		// System.out.println(p.dump("PARENT OF CURRENT"));
		// parent.cleanup();
		// parent = index.tree.descendToParent(ctx, uniqueRootPageID,
		// uniqueRootPageID, prevHighKey, prevPageID, 1);
		// System.out.println(p.dump("PARENT OF PREV"));
		// parent.cleanup();
		// }
		// if (!p.getLowPageID().equals(prevPageID))
		// {
		// System.out.println("ERROR: ILLEGAL LINK");
		// PageContext parent = index.tree.descendToParent(ctx,
		// uniqueRootPageID,
		// uniqueRootPageID, p.getKey(), p.getPageID(), 1);
		// System.out.println(p.dump("PARENT"));
		// parent.cleanup();
		// }
		// }
		//			
		// p.moveLast();
		// if (p.isLastInLevel())
		// {
		// break;
		// }
		// p.moveLast();
		// prevHighKey = p.getKey();
		// prevPageID = p.getPageID();
		// PageContext n = index.tree.getPage(ctx, p.getValueAsPageID(), false,
		// false);
		// p.cleanup();
		// p = n;
		// }
		// p.cleanup();

		// printIndex(new IndexDisplay(t2, buffer, ValueType.UINTEGER,
		// ValueType.UINTEGER, rootPageID), "/home/sbaechl/index.dot", false);

		// if (!stop)
		// indexPageHelper.checkIndexConsistency(t2, buffer, uniqueRootPageID);
		// else
		// fail("Users did not succeed.");
	}

	@Test
	public void testInsertAndDeleteUniqueIndexRandomKeysConcurrent()
			throws ServerException, InterruptedException {
		int numberOfUsers = 2 * (SysMockup.BUFFER_SIZE / 3) + 0;
		Thread[] users = new Thread[numberOfUsers];
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		LinkedList<Entry> deleteEntries = new LinkedList<Entry>();
		Collections.shuffle(entries, rand);

		for (int i = 0; i < INDEX_LOAD_SIZE / 2; i++) {
			Entry entry = entries.removeFirst();
			deleteEntries.add(entry);
			index.insert(t2, uniqueRootPageID, entry.key, entry.value);
		}
		t2.commit();

		for (int i = 0; i < numberOfUsers / 2; i++) {
			LinkedList<Entry> userEntries = new LinkedList<Entry>();
			for (int j = 0; j < INDEX_LOAD_SIZE / numberOfUsers
					&& !entries.isEmpty(); j++) {
				Entry entry = entries.removeFirst();
				// System.out.println("InsertUser " + i + " : " +
				// Field.UINTEGER.valueToString(entry.key) + " " +
				// Field.UINTEGER.valueToString(entry.value));
				userEntries.add(entry);
			}

			users[i] = new Thread(new InsertUser(uniqueRootPageID, i,
					userEntries, false), "User" + i);
			users[i].setDaemon(true);
		}

		for (int i = numberOfUsers / 2; i < numberOfUsers; i++) {
			LinkedList<Entry> userEntries = new LinkedList<Entry>();
			for (int j = 0; j < INDEX_LOAD_SIZE / numberOfUsers
					&& !deleteEntries.isEmpty(); j++) {
				Entry entry = deleteEntries.removeFirst();
				// System.out.println("DeleteUser " + i + " : " +
				// Field.UINTEGER.valueToString(entry.key) + " " +
				// Field.UINTEGER.valueToString(entry.value));
				userEntries.add(entry);
			}

			users[i] = new Thread(new DeleteUser(uniqueRootPageID, i,
					userEntries, false), "User" + i);
			users[i].setDaemon(true);
		}

		for (int i = 1; i <= numberOfUsers; i++) {
			users[i - 1].start();
		}

		for (int i = 1; i <= numberOfUsers; i++) {
			users[i - 1].join();
		}

		t2 = sm.taMgr.begin();
		// printIndex(new IndexDisplay(t2, buffer, ValueType.UINTEGER,
		// ValueType.UINTEGER, rootPageID), "/home/sbaechl/index.dot", false);

		if (!stop)
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					uniqueRootPageID);
		else {
			fail("Users did not succeed.");
		}
	}

	@Test
	public void testDeleteUniqueIndexRandomKeysConcurrent()
			throws ServerException, InterruptedException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		t2.commit();

		Thread[] users = new Thread[CONCURRENT_USER_SIZE];
		Collections.shuffle(entries, rand);

		// Logger.getLogger("org.brackit.server.io.file.index").setLevel(Level.TRACE);

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			LinkedList<Entry> userEntries = new LinkedList<Entry>();
			for (int j = 0; j < INDEX_LOAD_SIZE / CONCURRENT_USER_SIZE
					&& !entries.isEmpty(); j++) {
				Entry entry = entries.removeFirst();
				userEntries.add(entry);
			}
			users[i - 1] = new Thread(new DeleteUser(uniqueRootPageID, i,
					userEntries, false), "User" + i);
			users[i - 1].setDaemon(true);
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].start();
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].join();
		}

		t2 = sm.taMgr.begin();

		// printIndex(display, "/home/sbaechl/projects/xtc/test/index.dot",
		// true);
		if (!stop)
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					uniqueRootPageID);
		else
			fail("Users did not succeed.");

	}

	@Test
	public void testRollbackInsertUniqueIndexRandomKeysConcurrent()
			throws IndexAccessException, IndexOperationException,
			InterruptedException {
		Thread[] users = new Thread[CONCURRENT_USER_SIZE];
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		Collections.shuffle(entries, rand);

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			LinkedList<Entry> userEntries = new LinkedList<Entry>();
			for (int j = 0; j < INDEX_LOAD_SIZE / CONCURRENT_USER_SIZE
					&& !entries.isEmpty(); j++) {
				Entry entry = entries.removeFirst();
				userEntries.add(entry);
			}
			users[i - 1] = new Thread(new InsertUser(uniqueRootPageID, i,
					userEntries, true), "User" + i);
			users[i - 1].setDaemon(true);
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].start();
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].join();
		}

		// printIndex(new IndexDisplay(t2, sm.buffer, ValueType.UINTEGER,
		// ValueType.UINTEGER, rootPageID), "/home/sbaechl/index.dot", false);

		if (!stop) {
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					uniqueRootPageID);
		} else {
			fail("Users did not succeed.");
		}
	}

	@Test
	public void testRollbackDeleteUniqueIndexRandomKeysConcurrent()
			throws ServerException, InterruptedException {
		LinkedList<Entry> entries = generateEntries(INDEX_LOAD_SIZE, 0);
		loadIndex(t2, entries, uniqueRootPageID);
		t2.commit();

		Thread[] users = new Thread[CONCURRENT_USER_SIZE];
		Collections.shuffle(entries, rand);

		// Logger.getLogger("org.brackit.server.io.file.index").setLevel(Level.TRACE);

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			LinkedList<Entry> userEntries = new LinkedList<Entry>();
			for (int j = 0; j < INDEX_LOAD_SIZE / CONCURRENT_USER_SIZE
					&& !entries.isEmpty(); j++) {
				Entry entry = entries.removeFirst();
				userEntries.add(entry);
			}
			users[i - 1] = new Thread(new DeleteUser(uniqueRootPageID, i,
					userEntries, true), "User" + i);
			users[i - 1].setDaemon(true);
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].start();
		}

		for (int i = 1; i <= CONCURRENT_USER_SIZE; i++) {
			users[i - 1].join();
		}

		t2 = sm.taMgr.begin();

		// printIndex(display, "/home/sbaechl/projects/xtc/test/index.dot",
		// true);
		if (!stop) {
			indexPageHelper.checkIndexConsistency(t2, sm.buffer,
					uniqueRootPageID);
		} else {
			fail("Users did not succeed.");
		}
	}

}