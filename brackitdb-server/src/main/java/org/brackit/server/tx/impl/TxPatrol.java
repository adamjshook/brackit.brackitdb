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
package org.brackit.server.tx.impl;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.DeadlockDetector;

class TxPatrol extends Thread {
	private static final Logger log = Logger.getLogger(TxPatrol.class);

	private volatile boolean active = false;

	private final int deadlockDetectionInterval;

	private final TxMgr taMgr;

	private final DeadlockDetector deadlockDetector;

	public TxPatrol(TxMgr taMgr, int deadlockDetectionInterval) {
		setName("TxPatrol");

		this.deadlockDetectionInterval = deadlockDetectionInterval;
		this.taMgr = taMgr;
		this.deadlockDetector = new DeadlockDetector(taMgr, true);
		this.active = true;

		setDaemon(true);
	}

	@Override
	public void run() {
		// Do not run directly at startup
		takeANap();

		while (active) {
			deadlockDetector.detectDeadlocks();
			takeANap();
		}
	}

	public void terminate() {
		active = false;
	}

	private void takeANap() {
		try {
			sleep(deadlockDetectionInterval);
		} catch (Exception e) {
		}
	}
}
