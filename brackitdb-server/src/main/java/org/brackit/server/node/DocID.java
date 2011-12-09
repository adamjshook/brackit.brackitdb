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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unique identifier for a document. Introduced for transparent collection
 * support
 * 
 * @author Sebastian Baechle
 * 
 */
public final class DocID implements Serializable, Comparable<DocID> {

	private final static Pattern parsePattern = Pattern.compile("(\\d+)(?:\\[(\\d+)\\])?");
	private final static String toStringPattern = "%s[%s]";

	private final int collectionID;
	private final int docNumber;

	public DocID(int collID, int docNumber) {
		this.collectionID = collID;
		this.docNumber = docNumber;
	}

	public static DocID parse(String identifierString) throws ParseException {
		try {			
			Matcher matcher = parsePattern.matcher(identifierString);
			
			if (!matcher.matches()) {
				throw new ParseException(String.format(
						"Invalid document identifier '%s'.", identifierString),
						0);
			}

			int collectionID = Integer.parseInt(matcher.group(1));
			
			int docNumber = 0;
			String docNumberString = matcher.group(2);
			if (docNumberString != null) {
				docNumber = Integer.parseInt(docNumberString);
			}

			return new DocID(collectionID, docNumber);
		} catch (NumberFormatException e) {
			throw new ParseException(String.format(
					"Invalid document identifier '%s'.", identifierString), 0);
		}
	}

	@Override
	public int hashCode() {
		return collectionID ^ docNumber;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DocID other = (DocID) obj;
		if (collectionID != other.collectionID)
			return false;
		if (docNumber != other.docNumber)
			return false;
		return true;
	}

	public byte[] getBytes() {
		byte[] buffer = new byte[8];
		toBytes(buffer);
		return buffer;
	}

	public void toBytes(byte[] buffer) {
		toBytes(buffer, 0);
	}

	public void toBytes(byte[] buffer, int offset) {
		buffer[offset] = (byte) ((collectionID >> 24) & 255);
		buffer[offset + 1] = (byte) ((collectionID >> 16) & 255);
		buffer[offset + 2] = (byte) ((collectionID >> 8) & 255);
		buffer[offset + 3] = (byte) (collectionID & 255);
		buffer[offset + 4] = (byte) ((docNumber >> 24) & 255);
		buffer[offset + 5] = (byte) ((docNumber >> 16) & 255);
		buffer[offset + 6] = (byte) ((docNumber >> 8) & 255);
		buffer[offset + 7] = (byte) (docNumber & 255);
	}

	public static DocID fromBytes(byte[] buffer) {
		return fromBytes(buffer, 0);
	}

	public static DocID fromBytes(byte[] buffer, int offset) {

		int collectionID = ((buffer[offset] & 255) << 24)
				| ((buffer[offset + 1] & 255) << 16)
				| ((buffer[offset + 2] & 255) << 8)
				| (buffer[offset + 3] & 255);

		if (collectionID == 0) {
			// invalid collectionID
			return null;
		}

		int docNumber = ((buffer[offset + 4] & 255) << 24)
				| ((buffer[offset + 5] & 255) << 16)
				| ((buffer[offset + 6] & 255) << 8)
				| (buffer[offset + 7] & 255);

		return new DocID(collectionID, docNumber);
	}

	public int getCollectionID() {
		return collectionID;
	}
	
	public int getDocNumber() {
		return docNumber;
	}

	public static int getSize() {
		return 8;
	}

	@Override
	public int compareTo(DocID other) {
		
		int collCompare = collectionID - other.collectionID;
		if (collCompare != 0) {
			return collCompare;
		}
		return docNumber - other.docNumber;
	}

	@Override
	public String toString() {
		return String.format(toStringPattern, collectionID, docNumber);
	}
}
