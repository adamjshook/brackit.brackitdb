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
package org.brackit.server.store;

import java.util.Arrays;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.converter.PSNodeRecordAccess;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.util.Calc;
import org.brackit.xquery.atomic.QNm;

/**
 * Various standard field types used in the system.
 * 
 * @author Sebastian Baechle
 * 
 */
public class Field {
	/**
	 * Undefined default key
	 */
	public static final Field NULL = new Field();

	public static final UIntVarField UINTEGER = new UIntVarField();

	public static final IntegerField INTEGER = new IntegerField();

	public static final LongField LONG = new LongField();

	public static final FloatField FLOAT = new FloatField();

	public static final DoubleField DOUBLE = new DoubleField();

	public static final BigIntegerField BIGINTEGER = new BigIntegerField();

	public static final BigDecimalField BIGDECIMAL = new BigDecimalField();

	public static final StringField STRING = new StringField();

	public static final QVocIDField QVOCID = new QVocIDField();

	/**
	 * Standard byte array
	 */
	public static final ByteArrayField BYTEARRAY = new ByteArrayField();

	/**
	 * {@link PageID}
	 */
	public static final PageIDField PAGEID = new PageIDField();

	/**
	 * {@link XTCdeweyID}
	 */
	public static final DeweyIDField DEWEYID = new DeweyIDField();

	/**
	 * {@link DocID} | {@link XTCdeweyID}
	 */
	public static final FullDeweyIDField FULLDEWEYID = new FullDeweyIDField();

	/**
	 * DocumentNumber (within collection) | {@link XTCdeweyID}
	 */
	public static final CollectionDeweyIDField COLLECTIONDEWEYID = new CollectionDeweyIDField();

	/**
	 * {@link XTCdeweyID} | PCR
	 */
	public static final DeweyIDPCRField DEWEYIDPCR = new DeweyIDPCRField();

	/**
	 * {@link DocID} | {@link XTCdeweyID} | PCR
	 */
	public static FullDeweyIDPCRField FULLDEWEYIDPCR = new FullDeweyIDPCRField();

	/**
	 * PCR | {@link XTCdeweyID}
	 */
	public static final PCRDeweyIDField PCRDEWEYID = new PCRDeweyIDField();

	/**
	 * PCR | {@link DocID} | {@link XTCdeweyID}
	 */
	public static PCRFullDeweyIDField PCRFULLDEWEYID = new PCRFullDeweyIDField();

	public static final ElRecordField EL_REC = new ElRecordField();

	public static final PSRecordField PS_REC = new PSRecordField();

	/**
	 * Positive {@link Integer} (1 - 4 bytes)
	 */
	public static class UIntVarField extends Field {
		public byte[] fromInt(int i) {
			return Calc.fromUIntVar(i);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Integer.toString(Calc.toUIntVar(value));
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareUIntVar(v1, v2);
		}
	}

	/**
	 * {@link Integer} (4 bytes)
	 */
	public static class IntegerField extends Field {
		public byte[] fromInt(int i) {
			return Calc.fromInt(i);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Integer.toString(Calc.toInt(value));
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareInt(v1, v2);
		}
	}

