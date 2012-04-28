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
package org.brackit.server.tx;

import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.cache.CachedObjectHook;
import org.brackit.server.session.Session;
import org.brackit.server.tx.locking.LockControlBlock;
import org.brackit.server.tx.log.LogOperation;

/**
 * @author Sebastian Baechle
 * 
 */
public class DummyTX implements Tx {

	@Override
	public void abort() {
	}

	@Override
	public void addFlushHook(int containerNo) {
	}

	@Override
	public void addPostCommitHook(PostCommitHook hook) {
	}

	@Override
	public void addPreCommitHook(PreCommitHook hook, String name) {
	}

	@Override
	public long checkPrevLSN() {
		return 0;
	}

	@Override
	public void commit() throws TxException {
	}

	@Override
	public BufferMgr getBufferManager() {
		return null;
	}

	@Override
	public TxID getID() {
		return null;
	}

	@Override
	public IsolationLevel getIsolationLevel() {
		return null;
	}

	@Override
	public LockControlBlock getLockCB() {
		return null;
	}

	@Override
	public int getLockDepth() {
		return 0;
	}

	@Override
	public int getLockingScheme() {
		return 0;
	}

	@Override
	public TxState getState() {
		return null;
	}

	@Override
	public TxStats getStatistics() {
		return null;
	}

	@Override
	public boolean join() {
		return true;
	}

	@Override
	public void leave() throws TxException {
	}

	@Override
	public long logCLR(LogOperation logOperation, long undoNextLSN)
			throws TxException {
		return 0;
	}
	
	@Override
	public long logUpdateSpecial(LogOperation logOperation, long undoNextLSN)
			throws TxException {
		return 0;
	}

	@Override
	public long logDummyCLR(long undoNextLSN) throws TxException {
		return 0;
	}

	@Override
	public long logUpdate(LogOperation logOperation) throws TxException {
		return 0;
	}

	@Override
	public void rollback() throws TxException {
	}

	@Override
	public void setLockDepth(int lockDepth) {
	}

	@Override
	public String toShortString() {
		return null;
	}

	@Override
	public void undo(long savepointLSN) throws TxException {
	}

	@Override
	public void addHook(CachedObjectHook hook) {
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public Session getSession() {
		return null;
	}

	@Override
	public PreCommitHook getPreCommitHook(String name) {
		return null;
	}

	@Override
	public void addPostRedoHook(PostRedoHook hook) {
	}

	@Override
	public void executePostRedoHooks() {
	}
}
