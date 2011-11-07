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
package org.brackit.server.node.el;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.index.ElPlaceHolderHelper;
import org.brackit.server.util.Calc;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.xdm.Kind;

/**
 * En/Decoder for elementless records.
 * 
 * CAVEAT: If changes are made to the encoding one must ensure that the size of
 * empty element entries (key + value) is always at most of equal size (in
 * bytes)! Otherwise some parts of the replace logic in the index may break or
 * have to be adjusted.
 * 
 * @author Sebastian Baechle
 * 
 */
public class ElRecordAccess implements ElPlaceHolderHelper {
	/**
	 * 0000 0011
	 */
	private final static int PCR_SIZE_MASK = 3;

	/**
	 * 0000 0111
	 */
	private final static int TYPE_MASK = 7;

	public final static int getPCR(byte[] physicalRecord) {
		int pcrSize = getPCRsize(physicalRecord);
		return (pcrSize > 0) ? Calc.toInt(physicalRecord, 1, pcrSize) : 0;
	}
	
	public final static int getPCR(byte[] buf, int offset, int len) {
		int pcrSize = ((buf[offset] & PCR_SIZE_MASK) + 1);
		return (pcrSize > 0) ? Calc.toInt(buf, offset + 1, pcrSize) : 0;
	}

	public final static byte getType(byte[] physicalRecord) {
		return (byte) ((physicalRecord[0] >> 2) & TYPE_MASK);
	}
	
	public final static byte getType(byte[] buf, int offset, int len) {
		return (byte) ((buf[offset] >> 2) & TYPE_MASK);
	}

	public final static void setType(byte[] physicalRecord, byte type) {
		physicalRecord[0] = (byte) ((physicalRecord[0] & ~(TYPE_MASK << 2)) | (type << 2));
	}

	public final static String getValue(byte[] physicalRecord) {
		int pcrSize = getPCRsize(physicalRecord);
		int valueOffset = 1 + pcrSize;
		int valueLength = physicalRecord.length - valueOffset;
		String value = "";

		if (valueLength > 0) {
			value = Calc.toString(physicalRecord, valueOffset, valueLength);
		}

		return value;
	}
	
	public final static String getValue(byte[] buf, int offset, int len) {
		int pcrSize = ((buf[offset] & PCR_SIZE_MASK) + 1);
		int valueOffset = 1 + pcrSize;
		int valueLength = len - valueOffset;
		String value = "";

		if (valueLength > 0) {
			if (offset == 0 && buf.length == len) {
				// probably an external value -> do not use an arraycopy again
				value = Calc.toString(buf, valueOffset, valueLength);
			} else {
				byte[] stringBytes = new byte[valueLength];
				System.arraycopy(buf, offset + valueOffset, stringBytes, 0, valueLength);
				value = Calc.toString(stringBytes);
			}
		}

		return value;
	}

	public final static Atomic getTypedValue(byte[] physicalRecord) {
		String untypedValue = getValue(physicalRecord);
		byte type = getType(physicalRecord);
		return getTypedValue(type, untypedValue);
	}
	
	public final static Atomic getTypedValue(byte[] buf, int offset, int len) {
		String untypedValue = getValue(buf, offset, len);
		byte type = getType(buf, offset, len);
		return getTypedValue(type, untypedValue);
	}
	
	public final static Atomic getTypedValue(byte[] buf, int offset, int len, byte type) {
		String untypedValue = getValue(buf, offset, len);
		return getTypedValue(type, untypedValue);
	}
	
	private static final Atomic getTypedValue(byte type, String untypedValue) {
		// default type mapping
		if (type == Kind.COMMENT.ID || type == Kind.PROCESSING_INSTRUCTION.ID) {
			return new Str(untypedValue);
		} else {
			return new Una(untypedValue);
		}
	}

	public static final byte[] createRecord(int PCR, byte type, String value) {
		int valueLength = 0;
		int pcrLength = 0;
		byte[] valueBytes = null;
		byte[] pcrBytes = null;

		if (value != null) {
			valueBytes = Calc.fromString(value);
			valueLength = valueBytes.length;
		}

		if (PCR > 0) {
			pcrBytes = Calc.fromUIntVar(PCR);
			pcrLength = pcrBytes.length;
		}

		byte[] physicalRecord = new byte[1 + pcrLength + valueLength];

		setType(physicalRecord, type);

		if (value != null) {
			System.arraycopy(valueBytes, 0, physicalRecord, 1 + pcrLength,
					valueLength);
		}

		if (PCR > 0) {
			System.arraycopy(pcrBytes, 0, physicalRecord, 1, pcrLength);
			setPCRsize(physicalRecord, pcrLength);
		}

		return physicalRecord;
	}

	public final static int getPCRsize(byte[] physicalRecord) {
		return ((physicalRecord[0] & PCR_SIZE_MASK) + 1);
	}

	private final static void setPCRsize(byte[] physicalRecord, int pcrSize) {
		physicalRecord[0] = (byte) ((physicalRecord[0] & ~PCR_SIZE_MASK) | (PCR_SIZE_MASK & (pcrSize - 1)));
	}

	public static final String toString(byte[] physicalRecord) {
		int pcr = getPCR(physicalRecord);
		String value = getValue(physicalRecord);

		if (value == null) {
			return String.format("(%s)", pcr);
		} else {
			return String.format("\"%s\"(%s)", value, pcr);
		}
	}

	@Override
	public final byte[] createPlaceHolderValue(byte[] value) {
		int PCR = getPCR(value);
		return createRecord(PCR, Kind.ELEMENT.ID, null);
	}

	@Override
	public final byte[] createPlaceHolderKey(byte[] key, int level) {
		XTCdeweyID deweyID = new XTCdeweyID(null, key);

		deweyID = deweyID.getAncestor(level);

		return deweyID.toBytes();
	}
}
