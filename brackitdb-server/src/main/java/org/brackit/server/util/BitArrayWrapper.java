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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * A wrapper for the BitArray to eliminate the restriction that the size must be
 * a multiple of 8
 * 
 * @author Ou Yi
 * 
 */
public class BitArrayWrapper implements BitMap {
	
	private BitArray ba;
	private int logicalSize;

	public BitArrayWrapper(int logicalSize) {
		this.logicalSize = logicalSize;
		int physicalSize = toPhysicalSize(logicalSize);
		ba = new BitArray(physicalSize);
	}
	
	public BitArrayWrapper() {
	}

	@Override
	public void extendTo(int newLogicalSize) {
		
		byte[] old = ba.words;
		
		this.logicalSize = newLogicalSize;
		this.ba = new BitArray(toPhysicalSize(newLogicalSize));
		
		System.arraycopy(old, 0, ba.words, 0, old.length);
	}

	@Override
	public int logicalSize() {
		return logicalSize;
	}

	static int toPhysicalSize(int logicalSize) {
		return (logicalSize % BitArray.BITS_PER_WORD == 0 ? logicalSize
				: (logicalSize / BitArray.BITS_PER_WORD + 1)
						* BitArray.BITS_PER_WORD);
	}

	@Override
	public boolean get(int bitIndex) {
		if (bitIndex < 0 || bitIndex >= logicalSize)
			throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);
		return ba.get(bitIndex);
	}

	@Override
	public void set(int bitIndex) {
		if (bitIndex < 0 || bitIndex >= logicalSize)
			throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);
		ba.set(bitIndex);
	}

	@Override
	public void clear(int bitIndex) {
		if (bitIndex < 0 || bitIndex >= logicalSize)
			throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);
		ba.clear(bitIndex);
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
	@Override
	public int nextClearBit(int fromIndex) {
		if (fromIndex < 0 || fromIndex >= logicalSize)
			throw new IndexOutOfBoundsException("fromIndex: " + fromIndex);
		int pos = ba.nextClearBit(fromIndex);
		if (pos < logicalSize) {
			return pos;
		}
		return -1;
	}

	@Override
	public Iterator<Integer> getSetBits() {
		return ba.getSetBits();
	}

	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(logicalSize);
		out.write(ba.words);
	}

	@Override
	public void read(DataInput in) throws IOException {
		logicalSize = in.readInt();
		ba = new BitArray(toPhysicalSize(logicalSize));
		in.readFully(ba.words);		
	}
}
