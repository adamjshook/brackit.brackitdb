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
package org.brackit.server.session;

/**
 * Immutable identifier token for client connections
 * 
 * @author Sebastian Baechle
 * 
 */
public final class SessionID implements java.io.Serializable {

	public final int ID;
	public final int key;

	public SessionID(byte[] b) throws SessionException {
		if ((b == null) || (b.length != 8)) {
			throw new SessionException("Invalid session ID token");
		}
		ID = ((b[0] & 255) << 24) | ((b[1] & 255) << 16) | ((b[2] & 255) << 8)
				| (b[3] & 255);
		key = ((b[4] & 255) << 24) | ((b[5] & 255) << 16) | ((b[6] & 255) << 8)
				| (b[7] & 255);
	}

	public SessionID(int ID, int key) {
		this.ID = ID;
		this.key = key;
	}

	@Override
	public boolean equals(Object object) {
		return ((object != null) && (object instanceof SessionID)
				&& (((SessionID) object).ID == this.ID) && (((SessionID) object).key == this.key));
	}

	public byte[] toBytes() {
		byte[] b = new byte[8];
		b[0] = (byte) ((ID >> 24) & 255);
		b[1] = (byte) ((ID >> 16) & 255);
		b[2] = (byte) ((ID >> 8) & 255);
		b[3] = (byte) (ID & 255);
		b[4] = (byte) ((key >> 24) & 255);
		b[5] = (byte) ((key >> 16) & 255);
		b[6] = (byte) ((key >> 8) & 255);
		b[7] = (byte) (key & 255);
		return b;
	}

	@Override
	public String toString() {
		return Integer.toString(key);
	}
}