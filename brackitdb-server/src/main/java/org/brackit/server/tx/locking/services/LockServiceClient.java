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

import java.util.Collection;
import java.util.List;

import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.locking.Blocking;
import org.brackit.server.tx.locking.LockServiceClientCB;

/**
 * Client access for a lock service.
 * 
 * @author Sebastian Baechle
 */
public interface LockServiceClient {
	/**
	 * Returns the corresponding lock service control block.
	 * 
	 * @return
	 */
	public LockServiceClientCB getLockServiceCB();

	/**
	 * Returns a list of currently blocked requests where this client blocks.
	 * 
	 * @return the object to be locked
	 */
	public Collection<Blocking> blockedAt();

	/**
	 * Removes all locks of the tx from the lock service.
	 */
	public void freeResources();

	/**
	 * Returns a list of all locks at this lock service of the tx.
	 * 
	 * @return list of all locks at this lock service of the tx
	 */
	public List<XTClock> getLocks();

	/**
	 * Returns a formatted String of all locks of this tx
	 * 
	 * @return formatted String of all locks of this tx
	 */
	public String listLocks();

	/**
	 * Externally unblocks the client, e.g., to resolve a deadlock
	 */
	public void unblock();
}
