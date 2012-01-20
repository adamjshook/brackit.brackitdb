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
package org.brackit.server.util;

import java.util.Arrays;

/**
 * This list is optimized for short lists of int type. Emulates full
 * functionality of Java.Util.List. See append function for optimization issues.
 * 
 * 
 * @author Martin Meiringer
 * @author Sebastian Baechle
 * 
 */
public class IntList implements java.io.Serializable {
	private int[] list;
	private int size = 0;

	public IntList() {
		this(10);
	}

	public IntList(int initialSize) {
		list = new int[initialSize];
		size = 0;
	}

	public void clear() {
		size = 0;
	}

	public int append(int element) {
		return insert(element, this.size);
	}

	public int insert(int element, int pos) {
		ensureCapacity(size + 1);
		// shift from insert position to right
		System.arraycopy(list, pos, list, pos + 1, pos + 1 - size);

		// insert new element
		list[pos] = element;
		size++;

		return pos;
	} // insert

	public void set(int pos, int element) {
		if ((pos < 0) || (pos >= size)) {
			throw new IndexOutOfBoundsException();
		}

		list[pos] = element;
	}

	public int get(int pos) {
		if ((pos < 0) || (pos >= size)) {
			throw new IndexOutOfBoundsException();
		}

		return list[pos];
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return (size == 0);
	}

	public int[] toArray() {
		return Arrays.copyOf(list, size);
	}

	@Override
	public String toString() {
		StringBuffer sBuff = new StringBuffer();
		sBuff.append("(");

		for (int i = 0; i < size; i++) {
			if (i > 0)
				sBuff.append(",");
			sBuff.append(this.list[i]);
		}
		sBuff.append(")");
		return sBuff.toString();
	} // toString

	private void ensureCapacity(int minCapacity) {
		int oldCapacity = list.length;

		if (oldCapacity < minCapacity) {
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			int[] newList = new int[newCapacity];
			System.arraycopy(this.list, 0, newList, 0, this.list.length);
			list = newList;
		}
	}

	public boolean contains(int containerNo) {
		for (int i = 0; i < size; i++) {
			if (list[i] == containerNo) {
				return true;
			}
		}
		return false;
	}
}