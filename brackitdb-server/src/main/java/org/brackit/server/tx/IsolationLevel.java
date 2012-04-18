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
package org.brackit.server.tx;

import org.brackit.server.tx.locking.LockClass;

/**
 * @author Sebastian Baechle
 * 
 */
public enum IsolationLevel {
	NONE(0, false, false, false), UNCOMMITTED(1, false, false, true), COMMITTED(
			2, true, false, true), REPEATABLE(3, false, true, true), SERIALIZABLE(
			4, false, true, true);

	static {
		mapping = IsolationLevel.values();
	}

	private static IsolationLevel[] mapping;

	private final int id;

	private final boolean shortReadLocks;

	private final boolean longReadLocks;

	private final boolean longWriteLocks;

	private IsolationLevel(int id, boolean shortReadLocks,
			boolean longReadLocks, boolean longWriteLocks) {
		this.id = id;
		this.shortReadLocks = shortReadLocks;
		this.longReadLocks = longReadLocks;
		this.longWriteLocks = longWriteLocks;
	}

	public LockClass lockClass(boolean write) {
		if (!write) {
			return (longReadLocks) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
		} else {
			return (longReadLocks) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
		}
	}

	public boolean useReadLocks() {
		return (shortReadLocks || longReadLocks);
	}

	public boolean shortReadLocks() {
		return shortReadLocks;
	}

	public boolean longReadLocks() {
		return longReadLocks;
	}

	public boolean longWriteLocks() {
		return longWriteLocks;
	}

	public int getID() {
		return id;
	}

	public static IsolationLevel fromID(int id) {
		if ((id < 0) || (id > mapping.length)) {
			throw new RuntimeException(String.format(
					"Unknown isolation level id %s.", id));
		}

		return mapping[id];
	}
}
