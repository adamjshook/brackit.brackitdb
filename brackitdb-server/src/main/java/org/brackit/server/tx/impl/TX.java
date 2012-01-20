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
package org.brackit.server.tx.impl;

import java.util.ArrayList;
import java.util.Collection;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.cache.CachedObjectHook;
import org.brackit.server.session.Session;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.server.tx.PostCommitHook;
import org.brackit.server.tx.PreCommitHook;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxID;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.TxStats;
import org.brackit.server.tx.locking.LockControlBlock;
import org.brackit.server.tx.locking.services.LockServiceClient;
import org.brackit.server.tx.log.Log;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.Loggable;
import org.brackit.server.util.IntList;

public class TX extends TxControlBlock implements org.brackit.server.tx.Tx {
	private static final Logger log = Logger.getLogger(TX.class.getName());

	private static final int MAX_UNDO_RETRIES = 3;

	protected final TxID txID;

	protected final long startTime;

	protected final boolean readOnly;

	// != null if transaction is
	// started from a connection
	protected final Session session;

	protected final IsolationLevel isolationLevel;

	protected final LockControlBlock lcb;

	protected int lockDepth = 20;

	protected long minUndoLSN = -1;

	protected long prevLSN = -1;

	protected final TaMgrImpl taMgr;

	private final Collection<CachedObjectHook> cacheHooks;

	private final Collection<PreCommitHook> preHooks;

	private final Collection<PostCommitHook> postHooks;

	private FlushBufferHook flushHook;

	private TxStats statistics = null;

	private final class FlushBufferHook implements PreCommitHook {
		private IntList list;

		public FlushBufferHook() {
			this.list = new IntList(3);
		}

		@Override
		public void abort(Tx transaction) throws ServerException {

		}

		@Override
		public void prepare(Tx transaction) throws ServerException {
			for (int containerNo : list.toArray()) {
				Buffer buffer = taMgr.getBufferManager().getBuffer(containerNo);
				buffer.flushAssigned(transaction);
			}
		}

		void addContainer(int containerNo) {
			if (!list.contains(containerNo)) {
				list.append(containerNo);
			}
		}
	}

	TX(TaMgrImpl taMgr, TxID txID, IsolationLevel isolationLevel,
			boolean readOnly, Session session, long timeout) {
		this.taMgr = taMgr;
		this.txID = txID;
		this.isolationLevel = isolationLevel;
		this.readOnly = readOnly;
		this.session = session;
		this.startTime = System.currentTimeMillis();
		this.lcb = new LockControlBlock(this, timeout);
		this.preHooks = new ArrayList<PreCommitHook>(4);
		this.postHooks = new ArrayList<PostCommitHook>(4);
		this.cacheHooks = new ArrayList<CachedObjectHook>(4);
		this.statistics = new TxStats();
	}

	@Override
	public void addHook(CachedObjectHook hook) {
		cacheHooks.add(hook);
	}

	@Override
	public void abort() {
		if (getState().isActive()) {
			setState(TxState.ABORTED);
		}
	}

	@Override
	public void commit() throws TxException {
		try {
			int TLSN = 0;
			boolean doCommit = voteCommit();

			if (doCommit) {
				doCommit();
			} else {
				waitEOT();

				if (getState() != TxState.COMMITTED) {
					throw new TxException("Commit failed.");
				}
			}
		} finally {
			// TODO perform the after hooks asynchronously
			for (PostCommitHook postHook : postHooks) {
				Tx postTX = taMgr.begin(IsolationLevel.SERIALIZABLE, null,
						false);
				try {
					postHook.execute(postTX);
					postTX.commit();
				} catch (ServerException e) {
					log.error(String.format(
							"Post commit hook %s for tx %s failed: %s",
							postHook, this, e.getMessage()), e);
					postTX.rollback();
				}
			}
		}
	}

	@Override
	public void rollback() throws TxException {
		if (getState() != TxState.RUNNING) {
			throw new TxException("Rollback failed: Tx %s is in state %s", txID, getState());
		}
		boolean processRollback = voteRollback();

		if (processRollback) {
			doRollback();
		} else {
			waitEOT();

			if (getState() != TxState.ROLLEDBACK) {
				throw new TxException("Rollback failed.");
			}
		}
	}

