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
package org.brackit.server.tx.locking.services;

import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.protocol.TreeLockMode;
import org.brackit.server.tx.locking.table.LockTable;
import org.brackit.server.tx.locking.table.TreeLockNameFactory;
import org.brackit.server.tx.locking.table.TreeLockTableClient;

/**
 * Generic {@link LockService} for arbitrary Resources
 * 
 * @author Sebastian Baechle
 * 
 */
public class GenericLockServiceImpl<T extends TreeLockMode<T>> extends
		BaseLockServiceImpl<T> implements GenericLockService<T> {
	public GenericLockServiceImpl(String name, int maxLocks, int maxTransactions) {
		super(new LockTable<T>(maxLocks, maxTransactions), name);
	}

	@Override
	public T request(Tx tx, LockName lockName, LockClass lockClass, T mode,
			boolean conditional) throws TxException {
		T result = getClient(tx)
				.request(lockName, lockClass, mode, conditional);
		return processResult(tx, lockName, mode, result, conditional);
	}

	@Override
	public T request(Tx tx, TreeLockNameFactory factory, LockClass lockClass,
			T mode, boolean conditional) throws TxException {
		T result = getClient(tx).request(factory, lockClass, mode, conditional);
		return processResult(tx, factory.getLockName(factory.getTargetLevel()),
				mode, result, conditional);
	}

	protected T processResult(Tx tx, LockName lockName, T requested, T result,
			boolean conditional) throws TxException {
		if (result != null) {
			return result;
		} else if (conditional) {
			return null;
		} else {
			String reason = (tx.getState() == TxState.ABORTED) ? "Kill"
					: "Timeout";
			throw new TxException("Lock request %s for %s (%s) failed: %s",
					requested, lockName, conditional ? "conditional"
							: "unconditional", reason);
		}
	}

	@Override
	public void release(Tx tx, LockName lockName) throws TxException {
		getClient(tx).release(lockName);
	}

	@Override
	public void release(Tx tx, TreeLockNameFactory factory) throws TxException {
		getClient(tx).release(factory);
	}

	@Override
	public XTClock getLock(Tx tx, LockName lockName) throws TxException {
		return getClient(tx).getLock(lockName);
	}

	@Override
	protected TreeLockTableClient<T> getClient(Tx tx) {
		TreeLockTableClient<T> client = (TreeLockTableClient<T>) tx.getLockCB()
				.get(this);

		if (client == null) {
			client = new TreeLockTableClient<T>(this, tx, table);
			tx.getLockCB().add(this, client);
		}

		return client;
	}

	@Override
	public String toString() {
		return table.toString();
	}
}