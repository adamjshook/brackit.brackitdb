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
package org.brackit.server.tx.locking;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.locking.util.DefaultLockName;

/**
 * The available Lock Types.
 * 
 * @author Sebastian Baechle
 */
public final class LockType {
	private final static long HASH_BASE = 0x9FECA08B60B67l;

	public final static int LOCK_TYPE_COUNT = 8;

	public final static int DEWEYID = 0;

	public final static int EDGE = 1;

	public final static int FIXMODE = 2;

	public final static int PHANTOM = 3;

	public final static int KEY = 4;

	public final static int GENERIC = 5;

	public final static int PATH = 6;

	public final static int UNUSED_3 = 7;

	// EDGE
	public final static LockName buildEdgeLockName(XTCdeweyID deweyID,
			String edge) {
		// TODO Check hash
		return buildEdgeLockName(deweyID, (int) hash(edge.getBytes()));
	}

	public final static LockName buildEdgeLockName(XTCdeweyID deweyID, int edge) {
		final int[] divisions = deweyID.getDivisionValues();
		final int docID = deweyID.getDocID().value();
		final LockName[] lockNames = new LockName[deweyID.getLevel() + 1];
		int[] key = deweyID.divisionValues;
		long template = ((long) docID) << 32;
		int h = 5381;

		int level = 0;
		int length = divisions.length;
		int i = -1;

		while (++i < length) {
			h = key[i] + (h << 6) + (h << 16) - h;
		}

		h = edge + (h << 6) + (h << 16) - h;

		return new DefaultLockName(template | h);
	}

	private final static long hash(byte[] value) {
		int value1 = value[0];
		if (value1 < 0)
			value1 += 256;
		long hash = value1 % HASH_BASE;
		for (int i = 1; i < value.length; i++) {
			int value2 = value[i];
			if (value2 < 0)
				value2 += 256;
			hash = ((hash << 8) + value2) % HASH_BASE;
		}
		return hash;
	}
}