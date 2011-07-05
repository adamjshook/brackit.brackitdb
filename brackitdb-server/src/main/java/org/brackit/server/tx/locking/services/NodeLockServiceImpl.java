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

import java.util.List;

import org.apache.log4j.Logger;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockException;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.protocol.TreeLockMode;
import org.brackit.server.tx.locking.protocol.TreeLockProtocol;
import org.brackit.server.tx.locking.table.LockTable;
import org.brackit.server.tx.locking.table.TreeLockNameFactory;
import org.brackit.server.tx.locking.table.TreeLockTableClient;
import org.brackit.server.tx.locking.util.DeweyIDLockNameFactoryFactory;
import org.brackit.xquery.util.Cfg;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class NodeLockServiceImpl<T extends TreeLockMode<T>> extends
		BaseLockServiceImpl<T> implements NodeLockService<T> {
	private static final Logger log = Logger.getLogger(NodeLockService.class);

	protected final TreeLockProtocol<T> protocol;

	protected final DeweyIDLockNameFactoryFactory factory;

	private volatile int maxEscalationCount;

	private volatile double escalationGain;

	private boolean simulateSequentialNodeLabeling;

	public NodeLockServiceImpl(TreeLockProtocol<T> protocol, String name,
			int maxLocks, int maxTransactions) {
		this(new LockTable<T>(maxLocks, maxTransactions), protocol, name);
	}

	public NodeLockServiceImpl(LockTable<T> table,
			TreeLockProtocol<T> protocol, String name) {
		this(table, protocol, name, Cfg.asInt(
				MetaLockService.LOCK_MAX_ESCALATION_COUNT, 1920), Cfg.asDouble(
				MetaLockService.LOCK_ESCALATION_GAIN, 2));
	}

	public NodeLockServiceImpl(LockTable<T> table,
			TreeLockProtocol<T> protocol, String name, int maxEscalationCount,
			double escalationGain) {
		super(table, name);
		this.protocol = protocol;
		this.maxEscalationCount = maxEscalationCount;
		this.escalationGain = escalationGain;
		this.factory = new DeweyIDLockNameFactoryFactory();
	}

	public boolean supportsExclusiveTreeLock(Tx tx) {
		return protocol.getTreeExclusiveMode() != null;
	}

	public boolean supportsSharedTreeLock(Tx tx) {
		return protocol.getTreeSharedMode() != null;
	}

	public boolean supportsSharedLevelLock(Tx tx) {
		return protocol.getLevelSharedMode() != null;
	}

	public T lockNodeExclusive(Tx tx, XTCdeweyID deweyID, LockClass lockClass,
			boolean conditional) throws LockException {
		T mode = protocol.getNodeExclusiveMode();
		return lockNode(tx, deweyID, 0, mode, lockClass, conditional);
	}

	public T lockNodeShared(Tx tx, XTCdeweyID deweyID, LockClass lockClass,
			boolean conditional) throws LockException {
		T mode = protocol.getNodeSharedMode();
		return lockNode(tx, deweyID, 0, mode, lockClass, conditional);
	}

	public T lockNodeUpdate(Tx tx, XTCdeweyID deweyID, LockClass lockClass,
			boolean conditional) throws LockException {
		T mode = protocol.getNodeUpdateMode();
		return lockNode(tx, deweyID, 0, mode, lockClass, conditional);
	}

	public T lockTreeExclusive(Tx tx, XTCdeweyID deweyID, LockClass lockClass,
			boolean conditional) throws LockException {
		T mode = protocol.getTreeExclusiveMode();
		return lockNode(tx, deweyID, 0, mode, lockClass, conditional);
	}

	public T lockTreeShared(Tx tx, XTCdeweyID deweyID, LockClass lockClass,
			boolean conditional) throws LockException {
		T mode = protocol.getTreeSharedMode();

		return lockNode(tx, deweyID, 0, mode, lockClass, conditional);
	}

	public T lockTreeUpdate(Tx tx, XTCdeweyID deweyID, LockClass lockClass,
			boolean conditional) throws LockException {
		T mode = protocol.getTreeUpdateMode();
		return lockNode(tx, deweyID, 0, mode, lockClass, conditional);
	}

	public T lockLevelShared(Tx tx, XTCdeweyID deweyID, LockClass lockClass,
			boolean conditional) throws LockException {
		T mode = protocol.getLevelSharedMode();
		return lockNode(tx, deweyID, 0, mode, lockClass, conditional);
	}

	public T lockLevelPartExclusive(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		// throw new BrackitException(getClass(), "lockLevelPartExclusive",
		// "Not implemented yet");
		return null;
	}

	protected T lockNode(Tx tx, XTCdeweyID deweyID, int tail, T mode,
			LockClass lockClass, boolean conditional) throws LockException {
		int lockDepth = tx.getLockDepth();
		int level = deweyID.level;

		if (mode != null) {
			if (lockDepth >= 0) {
				if (lockDepth < level) {
					int distanceToTargetLevel = level - lockDepth;

					if (log.isTraceEnabled()) {
						log.trace(String.format("Request of %s "
								+ "for %s by %s replaced " + "with %s for %s",
								tx.toShortString(), mode, deweyID, mode
										.escalate(distanceToTargetLevel),
								deweyID.getAncestor(lockDepth)));
					}

					deweyID = deweyID.getAncestor(lockDepth);
					mode = mode.escalate(distanceToTargetLevel);
				}

				if (log.isTraceEnabled()) {
					log.trace(String.format("%s requests %s for %s", tx
							.toShortString(), mode, deweyID));
				}

				if (simulateSequentialNodeLabeling) // simulate sequential node
				// labeling
				{
					if (tx.getIsolationLevel().useReadLocks()) {
						// all ancestor nodes have to be locked
						for (XTCdeweyID p = deweyID.getParent(); p != null; p = p
								.getParent()) {
							// System.out.println("Fetching ancestor " +
							// parent);
							// recordMgr.getRecord(tx, null, parent);
							throw new LockException("FIXME");
						}
					} else // we "assume" in this simulation the parent node is
					// already locked with a sufficient lock. So, we only
					// have to check the parent lock
					{
						XTCdeweyID parent = deweyID.getParent();
						if (parent != null) {
							// System.out.println("Fetching parent " + parent);
							// recordMgr.getRecord(tx, null,
							// deweyID.getParentDeweyID());
							throw new LockException("FIXME");
						}
					}
				}

				try {
					TreeLockNameFactory lockNames = factory.create(deweyID,
							tail);
					T result = getClient(tx).request(lockNames, lockClass,
							mode, false);
					return processResult(tx, lockNames, mode, result,
							conditional);
				} catch (TxException e) {
					throw new LockException(e);
				}
			} else {
				T documentLockMode = protocol.getTreeExclusiveMode();

				if (log.isTraceEnabled()) {
					log.trace(String.format("Request of %s"
							+ " for %s by %s replaced " + "with %s for %s", tx
							.toShortString(), mode, deweyID, documentLockMode,
							deweyID.getAncestor(0)));
				}

				deweyID = deweyID.getAncestor(0);

				try {
					TreeLockNameFactory lockNames = factory.create(deweyID,
							tail);
					T result = getClient(tx).request(lockNames, lockClass,
							documentLockMode, false);
					return processResult(tx, lockNames, mode, result,
							conditional);
				} catch (TxException e) {
					throw new LockException(e);
				}
			}
		}

		return null;
	}

	protected T processResult(Tx tx, TreeLockNameFactory lockNames,
			T requested, T result, boolean conditional) throws TxException {
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

	public void unlockNode(Tx tx, XTCdeweyID deweyID) throws LockException {
		getClient(tx).release(factory.create(deweyID, 0));
	}

	@Override
	public List<XTClock> getLocks(XTCdeweyID deweyID) throws LockException {
		LockName lockName = factory.create(deweyID, -1).getLockName(
				deweyID.getLevel() - 1);
		return table.getLocks(lockName);
	}

	@Override
	protected TreeLockTableClient<T> getClient(Tx tx) {
		TreeLockTableClient<T> client = (TreeLockTableClient<T>) tx.getLockCB()
				.get(this);

		if (client == null) {
			client = new TreeLockTableClient<T>(this, tx, table,
					maxEscalationCount, escalationGain);
			tx.getLockCB().add(this, client);
		}

		return client;
	}
}