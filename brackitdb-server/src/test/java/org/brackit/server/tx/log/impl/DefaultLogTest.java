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
package org.brackit.server.tx.log.impl;

import static org.junit.Assert.assertEquals;

import org.brackit.server.ServerException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.Loggable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Sebastian Baechle
 * 
 */
public class DefaultLogTest {
	static final String CONTAINER_NAME = DefaultLogTest.class.getSimpleName()
			+ ".cnt";

	static final int CONTAINER_NO = 8;

	static final int BUFFER_SIZE = 200;

	static final int EXTEND_SIZE = 300;

	static final int BLOCK_SIZE = 128; // 8192; //2048;

	private static final int INITIAL_SIZE = 20;

	TxMgr taMgr;

	Tx t1;

	Tx t2;

	BufferMgr bufferMgr;

	DefaultLog log;

	@Test
	public void testFlush() throws LogException, TxException {
		Loggable loggable1 = log.getLoggableHelper().createEOT(t1.getID(),
				t1.checkPrevLSN());
		Loggable loggable2 = log.getLoggableHelper().createEOT(t2.getID(),
				t1.checkPrevLSN());
		Loggable loggable3 = log.getLoggableHelper().createEOT(t1.getID(),
				t1.checkPrevLSN());
		Loggable loggable4 = log.getLoggableHelper().createEOT(t2.getID(),
				t1.checkPrevLSN());

		long lsn1 = log.append(loggable1);
		long lsn2 = log.append(loggable2);
		long lsn3 = log.append(loggable3);
		long lsn4 = log.append(loggable4);

		assertEquals("Assigned lsn", lsn1, loggable1.getLSN());
		assertEquals("Assigned lsn", lsn2, loggable2.getLSN());
		assertEquals("Assigned lsn", lsn3, loggable3.getLSN());
		assertEquals("Assigned lsn", lsn4, loggable4.getLSN());

		log.flushAll();

		Loggable restoredLoggable1 = log.get(lsn1);
		Loggable restoredLoggable2 = log.get(lsn2);
		Loggable restoredLoggable3 = log.get(lsn3);
		Loggable restoredLoggable4 = log.get(lsn4);

		assertEquals("Restored lsn", loggable1.getLSN(), restoredLoggable1
				.getLSN());
		assertEquals("Restored lsn", loggable2.getLSN(), restoredLoggable2
				.getLSN());
		assertEquals("Restored lsn", loggable3.getLSN(), restoredLoggable3
				.getLSN());
		assertEquals("Restored lsn", loggable4.getLSN(), restoredLoggable4
				.getLSN());

		assertEquals("Restored type", loggable1.getType(), restoredLoggable1
				.getType());
		assertEquals("Restored type", loggable2.getType(), restoredLoggable2
				.getType());
		assertEquals("Restored type", loggable3.getType(), restoredLoggable3
				.getType());
		assertEquals("Restored type", loggable4.getType(), restoredLoggable4
				.getType());

		assertEquals("Restored taId", loggable1.getTxID(), restoredLoggable1
				.getTxID());
		assertEquals("Restored taId", loggable2.getTxID(), restoredLoggable2
				.getTxID());
		assertEquals("Restored taId", loggable3.getTxID(), restoredLoggable3
				.getTxID());
		assertEquals("Restored taId", loggable4.getTxID(), restoredLoggable4
				.getTxID());
	}

	@Before
	public void setUp() throws ServerException {
		log = new DefaultLog(".", DefaultLog.class.getName(), 500);
		taMgr = new TaMgrMockup(log);
		t1 = taMgr.begin();
		t2 = taMgr.begin();
	}

	@After
	public void tearDown() throws LogException {
		log.close();
		log.clear();
	}
}
