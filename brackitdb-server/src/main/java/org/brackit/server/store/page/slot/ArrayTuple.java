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
package org.brackit.server.store.page.slot;

import java.util.Arrays;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ArrayTuple implements Tuple {
	private byte[][] elements;

	public ArrayTuple(int size) {
		elements = new byte[size][];
	}

	public ArrayTuple(byte[][] elements) {
		set(elements);
	}

	@Override
	public byte[] get(int pos) {
		return ((pos >= 0) && (pos < elements.length)) ? elements[pos] : null;
	}

	@Override
	public int getSize() {
		return elements.length;
	}

	@Override
	public void set(int pos, byte[] value) {
		if ((pos < 0) || (pos > elements.length)) {
			throw new IllegalArgumentException();
		}

		if (pos >= elements.length) {
			byte[][] newElements = new byte[elements.length + 1][];
			System.arraycopy(elements, 0, newElements, 0, elements.length);
			elements = newElements;
		}

		elements[pos] = value;
	}

	public void set(byte[][] elements) {
		if (elements == null) {
			throw new IllegalArgumentException();
		}

		this.elements = elements;
	}

	public byte[][] toArray() {
		return Arrays.copyOf(elements, elements.length);
	}

	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		out.append("{");
		boolean first = true;
		for (byte[] element : elements) {
			if (!first) {
				out.append(",");
			}

			if (element == null) {
				out.append((String) null);
			} else {
				out.append(Arrays.toString(element));
			}

			first = false;
		}
		out.append("}");
		return out.toString();
	}
}
