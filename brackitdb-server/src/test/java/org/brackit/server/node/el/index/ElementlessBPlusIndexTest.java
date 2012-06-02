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

import java.util.Arrays;

import junit.framework.Assert;

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
import org.brackit.server.store.index.aries.AbstractBPlusIndexTest;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.xquery.util.log.Logger;
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

	@Test
	public void testInsertAttributeUnderEmptyElementA()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5");
		XTCdeweyID attribute = element.getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementA" + number(1) +
		// ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue,
				element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementA" + number(2) +
		// ".dot", true);

		Assert.assertNull("Element record was removed",
				index.read(t1, rootPageID, element.toBytes()));
		Assert.assertNotNull("Attribute record was inserted",
				index.read(t1, rootPageID, attribute.toBytes()));
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
		index.insert(t1, rootPageID, element.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementB" + number(1) +
		// ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue,
				element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementB" + number(2) +
		// ".dot", true);

		Assert.assertNull("Element record was removed",
				index.read(t1, rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed",
				index.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Attribute record was inserted",
				index.read(t1, rootPageID, attribute.toBytes()));
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
		index.insert(t1, rootPageID, element.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element3.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementC" + number(1) +
		// ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue,
				element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderEmptyElementC" + number(2) +
		// ".dot", true);

		Assert.assertNull("Element record was removed",
				index.read(t1, rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed",
				index.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed",
				index.read(t1, rootPageID, element3.toBytes()));
		Assert.assertNotNull("Attribute record was inserted",
				index.read(t1, rootPageID, attribute.toBytes()));
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
		index.insert(t1, rootPageID, element.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementA" + number(1)
		// + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue,
				element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementA" + number(2)
		// + ".dot", true);

		Assert.assertNotNull("Element record was not removed",
				index.read(t1, rootPageID, element.toBytes()));
		Assert.assertNotNull("Attribute record was inserted",
				index.read(t1, rootPageID, attribute.toBytes()));

	}

	@Test
	public void testInsertAttributeUnderNonEmptyElementB()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5.3");
		XTCdeweyID element2 = new XTCdeweyID("4711:1.7");
		XTCdeweyID attribute = element.getParent().getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementB" + number(1)
		// + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue,
				element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementB" + number(2)
		// + ".dot", true);

		Assert.assertNotNull("Element record was not removed",
				index.read(t1, rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed",
				index.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Attribute record was inserted",
				index.read(t1, rootPageID, attribute.toBytes()));
	}

	@Test
	public void testInsertAttributeUnderNonEmptyElementC()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5.3");
		XTCdeweyID element2 = new XTCdeweyID("4711:1.7");
		XTCdeweyID element3 = new XTCdeweyID("4711:1.3");
		XTCdeweyID attribute = element.getParent().getNewAttributeID();
		index.insert(t1, rootPageID, element.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element2.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));
		index.insert(t1, rootPageID, element3.toBytes(),
				ElRecordAccess.createRecord(2, (byte) 1, null));

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementC" + number(1)
		// + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		byte[] attributeValue = ElRecordAccess
				.createRecord(3, (byte) 1, "test");
		iterator.insertPrefixAware(attribute.toBytes(), attributeValue,
				element.getLevel());
		iterator.close();

		indexPageHelper.checkIndexConsistency(t2, sm.buffer, rootPageID);

		// printIndex(ctx, rootPageID,
		// "/media/ramdisk/testInsertAttributeUnderNonEmptyElementC" + number(2)
		// + ".dot", true);

		Assert.assertNotNull("Element record was not removed",
				index.read(t1, rootPageID, element.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed",
				index.read(t1, rootPageID, element2.toBytes()));
		Assert.assertNotNull("Sibling element record was not removed",
				index.read(t1, rootPageID, element3.toBytes()));
		Assert.assertNotNull("Attribute record was inserted",
				index.read(t1, rootPageID, attribute.toBytes()));

	}

	@Test
	public void testDeleteAttributeUnderEmptyElementA()
			throws IndexAccessException, IndexOperationException,
			DocumentException {
		XTCdeweyID element = new XTCdeweyID("4711:1.5");
		XTCdeweyID attribute = element.getNewAttributeID();
		index.insert(t1, rootPageID, attribute.toBytes(),
				ElRecordAccess.createRecord(3, (byte) 1, "Demo"));

//		printIndex(t1, rootPageID,
//				"/media/ramdisk/testDeleteAttributeUnderEmptyElementA"
//						+ number(1) + ".dot", true);

		ElIndexIterator iterator = index.open(t1, rootPageID,
				SearchMode.GREATER_OR_EQUAL, attribute.toBytes(), null,
				OpenMode.UPDATE);

		Assert.assertNotNull("Iterator opened at inserted record",
				iterator.getKey());
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