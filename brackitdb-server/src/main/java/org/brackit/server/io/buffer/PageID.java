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
package org.brackit.server.io.buffer;

import java.nio.ByteBuffer;

import org.brackit.server.tx.log.SizeConstants;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public final class PageID implements Comparable<PageID> {
	/**
	 * Bit mask for the block number = 00000000 11111111 11111111 11111111
	 */
	// 
	private final static int BLOCK_NO_MASK = 16777215;

	/**
	 * Bit mask for the container number = 11111111 00000000 00000000 00000000
	 */
	private static final int CONTAINER_MASK = -16777216;

	private final int no;

	public PageID(int no) {
		this.no = no;
	}

	public PageID(int containerNo, int pageNo) {
		this.no = ((containerNo << 24) & CONTAINER_MASK)
				| (pageNo & BLOCK_NO_MASK);
	}

	public int getBlockNo() {
		return (no & BLOCK_NO_MASK);
	}

	public int getContainerNo() {
		return ((no & CONTAINER_MASK) >> 24);
	}

	public int value() {
		return no;
	}

	public static PageID read(ByteBuffer buffer) {
		int no = buffer.getInt();
		return (no != 0) ? new PageID(no) : null;
	}

	public void write(ByteBuffer buffer) {
		buffer.putInt(no);
	}

	public void toBytes(byte[] buffer) {
		toBytes(buffer, 0);
	}

	public void toBytes(byte[] buffer, int offset) {
		buffer[offset] = (byte) ((no >> 24) & 255);
		buffer[offset + 1] = (byte) ((no >> 16) & 255);
		buffer[offset + 2] = (byte) ((no >> 8) & 255);
		buffer[offset + 3] = (byte) (no & 255);
	}

	public static PageID fromString(String s) {
		try {
			int pageNo = Integer.parseInt(s);
			return new PageID(pageNo);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(String.format(
					"Invalid page id :'%s'", s));
		}
	}

	public static PageID fromBytes(byte[] buffer) {
		return fromBytes(buffer, 0);
	}

	public static PageID fromBytes(byte[] buffer, int offset) {
		int no = ((buffer[offset] & 255) << 24)
				| ((buffer[offset + 1] & 255) << 16)
				| ((buffer[offset + 2] & 255) << 8)
				| (buffer[offset + 3] & 255);
		return (no != 0) ? new PageID(no) : null;
	}

	public static boolean isValid(byte[] buffer) {
		return isValid(buffer, 0);
	}

	public static boolean isValid(byte[] buffer, int offset) {
		int no = ((buffer[offset] & 255) << 24)
				| ((buffer[offset + 1] & 255) << 16)
				| ((buffer[offset + 2] & 255) << 8)
				| (buffer[offset + 3] & 255);
		return (no != 0);
	}

	public byte[] getBytes() {
		return new byte[] { (byte) ((no >> 24) & 255),
				(byte) ((no >> 16) & 255), (byte) ((no >> 8) & 255),
				(byte) (no & 255) };
	}

	@Override
	public String toString() {
		return Integer.toString(no);
	}

	public static int getSize() {
		return SizeConstants.INT_SIZE;
	}

	@Override
	public final boolean equals(Object obj) {
		return ((obj != null) && (((PageID) obj).no == no));
	}

	@Override
	public int compareTo(PageID o) {
		int ono = o.no;
		return (no < ono) ? -1 : (no == ono) ? 0 : 1;
	}

	@Override
	public final int hashCode() {
		return no;
	}

	public static byte[] noPageBytes() {
		return new byte[4];
	}

	public static void noPageToBytes(byte[] buffer, int offset) {
		buffer[offset] = (byte) 0;
		buffer[offset + 1] = (byte) 0;
		buffer[offset + 2] = (byte) 0;
		buffer[offset + 3] = (byte) 0;
	}
}
