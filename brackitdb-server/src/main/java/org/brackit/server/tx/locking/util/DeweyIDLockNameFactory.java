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
package org.brackit.server.tx.locking.util;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.table.TreeLockNameFactory;

/**
 * Simple lock factory that wraps DeweyIDs as lock names. For debugging purposes
 * only.
 * 
 * @author Sebastian Baechle
 * 
 */
public class DeweyIDLockNameFactory implements TreeLockNameFactory {
	private final XTCdeweyID deweyID;

	private int tail;

	private class DeweyIDLockName implements LockName {
		private final XTCdeweyID deweyID;

		private final int tail;

		public DeweyIDLockName(XTCdeweyID deweyID, int tail) {
			super();
			this.deweyID = deweyID;
			this.tail = tail;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}

			if (obj instanceof DeweyIDLockName) {
				DeweyIDLockName name = (DeweyIDLockName) obj;
				return name.deweyID.equals(deweyID) && name.tail == tail;
			}

			return false;
		}

		@Override
		public int hashCode() {
			return deweyID.hashCode();
		}

		@Override
		public String toString() {
			return (tail == 0) ? deweyID.toString() : deweyID.toString() + "#"
					+ tail;
		}
	}

	public DeweyIDLockNameFactory(XTCdeweyID deweyID, int tail) {
		if (deweyID.getLevel() == 0)
			deweyID = deweyID.getNewChildID();
		this.deweyID = deweyID;
		this.tail = tail;
	}

	public DeweyIDLockNameFactory(XTCdeweyID deweyID) {
		if (deweyID.getLevel() == 0)
			deweyID = deweyID.getNewChildID();
		this.deweyID = deweyID;
	}

	@Override
	public LockName getLockName(int level) {
		if ((level == deweyID.getLevel()) && (tail != 0)) {
			DeweyIDLockName deweyIDLockName = new DeweyIDLockName(deweyID, tail);
			// System.err.println(String.format("Lock for (%s, %s) at level %s: %s",
			// deweyID, tail, level, deweyIDLockName));
			return deweyIDLockName;
		}

		XTCdeweyID temp = deweyID;

		while ((temp.getLevel() - 1 > level)) {
			temp = temp.getParent();
		}
		DeweyIDLockName deweyIDLockName = new DeweyIDLockName(temp, 0);
		// System.err.println(String.format("Lock for (%s, %s) at level %s: %s",
		// deweyID, tail, level, deweyIDLockName));
		return deweyIDLockName;
	}

	@Override
	public int getTargetLevel() {
		return (tail == 0) ? deweyID.getLevel() - 1 : deweyID.getLevel();
	}

	@Override
	public String toString() {
		return deweyID.toString();
	}
}
