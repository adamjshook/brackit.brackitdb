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
package org.brackit.server.tx.locking.services;

import static org.brackit.server.tx.locking.protocol.RUX.Mode.R;
import static org.brackit.server.tx.locking.protocol.RUX.Mode.U;
import static org.brackit.server.tx.locking.protocol.RUX.Mode.X;
import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.FIRST_CHILD;
import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.LAST_CHILD;
import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.NEXT_SIBLING;
import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.PREV_SIBLING;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.server.tx.locking.protocol.RUXEdgeLockProtocol;
import org.brackit.server.tx.log.LogException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EdgeNodeLockServiceTest {
	private final static String SERVICE_NAME = "EDGELOCK_TEST_SERVICE";

	private final static int MAX_LOCKS = 1000;

	private final static int MAX_TRANSACTIONS = 3;

	private TxMgr taMgr;

	private EdgeLockService lockService;

	private Tx t1;

	private Tx t2;

	private Tx t3;

	@Test
	public void lockEdgeFirstChildShared() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeShared(t1, deweyID, FIRST_CHILD);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, FIRST_CHILD);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", R, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeFirstChildUpdate() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeUpdate(t1, deweyID, FIRST_CHILD);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, FIRST_CHILD);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", U, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeFirstChildExclusive() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeExclusive(t1, deweyID, FIRST_CHILD);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, FIRST_CHILD);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", X, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeLastChildShared() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeShared(t1, deweyID, LAST_CHILD);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, LAST_CHILD);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", R, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeLastChildUpdate() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeUpdate(t1, deweyID, LAST_CHILD);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, LAST_CHILD);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", U, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeLastChildExclusive() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeExclusive(t1, deweyID, LAST_CHILD);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, LAST_CHILD);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", X, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeNextSiblingShared() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeShared(t1, deweyID, NEXT_SIBLING);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, NEXT_SIBLING);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", R, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeNextSiblingUpdate() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeUpdate(t1, deweyID, NEXT_SIBLING);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, NEXT_SIBLING);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", U, locks.get(0).getMode());
	}

	@Test
	public void lockEdgeNextSiblingExclusive() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeExclusive(t1, deweyID, NEXT_SIBLING);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, NEXT_SIBLING);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", X, locks.get(0).getMode());
	}

	@Test
	public void lockEdgePrevSiblingShared() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeShared(t1, deweyID, PREV_SIBLING);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, PREV_SIBLING);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", R, locks.get(0).getMode());
	}

	@Test
	public void lockEdgePrevSiblingUpdate() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeUpdate(t1, deweyID, PREV_SIBLING);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, PREV_SIBLING);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", U, locks.get(0).getMode());
	}

	@Test
	public void lockEdgePrevSiblingExclusive() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockEdgeExclusive(t1, deweyID, PREV_SIBLING);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 1, locks.size());

		locks = lockService.getLocks(deweyID, PREV_SIBLING);
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", X, locks.get(0).getMode());
	}

	@Before
	public void setUp() throws Exception, LogException {
		taMgr = new TaMgrMockup();
		lockService = new EdgeLockServiceImpl(new RUXEdgeLockProtocol(),
				SERVICE_NAME, MAX_LOCKS, MAX_TRANSACTIONS);
	}

	@After
	public void tearDown() throws Exception {
	}
}
