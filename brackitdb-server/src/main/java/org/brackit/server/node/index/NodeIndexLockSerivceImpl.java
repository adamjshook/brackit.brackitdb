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
package org.brackit.server.node.index;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.aries.IndexLockService;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.services.NodeLockService;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class NodeIndexLockSerivceImpl implements IndexLockService {
	private final NodeLockService nodeLockService;

	public NodeIndexLockSerivceImpl(NodeLockService nodeLockService) {
		this.nodeLockService = nodeLockService;
	}

	@Override
	public void downgradeLock(Tx transaction, int unitID, PageID rootPageID,
			byte[] key, byte[] value) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean lockDelete(Tx transaction, int unitID, PageID rootPageID,
			byte[] key, byte[] value, byte[] nextKey, byte[] nextValue,
			boolean conditional) throws IndexOperationException {
		return true;
		// try
		// {
		// XTCdeweyID deweyID = new XTCdeweyID(rootPageID.value(), nextKey);
		// return nodeLockService.lockNodeExclusive(transaction, deweyID,
		// LockClass.COMMIT_DURATION, conditional);
		// }
		// catch (BrackitException e)
		// {
		// throw new IndexOperationException(e,
		// "Acquisition of commit duration exclusive lock on %s for delete of %s from index %s failed",
		// new XTCdeweyID(rootPageID.value(), nextKey), new
		// XTCdeweyID(rootPageID.value(), key), rootPageID);
		// }
	}

	@Override
	public boolean lockInsert(Tx transaction, int unitID, PageID rootPageID,
			byte[] key, byte[] value, byte[] nextKey, byte[] nextValue,
			boolean conditional) throws IndexOperationException {
		return true;
		// try
		// {
		// XTCdeweyID deweyID = new XTCdeweyID(rootPageID.value(), nextKey);
		// return nodeLockService.lockNodeExclusive(transaction, deweyID,
		// LockClass.INSTANT_DURATION, conditional);
		// }
		// catch (BrackitException e)
		// {
		// throw new IndexOperationException(e,
		// "Acquisition of instant duration exclusive lock on %s for insert of %s into index %s failed",
		// new XTCdeweyID(rootPageID.value(), nextKey), new
		// XTCdeweyID(rootPageID.value(), key), rootPageID);
		// }
	}

	@Override
	public boolean lockRead(Tx transaction, int unitID, PageID rootPageID,
			byte[] key, byte[] value, boolean conditional)
			throws IndexOperationException {
		return true;
		// try
		// {
		// XTCdeweyID deweyID = new XTCdeweyID(rootPageID.value(), key);
		// LockClass lockClass =
		// (transaction.getIsolationLevel().longReadLocks()) ?
		// LockClass.COMMIT_DURATION : LockClass.SHORT_DURATION;
		// return nodeLockService.lockNodeExclusive(transaction, deweyID,
		// lockClass, conditional);
		// }
		// catch (BrackitException e)
		// {
		// throw new IndexOperationException(e,
		// "Acquisition of read lock for %s in index %s failed", new
		// XTCdeweyID(rootPageID.value(), key), rootPageID);
		// }
	}

	@Override
	public boolean lockUpdate(Tx transaction, int unitID, PageID rootPageID,
			byte[] key, byte[] value, boolean conditional)
			throws IndexOperationException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getName() {
		return nodeLockService.getName();
	}

	@Override
	public String listLocks() {
		return nodeLockService.listLocks();
	}
}
