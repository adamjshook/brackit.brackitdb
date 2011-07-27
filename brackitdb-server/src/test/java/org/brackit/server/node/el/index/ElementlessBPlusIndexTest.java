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
package org.brackit.server.node.el.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.DocID;
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
import org.brackit.server.util.Calc;
import org.brackit.xquery.xdm.DocumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ElementlessBPlusIndexTest extends AbstractBPlusIndexTest {
	private static final Logger log = Logger
			.getLogger(ElementlessBPlusIndexTest.class.getName());

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
			IndexIterator iterator = index.open(transaction, rootPageID,
					SearchMode.GREATEST_HAVING_PREFIX_RIGHT, deweyID.toBytes(),
					null, OpenMode.READ);
			if (iterator.getKey() == null) {
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
			Assert.assertEquals("Test list has same size as expected", original
					.size(), test.size());

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
					XTCdeweyID currentID = new XTCdeweyID(subtreeRoot
							.getDocID(), iterator.getKey());
					if (currentID.isDescendantOf(subtreeRoot)) {
						Assert
								.assertTrue(
										"First entry in subtree or a greater than previous",
										(previousID == null)
												|| (currentID
														.isFollowingOf(previousID)));
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
					Assert.assertNotNull("Index read valid key", index.read(
							transaction, rootPageID, key));
				} catch (Error e) {
					// TODO Auto-generated catch block
					System.err.println(String.format("Failed to fetch " + id));
					e.printStackTrace();
					Assert.assertNotNull("Index read valid key2", index.read(
							transaction, rootPageID, key));
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
			// XTCdeweyID next = start;

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
						Assert
								.fail(String
										.format(
												"Missed %s in next sibling run. Last seen was %s",
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
						Assert
								.fail(String
										.format(
												"Missed %s in prev sibling run. Last seen was %s",
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

					System.err.println(String.format("[%s] finished", Thread
							.currentThread().getName()));

				}
			} catch (Throwable e) {
				e.printStackTrace();
				terminate.set(true);
			}
		}

		private void deleteSubtree(List<XTCdeweyID> inserted)
				throws IndexAccessException {
			ElIndexIterator iterator = index.open(transaction, rootPageID,
					SearchMode.GREATER_OR_EQUAL, start.toBytes(), null,
					OpenMode.UPDATE);

			try {
				for (int i = 0; i < inserted.size(); i++) {
					byte[] key = inserted.get(i).toBytes();
					Assert.assertNotNull("Index opened at valid key", iterator
							.getKey());
					Assert.assertTrue("Index opened correct key", Arrays
							.equals(iterator.getKey(), key));

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
				iterator.close();
			} catch (RuntimeException e) {
				e.printStackTrace();
				iterator.close();
				throw e;
			}
		}

		private List<XTCdeweyID> insertSubtree() throws IndexAccessException {
			List<XTCdeweyID> inserted = new ArrayList<XTCdeweyID>();

			XTCdeweyID openAt = start.getNewChildID(1).getNewChildID(1);
			ElIndexIterator iterator = index.open(transaction, rootPageID,
					SearchMode.GREATER_OR_EQUAL, openAt.toBytes(), null,
					OpenMode.UPDATE);
			byte[] startKey = iterator.getKey();
			// System.err.println(String.format("[%s] opened index at %s to store subtree of %s",
			// Thread.currentThread().getName(),
			// Field.DEWEYID.valueToString(iterator.getKey()), start));

			try {
				for (int i = 1; i < 11; i++) {
					XTCdeweyID toInsert = start.getNewChildID(i);
					for (int j = 1; j < 11 && !terminate.get(); j++) {
						XTCdeweyID newChildID = toInsert.getNewChildID(j);
						if ((i == 1) && (j == 1)) {
							iterator.insertPrefixAware(newChildID.toBytes(),
									Calc.fromString("This is a value"), start
											.getLevel());
							if (!Arrays.equals(iterator.getKey(), newChildID
									.toBytes())) {
								System.err.println(String.format(
										"Iterator stands after "
												+ "prefix-aware insert at "
												+ "%s instead of %s", iterator
												.getKeyType().toString(
														iterator.getKey()),
										iterator.getKeyType()
												.toString(startKey)));
							}
						} else {
							iterator.insert(newChildID.toBytes(), Calc
									.fromString("This is a value"));

							if (!Arrays.equals(iterator.getKey(), newChildID
									.toBytes())) {
								System.err.println(String.format(
										"Iterator stands after "
												+ "insert of %s at"
												+ " %s instead of %s", iterator
												.getKeyType().toString(
														newChildID.toBytes()),
										iterator.getKeyType().toString(
												iterator.getKey()), iterator
												.getKeyType()
												.toString(startKey)));
							}
						}
						inserted.add(newChildID);
						if (!iterator.next() && startKey != null) {
							System.err.println(String.format(
									"Could not move Iterator"
											+ " after insert of "
											+ "%s to %s. Instead, "
											+ "iterator is at %s", newChildID,
									(startKey != null) ? iterator.getKeyType()
											.toString(startKey) : null,
									iterator.getKey() != null ? iterator
											.getKeyType().toString(
													iterator.getKey()) : null));
							throw new RuntimeException();
						}
						if (!Arrays.equals(iterator.getKey(), startKey)
								&& !Arrays.equals(iterator.getKey(),
										(new XTCdeweyID(null, startKey))
												.getNewChildID(1)
												.getNewChildID(1).toBytes())) {
							System.err.println(String.format(
									"Iterator moved to %s instead of %s (%s)",
									iterator.getKeyType().toString(
											iterator.getKey()), iterator
											.getKeyType().toString(startKey),
									(i == 1 && j == 1)));
							// terminate.set(true);
						}
					}
				}
				iterator.close();
			} catch (RuntimeException e) {
				e.printStackTrace();
				iterator.close();
				throw e;
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
					.createRecord(element
							.getDivisionValue(element.getLevel() - 1),
							(byte) 1, null));

			writers.add(new Thread(new SubtreeWriter(NO_WRITE_ROUNDS, sm.taMgr
					.begin(), element, terminate, null, null, false)));

			element = XTCdeweyID.newBetween(element, null);
			// readers.add(new Thread(new
			// SubtreeReader(sm.taMgr.begin(IsolationLevel.SERIALIZABLE, null,
			// false), element, terminate, null, null)));
			// element = XTCdeweyID.newBetween(element, null);
		}

		for (Thread reader : readers) {
			reader.start();
		}
		for (Thread writer : writers) {
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

		// index.dump(ctx, rootPageID, System.out);

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
					.createRecord(element
							.getDivisionValue(element.getLevel() - 1),
							(byte) 1, null));

			writers.add(new Thread(new SubtreeWriter(NO_WRITE_ROUNDS, sm.taMgr
					.begin(), element, terminate, null, null, false)));

			element = XTCdeweyID.newBetween(element, null);
			// readers.add(new Thread(new
			// SubtreeReader(sm.taMgr.begin(IsolationLevel.SERIALIZABLE, null,
			// false), element, terminate, null, null)));
			// element = XTCdeweyID.newBetween(element, null);
		}

		for (Thread reader : readers) {
			reader.start();
		}
		for (Thread writer : writers) {
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

		// index.dump(ctx, rootPageID, System.out);

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
					.createRecord(element
							.getDivisionValue(element.getLevel() - 1),
							(byte) 1, null));

			writers.add(new Thread(new SubtreeWriter(NO_WRITE_ROUNDS, sm.taMgr
					.begin(), element, terminate, null, null, true)));

			element = XTCdeweyID.newBetween(element, null);
			// readers.add(new Thread(new
			// SubtreeReader(sm.taMgr.begin(IsolationLevel.SERIALIZABLE, null,
			// false), element, terminate, null, null)));
			// element = XTCdeweyID.newBetween(element, null);
		}

		for (Thread reader : readers) {
			reader.start();
		}
		for (Thread writer : writers) {
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

		// index.dump(ctx, rootPageID, System.out);

		Assert.assertTrue("All clients succeeded without error", allWritesOK);
	}

	@Test
	public void testIsolationOfPrefixAwareInsert2()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5.7");
		prepareIndexWithEmptyElementLastInALeaf(element);
	}

	private void prepareIndexWithEmptyElementFirstInALeaf(XTCdeweyID element)
			throws IndexAccessException, DocumentException {
		XTCdeweyID prevSibling = null;
		XTCdeweyID nextSibling = null;
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		int print = 0;

		/*
		 * Now insert entries before and after our element until we have at
		 * least three leaf pages. Then delete all entries in the leaf page of
		 * the element to make it the first in its page.
		 */
		boolean prepared = false;
		do {
			nextSibling = XTCdeweyID.newBetween(element, nextSibling);
			index.insert(t1, rootPageID, nextSibling.toBytes(), ElRecordAccess
					.createRecord(2, (byte) 1, null));
			prevSibling = XTCdeweyID.newBetween(prevSibling, element);
			index.insert(t1, rootPageID, prevSibling.toBytes(), ElRecordAccess
					.createRecord(2, (byte) 1, null));

			printIndex(t1, rootPageID,
					"/media/ramdisk/prepareIndexWithEmptyElementFirstInALeaf"
							+ number(++print) + ".dot", true);

			ElIndexIterator iterator = index.open(t1, rootPageID,
					SearchMode.GREATER_OR_EQUAL, element.toBytes(), null,
					OpenMode.READ);
			PageID elementPageID = iterator.getCurrentPageID();

			while (!prepared && iterator.next()) {
				if (!elementPageID.equals(iterator.getCurrentPageID())) {
					// we are in the next page
					iterator.close();
					iterator = index.open(t1, rootPageID,
							SearchMode.GREATER_OR_EQUAL, element.toBytes(),
							null, OpenMode.UPDATE);
					int stepsBack = 0;

					while (iterator.previous()) {
						stepsBack++;

						if (!elementPageID.equals(iterator.getCurrentPageID())) {
							// we are in the previous page -> delete the
							// preceding entries in the page
							iterator.next();

							for (int i = 0; i < stepsBack - 1; i++) {
								iterator.delete();
							}

							prepared = true;
							break;
						}
					}

					break;
				}
			}

			iterator.close();
		} while (!prepared);

		printIndex(t1, rootPageID,
				"/media/ramdisk/prepareIndexWithEmptyElementFirstInALeaf"
						+ number(++print) + ".dot", true);
	}

	private void prepareIndexWithEmptyElementLastInALeaf(XTCdeweyID element)
			throws IndexAccessException, DocumentException {
		XTCdeweyID prevSibling = null;
		XTCdeweyID nextSibling = null;
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		int print = 0;

		/*
		 * Now insert entries before and after our element until we have at
		 * least three leaf pages. Then delete all entries in the leaf page of
		 * the element to make it the first in its page.
		 */
		boolean prepared = false;
		do {
			nextSibling = XTCdeweyID.newBetween(element, nextSibling);
			index.insert(t1, rootPageID, nextSibling.toBytes(), ElRecordAccess
					.createRecord(2, (byte) 1, null));
			prevSibling = XTCdeweyID.newBetween(prevSibling, element);
			index.insert(t1, rootPageID, prevSibling.toBytes(), ElRecordAccess
					.createRecord(2, (byte) 1, null));

			printIndex(t1, rootPageID,
					"/media/ramdisk/prepareIndexWithEmptyElementLastInALeaf"
							+ number(++print) + ".dot", true);

			ElIndexIterator iterator = index.open(t1, rootPageID,
					SearchMode.GREATER_OR_EQUAL, element.toBytes(), null,
					OpenMode.READ);
			PageID elementPageID = iterator.getCurrentPageID();

			while (!prepared && iterator.previous()) {
				if (!elementPageID.equals(iterator.getCurrentPageID())) {
					// we are in the previous page
					iterator.close();
					iterator = index.open(t1, rootPageID,
							SearchMode.GREATER_OR_EQUAL, element.toBytes(),
							null, OpenMode.UPDATE);
					int stepsForward = 0;

					while (iterator.next()) {
						stepsForward++;

						if (!elementPageID.equals(iterator.getCurrentPageID())) {
							// we are in the next page -> delete the proceeding
							// entries in the page
							iterator.previous();

							for (int i = 0; i < stepsForward - 1; i++) {
								iterator.delete();
								iterator.previous();
							}

							prepared = true;
							break;
						}
					}

					break;
				}
			}

			iterator.close();
		} while (!prepared);

		printIndex(t1, rootPageID,
				"/media/ramdisk/prepareIndexWithEmptyElementLastInALeaf"
						+ number(++print) + ".dot", true);
	}

	@Test
	public void testInsertAttributeUnderEmptyElementA()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5");
		XTCdeweyID attribute = element.getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementA" + number(1) +
		// ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue, element
				.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementA" + number(2) +
		// ".dot", true);

		Assert.assertNull("Element record was removed", index.read(t1,
				rootPageID, element.toBytes()));
		Assert.assertNotNull("Attribute record was inserted", index.read(t1,
				rootPageID, attribute.toBytes()));
		Assert.assertTrue("Correct attribute record was inserted", Arrays
				.equals(index.read(t1, rootPageID, attribute.toBytes()),
						attributeValue));
	}

	@Test
	public void testInsertAttributeUnderEmptyElementB()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5");
		XTCdeweyID element2 = new XTCdeweyID("4711:1.7");
		XTCdeweyID attribute = element.getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementB" + number(1) +
		// ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue, element
				.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementB" + number(2) +
		// ".dot", true);

		Assert.assertNull("Element record was removed", index.read(t1,
				rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed", index
				.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Attribute record was inserted", index.read(t1,
				rootPageID, attribute.toBytes()));
		Assert.assertTrue("Correct attribute record was inserted", Arrays
				.equals(index.read(t1, rootPageID, attribute.toBytes()),
						attributeValue));
	}

	@Test
	public void testInsertAttributeUnderEmptyElementC()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5");
		XTCdeweyID element2 = new XTCdeweyID("4711:1.7");
		XTCdeweyID element3 = new XTCdeweyID("4711:1.3");
		XTCdeweyID attribute = element.getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element3.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementC" + number(1) +
		// ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue, element
				.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementC" + number(2) +
		// ".dot", true);

		Assert.assertNull("Element record was removed", index.read(t1,
				rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed", index
				.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed", index
				.read(t1, rootPageID, element3.toBytes()));
		Assert.assertNotNull("Attribute record was inserted", index.read(t1,
				rootPageID, attribute.toBytes()));
		Assert.assertTrue("Correct attribute record was inserted", Arrays
				.equals(index.read(t1, rootPageID, attribute.toBytes()),
						attributeValue));
	}

	@Test
	public void testInsertAttributeUnderNonEmptyElementA()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5.3");
		XTCdeweyID attribute = element.getParent().getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementA" + number(1)
		// + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue, element
				.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementA" + number(2)
		// + ".dot", true);

		Assert.assertNotNull("Element record was not removed", index.read(t1,
				rootPageID, element.toBytes()));
		Assert.assertNotNull("Attribute record was inserted", index.read(t1,
				rootPageID, attribute.toBytes()));

	}

	@Test
	public void testInsertAttributeUnderNonEmptyElementB()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5.3");
		XTCdeweyID element2 = new XTCdeweyID("4711:1.7");
		XTCdeweyID attribute = element.getParent().getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementB" + number(1)
		// + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue, element
				.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementB" + number(2)
		// + ".dot", true);

		Assert.assertNotNull("Element record was not removed", index.read(t1,
				rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed", index
				.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Attribute record was inserted", index.read(t1,
				rootPageID, attribute.toBytes()));
	}

	@Test
	public void testInsertAttributeUnderNonEmptyElementC()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5.3");
		XTCdeweyID element2 = new XTCdeweyID("4711:1.7");
		XTCdeweyID element3 = new XTCdeweyID("4711:1.3");
		XTCdeweyID attribute = element.getParent().getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element3.toBytes(), ElRecordAccess
				.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementC" + number(1)
		// + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue, element
				.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementC" + number(2)
		// + ".dot", true);

		Assert.assertNotNull("Element record was not removed", index.read(t1,
				rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed", index
				.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed", index
				.read(t1, rootPageID, element3.toBytes()));
		Assert.assertNotNull("Attribute record was inserted", index.read(t1,
				rootPageID, attribute.toBytes()));

	}

	@Test
	public void testInsertAttributeUnderNonEmptyElementInPreviousPageA()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.3");
		ElIndexIterator iterator = index.open(t1, rootPageID, SearchMode.FIRST,
				null, null, OpenMode.BULK);

		int i = 0;
		while (iterator.getCurrentPageID().equals(rootPageID)) {
			iterator.insert(element.toBytes(), ElRecordAccess.createRecord(2,
					(byte) 1, null));
			iterator.next();
			element = XTCdeweyID.newBetween(element, null);
		}

		PageID currentLeafPageID = iterator.getCurrentPageID();
		while (iterator.getCurrentPageID().equals(currentLeafPageID)) {
			iterator.previous();
		}
		element = new XTCdeweyID(new DocID(4711), iterator.getKey());
		iterator.close();

		printIndex(t1, rootPageID,
				"/media/ramdisk/testInsertAttributeUnderNonEmptyElementInPreviousPageA"
						+ number(i++) + ".dot", true);

		byte[] key = element.getNewAttributeID().toBytes();
		iterator = index.open(t1, rootPageID, SearchMode.GREATER_OR_EQUAL, key,
				null, OpenMode.UPDATE);
		iterator.insertPrefixAware(key, ElRecordAccess.createRecord(3,
				(byte) 1, "test"), element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		printIndex(t1, rootPageID,
				"/media/ramdisk/testInsertAttributeUnderNonEmptyElementInPreviousPageA"
						+ number(i++) + ".dot", true);
	}

	@Test
	public void testDeleteAttributeUnderEmptyElementA()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5");
		XTCdeweyID attribute = element.getNewAttributeID();
		index.insert(t1, rootPageID, attribute.toBytes(), ElRecordAccess
				.createRecord(3, (byte) 1, "Demo"));

		printIndex(t1, rootPageID,
				"/media/ramdisk/testDeleteAttributeUnderEmptyElementA"
						+ number(1) + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		Assert.assertNotNull("Iterator opened at inserted record", iterator
				.getKey());
		iterator.deletePrefixAware(element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		printIndex(t1, rootPageID,
				"/media/ramdisk/testDeleteAttributeUnderEmptyElementA"
						+ number(2) + ".dot", true);
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