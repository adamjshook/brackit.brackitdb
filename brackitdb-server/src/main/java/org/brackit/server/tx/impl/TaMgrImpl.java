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
package org.brackit.server.tx.impl;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.session.SessionID;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxID;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.log.Log;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.Loggable;
import org.brackit.xquery.util.Cfg;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class TaMgrImpl implements TxMgr {
	private static final Logger log = Logger.getLogger(TaMgrImpl.class
			.getName());

	protected final Log txLog;

	protected final BufferMgr bufferMgr;

	protected final TxPatrol patrol;

	protected final TxTable txTable;

	protected final int maxTransactions;

	protected final AtomicLong TSNSequence;

	protected int timeout = 10000;

	protected long maxTransactionRuntime = 90000;

	public TaMgrImpl(Log transactionLog, BufferMgr bufferMgr) {
		log.info("Initializing transaction manager.");

		this.txLog = transactionLog;
		this.bufferMgr = bufferMgr;
		this.txTable = new TxTable(this);
		this.TSNSequence = new AtomicLong(0);

		maxTransactions = Cfg.asInt(TxMgr.MAX_TX, 100);
		timeout = Cfg.asInt(TxMgr.LOCK_WAIT_TIMEOUT, 20000);

		this.maxTransactionRuntime = Cfg.asLong(TxMgr.MAX_TX_RUNTIME, 900000);
		this.patrol = new TxPatrol(this, Cfg.asInt(
				TxMgr.DEADLOCK_DETECTION_INTERVAL, 300));
		this.patrol.start();

		log.info("Transaction manager initialized.");
	}

	public BufferMgr getBufferManager() {
		return bufferMgr;
	}

	public TxTable getTxTable() {
		return txTable;
	}

	public Log getLog() {
		return txLog;
	}

	public void recover() throws TxException {
		restart();
	}

	private void restart() throws LogException, TxException {
		txTable.clear();

		if (log.isInfoEnabled()) {
			log.info("Starting Redo pass.");
		}

		/*
		 * Analysis/Redo pass.
		 */
		long currentEndOfLog = txLog.getNextLSN();

		for (Loggable loggable = txLog.first(); (loggable != null)
				&& (loggable.getLSN() < currentEndOfLog); loggable = txLog
				.next(loggable)) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Read loggable %s of %s type %s.",
						loggable.getLSN(), loggable.getTxID(), loggable
								.getType()));
			}

			TX tx = txTable.get(loggable.getTxID());

			if (tx == null) {
				tx = new TX(this, loggable.getTxID(),
						IsolationLevel.SERIALIZABLE, false, null, timeout);

				if (log.isDebugEnabled()) {
					log.debug(String.format(
							"Resurrecting transaction %s for redo.", tx));
				}

				txTable.put(loggable.getTxID(), tx);
			}

			LogOperation logOp = loggable.getLogOperation();

			switch (loggable.getType()) {
			case Loggable.TYPE_EOT:
				if (log.isDebugEnabled()) {
					log.debug(String.format("Finishing %s.", tx));
				}

				txTable.remove(loggable.getTxID());
				break;
			case Loggable.TYPE_UPDATE:
				tx.setPrevLSN(loggable.getLSN());

				if (log.isDebugEnabled()) {
					log.debug(String.format(
							"Performing Redo of UPDATE %s by TX %s: %s",
							loggable.getLSN(), tx.getID(), logOp));
				}

				logOp.redo(tx, loggable.getLSN());
				break;
			case Loggable.TYPE_CLR:
				tx.setPrevLSN(loggable.getUndoNextLSN());

				if (log.isDebugEnabled()) {
					log.debug(String.format("Performing Redo of CLR %s: %s",
							loggable.getLSN(), logOp));
				}

				logOp.redo(tx, loggable.getLSN());
				break;
			case Loggable.TYPE_UPDATE_SPECIAL:
				tx.setPrevLSN(loggable.getUndoNextLSN());

				if (log.isDebugEnabled()) {
					log.debug(String.format("Performing Redo of UPDATE SPECIAL %s: %s",
							loggable.getLSN(), logOp));
				}

				logOp.redo(tx, loggable.getLSN());
				break;
			case Loggable.TYPE_DUMMY:
				tx.setPrevLSN(loggable.getUndoNextLSN());
				break;
			default:
				tx.setPrevLSN(loggable.getPrevLSN());
			}
		}

		if (log.isDebugEnabled()) {
			log.debug("Updating TxID sequence with maximum redo TxID.");
		}

		TxID maxTxID = txTable.getMaxTxID();
		TSNSequence.set((maxTxID != null) ? maxTxID.longValue() : 0);

		if (log.isDebugEnabled()) {
			log.debug("Writing missing ROLLBACK for all "
					+ "losers that have nothing to undo.");
		}

		for (TX transaction : txTable.getRolledbackLosers()) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Finalizing rolled back loser %s.",
						transaction));
			}

			transaction.logEOT();
			txTable.remove(transaction.getID());
		}

		if (log.isInfoEnabled()) {
			log.info("Starting Undo pass.");
		}

		/*
		 * Undo pass.
		 */
		for (TX tx = txTable.getNextTransactionForUndo(); tx != null; tx = txTable
				.getNextTransactionForUndo()) {
			Loggable loggable = txLog.get(tx.checkPrevLSN());

			if (log.isDebugEnabled()) {
				log.debug(String.format("Performing UNDO of %s by TX %s: %s",
						loggable.getLSN(), tx.getID(), loggable
								.getLogOperation()));
			}

			long nextUndoLSN = tx.undo(loggable);

			if (nextUndoLSN == -1) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Finalizing rollback of loser %s.",
							tx));
				}

				tx.logEOT();
				txTable.remove(loggable.getTxID());
			} else {
				tx.setPrevLSN(nextUndoLSN);
			}
		}

		/*
		 * Force changes to stable storage and cleanup
		 */
		if (log.isInfoEnabled()) {
			log.info("Flushing buffers and writing a checkpoint.");
		}

		for (Buffer buffer : bufferMgr.getBuffers()) {
			try {
				buffer.flush();
			} catch (BufferException e) {
				log.error(String.format("Flush of buffer %s failed.", buffer),
						e);
			}
		}

		checkpoint();
	}

	public TX begin() throws TxException {
		return begin(IsolationLevel.SERIALIZABLE, null, false);
	}

	public TX begin(IsolationLevel isolationLevel, SessionID connectionID,
			boolean readOnly) throws TxException {
		long nextTSN = TSNSequence.incrementAndGet();
		TxID txID = new TxID(nextTSN);
		TX transaction = new TX(this, txID, isolationLevel, readOnly,
				connectionID, timeout);
		txTable.put(txID, transaction);
		transaction.join();

		if (log.isDebugEnabled()) {
			log.debug(String.format("Transaction %s started.", transaction));
		}

		return transaction;
	}

	public Collection<TX> getTransactions() {
		return txTable.getTransactions();
	}

	public void shutdown() throws TxException {
		log.info("Shutting down transaction manager.");

		for (Buffer buffer : bufferMgr.getBuffers()) {
			try {
				buffer.flush();
			} catch (BufferException e) {
				log
						.warn(String.format("Flush of buffer %s failed.",
								buffer), e);
			}
		}

		if (this.patrol != null) {
			patrol.terminate();
		}

		checkpoint();

		log.info("Transaction manager shut down.");
	}

	/**
	 * The checkpoint is done by a simple log truncation. After the truncation,
	 * the log will still contain all entries required to a) redo all changes to
	 * current dirty pages, and b) undo all changes of the currently running
	 * transactions.
	 */
	@Override
	public void checkpoint() throws TxException {
		long minUndoLSN = Long.MAX_VALUE;
		long minRedoLSN = Long.MAX_VALUE;

		minRedoLSN = bufferMgr.checkMinRedoLSN();

		for (TX transaction : getTransactions()) {
			minUndoLSN = Math.min(transaction.checkMinUndoLSN(), minUndoLSN);
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format(
					"Minimum undo LSN from transaction table: %s", minUndoLSN));
			log.debug(String.format("Minimum redo LSN from buffer manager: %s",
					minRedoLSN));
		}

		long minLSN = Math.min(minUndoLSN, minRedoLSN);

		if (log.isInfoEnabled()) {
			log.info(String.format(
					"Creating a checkpoint and trunk log to LSN %s", minLSN));
		}

		try {
			bufferMgr.syncAll();
		} catch (BufferException e) {
			log.error("Error syncing buffers for checkpoint", e);
			throw new TxException(e, "Error truncating log to LSN %s.", minLSN);
		}

		try {
			txLog.truncateTo(minLSN);
		} catch (LogException e) {
			log.error(String.format("Error truncating log "
					+ "to LSN %s for checkpoint"), e);
			throw new TxException(e, "Error truncating log to LSN %s.", minLSN);
		}
	}
}