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
package org.brackit.server.tx.locking.services;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.protocol.URIX;
import org.brackit.server.tx.locking.protocol.URIX.Mode;
import org.brackit.server.tx.locking.table.TreeLockNameFactory;
import org.brackit.xquery.util.Cfg;

/**
 * Simple lock service that serializes txs directly.
 * 
 * @author Sebastian Baechle
 * 
 */
public class SimpleLockService<E> extends GenericLockServiceImpl<URIX.Mode> {
	private class ObjectLockName<F> implements LockName {
		private final F obj;

		public ObjectLockName(F string) {
			this.obj = string;
		}

		@Override
		public boolean equals(Object obj) {
			return ((obj instanceof ObjectLockName) && (((ObjectLockName<F>) obj).obj
					.equals(this.obj)));
		}

		@Override
		public int hashCode() {
			return obj.hashCode();
		}

		@Override
		public String toString() {
			return obj.toString();
		}
	}

	public List<E> getLockedResources() {
		List<LockName> resources = table.getLockedResources();
		List<E> objects = new ArrayList<E>(resources.size());

		for (LockName name : resources) {
			objects.add(((ObjectLockName<E>) name).obj);
		}

		return objects;
	}

	public void lock(Tx tx, E object, LockClass lockClass, boolean shared)
			throws TxException {
		LockName lockName = new ObjectLockName<E>(object);
		Mode mode = (shared) ? Mode.R : Mode.X;
		processResult(tx, lockName, mode, getClient(tx).request(lockName,
				lockClass, mode, false), false);
	}

	protected URIX.Mode processResult(Tx tx, TreeLockNameFactory lockNames,
			Mode requested, Mode result, boolean conditional)
			throws TxException {
		if (result != null) {
			return result;
		} else if (conditional) {
			return null;
		} else {
			String reason = (tx.getState() == TxState.ABORTED) ? "Kill"
					: "Timeout";
			throw new TxException("Lock request %s for %s (%s) failed: %s",
					requested, lockNames
							.getLockName(lockNames.getTargetLevel()),
					conditional ? "conditional" : "unconditional", reason);
		}
	}

	public SimpleLockService(String name) {
		this(name, Cfg.asInt(TxMgr.MAX_LOCKS, 200000), Cfg.asInt(TxMgr.MAX_TX,
				100));
	}

	public SimpleLockService(String name, int maxLocks, int maxTransactions) {
		super(name, maxLocks, maxTransactions);
	}
}