	public static final class LongField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Long.toString(Calc.toLong(value));
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareLong(v1, v2);
		}
	}

	public static final class FloatField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Float.toString(Calc.toFloat(value));
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareFloat(v1, v2);
		}
	}

	public static final class DoubleField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Double.toString(Calc.toDouble(value));
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareDouble(v1, v2);
		}
	}

	public static final class BigIntegerField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Calc.toBigInteger(value).toString();
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareBigInteger(v1, v2);
		}
	}

	public static final class BigDecimalField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Calc.toBigDecimal(value).toString();
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareBigDecimal(v1, v2);
		}
	}

	/**
	 * {@link String}
	 */
	public static class StringField extends Field {
		public byte[] fromString(String s) {
			return Calc.fromString(s);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return Calc.toString(value);
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compare(v1, v2);
		}

		@Override
		public int compareAsPrefix(byte[] v1, byte[] v2) {
			return Calc.compareAsPrefix(v1, v2);
		}
	}

	/**
	 * {@link QNm}
	 * 
	 */
	public static class QVocIDField extends Field {
		// TODO Need customized compare methods?
	}

	public static final class ByteArrayField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return String.format("[%s bytes]", value.length);
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compare(v1, v2);
		}

		@Override
		public int compareAsPrefix(byte[] v1, byte[] v2) {
			return Calc.compareAsPrefix(v1, v2);
		}
	}

	public static final class PageIDField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			PageID pageID = PageID.fromBytes(value);
			return (pageID != null) ? pageID.toString() : null;
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareInt(v1, v2);
		}

	}

	public static final class DeweyIDField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return (new XTCdeweyID(null, value)).toString();
		}

		@Override
		public int compare(byte[] v1, byte[] v2) {
			return Calc.compareU(v1, v2);
		}

		@Override
		public int compareAsPrefix(byte[] v1, byte[] v2) {
			return Calc.compareUAsPrefix(v1, v2);
		}
	}

	public static final class FullDeweyIDField extends Field {

		public byte[] encode(XTCdeweyID deweyID) {
			byte[] tmp = deweyID.toBytes();
			byte[] b = new byte[DocID.getSize() + tmp.length];
			deweyID.docID.toBytes(b, 0);
			System.arraycopy(tmp, 0, b, DocID.getSize(), tmp.length);
			return b;
		}

		public XTCdeweyID decodeDeweyID(byte[] b) {
			DocID docID = DocID.fromBytes(b, 0);
			// TODO wrong end index? b.length
			return new XTCdeweyID(docID, Arrays.copyOfRange(b, DocID.getSize(),
					b.length - 3));
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return decodeDeweyID(value).toString();
		}

		@Override
		public int compare(byte[] value1, byte[] value2) {
			if (value1 != null) {
				if (value2 != null) {
					// compare DocID
					int diff = Calc.compareLong(value1, 0, value2, 0);
					if (diff != 0) {
						return diff;
					}
					// compare remaining DeweyID
					int len1 = value1.length - 8;
					int len2 = value2.length - 8;
					return Calc.compareU(value1, 8, len1, value2, 8, len2);
				} else {
					return -1;
				}
			} else if (value2 != null) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	/**
	 * This field includes a DeweyID together with its document number (within a
	 * certain collection).
	 */
	public static final class CollectionDeweyIDField extends Field {

		public byte[] encode(XTCdeweyID deweyID) {
			byte[] tmp = deweyID.toBytes();
			byte[] b = new byte[4 + tmp.length];
			Calc.fromInt(deweyID.docID.getDocNumber(), b, 0);
			System.arraycopy(tmp, 0, b, 4, tmp.length);
			return b;
		}

		public XTCdeweyID decode(int collectionID, byte[] b) {
			DocID docID = new DocID(collectionID, Calc.toInt(b, 0));
			return new XTCdeweyID(docID, Arrays.copyOfRange(b, 4, b.length));
		}

		public XTCdeweyID decode(int collectionID, byte[] b, int offset, int length) {
			DocID docID = new DocID(collectionID, Calc.toInt(b, offset));
			return new XTCdeweyID(docID, b, offset + 4, length - 4);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return decode(0, value).toString();
		}

		@Override
		public int compare(byte[] value1, byte[] value2) {
			if (value1 != null) {
				if (value2 != null) {
					// compare document number
					int diff = Calc.compareInt(value1, 0, value2, 0);
					if (diff != 0) {
						return diff;
					}
					// compare remaining DeweyID
					int len1 = value1.length - 4;
					int len2 = value2.length - 4;
					return Calc.compareU(value1, 4, len1, value2, 4, len2);
				} else {
					return -1;
				}
			} else if (value2 != null) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	public static final class DeweyIDPCRField extends Field {
		public byte[] encode(XTCdeweyID deweyID, int PCR) {
			byte[] tmp = deweyID.toBytes();
			byte[] b = new byte[4 + tmp.length];
			System.arraycopy(tmp, 0, b, 0, tmp.length);
			Calc.fromInt(PCR, b, tmp.length);
			return b;
		}

		public XTCdeweyID decodeDeweyID(DocID docID, byte[] b) {
			// TODO wrong end index? b.length - 4
			return new XTCdeweyID(docID, Arrays.copyOfRange(b, 0, b.length - 3));
		}

		public int decodePCR(byte[] b) {
			return Calc.toInt(b, b.length - 4, 4);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			int pcr = decodePCR(value);
			XTCdeweyID deweyID = decodeDeweyID(null, value);
			return String.format("%s(%s)", deweyID, pcr);
		}

		@Override
		public int compare(byte[] value1, byte[] value2) {
			if (value1 != null) {
				if (value2 != null) {
					// compare DeweyID
					int len1 = value1.length - 4;
					int len2 = value2.length - 4;
					return Calc.compareU(value1, 0, len1, value2, 0, len2);
					// no need to compare trailing PCR
				} else {
					return -1;
				}
			} else {
				return (value2 != null) ? 1 : 0;
			}
		}
	}

	public static class FullDeweyIDPCRField extends Field {
		public byte[] encode(XTCdeweyID deweyID, int PCR) {
			byte[] tmp = deweyID.toBytes();
			byte[] b = new byte[4 + DocID.getSize() + tmp.length];
			deweyID.docID.toBytes(b, 0);
			System.arraycopy(tmp, 0, b, DocID.getSize(), tmp.length);
			Calc.fromInt(PCR, b, DocID.getSize() + tmp.length);
			return b;
		}

		public XTCdeweyID decodeDeweyID(byte[] b) {
			DocID docID = DocID.fromBytes(b, 0);
			// TODO wrong end index? b.length - 4
			return new XTCdeweyID(docID, Arrays.copyOfRange(b, DocID.getSize(),
					b.length - 3));
		}

		public int decodePCR(byte[] b) {
			return Calc.toInt(b, b.length - 4, 4);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			int pcr = decodePCR(value);
			XTCdeweyID deweyID = decodeDeweyID(value);
			return String.format("%s(%s)", deweyID, pcr);
		}

		@Override
		public int compare(byte[] value1, byte[] value2) {
			if (value1 != null) {
				if (value2 != null) {
					// first compare docID
					int diff = Calc.compareLong(value1, 0, value2, 0);
					if (diff != 0) {
						return diff;
					}
					// compare remaining deweyID
					int len1 = value1.length - 12;
					int len2 = value2.length - 12;
					return Calc.compareU(value1, 8, len1, value2, 8, len2);
					// no need to compare trailing PCR
				} else {
					return -1;
				}
			} else {
				return (value2 != null) ? 1 : 0;
			}
		}
	}

	public static final class PCRDeweyIDField extends Field {
		public byte[] encode(XTCdeweyID deweyID, int PCR) {
			byte[] tmp = deweyID.toBytes();
			byte[] b = new byte[4 + tmp.length];
			Calc.fromInt(PCR, b, 0);
			System.arraycopy(tmp, 0, b, 4, tmp.length);
			decodeDeweyID(null, b);
			return b;
		}

		public XTCdeweyID decodeDeweyID(DocID docID, byte[] b) {
			byte[] copyOfRange = Arrays.copyOfRange(b, 4, b.length);
			return new XTCdeweyID(docID, copyOfRange);
		}

		public int decodePCR(byte[] b) {
			return Calc.toInt(b, 0, 4);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			int pcr = decodePCR(value);
			XTCdeweyID deweyID = decodeDeweyID(null, value);
			return String.format("%s(%s)", deweyID, pcr);
		}

		@Override
		public int compare(byte[] value1, byte[] value2) {
			if (value1 != null) {
				if (value2 != null) {
					// compare PCR
					int diff = Calc.compareInt(value1, 0, value2, 0);
					if (diff != 0) {
						return diff;
					}
					// compare remaining DeweyID
					int len1 = value1.length - 4;
					int len2 = value2.length - 4;
					return Calc.compareU(value1, 4, len1, value2, 4, len2);
				} else {
					return -1;
				}
			} else {
				return (value2 != null) ? 1 : 0;
			}
		}
	}

	public static final class PCRFullDeweyIDField extends Field {
		public byte[] encode(XTCdeweyID deweyID, int PCR) {
			byte[] tmp = deweyID.toBytes();
			byte[] b = new byte[4 + DocID.getSize() + tmp.length];
			Calc.fromInt(PCR, b, 0);
			deweyID.docID.toBytes(b, 4);
			System.arraycopy(tmp, 0, b, 4 + DocID.getSize(), tmp.length - 4
					- DocID.getSize());
			return b;
		}

		public XTCdeweyID decodeDeweyID(byte[] b) {
			DocID docID = DocID.fromBytes(b, 4);
			// TODO wrong start index? 4 + DocID.getSize()
			return new XTCdeweyID(docID, Arrays.copyOfRange(b, 4, b.length));
		}

		public int decodePCR(byte[] b) {
			return Calc.toInt(b, 0, 4);
		}

		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			int pcr = decodePCR(value);
			XTCdeweyID deweyID = decodeDeweyID(value);
			return String.format("%s(%s)", deweyID, pcr);
		}

		@Override
		public int compare(byte[] value1, byte[] value2) {
			if (value1 != null) {
				if (value2 != null) {
					// first compare PCR only
					int diff = Calc.compareInt(value1, 0, value2, 0);
					if (diff != 0) {
						return diff;
					}
					// compare DocID
					diff = Calc.compareInt(value1, 4, value2, 4);
					if (diff != 0) {
						return diff;
					}
					// compare remaining DeweyID
					int len1 = value1.length - 8;
					int len2 = value2.length - 8;
					return Calc.compareU(value1, 8, len1, value2, 8, len2);
				} else {
					return -1;
				}
			} else {
				return (value2 != null) ? 1 : 0;
			}
		}
	}

	public static final class ElRecordField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return ElRecordAccess.toString(value);
		}
	}

	public static final class PSRecordField extends Field {
		@Override
		public String toString(byte[] value) {
			if (value == null) {
				return null;
			}
			return PSNodeRecordAccess.valueToString(value);
		}
	}

	private static Field[] mapping;

	private static int idSequence;

	static {
		mapping = new Field[] { NULL, UINTEGER, INTEGER, LONG, FLOAT, DOUBLE,
				BIGINTEGER, BIGDECIMAL, STRING, QVOCID, BYTEARRAY, PAGEID,
				DEWEYID, FULLDEWEYID, DEWEYIDPCR, FULLDEWEYIDPCR, PCRDEWEYID,
				PCRFULLDEWEYID, EL_REC, PS_REC, };
		for (int i = 0; i < mapping.length; i++) {
			if (mapping[i].ID != i)
				throw new RuntimeException("Field: " + mapping[i].getClass()
						+ " has wrong position");
		}
	}

	public final int ID;

	private Field() {
		this.ID = idSequence++;
	}

	public static Field fromId(int id) {
		if ((id < 0) || (id > mapping.length)) {
			throw new RuntimeException(String.format(
					"There is no field type with id %s", id));
		}
		return mapping[id];
	}

	public String toString(byte[] b) {
		if (b == null) {
			return null;
		}
		String s = null;
		for (int i = 0; i < b.length; i++) {
			s = " " + b[i];
		}
		return s;
	}

	public int compare(byte[] v1, byte[] v2) {
		return Calc.compare(v1, v2);
	}

	public int compareAsPrefix(byte[] v1, byte[] v2) {
		throw new RuntimeException(
				"Prefix comparison undefined for fields of type "
						+ getClass().getSimpleName());
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}