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

import static org.junit.Assert.assertEquals;

import java.util.Comparator;
import java.util.List;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.protocol.TaDOM3Plus;
import org.brackit.server.tx.locking.protocol.TaDOM3Plus.Mode;
import org.brackit.server.tx.log.LogException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NodeLockServiceTest {
	private final static String SERVICE_NAME = "NODELOCK_TEST_SERVICE";

	private final static int MAX_LOCKS = 1000;

	private final static int MAX_TRANSACTIONS = 3;

	private TxMgr taMgr;

	private NodeLockService lockService;

	private Tx t1;

	private Tx t2;

	private Tx t3;

	private class XTClockTAIDComparator implements Comparator<XTClock> {
		@Override
		public int compare(XTClock o1, XTClock o2) {
			return o1.getTxID().compareTo(o2.getTxID());
		}
	}

	@Test
	public void lockNodeSharedLevel() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockLevelShared(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		// System.out.println(lockMgr.listLocks());
		assertEquals("ta lock count", 5, locks.size());

		locks = lockService.getLocks(deweyID);
		assertEquals("leaf lock count", 1, locks.size());
		assertEquals("leaf lock mode", Mode.LR, locks.get(0).getMode());

		for (XTCdeweyID ancestor : deweyID.getAncestors()) {
			if (ancestor.getLevel() > 0) {
				locks = lockService.getLocks(ancestor);
				assertEquals("parent lock count", 1, locks.size());
				assertEquals("parent lock mode", Mode.IR, locks.get(0)
						.getMode());
			}
		}
	}

	@Test
	public void lockNodeShared() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockNodeShared(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		// System.out.println(lockService.listLocks());
		assertEquals("ta lock count", 5, locks.size());

		locks = lockService.getLocks(deweyID);
		assertEquals("leaf lock count", 1, locks.size());
		assertEquals("leaf lock mode", Mode.NR, locks.get(0).getMode());

		for (XTCdeweyID parent : deweyID.getAncestors()) {
			if (parent.getLevel() > 0) {
				locks = lockService.getLocks(parent);
				assertEquals("ancestor lock count", 1, locks.size());
				assertEquals("ancestor lock mode", Mode.IR, locks.get(0)
						.getMode());
			}
		}
	}

	@Test
	public void lockNodeUpdate() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockNodeUpdate(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		// System.out.println(lockService.listLocks());
		assertEquals("ta lock count", 5, locks.size());

		locks = lockService.getLocks(deweyID);
		assertEquals("leaf lock count", 1, locks.size());
		assertEquals("leaf lock mode", Mode.NU, locks.get(0).getMode());

		for (XTCdeweyID ancestor : deweyID.getAncestors()) {
			if (ancestor.getLevel() > 0) {
				locks = lockService.getLocks(ancestor);
				assertEquals("ancestor lock count", 1, locks.size());
				assertEquals("ancestor lock mode", Mode.IR, locks.get(0)
						.getMode());
			}
		}
	}

	@Test
	public void lockNodeExclusive() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockNodeExclusive(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		// System.out.println(lockMgr.listLocks());
		assertEquals("ta lock count", 5, locks.size());

		locks = lockService.getLocks(deweyID);
		assertEquals("leaf lock count", 1, locks.size());
		assertEquals("leaf lock mode", Mode.NX, locks.get(0).getMode());

		locks = lockService.getLocks(deweyID.getParent());
		assertEquals("parent lock count", 1, locks.size());
		assertEquals("parent lock mode", Mode.CX, locks.get(0).getMode());

		for (XTCdeweyID ancestor : deweyID.getParent().getAncestors()) {
			if (ancestor.getLevel() > 0) {
				locks = lockService.getLocks(ancestor);
				assertEquals("ancestor lock count", 1, locks.size());
				assertEquals("ancestor lock mode", Mode.IX, locks.get(0)
						.getMode());
			}
		}
	}

	@Test
	public void lockNodeSharedTree() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockTreeShared(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 5, locks.size());

		locks = lockService.getLocks(deweyID);
		assertEquals("leaf lock count", 1, locks.size());
		assertEquals("leaf lock mode", Mode.SR, locks.get(0).getMode());

		for (XTCdeweyID ancestor : deweyID.getAncestors()) {
			if (ancestor.getLevel() > 0) {
				locks = lockService.getLocks(ancestor);
				assertEquals("ancestor lock count", 1, locks.size());
				assertEquals("ancestor lock mode", Mode.IR, locks.get(0)
						.getMode());
			}
		}
	}

	@Test
	public void lockNodeUpdateTree() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockTreeUpdate(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		// System.out.println(lockMgr.listLocks());
		assertEquals("ta lock count", 5, locks.size());

		locks = lockService.getLocks(deweyID);
		assertEquals("leaf lock count", 1, locks.size());
		assertEquals("leaf lock mode", Mode.SU, locks.get(0).getMode());

		for (XTCdeweyID ancestor : deweyID.getAncestors()) {
			if (ancestor.getLevel() > 0) {
				locks = lockService.getLocks(ancestor);
				assertEquals("ancestor lock count", 1, locks.size());
				assertEquals("ancestor lock mode", Mode.IR, locks.get(0)
						.getMode());
			}
		}
	}

	@Test
	public void lockNodeExclusiveTree() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7.9");
		lockService.lockTreeExclusive(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		// System.out.println(lockMgr.listLocks());
		assertEquals("ta lock count", 5, locks.size());

		locks = lockService.getLocks(deweyID);
		assertEquals("leaf lock count", 1, locks.size());
		assertEquals("leaf lock mode", Mode.SX, locks.get(0).getMode());

		locks = lockService.getLocks(deweyID.getParent());
		assertEquals("parent lock count", 1, locks.size());
		assertEquals("parent lock mode", Mode.CX, locks.get(0).getMode());

		for (XTCdeweyID ancestor : deweyID.getParent().getAncestors()) {
			if (ancestor.getLevel() > 0) {
				locks = lockService.getLocks(ancestor);
				assertEquals("ancestor lock count", 1, locks.size());
				assertEquals("ancestor lock mode", Mode.IX, locks.get(0)
						.getMode());
			}
		}
	}

	@Test
	public void lockDepthRuleLockNodeSharedDistance1() throws Exception {
		t1 = taMgr.begin();
		t1.setLockDepth(2);
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5");
		lockService.lockNodeShared(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 2, locks.size()); // only lock 1. and 1.3

		locks = lockService.getLocks(deweyID.getParent());
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", Mode.LR, locks.get(0).getMode());
	}

	@Test
	public void lockDepthRuleLockNodeSharedDistance2() throws Exception {
		t1 = taMgr.begin();
		t1.setLockDepth(2);
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5.7");
		lockService.lockNodeShared(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 2, locks.size()); // only lock 1. and 1.3

		locks = lockService.getLocks(deweyID.getParent().getParent());
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", Mode.SR, locks.get(0).getMode());
	}

	@Test
	public void lockDepthRuleLockNodeUpdate() throws Exception {
		t1 = taMgr.begin();
		t1.setLockDepth(2);
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5");
		lockService.lockNodeUpdate(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 2, locks.size()); // only lock 1. and 1.3

		locks = lockService.getLocks(deweyID.getParent());
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", Mode.SU, locks.get(0).getMode());
	}

	@Test
	public void lockDepthRuleLockNodeExclusive() throws Exception {
		t1 = taMgr.begin();
		t1.setLockDepth(2);
		XTCdeweyID deweyID = new XTCdeweyID("4:1.3.5");
		lockService.lockNodeExclusive(t1, deweyID, LockClass.COMMIT_DURATION,
				false);

		List<XTClock> locks = t1.getLockCB().get(lockService).getLocks();
		assertEquals("ta lock count", 2, locks.size()); // only lock 1. and 1.3
		locks = lockService.getLocks(deweyID.getParent());
		assertEquals("deweyid lock count", 1, locks.size());
		assertEquals("lock mode", Mode.SX, locks.get(0).getMode());
	}

	@Test
	public void overruleSharedTreeSharedNode() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID1 = new XTCdeweyID("4:1.3.5");
		XTCdeweyID deweyID2 = new XTCdeweyID("4:1.3.5.7.8");
		lockService.lockTreeShared(t1, deweyID1, LockClass.COMMIT_DURATION,
				false);
		lockService.lockNodeShared(t1, deweyID2, LockClass.COMMIT_DURATION,
				false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void overruleExclusiveTreeSharedNode() throws Exception {
		try {
			t1 = taMgr.begin();
			XTCdeweyID deweyID1 = new XTCdeweyID("4:1.3.5");
			XTCdeweyID deweyID2 = new XTCdeweyID("4:1.3.5.7.8");
			lockService.lockTreeExclusive(t1, deweyID1,
					LockClass.COMMIT_DURATION, false);
			lockService.lockNodeShared(t1, deweyID2, LockClass.COMMIT_DURATION,
					false);

			assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
					.getLocks().size());
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void overruleUpdateTreeSharedNode() throws Exception {
		t1 = taMgr.begin();
		XTCdeweyID deweyID1 = new XTCdeweyID("4:1.3.5");
		XTCdeweyID deweyID2 = new XTCdeweyID("4:1.3.5.7.8");
		lockService.lockTreeUpdate(t1, deweyID1, LockClass.COMMIT_DURATION,
				false);
		lockService.lockNodeShared(t1, deweyID2, LockClass.COMMIT_DURATION,
				false);

		assertEquals("ta lock count", 3, t1.getLockCB().get(lockService)
				.getLocks().size());
	}

	@Test
	public void conversionScenario1() throws Exception {
		List<XTClock> locks = null;
		t1 = taMgr.begin();
		t2 = taMgr.begin();
		XTCdeweyID deweyIDLevel4 = new XTCdeweyID("4:1.3.5.7");
		XTCdeweyID deweyIDLevel3 = deweyIDLevel4.getParent();
		XTCdeweyID deweyIDLevel2 = deweyIDLevel3.getParent();
		XTCdeweyID deweyIDLevel1 = deweyIDLevel2.getParent();

		lockService.lockNodeShared(t1, deweyIDLevel4,
				LockClass.COMMIT_DURATION, false);
		lockService.lockTreeShared(t2, deweyIDLevel2,
				LockClass.COMMIT_DURATION, false);

		locks = lockService.getLocks(deweyIDLevel4);
		assertEquals(deweyIDLevel4 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel4 + " lock mode", Mode.NR, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel3 + " lock mode", Mode.IR, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count", 2, locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.IR, locks.get(0)
				.getMode());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.SR, locks.get(1)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count", 2, locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IR, locks.get(0)
				.getMode());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IR, locks.get(1)
				.getMode());

		t2.commit();

		locks = lockService.getLocks(deweyIDLevel4);
		assertEquals(deweyIDLevel4 + " lock count after commit", 1, locks
				.size());
		assertEquals(deweyIDLevel4 + " lock mode after commit", Mode.NR, locks
				.get(0).getMode());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count after commit", 1, locks
				.size());
		assertEquals(deweyIDLevel3 + " lock mode after commit", Mode.IR, locks
				.get(0).getMode());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count after commit", 1, locks
				.size());
		assertEquals(deweyIDLevel2 + " lock mode after commit", Mode.IR, locks
				.get(0).getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count after commit", 1, locks
				.size());
		assertEquals(deweyIDLevel1 + " lock mode after commit", Mode.IR, locks
				.get(0).getMode());
	}

	@Test
	public void conversionScenario2() throws Exception {
		List<XTClock> locks = null;
		t1 = taMgr.begin();
		t2 = taMgr.begin();
		XTCdeweyID deweyIDLevel4 = new XTCdeweyID("4:1.3.5.7");
		XTCdeweyID deweyIDLevel3 = deweyIDLevel4.getParent();
		XTCdeweyID deweyIDLevel2 = deweyIDLevel3.getParent();
		XTCdeweyID deweyIDLevel1 = deweyIDLevel2.getParent();

		lockService.lockNodeShared(t1, deweyIDLevel4,
				LockClass.COMMIT_DURATION, false);
		lockService.lockTreeShared(t2, deweyIDLevel2,
				LockClass.COMMIT_DURATION, false);

		locks = lockService.getLocks(deweyIDLevel4);
		assertEquals(deweyIDLevel4 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel4 + " lock mode", Mode.NR, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel3 + " lock mode", Mode.IR, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count", 2, locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.IR, locks.get(0)
				.getMode());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.SR, locks.get(1)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count", 2, locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IR, locks.get(0)
				.getMode());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IR, locks.get(1)
				.getMode());

		t1.commit();

		locks = lockService.getLocks(deweyIDLevel4);
		assertEquals(deweyIDLevel4 + " lock count after commit", 0, locks
				.size());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count after commit", 0, locks
				.size());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count after commit", 1, locks
				.size());
		assertEquals(deweyIDLevel2 + " lock mode after commit", Mode.SR, locks
				.get(0).getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count after commit", 1, locks
				.size());
		assertEquals(deweyIDLevel1 + " lock mode after commit", Mode.IR, locks
				.get(0).getMode());
	}

	@Test
	public void conversionScenario3() throws Exception {
		List<XTClock> locks = null;
		t1 = taMgr.begin();
		t2 = taMgr.begin();
		t3 = taMgr.begin();
		XTCdeweyID deweyIDLevel4A = new XTCdeweyID("4:1.3.5.7");
		XTCdeweyID deweyIDLevel4B = new XTCdeweyID("4:1.3.5.9");
		XTCdeweyID deweyIDLevel3 = deweyIDLevel4A.getParent();
		XTCdeweyID deweyIDLevel2 = deweyIDLevel3.getParent();
		XTCdeweyID deweyIDLevel1 = deweyIDLevel2.getParent();

		lockService.lockNodeShared(t1, deweyIDLevel4A,
				LockClass.COMMIT_DURATION, false);
		lockService.lockNodeExclusive(t2, deweyIDLevel4B,
				LockClass.COMMIT_DURATION, false);
		lockService.lockNodeShared(t3, deweyIDLevel3,
				LockClass.COMMIT_DURATION, false);

		locks = lockService.getLocks(deweyIDLevel4A);
		assertEquals(deweyIDLevel4A + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel4A + " lock mode", Mode.NR, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel4B);
		assertEquals(deweyIDLevel4B + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel4B + " lock mode", Mode.NX, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count", 3, locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel3 + " lock mode", Mode.IR, locks.get(0)
				.getMode());
		assertEquals(deweyIDLevel3 + " lock mode", Mode.CX, locks.get(1)
				.getMode());
		assertEquals(deweyIDLevel3 + " lock mode", Mode.NR, locks.get(2)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count", 3, locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.IR, locks.get(0)
				.getMode());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.IX, locks.get(1)
				.getMode());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.IR, locks.get(2)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count", 3, locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IR, locks.get(0)
				.getMode());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IX, locks.get(1)
				.getMode());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IR, locks.get(2)
				.getMode());

		t2.commit();

		locks = lockService.getLocks(deweyIDLevel4A);
		assertEquals(deweyIDLevel4A + " lock count after commit of t2", 1,
				locks.size());
		assertEquals(deweyIDLevel4A + " lock mode after commit of t2", Mode.NR,
				locks.get(0).getMode());

		locks = lockService.getLocks(deweyIDLevel4B);
		assertEquals(deweyIDLevel4B + " lock count after commit of t2", 0,
				locks.size());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count after commit of t2", 2, locks
				.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel3 + " lock mode after commit of t2", Mode.IR,
				locks.get(0).getMode());
		assertEquals(deweyIDLevel3 + " lock mode after commit of t2", Mode.NR,
				locks.get(1).getMode());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count  after commit of t2", 2,
				locks.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel2 + " lock mode after commit of t2", Mode.IR,
				locks.get(0).getMode());
		assertEquals(deweyIDLevel2 + " lock mode after commit of t2", Mode.IR,
				locks.get(1).getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count after commit of t2", 2, locks
				.size());
		java.util.Collections.sort(locks, new XTClockTAIDComparator());
		assertEquals(deweyIDLevel1 + " lock mode after commit of t2", Mode.IR,
				locks.get(0).getMode());
		assertEquals(deweyIDLevel1 + " lock mode after commit of t2", Mode.IR,
				locks.get(1).getMode());

		t3.commit();

		locks = lockService.getLocks(deweyIDLevel4A);
		assertEquals(deweyIDLevel4A + " lock count after abort of t3", 1, locks
				.size());
		assertEquals(deweyIDLevel4A + " lock mode after abort of t3", Mode.NR,
				locks.get(0).getMode());
		locks = lockService.getLocks(deweyIDLevel4B);
		assertEquals(deweyIDLevel4B + " lock count after abort of t3", 0, locks
				.size());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count after abort of t3", 1, locks
				.size());
		assertEquals(deweyIDLevel3 + " lock mode after abort of t3", Mode.IR,
				locks.get(0).getMode());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count  after abort of t3", 1, locks
				.size());
		assertEquals(deweyIDLevel2 + " lock mode after abort of t3", Mode.IR,
				locks.get(0).getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count after abort of t3", 1, locks
				.size());
		assertEquals(deweyIDLevel1 + " lock mode after abort of t3", Mode.IR,
				locks.get(0).getMode());
	}

	public void conversionScenario4() throws Exception {
		List<XTClock> locks = null;
		t1 = taMgr.begin();
		XTCdeweyID deweyIDLevel4 = new XTCdeweyID("4:1.3.5.7");
		XTCdeweyID deweyIDLevel3 = deweyIDLevel4.getParent();
		XTCdeweyID deweyIDLevel2 = deweyIDLevel3.getParent();
		XTCdeweyID deweyIDLevel1 = deweyIDLevel2.getParent();

		lockService.lockNodeShared(t1, deweyIDLevel4,
				LockClass.COMMIT_DURATION, false);
		lockService.lockNodeExclusive(t1, deweyIDLevel3,
				LockClass.COMMIT_DURATION, false);

		locks = lockService.getLocks(deweyIDLevel4);
		assertEquals(deweyIDLevel4 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel4 + " lock mode", Mode.NR, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel3);
		assertEquals(deweyIDLevel3 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel3 + " lock mode", Mode.NX, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.CX, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.IX, locks.get(0)
				.getMode());

		t1.commit();
	}

	public void conversionScenario5() throws Exception {
		List<XTClock> locks = null;
		t1 = taMgr.begin();
		XTCdeweyID deweyIDLevel2 = new XTCdeweyID("4:1.3");
		XTCdeweyID deweyIDLevel1 = deweyIDLevel2.getParent();

		lockService.lockNodeShared(t1, deweyIDLevel2,
				LockClass.COMMIT_DURATION, false);
		lockService.lockNodeExclusive(t1, deweyIDLevel2,
				LockClass.COMMIT_DURATION, false);

		locks = lockService.getLocks(deweyIDLevel2);
		assertEquals(deweyIDLevel2 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel2 + " lock mode", Mode.NX, locks.get(0)
				.getMode());

		locks = lockService.getLocks(deweyIDLevel1);
		assertEquals(deweyIDLevel1 + " lock count", 1, locks.size());
		assertEquals(deweyIDLevel1 + " lock mode", Mode.CX, locks.get(0)
				.getMode());

		t1.commit();
	}

	@Before
	public void setUp() throws Exception, LogException {
		taMgr = new TaMgrMockup();
		lockService = new NodeLockServiceImpl(new TaDOM3Plus(), SERVICE_NAME,
				MAX_LOCKS, MAX_TRANSACTIONS);
	}

	@After
	public void tearDown() throws Exception {
	}
}
