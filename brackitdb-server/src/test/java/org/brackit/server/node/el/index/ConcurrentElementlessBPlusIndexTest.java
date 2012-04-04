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
package org.brackit.server.node.el.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElIndexIterator;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.AbstractBPlusIndexTest;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.thread.ThreadCB;
import org.brackit.server.util.Calc;
import org.brackit.xquery.util.log.Logger;
import org.brackit.xquery.xdm.DocumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
@Ignore
public class ConcurrentElementlessBPlusIndexTest extends AbstractBPlusIndexTest {
	private static final Logger log = Logger
			.getLogger(ConcurrentElementlessBPlusIndexTest.class.getName());

	protected ElRecordAccess recordAccess = new ElRecordAccess();

	private ElBPlusIndex index;

	private PageID rootPageID;

	protected abstract class Navigator implements Runnable {
		protected Tx transaction;
		protected AtomicBoolean terminate;
		protected XTCdeweyID start;
		protected XTCdeweyID min;
		protected XTCdeweyID max;

		Navigator(Tx transaction, XTCdeweyID start, AtomicBoolean terminate,
				XTCdeweyID min, XTCdeweyID max) {
			this.transaction = transaction;
			this.start = start;
			this.terminate = terminate;
			this.min = min;
			this.max = max;
		}

		XTCdeweyID nextSibling(Tx transaction, XTCdeweyID deweyID)
				throws IndexAccessException {
			Assert.assertEquals("Dont hold any latches ", 0, ThreadCB.get()
					.getLatchedCount());
			try {
				return _nextSibling(transaction, deweyID);
			} finally {
				Assert.assertEquals("Released all latches ", 0, ThreadCB.get()
						.getLatchedCount());
			}
		}

		private XTCdeweyID _nextSibling(Tx transaction, XTCdeweyID deweyID)
				throws IndexAccessException {
			IndexIterator iterator = index.open(transaction, rootPageID,
					SearchMode.GREATEST_HAVING_PREFIX_RIGHT, deweyID.toBytes(),
					null, OpenMode.READ);
			if (iterator.getKey() == null) {
				iterator.close();
				return null;
			}

			XTCdeweyID currentDeweyID = new XTCdeweyID(deweyID.getDocID(),
					iterator.getKey());
			XTCdeweyID nsDeweyID = currentDeweyID.getAncestor(deweyID
					.getLevel());

			if ((nsDeweyID != null) && deweyID.isSiblingOf(nsDeweyID)) {
				if (log.isTraceEnabled()) {
					log.trace(deweyID + " -> " + nsDeweyID);
				}

				iterator.close();
				return nsDeweyID;
			}

			iterator.close();
			return null;
		}

		XTCdeweyID prevSibling(Tx transaction, XTCdeweyID deweyID)
				throws IndexAccessException {
			Assert.assertEquals("Dont hold any latches ", 0, ThreadCB.get()
					.getLatchedCount());
			try {
				return _prevSibling(transaction, deweyID);
			} finally {
				Assert.assertEquals("Released all latches ", 0, ThreadCB.get()
						.getLatchedCount());
			}
		}

		private XTCdeweyID _prevSibling(Tx transaction, XTCdeweyID deweyID)
				throws IndexAccessException {
			IndexIterator iterator = index.open(transaction, rootPageID,
					SearchMode.LEAST_HAVING_PREFIX_LEFT, deweyID.toBytes(),
					null, OpenMode.READ);
			if (iterator.getKey() == null) {
				iterator.close();
				return null;
			}

			XTCdeweyID currentDeweyID = new XTCdeweyID(deweyID.getDocID(),
					iterator.getKey());
			XTCdeweyID psDeweyID = currentDeweyID.getAncestor(deweyID
					.getLevel());

			if ((psDeweyID != null) && deweyID.isSiblingOf(psDeweyID)) {
				if (log.isTraceEnabled()) {
					log.trace(psDeweyID + " <- " + deweyID);
				}

				iterator.close();
				return psDeweyID;
			}

			iterator.close();
			return null;
		}

