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
import org.brackit.server.tx.locking.LockException;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public interface EdgeLockService extends LockService {
	public enum Edge {
		FIRST_CHILD(-1), LAST_CHILD(-2), PREV_SIBLING(-3), NEXT_SIBLING(-4), ATTRIBUTE(
				-5);

		private final int id;

		private Edge(int id) {
			this.id = id;
		}

		public int getID() {
			return this.id;
		}
	};

	public void lockEdgeShared(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException;

	public void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException;

	public void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException;

	public void lockEdgeShared(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException;

	public void lockEdgeUpdate(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException;

	public void lockEdgeExclusive(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException;

	public void unlockEdge(Tx tx, XTCdeweyID deweyID, Edge edge)
			throws LockException;

	public void unlockEdge(Tx tx, XTCdeweyID deweyID, String edgeName)
			throws LockException;

	public List<XTClock> getLocks(XTCdeweyID deweyID, Edge edge);

	public List<XTClock> getLocks(XTCdeweyID deweyID, String edgeName);
}
