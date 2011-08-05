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

import org.apache.log4j.Logger;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.TxState;

/**
 * Server-side session object managing state of client connections. Caution:
 * This container class is not thread safe! Take care of correct usage!
 * 
 * @author Sebastian Baechle
 * 
 */
public final class Session {
	private static final Logger log = Logger.getLogger(Session.class);

	final TxMgr taMgr;

	final SessionID sessionID;

	private volatile boolean autoCommit = true;

	private IsolationLevel isolationLevel = IsolationLevel.SERIALIZABLE;

	private int lockDepth = 20;

	private Tx tx = null;

	private String currentDir = "/";

	private Thread joined;

	private volatile long ping = System.currentTimeMillis();

	public final static int MEMINFOSIZE = 150; // TODO: this value needs review

	// !!

	Session(TxMgr taMgr, SessionID connection) {
		this.taMgr = taMgr;
		this.sessionID = connection;
	}

	void ping() {
		this.ping = System.currentTimeMillis();
	}

	long getPing() {
		return ping;
	}

	void cleanup(boolean success, boolean forceEOT) throws SessionException {
		endTransaction(autoCommit || forceEOT, success);
	}

	/**
	 * Rollbacks the current tx associated with this session. Must only be
	 * called when the calling thread not already joined the tx.
	 * 
	 * @throws SessionException
	 */
	public void rollback() throws SessionException {
		endTransaction(true, false);
	}

	/**
	 * Commits the current tx associated with this session. Must only be called
	 * when the calling thread not already joined the tx.
	 * 
	 * @throws SessionException
	 */
	public void commit() throws SessionException {
		endTransaction(true, true);
	}

	public synchronized void begin(boolean readOnly) throws SessionException {
		beginTransaction(false, readOnly);
	}

	public boolean isAutoCommit() {
		return autoCommit;
	}

	public void setAutoCommit(boolean autoCommit) {
		this.autoCommit = autoCommit;
	}

	public synchronized IsolationLevel getIsolationLevel() {
		return isolationLevel;
	}

	public synchronized void setIsolationLevel(IsolationLevel isolationLevel) {
		this.isolationLevel = isolationLevel;
	}

	public synchronized int getLockDepth() {
		return lockDepth;
	}

	public synchronized void setLockDepth(int lockDepth) {
		this.lockDepth = lockDepth;
	}

	public synchronized Tx getTX() throws SessionException {
		if (tx == null) {
			beginTransaction(true, false);
		} else {
			if (!tx.join()) {
				throw new SessionException("Cannot join current tx.");
			}
		}

		return tx;
	}

	public synchronized String getCurrentDirectory() {
		return currentDir;
	}

	public synchronized void setCurrentDirectory(String currentDir) {
		this.currentDir = currentDir;
	}

	public synchronized SessionID getSessionID() {
		return sessionID;
	}

	public synchronized Tx checkTX() {
		return tx;
	}

	private synchronized void beginTransaction(boolean autocommit,
			boolean readOnly) throws SessionException {
		try {
			if (tx != null) {
				throw new SessionException("Session is already in a tx.");
			}

			tx = taMgr.begin(isolationLevel, this, readOnly);
			tx.setLockDepth(lockDepth);

			if (log.isDebugEnabled()) {
				log.debug(String.format("Started TX %s of session %s.", tx
						.getID(), sessionID));
			}
		} catch (TxException e) {
			throw new SessionException(e);
		}

		autoCommit = autocommit;
	}

	private void endTransaction(boolean finish, boolean commit)
			throws SessionException {
		if ((tx == null) || (tx.getState() != TxState.RUNNING) || (!tx.join())) {
			tx = null;
			return;
		}

		if (commit) {
			if (finish) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Committing TX %s of session %s.",
							tx.getID(), sessionID));
				}

				try {
					tx.commit();
				} catch (TxException e) {
					throw new SessionException(e);
				} finally {
					tx = null;
				}
			} else {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Leaving TX %s of session %s.", tx
							.getID(), sessionID));
				}

				try {
					tx.leave();
				} catch (TxException e) {
					throw new SessionException(e);
				}
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Rolling back TX %s of session %s.", tx
						.getID(), sessionID));
			}

			try {
				tx.rollback();
			} catch (TxException e) {
				throw new SessionException(e);
			} finally {
				tx = null;
			}
		}
	}

	@Override
	public String toString() {
		return this.sessionID + " current path:" + this.currentDir
				+ " last ping before: "
				+ (System.currentTimeMillis() - this.ping) + "ms"
				+ " autocommit=" + this.autoCommit;
	}
}