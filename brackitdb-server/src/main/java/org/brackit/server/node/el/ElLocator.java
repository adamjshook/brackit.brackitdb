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

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public final class ElLocator {

	public final DocID docID;

	public final PageID rootPageID;

	public final PathSynopsisMgr pathSynopsis;

	public final ElCollection collection;

	public ElLocator(ElCollection collection, DocID docID, PageID rootPageID) {
		this.docID = docID;
		this.rootPageID = rootPageID;
		this.collection = collection;
		this.pathSynopsis = collection.getPathSynopsis();
	}

	public ElLocator(ElCollection collection, ElLocator locator) {
		this.docID = locator.docID;
		this.rootPageID = locator.rootPageID;
		this.collection = collection;
		this.pathSynopsis = locator.pathSynopsis;
	}

	public ElNode fromBytes(XTCdeweyID deweyID, byte[] record)
			throws DocumentException {
		int pcr = ElRecordAccess.getPCR(record);
		PSNode psn = pathSynopsis.get(pcr);
		int dist = deweyID.getLevel() - psn.getLevel();

		if ((dist == 1)) {
			return new ElNode(this, deweyID, ElRecordAccess.getType(record),
					ElRecordAccess.getTypedValue(record), psn);
		} else if (dist <= 0) {
			while (dist++ < 0) {
				psn = psn.getParent();
			}
			return new ElNode(this, deweyID, Kind.ELEMENT.ID, null, psn);
		} else {
			throw new DocumentException(
					"Node %s has level %s but PCR %s has level %s", deweyID,
					deweyID.getLevel(), pcr, psn.getLevel());
		}
	}
}