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
package org.brackit.server.util;

/**
 * BitArray (used for free space administration). We can't use boolean[],
 * because size of a boolean primitive type is one byte. We can't use
 * java.util.BitSet either, because we need a presentation in bytes that can be
 * persisted directly.
 * 
 * This could also be implemented as a utility class with static methods on a
 * byte array.
 * 
 * @author Ou Yi
 * 
 */
public class BitArray {

	public static final int BITS_PER_WORD = 8;

	private static final byte WORD_MASK = (byte) 0xFF;

	protected byte[] words;

	public BitArray(int size) {
		if (size % BITS_PER_WORD != 0 || size < BITS_PER_WORD) {
			throw new RuntimeException("invalid Bitmap size");
		}
		this.words = new byte[size / BITS_PER_WORD];
	}

	public BitArray(byte[] bytes) {
		this.words = bytes;
	}

	public byte[] getBytes() {
		return words;
	}

	public boolean get(int bitIndex) {
		if (bitIndex < 0 || bitIndex >= words.length * BITS_PER_WORD)
			throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);

		int wIndex = bitIndex / BITS_PER_WORD;
		int bIndex = bitIndex % BITS_PER_WORD;
		return ((words[wIndex] & ((byte) 1 << bIndex)) != 0);
	}

	public void set(int bitIndex) {
		if (bitIndex < 0 || bitIndex >= words.length * BITS_PER_WORD)
			throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);

		int wIndex = bitIndex / BITS_PER_WORD;
		int bIndex = bitIndex % BITS_PER_WORD;

		words[wIndex] |= ((byte) 1 << bIndex);
	}

	public void clear(int bitIndex) {
		if (bitIndex < 0 || bitIndex >= words.length * BITS_PER_WORD)
			throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);

		int wIndex = bitIndex / BITS_PER_WORD;
		int bIndex = bitIndex % BITS_PER_WORD;

		words[wIndex] &= ~((byte) 1 << bIndex);
	}

	public int size() {
		return words.length * BITS_PER_WORD;
	}

	/**
	 * Returns the index of the first bit that is set to false that occurs on or
	 * after the specified starting index. If there is no such bit, -1 will be
	 * returned.
	 * 
	 * @param fromIndex
	 *            the index to start checking from (inclusive).
	 * @return
	 */
	public int nextClearBit(int fromIndex) {
		if (fromIndex < 0 || fromIndex >= words.length * BITS_PER_WORD)
			throw new IndexOutOfBoundsException("fromIndex: " + fromIndex);

		int wIndex = fromIndex / BITS_PER_WORD;

		if (words[wIndex] != WORD_MASK) {
			for (int i = fromIndex; i < (wIndex + 1) * BITS_PER_WORD; i++) {
				if (!get(i)) {
					return i;
				}
			}
			return -1;
		}

		while (wIndex < words.length) {

			if (words[wIndex] != WORD_MASK) {
				for (int i = wIndex * BITS_PER_WORD; i < (wIndex + 1)
						* BITS_PER_WORD; i++) {
					if (!get(i)) {
						return i;
					}
				}
			}
			wIndex++;
		}

		return -1;

	}

}