	private void doCommit() throws TxException {
		if (getState() != TxState.RUNNING) {
			throw new TxException("Commit failed: Tx %s is in state %s", txID, getState());
		}
		try {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Commit of %s started.", toString()));
			}

			for (PreCommitHook hook : preHooks) {
				hook.prepare(this);
			}

			if (!readOnly) {
				long commitLsn = logEOT();
				taMgr.getLog().flush(commitLsn);
			}

			for (LockServiceClient lockServiceClient : lcb
					.getLockServiceClients()) {
				lockServiceClient.freeResources();
			}

			signalEOT(true);

			taMgr.getTxTable().remove(txID);
		} catch (ServerException e) {
			log.error(String.format(
					"Prepare of resource managers for commit failed. "
							+ "Starting rollback.", toString()), e);

			doRollback();
			throw new TxException(
					e,
					"Prepare of resource managers for commit failed. The transaction was rolled back.",
					toString());
		}
	}

	private void doRollback() throws TxException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Rollback of %s started.", toString()));
		}

		setState(TxState.ROLLBACK);

		for (PreCommitHook hook : preHooks) {
			try {
				hook.abort(this);
			} catch (ServerException e) {
				log.error(String.format(
						"Abort of %s failed for resource manager %s.",
						toString(), hook), e);
			}
		}

		// first process log buffer and write compensation log file if the
		// transaction is not read-only
		if (!readOnly) {
			try {
				// undo the operations done by transaction
				undo(-1);
			} catch (TxException e) {
				log.error(String.format("Undo of %s failed.", toString()), e);
			}

			try {
				long commitLsn = logEOT();
			} catch (TxException e) {
				if (log.isDebugEnabled()) {
					log.debug(
							String.format(
									"Writing abort EOT log for %s failed. Starting rollback.",
									toString()), e);
				}
			}
		}

		for (LockServiceClient lockServiceClient : lcb.getLockServiceClients()) {
			lockServiceClient.freeResources();
		}

		signalEOT(false);

		// remove transaction control block
		taMgr.getTxTable().remove(txID);

		if (log.isDebugEnabled()) {
			log.debug(String.format("Transaction %s rolled back.", toString()));
		}
	}

	public TxID getID() {
		return this.txID;
	}

	public boolean isReadOnly() {
		return this.readOnly;
	}

	public IsolationLevel getIsolationLevel() {
		return this.isolationLevel;
	}
	
	public Session getSession() {
		return this.session;
	}

	public int getLockDepth() {
		return this.lockDepth;
	}

	public void setLockDepth(int lockDepth) {
		this.lockDepth = lockDepth;
	}

	public int getLockingScheme() {
		return 0;
	}

	public long logEOT() throws TxException {
		if (prevLSN != -1) {
			Loggable loggable = taMgr.getLog().getLoggableHelper()
					.createEOT(txID, prevLSN);
			long LSN = log(loggable, true);

			if (log.isDebugEnabled()) {
				log.debug(String.format("%s logged EOT %s.", toShortString(),
						LSN));
			}

			return LSN;
		} else {
			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"%s skipping log EOT because it is not necessary.",
						toShortString()));
			}

			return -1;
		}
	}

	public long logUpdate(LogOperation logOperation) throws TxException {
		Loggable loggable = taMgr.getLog().getLoggableHelper()
				.createUpdate(txID, prevLSN, logOperation);
		long LSN = log(loggable, true);

		if (log.isDebugEnabled()) {
			log.debug(String.format("%s logged UPDATE %s for %s.",
					toShortString(), LSN, logOperation));
		}

		return LSN;
	}

	public long logDummyCLR(long undoNextLSN) throws TxException {
		Loggable loggable = taMgr.getLog().getLoggableHelper()
				.createDummyCLR(txID, prevLSN, undoNextLSN);
		long LSN = log(loggable, true);

		if (log.isDebugEnabled()) {
			log.debug(String.format(
					"%s logged DUMMYCLR %s pointing to nextUndoLSN %s.",
					toShortString(), LSN, undoNextLSN));
		}

		return LSN;
	}

	public long logCLR(LogOperation logOperation, long undoNextLSN)
			throws TxException {
		Loggable loggable = taMgr.getLog().getLoggableHelper()
				.createCLR(txID, prevLSN, logOperation, undoNextLSN);
		long LSN = log(loggable, false);

		if (log.isDebugEnabled()) {
			log.debug(String.format(
					"%s logged CLR %s for %s pointing to nextUndoLSN %s.",
					toShortString(), LSN, logOperation, undoNextLSN));
		}

		return LSN;
	}

	public long logUpdateSpecial(LogOperation logOperation, long undoNextLSN)
			throws TxException {
		Loggable loggable = taMgr.getLog().getLoggableHelper()
				.createUpdateSpecial(txID, prevLSN, logOperation, undoNextLSN);
		long LSN = log(loggable, true);

		if (log.isDebugEnabled()) {
			log.debug(String.format(
					"%s logged CLR %s for %s pointing to nextUndoLSN %s.",
					toShortString(), LSN, logOperation, undoNextLSN));
		}

		return LSN;
	}

	public long log(Loggable loggable, boolean updatePrevLSN)
			throws TxException {
		if (readOnly) {
			throw new TxException("%s is readonly.", this);
		}

		try {
			long LSN = taMgr.getLog().append(loggable);

			if (updatePrevLSN) {
				if (prevLSN == -1) {
					minUndoLSN = LSN;
				}

				prevLSN = LSN;
			}

			return LSN;
		} catch (LogException e) {
			log.error(
					String.format("Could not write log record '%s'.", loggable),
					e);
			throw new TxException(e, "Could not write log record '%s'.",
					loggable);
		}
	}

	public void undo(long checkPointLSN) throws TxException {
		Log transactionLog = taMgr.getLog();
		Loggable record = null;

		while ((prevLSN >= checkPointLSN) && (prevLSN > -1)) {
			for (int retry = 0; retry < MAX_UNDO_RETRIES; retry++) {
				try {
					record = transactionLog.get(prevLSN);
					prevLSN = undo(record);
					break;
				} catch (LogException e) {
					log.error(String.format("Undo of %s failed", record), e);

					if (retry == MAX_UNDO_RETRIES) {
						throw new TxException(e,
								"Undo of LSN %s failed (Retried %s times).",
								prevLSN, retry);
					}
				}
			}
		}
	}

	long undo(Loggable record) throws LogException {
		long nextUndoLSN;
		LogOperation logOperation = null;
		switch (record.getType()) {
		case Loggable.TYPE_UPDATE:
			if (log.isDebugEnabled()) {
				log.debug(String.format("%s undo %s.", toShortString(), record
						.getLSN()));
			}

			logOperation = record.getLogOperation();
			nextUndoLSN = record.getPrevLSN();
			logOperation.undo(this, record.getLSN(), nextUndoLSN);
			break;
		case Loggable.TYPE_UPDATE_SPECIAL:
			if (log.isDebugEnabled()) {
				log.debug(String.format("%s undo %s.", toShortString(),
						record.getLSN()));
			}

			logOperation = record.getLogOperation();
			nextUndoLSN = record.getUndoNextLSN();
			logOperation.undo(this, record.getLSN(), nextUndoLSN);
			break;
		case Loggable.TYPE_DUMMY:
		case Loggable.TYPE_CLR:
			nextUndoLSN = record.getUndoNextLSN();
			break;
		default:
			nextUndoLSN = record.getPrevLSN();
		}
		return nextUndoLSN;
	}

	public long checkPrevLSN() {
		return prevLSN;
	}

	void setPrevLSN(long prevLSN) {
		this.prevLSN = prevLSN;
	}

	public long checkMinUndoLSN() {
		return minUndoLSN;
	}

	@Override
	public LockControlBlock getLockCB() {
		return lcb;
	}

	public TxMgr getTaManager() {
		return this.taMgr;
	}

	public BufferMgr getBufferManager() {
		return this.taMgr.getBufferManager();
	}

	public void addPreCommitHook(PreCommitHook hook) {
		preHooks.add(hook);
	}

	public void addPostCommitHook(PostCommitHook hook) {
		postHooks.add(hook);
	}

	public void addFlushHook(int containerNo) {
		if (flushHook == null) {
			flushHook = new FlushBufferHook();
			preHooks.add(flushHook);
		}

		flushHook.addContainer(containerNo);
	}

	public Collection<PreCommitHook> getPreCommitHooks() {
		return preHooks;
	}

	public String toShortString() {
		return String.format("TX[ID=%s]", txID);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (obj instanceof TX) {
			return txID.equals(((TX) obj).txID);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return txID.hashCode();
	}

	@Override
	public String toString() {
		return String.format("TX[ID=%s, CID=%s, IL=%s, LD=%s, RO=%s]", txID,
				session, isolationLevel, lockDepth, readOnly);
	}

	@Override
	public TxStats getStatistics() {
		return this.statistics;
	}
}