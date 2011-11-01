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
package org.brackit.server.node;

import java.io.Serializable;
import java.text.ParseException;

/**
 * Unique identifier for a document. Introduced for transparent collection
 * support
 * 
 * @author Sebastian Baechle
 * 
 */
public final class DocID implements Serializable, Comparable<DocID> {

	private final int docID;

	public DocID(int docID) {
		this.docID = docID;
	}

	@Override
	public int hashCode() {
		return this.docID;
	}

	@Override
	public boolean equals(Object obj) {
		return ((obj instanceof DocID) && (((DocID) obj).docID == docID));
	}

	public static DocID parse(String identifierString) throws ParseException {
		try {
			int docID = Integer.parseInt(identifierString);
			return new DocID(docID);
		} catch (NumberFormatException e) {
			throw new ParseException(String.format(
					"Invalid document identifier '%s'.", identifierString), 0);
		}
	}

	public byte[] getBytes() {
		byte[] buffer = new byte[4];
		toBytes(buffer);
		return buffer;
	}

	public void toBytes(byte[] buffer) {
		toBytes(buffer, 0);
	}

	public void toBytes(byte[] buffer, int offset) {
		buffer[offset] = (byte) ((docID >> 24) & 255);
		buffer[offset + 1] = (byte) ((docID >> 16) & 255);
		buffer[offset + 2] = (byte) ((docID >> 8) & 255);
		buffer[offset + 3] = (byte) (docID & 255);
	}

	public static DocID fromBytes(byte[] buffer) {
		return fromBytes(buffer, 0);
	}

	public static DocID fromBytes(byte[] buffer, int offset) {
		int no = ((buffer[offset] & 255) << 24)
				| ((buffer[offset + 1] & 255) << 16)
				| ((buffer[offset + 2] & 255) << 8)
				| (buffer[offset + 3] & 255);
		return (no != 0) ? new DocID(no) : null;
	}

	public int value() {
		return docID;
	}

	public static int getSize() {
		return 4;
	}

	@Override
	public int compareTo(DocID other) {
		return docID - other.docID;
	}

	@Override
	public String toString() {
		return Integer.toString(docID);
	}
}