		void compare(List<XTCdeweyID> original, List<XTCdeweyID> test) {
			Assert.assertEquals("Test list has same size as expected",
					original.size(), test.size());

			for (int i = 0; i < original.size(); i++) {
				Assert.assertEquals(original.get(i), test.get(i));
			}
		}

		List<XTCdeweyID> scanSubtree(XTCdeweyID subtreeRoot)
				throws IndexAccessException {
			List<XTCdeweyID> subtreeIDs = new ArrayList<XTCdeweyID>();
			ElIndexIterator iterator;
			iterator = index.open(transaction, rootPageID,
					SearchMode.GREATER_OR_EQUAL, subtreeRoot.toBytes(), null,
					OpenMode.READ);
			XTCdeweyID previousID = null;

			if (iterator.getKey() != null) {
				do {
					XTCdeweyID currentID = new XTCdeweyID(
							subtreeRoot.getDocID(), iterator.getKey());
					if (currentID.isDescendantOf(subtreeRoot)) {
						Assert.assertTrue(
								"First entry in subtree or a greater than previous",
								(previousID == null)
										|| (currentID.isFollowingOf(previousID)));
						subtreeIDs.add(currentID);
						previousID = currentID;
					}
				} while (iterator.next());
			}

			iterator.close();
			return subtreeIDs;
		}

		void stepSubtree(List<XTCdeweyID> subtreeIDs)
				throws IndexAccessException {
			for (XTCdeweyID id : subtreeIDs) {
				byte[] key = id.toBytes();
				try {
					Assert.assertNotNull("Index read valid key",
							index.read(transaction, rootPageID, key));
				} catch (Error e) {
					/*
					 * System.err.println(String.format("Failed to fetch " +
					 * id)); e.printStackTrace();
					 * Assert.assertNotNull("Index read valid key2",
					 * index.read(transaction, rootPageID, key));
					 */
					throw e;
				}
			}
		}
	}

	private class SiblingNavigator extends Navigator {
		public SiblingNavigator(Tx transaction, XTCdeweyID start,
				AtomicBoolean terminate, XTCdeweyID min, XTCdeweyID max) {
			super(transaction, start, terminate, min, max);
		}

