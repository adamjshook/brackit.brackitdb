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

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockException;
import org.brackit.server.tx.locking.protocol.RUX;
import org.brackit.server.tx.locking.protocol.RUXEdgeLockProtocol;
import org.brackit.server.tx.locking.protocol.TaDOM3Plus;
import org.brackit.xquery.util.Cfg;

/**
 * @author Sebastian Baechle
 * 
 */
public final class MetaLockServiceImpl implements
		MetaLockService<TaDOM3Plus.Mode> {
	private final static boolean DEBUG = false;

	private final String name;

	private double escalationGain = 1.0;

	private int maxEscalationCount = -1;

	private final NodeLockService<TaDOM3Plus.Mode> nodeLockService;

	private final EdgeLockService edgeLockService;

	public MetaLockServiceImpl() {
		this(MetaLockServiceImpl.class.getName(), Cfg.asInt(TxMgr.MAX_LOCKS),
				Cfg.asInt(TxMgr.MAX_TRANSACTIONS));
	}

	public MetaLockServiceImpl(String name, int maxLocks, int maxTransactions) {
		this.name = name;

		boolean useLevelLockEscalation = Cfg.asBool(USE_LEVEL_LOCK_ESCALATION);
		boolean simulateSequentialLabeling = Cfg
				.asBool(SIMULATE_SEQUENTIAL_LABELING);

		if (simulateSequentialLabeling) {
			nodeLockService = new NodeLockServiceImpl<TaDOM3Plus.Mode>(
					new TaDOM3Plus(), name + "#node", maxLocks, maxTransactions);
		} else {
			nodeLockService = new NodeLockServiceImpl<TaDOM3Plus.Mode>(
					new TaDOM3Plus(), name + "#node", maxLocks, maxTransactions);
		}

		edgeLockService = new EdgeLockServiceImpl<RUX.Mode>(
				new RUXEdgeLockProtocol(), name + "#edge", maxLocks,
				maxTransactions);

		this.escalationGain = Cfg.asDouble(LOCK_ESCALATION_GAIN);
		this.maxEscalationCount = Cfg.asInt(LOCK_MAX_ESCALATION_COUNT);
	}

	public String getName() {
		return name;
	}

	public List<XTClock> getLocks(XTCdeweyID deweyID) throws LockException {
		return nodeLockService.getLocks(deweyID);
	}

	@Override
	public List<XTClock> getLocks(XTCdeweyID deweyID, Edge edge) {
		return edgeLockService.getLocks(deweyID, edge);
	}

	@Override
	public List<XTClock> getLocks(XTCdeweyID deweyID, String edgeName) {
		return edgeLockService.getLocks(deweyID, edgeName);
	}

	public String listLocks() {
		return nodeLockService.listLocks() + edgeLockService.listLocks();
	}

	public TaDOM3Plus.Mode lockLevelPartExclusive(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockLevelPartExclusive(tx, deweyID, lockClass,
				false);
	}

	public TaDOM3Plus.Mode lockLevelShared(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockLevelShared(tx, deweyID, lockClass, false);
	}

	public TaDOM3Plus.Mode lockNodeExclusive(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockNodeExclusive(tx, deweyID, lockClass, false);
	}

	public TaDOM3Plus.Mode lockNodeShared(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockNodeShared(tx, deweyID, lockClass, false);
	}

	public TaDOM3Plus.Mode lockNodeUpdate(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockNodeUpdate(tx, deweyID, lockClass, false);
	}

	public TaDOM3Plus.Mode lockTreeExclusive(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockTreeExclusive(tx, deweyID, lockClass, false);
	}

	public TaDOM3Plus.Mode lockTreeShared(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockTreeShared(tx, deweyID, lockClass, false);
	}

	public TaDOM3Plus.Mode lockTreeUpdate(Tx tx, XTCdeweyID deweyID,
			LockClass lockClass, boolean conditional) throws LockException {
		return nodeLockService.lockTreeUpdate(tx, deweyID, lockClass, false);
	}

	public boolean supportsExclusiveTreeLock(Tx tx) {
		return nodeLockService.supportsExclusiveTreeLock(tx);
	}

	public boolean supportsSharedLevelLock(Tx tx) {
		return nodeLockService.supportsSharedLevelLock(tx);
	}

	public boolean supportsSharedTreeLock(Tx tx) {
		return nodeLockService.supportsSharedTreeLock(tx);
	}

	public void unlockNode(Tx tx, XTCdeweyID deweyID) throws LockException {
		nodeLockService.unlockNode(tx, deweyID);
	}

	public void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		edgeLockService.lockEdgeExclusive(tx, deweyID, edge);
	}

	public void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		edgeLockService.lockEdgeExclusive(tx, deweyID, edgeName);
	}

	public void lockEdgeShared(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		edgeLockService.lockEdgeShared(tx, deweyID, edge);
	}

	public void lockEdgeShared(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		edgeLockService.lockEdgeShared(tx, deweyID, edgeName);
	}

	public void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		edgeLockService.lockEdgeUpdate(tx, deweyID, edge);
	}

	public void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		edgeLockService.lockEdgeUpdate(tx, deweyID, edgeName);
	}

	public void unlockEdge(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		edgeLockService.unlockEdge(tx, deweyID, edge);
	}

	public void unlockEdge(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		edgeLockService.unlockEdge(tx, deweyID, edgeName);
	}
}
