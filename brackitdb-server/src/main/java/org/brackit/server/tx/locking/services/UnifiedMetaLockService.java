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

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.LockException;
import org.brackit.server.tx.locking.protocol.TaDOM3Plus;
import org.brackit.xquery.util.Cfg;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class UnifiedMetaLockService extends
		NodeLockServiceImpl<TaDOM3Plus.Mode> implements
		MetaLockService<TaDOM3Plus.Mode> {
	private final static boolean DEBUG = false;

	private double escalationGain = 1.0;

	private int maxEscalationCount = -1;

	public UnifiedMetaLockService() {
		this(UnifiedMetaLockService.class.getName(), Cfg.asInt(TxMgr.MAX_LOCKS,
				TxMgr.DEFAULT_MAX_LOCKS), Cfg.asInt(TxMgr.MAX_TX,
				TxMgr.DEFAULT_MAX_TX));
	}

	public UnifiedMetaLockService(String name, int maxLocks, int maxTransactions) {
		super(new TaDOM3Plus(), name, maxLocks, maxTransactions);
		this.escalationGain = Cfg.asDouble(LOCK_ESCALATION_GAIN, 2);
		this.maxEscalationCount = Cfg.asInt(LOCK_MAX_ESCALATION_COUNT, 1920);
	}

	@Override
	public List<XTClock> getLocks(XTCdeweyID deweyID, Edge edge) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<XTClock> getLocks(XTCdeweyID deweyID, String edgeName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		TaDOM3Plus.Mode mode = protocol.getNodeExclusiveMode();
		lockNode(tx, deweyID, edge.getID(), mode, tx.getIsolationLevel()
				.lockClass(true), false);
	}

	@Override
	public void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		TaDOM3Plus.Mode mode = protocol.getNodeExclusiveMode();
		lockNode(tx, deweyID, edgeName.hashCode(), mode, tx.getIsolationLevel()
				.lockClass(true), false);
	}

	@Override
	public void lockEdgeShared(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		TaDOM3Plus.Mode mode = protocol.getNodeSharedMode();
		lockNode(tx, deweyID, edge.getID(), mode, tx.getIsolationLevel()
				.lockClass(true), false);
	}

	@Override
	public void lockEdgeShared(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		TaDOM3Plus.Mode mode = protocol.getNodeSharedMode();
		lockNode(tx, deweyID, edgeName.hashCode(), mode, tx.getIsolationLevel()
				.lockClass(true), false);
	}

	public void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		TaDOM3Plus.Mode mode = protocol.getNodeUpdateMode();
		lockNode(tx, deweyID, edge.getID(), mode, tx.getIsolationLevel()
				.lockClass(true), false);
	}

	@Override
	public void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		TaDOM3Plus.Mode mode = protocol.getNodeUpdateMode();
		lockNode(tx, deweyID, edgeName.hashCode(), mode, tx.getIsolationLevel()
				.lockClass(true), false);
	}

	@Override
	public void unlockEdge(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException {
		getClient(tx).release(factory.create(deweyID, edge.getID()));
	}

	@Override
	public void unlockEdge(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException {
		getClient(tx).release(factory.create(deweyID, edgeName.hashCode()));
	}
}
