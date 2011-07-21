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
package org.brackit.server.tx.locking.util;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.services.EdgeLockService.Edge;

public class DeweyIDLockNameBuilder implements LockNameBuilder {

	class DeweyIDLockName implements LockName {
		private final String name;

		public DeweyIDLockName(String name) {
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			return ((o instanceof DeweyIDLockName) && (((DeweyIDLockName) o).name
					.equals(name)));
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

	@Override
	public LockName edgeLockName(XTCdeweyID deweyID, Edge edge, String name) {
		return new DeweyIDLockName(deweyID.toString() + "@" + edge + "=" + name);
	}

	@Override
	public LockName[] edgeLockNamePath(XTCdeweyID deweyID, Edge edge,
			String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LockName nodeLockName(XTCdeweyID deweyID) {
		return new DeweyIDLockName(deweyID.toString());
	}

	@Override
	public LockName[] nodeLockNamePath(XTCdeweyID deweyID) {
		LockName[] lockNames = new LockName[deweyID.getLevel() - 1];
		for (int i = lockNames.length - 1; i >= 0; i--) {
			lockNames[i] = new DeweyIDLockName(deweyID.toString());
			deweyID = deweyID.getParent();
		}
		return lockNames;
	}

}
