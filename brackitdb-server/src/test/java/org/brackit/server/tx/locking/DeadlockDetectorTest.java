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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.tx.locking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.brackit.server.ServerException;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.server.tx.locking.services.LockService;
import org.brackit.server.tx.locking.services.LockServiceClient;
import org.junit.Before;
import org.junit.Test;

public class DeadlockDetectorTest {
	private TxMgr taMgr;

	private LockServiceMockup ls;

	private DeadlockDetector detector;

	private class LockServiceMockup implements LockService {

		@Override
		public String getName() {
			return null;
		}

		@Override
		public String listLocks() {
			return null;
		}

		@Override
		public String toString() {
			return LockServiceMockup.class.getSimpleName();
		}
	}

	private class LockServiceMockupClient implements LockServiceClient {
		private List<Blocking> blockedAt = new ArrayList<Blocking>();

		private final LockService ls;

		public LockServiceMockupClient(LockService ls) {
			super();
			this.ls = ls;
		}

		public void addBlockedRequest(Object object, Tx requestor,
				LockClass lockClass, Tx blocker) {
			blockedAt.add(new Blocking(object, lockClass, requestor, blocker));
		}

		@Override
		public Collection<Blocking> blockedAt() {
			return blockedAt;
		}

		@Override
		public void freeResources() {
		}

		@Override
		public List<XTClock> getLocks() {
			return null;
		}

		@Override
		public LockServiceClientCB getLockServiceCB() {
			return null;
		}

		@Override
		public String listLocks() {
			return null;
		}

		@Override
		public void unblock() {
		}

		@Override
		public String toString() {
			return ls.toString();
		}
	}

	@Test
	public void testListWFGNoDeadlock2TA() throws ServerException {
		Tx t1 = newTa();
		Tx t2 = newTa();
		newWaitFor(t1, t2, "A");
		System.out.println(detector.listWaitForGraph());
	}

	@Test
	public void testListWFGNoDeadlock5TA() throws ServerException {
		Tx t1 = newTa();
		Tx t2 = newTa();
		Tx t3 = newTa();
		Tx t4 = newTa();
		Tx t5 = newTa();
		newWaitFor(t1, t2, "A");
		newWaitFor(t2, t3, "B");
		newWaitFor(t1, t3, "B");
		newWaitFor(t3, t4, "C");
		newWaitFor(t5, t1, "A");
		System.out.println(detector.listWaitForGraph());
	}

	@Test
	public void testListWFGNoDeadlock6TA() throws ServerException {
		Tx t1 = newTa();
		Tx t2 = newTa();
		Tx t3 = newTa();
		Tx t4 = newTa();
		Tx t5 = newTa();
		Tx t6 = newTa();
		newWaitFor(t1, t2, "A");
		newWaitFor(t2, t3, "B");
		newWaitFor(t1, t3, "B");
		newWaitFor(t3, t4, "C");
		newWaitFor(t3, t6, "C");
		newWaitFor(t5, t1, "A");
		System.out.println(detector.listWaitForGraph());
	}

	@Test
	public void testListWFGDeadlock6TA() throws ServerException {
		Tx t1 = newTa();
		Tx t2 = newTa();
		Tx t3 = newTa();
		Tx t4 = newTa();
		Tx t5 = newTa();
		Tx t6 = newTa();
		newWaitFor(t1, t2, "A");
		newWaitFor(t2, t3, "B");
		newWaitFor(t1, t3, "B");
		newWaitFor(t3, t4, "C");
		newWaitFor(t3, t1, "D");
		newWaitFor(t3, t6, "C");
		newWaitFor(t5, t1, "A");
		newWaitFor(t6, t1, "D");
		System.out.println(detector.listWaitForGraph());
	}

	private void newWaitFor(Tx requestor, Tx blocker, Object object) {
		((LockServiceMockupClient) requestor.getLockCB().get(ls))
				.addBlockedRequest(object, requestor,
						LockClass.COMMIT_DURATION, blocker);
	}

	private Tx newTa() throws TxException {
		Tx transaction = taMgr.begin();
		transaction.getLockCB().add(ls, new LockServiceMockupClient(ls));
		return transaction;
	}

	@Before
	public void setup() {
		taMgr = new TaMgrMockup();
		ls = new LockServiceMockup();
		detector = new DeadlockDetector(taMgr, true);
	}
}
