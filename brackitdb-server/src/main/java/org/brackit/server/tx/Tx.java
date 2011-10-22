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

import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.cache.CachedObjectUser;
import org.brackit.server.session.Session;
import org.brackit.server.tx.locking.LockControlBlock;
import org.brackit.server.tx.log.LogOperation;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public interface Tx extends CachedObjectUser {
	public TxID getID();

	public IsolationLevel getIsolationLevel();
	
	public Session getSession();

	public boolean isReadOnly();

	public void setLockDepth(int lockDepth);

	public int getLockDepth();

	public String toShortString();

	public String toString();

	public int getLockingScheme();

	public long checkPrevLSN();

	public TxState getState();

	public LockControlBlock getLockCB();

	public void undo(long savepointLSN) throws TxException;

	public void rollback() throws TxException;

	public void abort();

	public void commit() throws TxException;

	public long logUpdate(LogOperation logOperation) throws TxException;

	public long logDummyCLR(long undoNextLSN) throws TxException;

	public long logCLR(LogOperation logOperation, long undoNextLSN)
			throws TxException;

	public void addPreCommitHook(PreCommitHook hook);

	public void addPostCommitHook(PostCommitHook hook);

	public void leave() throws TxException;

	public boolean join();

	public BufferMgr getBufferManager();

	public void addFlushHook(int containerNo);

	public TxStats getStatistics();
}