		public void run() {
			XTCdeweyID previous = start;

			try {
				while (!terminate.get()) {
					for (XTCdeweyID next = previous; next != null; next = nextSibling(
							transaction, next)) {
						if ((previous != null)
								&& (previous.isPrecedingOf(start))
								&& (next.isFollowingOf(start))) {
							Assert.fail(String.format("Skipped %s with %s->%s",
									start, previous, next));
						}
						previous = next;
					}

					if ((max != null) && (!previous.equals(max))) {
						Assert.fail(String
								.format("Missed %s in next sibling run. Last seen was %s",
										start, previous));
					}

					for (XTCdeweyID next = previous; next != null; next = prevSibling(
							transaction, next)) {
						if ((previous != null)
								&& (previous.isPrecedingOf(start))
								&& (next.isFollowingOf(start))) {
							Assert.fail(String.format("Skipped %s with %s->%s",
									start, previous, next));
						}
						previous = next;
					}

					if ((min != null) && (!previous.equals(min))) {
						Assert.fail(String
								.format("Missed %s in prev sibling run. Last seen was %s",
										start, previous));
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
				terminate.set(true);
			}
		}
	}

	private class SubtreeWriter extends Navigator {
		private final int maxNumberOfRounds;

		private final boolean deleteSubtree;

		public SubtreeWriter(int rounds, Tx transaction, XTCdeweyID start,
				AtomicBoolean terminate, XTCdeweyID min, XTCdeweyID max,
				boolean deleteSubtree) {
			super(transaction, start, terminate, min, max);
			this.maxNumberOfRounds = rounds;
			this.deleteSubtree = deleteSubtree;
		}

		public void run() {
			XTCdeweyID previous = start;
			int round = 0;

			try {
				while (!terminate.get() && (round++ < maxNumberOfRounds)) {
					List<XTCdeweyID> inserted = insertSubtree();
					List<XTCdeweyID> scanned = scanSubtree(start);
					compare(inserted, scanned);
					stepSubtree(inserted);

					if (deleteSubtree) {
						deleteSubtree(inserted);
					}

					// System.err.println(String.format("[%s] finished",
					// Thread.currentThread().getName()));

				}
			} catch (Throwable e) {
				e.printStackTrace();
				terminate.set(true);
			}
		}

		private void deleteSubtree(List<XTCdeweyID> inserted)
				throws IndexAccessException {
			Assert.assertEquals("Dont hold any latches ", 0, ThreadCB.get()
					.getLatchedCount());
			try {
				_deleteSubtree(inserted);
			} finally {
				Assert.assertEquals("Released all latches ", 0, ThreadCB.get()
						.getLatchedCount());
			}
		}

		private void _deleteSubtree(List<XTCdeweyID> inserted)
				throws IndexAccessException {
			ElIndexIterator iterator = index.open(transaction, rootPageID,
					SearchMode.GREATER_OR_EQUAL, start.toBytes(), null,
					OpenMode.UPDATE);

			try {
				for (int i = 0; i < inserted.size(); i++) {
					byte[] key = inserted.get(i).toBytes();
					Assert.assertNotNull("Index opened at valid key",
							iterator.getKey());
					Assert.assertTrue("Index opened correct key",
							Arrays.equals(iterator.getKey(), key));

					if (i > 0) {
						iterator.delete();

						if (i + 1 == inserted.size()) {
							key = inserted.get(0).toBytes();
							iterator.previous();
							Assert.assertNotNull("Index opened at valid key",
									iterator.getKey());

							Assert.assertTrue("Index opened correct key",
									Arrays.equals(iterator.getKey(), key));
							iterator.deletePrefixAware(start.getLevel());
						}
					} else {
						iterator.next();
					}
				}
			} catch (RuntimeException e) {
				e.printStackTrace();
				throw e;
			} finally {
				iterator.close();
			}
		}

		private List<XTCdeweyID> insertSubtree() throws IndexAccessException {
			Assert.assertEquals("Dont hold any latches ", 0, ThreadCB.get()
					.getLatchedCount());
			try {
				return _insertSubtree();
			} finally {
				Assert.assertEquals("Released all latches ", 0, ThreadCB.get()
						.getLatchedCount());
			}
		}

		private List<XTCdeweyID> _insertSubtree() throws IndexAccessException {
			List<XTCdeweyID> inserted = new ArrayList<XTCdeweyID>();

			XTCdeweyID openAt = start.getNewChildID(1).getNewChildID(1);
			ElIndexIterator iterator = index.open(transaction, rootPageID,
					SearchMode.GREATER_OR_EQUAL, openAt.toBytes(), null,
					OpenMode.UPDATE);

			try {
				byte[] startKey = iterator.getKey();

				for (int i = 1; i < 11; i++) {
					XTCdeweyID toInsert = start.getNewChildID(i);
					for (int j = 1; j < 11 && !terminate.get(); j++) {
						XTCdeweyID newChildID = toInsert.getNewChildID(j);
						if ((i == 1) && (j == 1)) {
							iterator.insertPrefixAware(newChildID.toBytes(),
									Calc.fromString("This is a value"),
									start.getLevel());
							if (!Arrays.equals(iterator.getKey(),
									newChildID.toBytes())) {
								Assert.fail(String.format(
										"Iterator stands after "
												+ "prefix-aware insert at "
												+ "%s instead of %s",
										iterator.getKeyType().toString(
												iterator.getKey()), iterator
												.getKeyType()
												.toString(startKey)));
							}
						} else {
							iterator.insert(newChildID.toBytes(),
									Calc.fromString("This is a value"));

							if (!Arrays.equals(iterator.getKey(),
									newChildID.toBytes())) {
								Assert.fail(String.format(
										"Iterator stands after "
												+ "insert of %s at"
												+ " %s instead of %s",
										iterator.getKeyType().toString(
												newChildID.toBytes()),
										iterator.getKeyType().toString(
												iterator.getKey()), iterator
												.getKeyType()
												.toString(startKey)));
							}
						}
						inserted.add(newChildID);
						if (!iterator.next() && startKey != null) {
							Assert.fail(String.format(
									"Could not move Iterator"
											+ " after insert of "
											+ "%s to %s. Instead, "
											+ "iterator is at %s",
									newChildID,
									(startKey != null) ? iterator.getKeyType()
											.toString(startKey) : null,
									iterator.getKey() != null ? iterator
											.getKeyType().toString(
													iterator.getKey()) : null));
						}
						if (!Arrays.equals(iterator.getKey(), startKey)
								&& !Arrays.equals(iterator.getKey(),
										(new XTCdeweyID(null, startKey))
												.getNewChildID(1)
												.getNewChildID(1).toBytes())) {
							// System.err.println(String.format(
							// "Iterator moved to %s instead of %s (%s)",
							// iterator.getKeyType().toString(
							// iterator.getKey()), iterator
							// .getKeyType().toString(startKey),
							// (i == 1 && j == 1)));
						}
					}
				}
			} finally {
				iterator.close();
			}
			return inserted;
		}
	}

	private class SubtreeReader extends Navigator {
		public SubtreeReader(Tx transaction, XTCdeweyID start,
				AtomicBoolean terminate, XTCdeweyID min, XTCdeweyID max) {
			super(transaction, start, terminate, min, max);
		}

		public void run() {
			XTCdeweyID previous = start;
			// XTCdeweyID next = start;

			try {
				while (!terminate.get()) {
					List<XTCdeweyID> scanned = scanSubtree(start);
					stepSubtree(scanned);
				}
			} catch (Throwable e) {
				e.printStackTrace();
				terminate.set(true);
			}
		}
	}

	@Test
	public void testSeriesOfPrefixAwareInsert() throws IndexAccessException,
			IndexOperationException, DocumentException, TxException,
			InterruptedException {
		XTCdeweyID element = new XTCdeweyID("4711:1.3");
		AtomicBoolean terminate = new AtomicBoolean(false);

		List<Thread> readers = new ArrayList<Thread>();
		List<Thread> writers = new ArrayList<Thread>();

		int NO_WRITE_ROUNDS = 1;
		int NO_OF_SIBLINGS = 300;
		int NO_SIBLING_NAVIGATORS = 30;
		for (int i = 0; i < NO_SIBLING_NAVIGATORS; i++) {
			readers.add(new Thread(new SiblingNavigator(sm.taMgr.begin(),
					element, terminate, null, null)));
		}

		for (int i = 0; i < NO_OF_SIBLINGS; i++) {
			index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
					.createRecord(
							element.getDivisionValue(element.getLevel() - 1),
							(byte) 1, null));

			writers.add(new Thread(new SubtreeWriter(NO_WRITE_ROUNDS, sm.taMgr
					.begin(), element, terminate, null, null, false)));

			element = XTCdeweyID.newBetween(element, null);
		}

		for (Thread reader : readers) {
			reader.setDaemon(true);
			reader.start();
		}
		for (Thread writer : writers) {
			writer.setDaemon(true);
			writer.start();
			writer.join();
		}

		boolean allWritesOK = false;
		if (!terminate.get())
			allWritesOK = true;

		terminate.set(true);
		for (Thread reader : readers) {
			reader.join();
		}

		indexPageHelper.checkIndexConsistency(t1, sm.buffer, rootPageID);
		Assert.assertTrue("All clients succeeded without error", allWritesOK);
	}

