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
package org.brackit.server.node.index.name.impl;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.store.Field;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.xdm.DocumentException;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class NameDirectoryEncoderImpl implements NameDirectoryEncoder {
	
	/**
	 * Represents the qualified name of a node as a pair of namespace uri and 
	 * local name, translated from strings to vocIds
	 * 
	 * @author Max Bechtold
	 *
	 */
	public static class QVocID implements Comparable<QVocID> {
		public final int uriVocID;
		public final int nameVocID;

		private QVocID(int uriVocID, int nameVocID) {
			this.uriVocID = uriVocID;
			this.nameVocID = nameVocID;
		}
		
		public static QVocID fromQNm(Tx tx, DictionaryMgr dictionary, 
				QNm name) throws DocumentException {
			return new QVocID(dictionary.translate(tx, name.nsURI),
					dictionary.translate(tx, name.localName));
		}
		
		@Override
		public int compareTo(QVocID o) {
			if (uriVocID < o.uriVocID) {
				return -1;
			}
			if (uriVocID > o.uriVocID) {
				return 1;
			}
			if (nameVocID < o.nameVocID) {
				return -1;
			}
			if (nameVocID > o.nameVocID) {
				return 1;
			}
			return 0;
		}
		
		@Override
		public String toString() {
			return String.format("%s (%d, %d)", getClass().getSimpleName(),
					uriVocID, nameVocID);
		}
	}
	
	@Override
	public PageID decodePageID(byte[] value) {
		return new PageID(Calc.toInt(value, 1, value.length - 1));
	}

	@Override
	public byte[] encodeKey(QVocID qVocID) {
		return Calc.fromQVocID(qVocID);
	}

	@Override
	public byte[] encodeValue(PageID pageID) {
		byte[] pageIDBytes = pageID.getBytes();
		return pageIDBytes;
	}

	@Override
	public Field getKeyType() {
		return Field.QVOCID;
	}

	@Override
	public Field getValueType() {
		return Field.PAGEID;
	}

	@Override
	public boolean sortRequired() {
		return true;
	}
}
