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

import java.util.List;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockException;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.LockType;
import org.brackit.server.tx.locking.protocol.EdgeLockProtocol;
import org.brackit.server.tx.locking.protocol.LockMode;
import org.brackit.server.tx.locking.table.LockTable;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class EdgeLockServiceImpl<T extends LockMode<T>> extends
		BaseLockServiceImpl<T> implements EdgeLockService {
	private static final Logger log = Logger.getLogger(EdgeLockService.class);

	private final EdgeLockProtocol<T> protocol;

	public EdgeLockServiceImpl(EdgeLockProtocol<T> protocol, String name,
			int maxLocks, int maxTransactions) {
		this(new LockTable<T>(maxLocks, maxTransactions), protocol, name,
				maxLocks, maxTransactions);
	}

	public EdgeLockServiceImpl(LockTable<T> table,
			EdgeLockProtocol<T> protocol, String name, int maxLocks,
			int maxTransactions) {
		super(table, name);
		this.protocol = protocol;
	}

	@Override
	public List<XTClock> getLocks(XTCdeweyID deweyID, Edge edge) {
		return table
				.getLocks(LockType.buildEdgeLockName(deweyID, edge.getID()));
	}

	@Override
	public List<XTClock> getLocks(XTCdeweyID deweyID, String edgeName) {
		return table.getLocks(LockType.buildEdgeLockName(deweyID, edgeName));
	}

	public final void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		// if (((tx.getLockDepth() >= deweyID.getLevel()) &&
		// (tx.getIsolationLevel() >
		// Tx.ISOLATIONLEVEL_NONE)) ||
		// (tx.getIsolationLevel() <
		// Tx.ISOLATIONLEVEL_REPEATABLE))
		{
			final T mode = protocol.getEdgeExclusiveMode();
			if (log.isTraceEnabled()) {
				System.out.println(String.format(
						"[%s] %s requests %s for edge %s of %s", this
								.getClass().getSimpleName(),
						tx.toShortString(), mode, edge, deweyID));
			}

			LockClass lockClass = (tx.getIsolationLevel().longWriteLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
			final LockName lockName = LockType.buildEdgeLockName(deweyID, edge
					.getID());
			request(tx, mode, lockClass, lockName);
		}
	}

	public final void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID,
			String edgeName) throws LockException {
		// if (((tx.getLockDepth() >= deweyID.getLevel()) &&
		// (tx.getIsolationLevel().longWriteLocks())) ||
		// (tx.getIsolationLevel() <
		// Tx.ISOLATIONLEVEL_REPEATABLE))
		{
			final T mode = protocol.getEdgeExclusiveMode();
			if (log.isTraceEnabled()) {
				System.out.println(String.format(
						"[%s] %s requests %s for edge %s of %s", this
								.getClass().getSimpleName(),
						tx.toShortString(), mode, edgeName, deweyID));
			}

			LockClass lockClass = (tx.getIsolationLevel().longWriteLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
			final LockName lockName = LockType.buildEdgeLockName(deweyID,
					edgeName);
			request(tx, mode, lockClass, lockName);
		}
	}

	public final void lockEdgeShared(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		if ((tx.getLockDepth() >= deweyID.getLevel())
				&& (tx.getIsolationLevel().useReadLocks())) {
			final T mode = protocol.getEdgeSharedMode();
			if (log.isTraceEnabled()) {
				System.out.println(String.format(
						"[%s] %s requests %s for edge %s of %s", this
								.getClass().getSimpleName(),
						tx.toShortString(), mode, edge, deweyID));
			}

			LockClass lockClass = (tx.getIsolationLevel().longReadLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
			final LockName lockName = LockType.buildEdgeLockName(deweyID, edge
					.getID());
			request(tx, mode, lockClass, lockName);
		}
	}

	public final void lockEdgeShared(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		if ((tx.getLockDepth() >= deweyID.getLevel())
				&& (tx.getIsolationLevel().useReadLocks())) {
			final T mode = protocol.getEdgeSharedMode();
			if (log.isTraceEnabled()) {
				System.out.println(String.format(
						"[%s] %s requests %s for edge %s of %s", this
								.getClass().getSimpleName(),
						tx.toShortString(), mode, edgeName, deweyID));
			}

			LockClass lockClass = (tx.getIsolationLevel().longReadLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
			final LockName lockName = LockType.buildEdgeLockName(deweyID,
					edgeName);
			request(tx, mode, lockClass, lockName);
		}
	}

	public final void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		// if ((tx.getLockDepth() >= deweyID.getLevel()) &&
		// (tx.getIsolationLevel().useReadLocks()))
		{
			final T mode = protocol.getEdgeUpdateMode();
			if (log.isTraceEnabled()) {
				System.out.println(String.format(
						"[%s] %s requests %s for edge %s of %s", this
								.getClass().getSimpleName(),
						tx.toShortString(), mode, edge, deweyID));
			}

			LockClass lockClass = (tx.getIsolationLevel().longReadLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
			final LockName lockName = LockType.buildEdgeLockName(deweyID, edge
					.getID());
			request(tx, mode, lockClass, lockName);
		}
	}

	public final void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		// if ((tx.getLockDepth() >= deweyID.getLevel()) &&
		// (tx.getIsolationLevel().useReadLocks()))
		{
			final T mode = protocol.getEdgeUpdateMode();
			if (log.isTraceEnabled()) {
				System.out.println(String.format(
						"[%s] %s requests %s for edge %s of %s", this
								.getClass().getSimpleName(),
						tx.toShortString(), mode, edgeName, deweyID));
			}

			LockClass lockClass = (tx.getIsolationLevel().longReadLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
			final LockName lockName = LockType.buildEdgeLockName(deweyID,
					edgeName);
			request(tx, mode, lockClass, lockName);
		}
	}

	private void request(Tx tx, final T mode, LockClass lockClass,
			final LockName lockName) throws LockException {
		getClient(tx).request(lockName, lockClass, mode, false);
	}

	public final void unlockEdge(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		getClient(tx)
				.release(LockType.buildEdgeLockName(deweyID, edge.getID()));
	}

	public final void unlockEdge(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		getClient(tx).release(LockType.buildEdgeLockName(deweyID, edgeName));
	}
}