	@Test
	public void testIsolationOfPrefixAwareInsert() throws IndexAccessException,
			IndexOperationException, DocumentException, TxException,
			InterruptedException {
		XTCdeweyID element = new XTCdeweyID("4711:1.3");
		AtomicBoolean terminate = new AtomicBoolean(false);

		List<Thread> readers = new ArrayList<Thread>();
		List<Thread> writers = new ArrayList<Thread>();

		int NO_WRITE_ROUNDS = 1;
		int NO_OF_SIBLINGS = 300;
		int NO_SIBLING_NAVIGATORS = 30;
		for (int i = 0; i < NO_SIBLING_NAVIGATORS; i++) {
			readers.add(new Thread(new SiblingNavigator(sm.taMgr.begin(),
					element, terminate, null, null)));
		}

		for (int i = 0; i < NO_OF_SIBLINGS; i++) {
			index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
					.createRecord(
							element.getDivisionValue(element.getLevel() - 1),
							(byte) 1, null));

			writers.add(new Thread(new SubtreeWriter(NO_WRITE_ROUNDS, sm.taMgr
					.begin(), element, terminate, null, null, false)));

			element = XTCdeweyID.newBetween(element, null);
		}

		for (Thread reader : readers) {
			reader.setDaemon(true);
			reader.start();
		}
		for (Thread writer : writers) {
			writer.setDaemon(true);
			writer.start();
		}
		for (Thread writer : writers) {
			writer.join();
		}

