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
package org.brackit.server.tx;

import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.LockState;
import org.brackit.server.tx.locking.protocol.LockMode;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class XTClock implements Comparable<XTClock> {
	private final LockName name;
	private final LockMode<?> lockMode;
	private final TxID txID;
	private final LockState state;
	private final int count;

	public XTClock(LockName name, TxID txID, LockMode<?> lockMode,
			LockState state, int count) {
		this.name = name;
		this.lockMode = lockMode;
		this.state = state;
		this.txID = txID;
		this.count = count;
	}

	public LockMode<?> getMode() {
		return this.lockMode;
	}

	public TxID getTxID() {
		return this.txID;
	}

	public final LockName getName() {
		return name;
	}

	public final LockState getState() {
		return state;
	}

	@Override
	public final String toString() {
		return String.format("[NAME=%s,TA=%s,MODE=%s,STATE=%S,COUNT=%s]", name,
				txID, lockMode, state, count);
	}

	@Override
	public int compareTo(XTClock o) {
		return txID.compareTo(o.getTxID());
	}
}