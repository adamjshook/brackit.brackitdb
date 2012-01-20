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
package org.brackit.server.util.sort;

import org.brackit.server.store.Field;

public class SortItem implements Comparable<SortItem> {

	final byte[] key;
	final byte[] value;
	SortItem next = null;
	SortItem last = null;

	public SortItem(byte[] key, byte[] value) {
		this.key = key;
		this.value = value;
	}

	public int getSize() {
		if (this.value != null)
			return this.key.length + this.value.length + 2;
		else
			return this.key.length + 2; // 4 Byte overhead??
	}

	/**
	 * Attention: Bytes are handled in the range of -127 to +127 and NOT 0 to
	 * 255! But if we handle all the byte processing in that way it works quite
	 * well - even to compare them ;o/
	 */
	public int compareTo(SortItem o2) {
		int compsize = 0;
		if (this.key.length <= o2.key.length)
			compsize = this.key.length;
		else
			compsize = o2.key.length;
		for (int i = 0; i < compsize; i++) {
			if (this.key[i] < o2.key[i])
				return -1;
			if (this.key[i] > o2.key[i])
				return +1;
		} // for i
		if (this.key.length > compsize)
			return +1;
		else if (compsize < o2.key.length)
			return -1;
		return 0;
	}

	public int compareTo(SortItem o2, Field compareType) {
		return compareType.compare(this.getKey(), o2.getKey());
	}

	public byte[] getKey() {
		return key;
	}

	public byte[] getValue() {
		return value;
	}

	public int compareDeepTo(SortItem cmpItem, Field keyType, Field valueType) {
		int keyComp = keyType.compare(key, cmpItem.key);

		if ((keyComp == 0) && (valueType != null)) {
			return valueType.compare(value, cmpItem.value);
		}

		return keyComp;
	}
}
