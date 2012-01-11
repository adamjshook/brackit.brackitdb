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
package org.brackit.server.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.brackit.server.ServerException;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.xquery.util.Cfg;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SessionMgrTest {
	private TxMgr taMgr;

	private SessionMgr sm;

	@Test
	public void testCleanupStaleSessionsAfterTimeout() throws ServerException {
		List<Session> staleSessions = new ArrayList<Session>();
		List<Tx> staleTransactions = new ArrayList<Tx>();
		List<Session> activeSessions = new ArrayList<Session>();
		int maxConnections = Cfg.asInt(SessionMgr.MAX_CONNECTIONS,
				SessionMgr.DEFAULT_MAX_CONNECTIONS);

		for (int i = 0; i < maxConnections; i++) {
			final SessionID sessionID = sm.login();
			Session session = sm.getSession(sessionID);
			session.setAutoCommit(false);
			session.begin(false);
			Tx tx = session.getTX();
			staleSessions.add(session);
			staleTransactions.add(tx);
			sm.cleanup(sessionID, true, false);
		}

		try {
			Thread.sleep(Cfg.asInt(SessionMgr.CONNECTION_TIMEOUT,
					SessionMgr.DEFAULT_CONNECTION_TIMEOUT) + 10);
		} catch (InterruptedException e) {
		}

		for (int i = 0; i < maxConnections; i++) {
			final SessionID sessionID = sm.login();
			Session session = sm.getSession(sessionID);
			session.setAutoCommit(false);
			session.begin(false);
			Tx tx = session.getTX();
			activeSessions.add(session);
			sm.cleanup(sessionID, true, false);
		}

		// wait to ensure that asynchronous cleanups can complete
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}

		for (Session staleSession : staleSessions) {
			assertNull("Stale session has been removed", sm
					.getSession(staleSession.sessionID));
			assertNull("Stale session is not attached to a transaction",
					staleSession.checkTX());
		}

		for (Tx staleTransaction : staleTransactions) {
			assertEquals("Stale TX was rolledback", TxState.ROLLEDBACK,
					staleTransaction.getState());
		}

		for (Session activeSession : activeSessions) {
			assertNotNull("Session is still active", sm
					.getSession(activeSession.sessionID));
			assertNotNull("Session is still attached to a transaction",
					activeSession.checkTX());
			assertEquals("TX is still running", TxState.RUNNING, activeSession
					.checkTX().getState());
		}
	}

	@Test
	public void testConcurrentlyKillRunningSessionManualCommit()
			throws ServerException {
		concurrentlyKillRunningSession(false, true);
	}

	@Test
	public void testConcurrentlyKillRunningSessionAutoCommit()
			throws ServerException {
		concurrentlyKillRunningSession(true, true);
	}

	@Test
	public void testConcurrentlyKillRunningFailedSessionManualCommit()
			throws ServerException {
		concurrentlyKillRunningSession(false, false);
	}

	@Test
	public void testConcurrentlyKillRunningFailedSessionAutoCommit()
			throws ServerException {
		concurrentlyKillRunningSession(true, false);
	}

	private void concurrentlyKillRunningSession(boolean autoCommit,
			boolean successful) throws SessionException {
		final AtomicBoolean sync = new AtomicBoolean(false);
		final SessionID sessionID = sm.login();
		sm.getSession(sessionID).setAutoCommit(autoCommit);
		if (!autoCommit) {
			sm.getSession(sessionID).begin(false);
		}
		Tx tx = sm.getSession(sessionID).getTX();

		Thread killer = new Thread() {
			@Override
			public void run() {
				while (!sync.get()) {
					try {
						Thread.sleep(40);
					} catch (InterruptedException e) {
					}
				}

				System.out.println("Attempting");
				try {
					sm.cleanup(sessionID, false, false);
				} catch (SessionException e) {
					e.printStackTrace();
				}
				System.out.println("Success");
			}
		};
		killer.start();
		sync.set(true);

		// pretend to do some work
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}
		try {
			sm.cleanup(sessionID, successful, false);
			if (successful) {
				fail("Transaction must not successfully cleanup");
			}
		} catch (Exception e) {
			if (!successful) {
				fail("Cleanup after fail must not fail.");
			}
		}
		assertNull("Session not bound to a transaction", sm.getSession(
				sessionID).checkTX());
		sm.logout(sessionID);
		assertNull("Session was removed", sm.getSession(sessionID));
		assertEquals("TX was rolledback", TxState.ROLLEDBACK, tx.getState());
	}

	@Test
	public void testLoginLogoutAutoCommit() throws ServerException {
		loginLogoutCycle(true, true);
	}

	@Test
	public void testLoginLogoutManualCommit() throws ServerException {
		loginLogoutCycle(false, true);
	}

	@Test
	public void testLoginLogoutFailedAutoCommit() throws ServerException {
		loginLogoutCycle(true, false);
	}

	@Test
	public void testLoginLogoutFailedManualCommit() throws ServerException {
		loginLogoutCycle(false, false);
	}

	private void loginLogoutCycle(boolean autoCommit, boolean successful)
			throws SessionException {
		SessionID sessionID = sm.login();
		sm.getSession(sessionID).setAutoCommit(autoCommit);
		Tx tx = sm.getSession(sessionID).getTX();
		sm.cleanup(sessionID, successful, false);
		sm.logout(sessionID);
		assertNull("Session was removed", sm.getSession(sessionID));

		if (successful) {
			assertEquals("TX was committed", TxState.COMMITTED, tx.getState());
		} else {
			assertEquals("TX was rolledback", TxState.ROLLEDBACK, tx.getState());
		}
	}

	@Before
	public void setUp() {
		taMgr = new TaMgrMockup();
		Cfg.set(SessionMgr.MAX_CONNECTIONS, 10);
		Cfg.set(SessionMgr.CONNECTION_TIMEOUT, 100);
		sm = new SessionMgrImpl(taMgr);
	}
}
