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
package org.brackit.server.tx.locking.services;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.aries.IndexLockService;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.protocol.URIX;
import org.brackit.server.tx.locking.protocol.URIX.Mode;
import org.brackit.server.tx.locking.util.DefaultLockName;
import org.brackit.xquery.util.Cfg;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class KVLLockService extends GenericLockServiceImpl<URIX.Mode> implements
		IndexLockService {
	private static final Logger log = Logger.getLogger(KVLLockService.class);

	public KVLLockService(String name) {
		this(name, Cfg.asInt(TxMgr.MAX_LOCKS, 20000), Cfg.asInt(TxMgr.MAX_TX,
				100));
	}

	public KVLLockService(String name, int maxLocks, int maxTransactions) {
		super(name, maxLocks, maxTransactions);
	}

	@Override
	public void downgradeLock(Tx tx, int unitID, PageID rootPageID, byte[] key,
			byte[] value) throws IndexOperationException {
		// FIXME
		log.info("Lock downgrade in index not supported yet.");
	}

	@Override
	public boolean lockDelete(Tx tx, int unitID, PageID rootPageID, byte[] key,
			byte[] value, byte[] nextKey, byte[] nextValue, boolean conditional)
			throws IndexOperationException {
		/*
		 * Get X lock on next key to cover the delete range, then probe the
		 * delete key to assure that no other tx read it or uses it as a
		 * boundary of a range lock.
		 */
		LockName nextKeyLockName = createLockName(unitID, nextKey, nextValue);
		Mode grantedMode = getClient(tx).request(nextKeyLockName,
				LockClass.COMMIT_DURATION, Mode.X, conditional);

		if (grantedMode == null) {
			if (!conditional) {
				throw new IndexOperationException(
						"Unconditional lock request %s for next key %s failed.",
						Mode.IX, nextKeyLockName);
			}

			return false;
		}

		LockName keyLockName = createLockName(unitID, key, value);
		grantedMode = getClient(tx).request(keyLockName,
				LockClass.INSTANT_DURATION, Mode.X, conditional);

		if (grantedMode == null) {
			if (!conditional) {
				throw new IndexOperationException(
						"Unconditional lock request %s for delete key %s failed.",
						Mode.X, keyLockName);
			}

			return false;
		}

		return grantedMode != null;
	}

	@Override
	public boolean lockInsert(Tx tx, int unitID, PageID rootPageID, byte[] key,
			byte[] value, byte[] nextKey, byte[] nextValue, boolean conditional)
			throws IndexOperationException {
		/*
		 * Probe (instant lock) insert interval (currentKey, nextKey] with IX.
		 * If next key is not locked by this tx in S, SIX, X (range is not read
		 * locked) then IX is sufficient for key else propagate read lock on new
		 * interval (key, nextKey] to (currentKey, key] with an X lock
		 */
		LockName nextKeyLockName = createLockName(unitID, nextKey, nextValue);
		Mode grantedMode = getClient(tx).request(nextKeyLockName,
				LockClass.INSTANT_DURATION, Mode.IX, conditional);

		if (grantedMode == null) {
			if (!conditional) {
				throw new IndexOperationException(
						"Unconditional lock request %s for next key %s failed.",
						Mode.IX, nextKeyLockName);
			}

			return false;
		}

		LockName keyLockName = createLockName(unitID, key, value);
		Mode requestMode = (grantedMode.isCompatible(Mode.IX)) ? Mode.IX
				: Mode.X;
		grantedMode = getClient(tx).request(keyLockName,
				LockClass.COMMIT_DURATION, requestMode, conditional);

		if (grantedMode == null) {
			if (!conditional) {
				throw new IndexOperationException(
						"Unconditional lock request %s for insert key %s failed.",
						requestMode, keyLockName);
			}

			return false;
		}

		return (grantedMode != null);
	}

	@Override
	public boolean lockRead(Tx tx, int unitID, PageID rootPageID, byte[] key,
			byte[] value, boolean conditional) throws IndexOperationException {
		LockName readKeyLockName = createLockName(unitID, key, value);
		Mode grantedMode = getClient(tx).request(readKeyLockName,
				LockClass.COMMIT_DURATION, Mode.R, conditional);

		if (grantedMode == null) {
			if (!conditional) {
				throw new IndexOperationException(
						"Unconditional lock request %s for next key %s failed.",
						Mode.R, readKeyLockName);
			}

			return false;
		}

		return true;
	}

	@Override
	public boolean lockUpdate(Tx tx, int unitID, PageID rootPageID, byte[] key,
			byte[] value, boolean conditional) throws IndexOperationException {
		return lockRead(tx, unitID, rootPageID, key, value, conditional);
	}

	protected LockName createLockName(int unitID, byte[] key, byte[] value) {
		return new DefaultLockName((((long) unitID) << 32)
				| ((key != null) ? Arrays.hashCode(key) : 0));
	}
}