		boolean allWritesOK = false;
		if (!terminate.get())
			allWritesOK = true;

		terminate.set(true);
		for (Thread reader : readers) {
			reader.join();
		}

		indexPageHelper.checkIndexConsistency(t1, sm.buffer, rootPageID);
		Assert.assertTrue("All clients succeeded without error", allWritesOK);
	}

	@Test
	public void testIsolationOfPrefixAwareInsertAndDelete()
			throws IndexAccessException, IndexOperationException,
			DocumentException, TxException, InterruptedException {
		XTCdeweyID element = new XTCdeweyID("4711:1.3");
		AtomicBoolean terminate = new AtomicBoolean(false);

		List<Thread> readers = new ArrayList<Thread>();
		List<Thread> writers = new ArrayList<Thread>();

		int NO_WRITE_ROUNDS = 1;
		int NO_OF_SIBLINGS = 300;
		int NO_SIBLING_NAVIGATORS = 30;
		for (int i = 0; i < NO_SIBLING_NAVIGATORS; i++) {
			readers.add(new Thread(new SiblingNavigator(sm.taMgr.begin(),
					element, terminate, null, null)));
		}

		for (int i = 0; i < NO_OF_SIBLINGS; i++) {
			index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
					.createRecord(
							element.getDivisionValue(element.getLevel() - 1),
							(byte) 1, null));

			writers.add(new Thread(new SubtreeWriter(NO_WRITE_ROUNDS, sm.taMgr
					.begin(), element, terminate, null, null, true)));

			element = XTCdeweyID.newBetween(element, null);
		}

		for (Thread reader : readers) {
			reader.setDaemon(true);
			reader.start();
		}
		for (Thread writer : writers) {
			writer.setDaemon(true);
			writer.start();
		}
		for (Thread writer : writers) {
			writer.join();
		}

		boolean allWritesOK = false;
		if (!terminate.get())
			allWritesOK = true;

		terminate.set(true);
		for (Thread reader : readers) {
			reader.join();
		}

		indexPageHelper.checkIndexConsistency(t1, sm.buffer, rootPageID);
		Assert.assertTrue("All clients succeeded without error", allWritesOK);
	}

	@Override
	@After
	public void tearDown() {
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		index = new ElBPlusIndex(sm.bufferManager, new ElRecordAccess());
		rootPageID = index.createIndex(t2, SysMockup.CONTAINER_NO,
				Field.DEWEYID, Field.EL_REC, true);
	}
}