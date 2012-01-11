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
package org.brackit.server.node.el.encoder;

import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElCollection;
import org.brackit.server.node.el.ElLocator;
import org.brackit.server.node.el.ElNode;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.store.Field;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Karsten Schmidt
 * 
 */
public class PCRClusterPathEncoder implements IndexEncoder<ElNode> {
	private final ElCollection collection;

	private static final byte[] DUMMY_VALUE = new byte[0];

	public PCRClusterPathEncoder(ElCollection collection) {
		this.collection = collection;
	}

	@Override
	public ElNode decode(byte[] key, byte[] value) throws DocumentException {
		ElNode document = collection.getSingleDocument();
		XTCdeweyID deweyID;
		int pcr;

		if (document == null) {
			deweyID = Field.PCRFULLDEWEYID.decodeDeweyID(key);
			pcr = Field.PCRFULLDEWEYID.decodePCR(key);
			document = collection.getDocument(deweyID.getDocID());
		} else {
			deweyID = Field.PCRDEWEYID.decodeDeweyID(document.getID(), key);
			pcr = Field.PCRDEWEYID.decodePCR(key);
		}

		if (deweyID.isAttribute()) {
			return document.getNode(deweyID);
		}

		ElLocator locator = document.getLocator();
		PSNode psNode = locator.pathSynopsis.get(pcr);
		byte type = (deweyID.level == psNode.getLevel() ? Kind.ELEMENT.ID
				: Kind.TEXT.ID);

		return new ElNode(locator, deweyID, type, null, psNode);
	}

	@Override
	public byte[] encodeKey(ElNode node) throws DocumentException {
		if (collection.getSingleDocument() == null) {
			return Field.PCRFULLDEWEYID
					.encode(node.getDeweyID(), node.getPCR());
		} else {
			return Field.PCRDEWEYID.encode(node.getDeweyID(), node.getPCR());
		}
	}

	@Override
	public byte[] encodeValue(ElNode node) throws DocumentException {
		return DUMMY_VALUE;
	}

	@Override
	public Field getKeyType() {
		return (collection.getSingleDocument() != null) ? Field.PCRDEWEYID
				: Field.PCRFULLDEWEYID;
	}

	@Override
	public Field getValueType() {
		return Field.NULL;
	}

	@Override
	public int getUnitID() {
		return collection.getID().value();
	}

	@Override
	public boolean sortKey() {
		return true;
	}

	@Override
	public boolean sortValue() {
		return false;
	}
}