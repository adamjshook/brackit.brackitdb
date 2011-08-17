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
package org.brackit.server.session;

import java.util.Random;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.procedure.InfoContributor;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.server.procedure.statistics.ListConnections;
import org.brackit.server.session.async.Job;
import org.brackit.server.session.async.JobScheduler;
import org.brackit.server.tx.TxMgr;
import org.brackit.xquery.util.Cfg;

/**
 * @author Sebastian Baechle
 * 
 */
public final class SessionMgrImpl implements SessionMgr, InfoContributor {
	private static final Logger log = Logger.getLogger(SessionMgrImpl.class);

	private static final int MAX_KEY = 9999999;

	private TxMgr taMgr;

	private JobScheduler scheduler;

	private Session[] sessions;

	private Random keySource = new Random();

	private int maxConnections;

	private int connectionTimeout;

	private class LogoutJob extends Job {
		private Session session;

		LogoutJob(Session session) {
			this.session = session;
		}

		@Override
		public void doWork() throws Throwable {
			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Starting asynchronous cleanup of session %s.",
						session.sessionID));
			}
			session.cleanup(false, true);
			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Finished asynchronous cleanup of session %s.",
						session.sessionID));
			}
		}
	}

	public SessionMgrImpl(TxMgr taMgr) {
		this.taMgr = taMgr;
		this.scheduler = new JobScheduler();
		maxConnections = Cfg.asInt(MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
		connectionTimeout = Cfg.asInt(CONNECTION_TIMEOUT,
				DEFAULT_CONNECTION_TIMEOUT);
		sessions = new Session[maxConnections];
		ProcedureUtil.register(ListConnections.class, this);
	}

	@Override
	public Session getSession(SessionID sessionID) throws SessionException {
		if ((sessionID == null) || (sessionID.ID < 0)
				|| (sessionID.ID >= sessions.length)) {
			log.warn(String.format("Invalid connection ID %s", sessionID));
			throw new SessionException("Invalid connection ID");
		}

		return getSessionInternal(sessionID);
	}

	@Override
	public boolean alive(SessionID sessionID) {
		if (sessionID == null)
			return false;

		synchronized (this.sessions) {
			return ((sessions[sessionID.ID] != null) && (sessions[sessionID.ID].sessionID.key == sessionID.key));
		}
	}

	@Override
	public void ping(SessionID sessionID) {
		synchronized (sessions) {
			if ((sessions[sessionID.ID] != null)
					&& (sessions[sessionID.ID].sessionID.key == sessionID.key)) {
				sessions[sessionID.ID].ping();
			}
		}
	}

	@Override
	public synchronized void shutdown() {
		ProcedureUtil.deregister(ListConnections.class, this);
		synchronized (sessions) {
			for (Session session : sessions) {
				if (session != null) {
					logout(session.sessionID);
				}
			}
		}
	}

	@Override
	public void clearStaleConnections() {
		synchronized (sessions) {
			long now = System.currentTimeMillis();

			for (Session session : sessions) {
				if (session != null) {
					if (now - session.getPing() > connectionTimeout) {
						logout(session.sessionID);
					}
				}
			}
		}
	}

	private Session newConnection() throws SessionException {
		Session session = null;

		synchronized (sessions) {
			for (int ID = 0; ID < maxConnections; ID++) {
				if (sessions[ID] == null) {
					session = createSession(ID);
					break;
				}
			}

			if (session == null) {
				if ((session = clearStale()) == null) {
					log.error(String.format(
							"Maximum number of %s connections reached",
							maxConnections));
					throw new SessionException(
							"Maximum number of connections reached");
				}
			}
		}

		return session;
	}

	private Session createSession(int ID) {
		Session session;
		int key = keySource.nextInt(MAX_KEY);

		SessionID sessionID = new SessionID(ID, key);
		session = new Session(taMgr, sessionID);
		sessions[ID] = session;

		if (log.isDebugEnabled()) {
			log.debug(String
					.format("Started new session %s", session.sessionID));
		}

		return session;
	}

	private Session clearStale() throws SessionException {
		long now = System.currentTimeMillis();

		for (int i = 0; i < sessions.length; i++) {
			Session session = sessions[i];

			if ((session != null)
					&& (now - session.getPing() >= connectionTimeout)) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Cleaning stale session %s",
							session.sessionID));
				}
				scheduler.schedule(new LogoutJob(session), false);
				session = createSession(i);
				return session;
			}
		}

		return null;
	}

	@Override
	public SessionID login() throws SessionException {
		// TODO implement full fledged authentication and authorization
		SessionID connID = newConnection().sessionID;
		return connID;
	}

	@Override
	public void logout(SessionID sessionID) {
		synchronized (sessions) {
			Session session = getSessionInternal(sessionID);

			if ((session != null) && (session.checkTX() != null)) {
				scheduler.schedule(new LogoutJob(session), false);
			}

			sessions[sessionID.ID] = null;
		}
	}

	private Session getSessionInternal(SessionID sessionID) {
		synchronized (sessions) {
			Session session = sessions[sessionID.ID];
			return ((session != null) && (session.sessionID.equals(sessionID))) ? session
					: null;
		}
	}

	@Override
	public void prepare(SessionID sessionID) throws SessionException {
		if (!alive(sessionID)) {
			throw new SessionException("Not connected.");
		}
	}

	@Override
	public void cleanup(SessionID sessionID, boolean success, boolean forceEOT)
			throws SessionException {
		Session session = null;

		synchronized (sessions) {
			if (sessions[sessionID.ID] != null)
				session = sessions[sessionID.ID];
		}

		if (session != null) {
			session.cleanup(success, forceEOT);
		}
	}

	@Override
	public String getInfo() {
		StringBuffer connections = new StringBuffer();
		String tableFormat = "| %10s | %10s | %10s | %5s | %s";
		connections.append(String.format(tableFormat, "ID", "last alive",
				"auto commit", "in TX", "Current Dir"));
		long now = System.currentTimeMillis();

		synchronized (sessions) {
			for (Session session : sessions) {
				if (session != null) {
					connections.append("\n");
					String ping = Long.toString(now - session.getPing());
					String ac = session.isAutoCommit() ? "1" : "0";
					String inTX = (session.checkTX() != null) ? "1" : "0";
					String cd = session.getCurrentDirectory();
					connections.append(String.format(tableFormat,
							session.sessionID, ping, ac, inTX, cd));
				}
			}
		}

		return connections.toString();
	}

	@Override
	public int getInfoID() {
		return InfoContributor.NO_ID;
	}
}