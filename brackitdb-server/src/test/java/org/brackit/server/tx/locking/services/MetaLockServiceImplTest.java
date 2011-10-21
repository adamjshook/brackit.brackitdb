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
package org.brackit.server.tx.locking.services;

import static org.junit.Assert.assertEquals;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.session.async.JobScheduler;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockServiceClientCB;
import org.brackit.server.tx.locking.services.EdgeLockService.Edge;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class MetaLockServiceImplTest {
	private final static String SERVICE_NAME = "LOCKBUFFER_TEST_SERVICE";

	private final static int BLOCK_SIZE = 100;

	private final static int MAX_LOCKS = 1000;

	private final static int PERFORMANCE_BLOCK_SIZE = 100;

	private final static int PERFORMANCE_MAX_LOCKS = 400000;

	private final static int PERFORMANCE_MONITOR_INTERVALL = 10000;

	private final static int PERFORMANCE_LOCKS_PER_REQUEST = 4;

	private final static int MAX_TRANSACTIONS = 3;

	private TxMgr taMgr;

	private MetaLockService lockService;

	private JobScheduler scheduler;

	private Tx t1;

	private Tx t2;

	private Tx t3;

	@Test
	public void lockAndReleaseEdgeNone() throws Exception {
		t1 = taMgr.begin();

		XTCdeweyID deweyID = new XTCdeweyID("1234:1.3.5.7.9");

		lockService.lockEdgeUpdate(t1, deweyID, Edge.FIRST_CHILD);
		final LockServiceClientCB lockServiceCB = t1.getLockCB().get(
				lockService).getLockServiceCB();
		assertEquals("ta lock count after update lock", 0, lockServiceCB
				.getCount());
		lockService.lockEdgeExclusive(t1, deweyID, Edge.FIRST_CHILD);
		assertEquals("ta lock count after exclusive lock", 1, lockServiceCB
				.getCount());
		lockService.unlockEdge(t1, deweyID, Edge.FIRST_CHILD);
		assertEquals("ta lock count after unlock", 0, lockServiceCB.getCount());
	}

	@Test
	public void lockAndReleaseNodeCommitted() throws Exception {
		t1 = taMgr.begin();

		XTCdeweyID deweyID1 = new XTCdeweyID("1234:1.3.5.7.9");
		XTCdeweyID deweyID2 = new XTCdeweyID("1234:1.3.5.7.9.11");

		lockService.lockNodeShared(t1, deweyID1, LockClass.SHORT_DURATION,
				false);
		assertEquals("ta lock count after request of id1", 5, t1.getLockCB()
				.get(lockService).getLockServiceCB().getCount());

		lockService.lockNodeShared(t1, deweyID2, LockClass.SHORT_DURATION,
				false);
		assertEquals("ta lock count after request of id2", 6, t1.getLockCB()
				.get(lockService).getLockServiceCB().getCount());

		lockService.unlockNode(t1, deweyID2);
		assertEquals("ta lock count after release of id2", 5, t1.getLockCB()
				.get(lockService).getLockServiceCB().getCount());

		lockService.unlockNode(t1, deweyID1);
		assertEquals("ta lock count after release of id1", 0, t1.getLockCB()
				.get(lockService).getLockServiceCB().getCount());
	}

	@Before
	public void setUp() throws Exception {
		taMgr = new TaMgrMockup();
		lockService = new MetaLockServiceImpl(SERVICE_NAME, MAX_LOCKS,
				MAX_TRANSACTIONS);
		scheduler = new JobScheduler();
	}

	@After
	public void tearDown() throws Exception {
		if (t1 != null)
			t1.rollback();
		if (t2 != null)
			t2.rollback();
		if (t3 != null)
			t3.rollback();
	}
}
