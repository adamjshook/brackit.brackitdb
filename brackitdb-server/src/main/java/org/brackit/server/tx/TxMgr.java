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
package org.brackit.server.tx;

import java.util.Collection;

import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.session.Session;
import org.brackit.server.tx.impl.TX;
import org.brackit.server.tx.log.Log;

public interface TxMgr {

	public final static String PATH_LOCK_VERSION = "org.brackit.server.tx.txMgr.pathLockVersion";
	public final static String MAX_TX = "org.brackit.server.tx.txMgr.maxTx";
	public static final String MAX_LOCKS = "org.brackit.server.tx.txMgr.maxLocks";
	public static final String MAX_TX_RUNTIME = "org.brackit.server.tx.txMgr.maxTxRuntime";
	public static final String DEADLOCK_DETECTION_INTERVAL = "org.brackit.server.tx.txMgr.deadlockDetectionInterval";
	public static final String LOG_DEADLOCKS = "org.brackit.server.tx.txMgr.logDeadlocks";
	public static final String LOCK_WAIT_TIMEOUT = "org.brackit.server.tx.txMgr.lockWaitTimeout";
	public static final String DEADLOCK_LOG_DIR = "org.brackit.server.tx.txMgr.deadlockLogDir";

	public static final int DEFAULT_MAX_TX = 50;
	public static final int DEFAULT_MAX_LOCKS = 200000;
	public static final int DEFAULT_LOCK_WAIT_TIMEOUT = 20000;

	public Log getLog();

	public void shutdown() throws TxException;

	public void recover() throws TxException;

	public Tx begin() throws TxException;

	public Tx begin(IsolationLevel isolationLevel, Session session,
			boolean readOnly) throws TxException;

	public Collection<TX> getTransactions();

	/**
	 * Write a checkpoint to speed up crash recovery and to safe log space.
	 * 
	 * @throws TxException
	 */
	public void checkpoint() throws TxException;

	/**
	 * Returns the system buffer manager.
	 * 
	 * @return
	 */
	public BufferMgr getBufferManager();
}