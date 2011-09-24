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
package org.brackit.server.node.bracket;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.store.index.bracket.page.BracketNodeLoader;
import org.brackit.server.store.page.bracket.RecordInterpreter;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Martin Hiller
 * 
 */
public class BracketLocator {

	public final DocID docID;

	public final PageID rootPageID;

	public final PathSynopsisMgr pathSynopsis;

	public final BracketCollection collection;

	private final class BracketNodeLoaderImpl implements BracketNodeLoader {
		@Override
		public BracketNode load(XTCdeweyID deweyID, RecordInterpreter record) throws DocumentException {

			int pcr = record.getPCR();
			PSNode psn = pathSynopsis.get(collection.getTX(), pcr);
			int dist = deweyID.getLevel() - psn.getLevel();

			if ((dist == 1)) {
				return new BracketNode(BracketLocator.this, deweyID,
						record.getType(),
						record.getValue(), psn);
			} else if (dist <= 0) {
				while (dist++ < 0) {
					psn = psn.getParent();
				}
				return new BracketNode(BracketLocator.this, deweyID,
						Kind.ELEMENT.ID, null, psn);
			} else {
				throw new DocumentException(
						"Node %s has level %s but PCR %s has level %s",
						deweyID, deweyID.getLevel(), pcr, psn.getLevel());
			}
		}
	}
	public BracketNodeLoader bracketNodeLoader;

	public BracketLocator(BracketCollection collection, DocID docID,
			PageID rootPageID) {
		this.docID = docID;
		this.rootPageID = rootPageID;
		this.collection = collection;
		this.pathSynopsis = collection.getPathSynopsis();
		this.bracketNodeLoader = new BracketNodeLoaderImpl();
	}

	public BracketLocator(BracketCollection collection, BracketLocator locator) {
		this.docID = locator.docID;
		this.rootPageID = locator.rootPageID;
		this.collection = collection;
		this.pathSynopsis = locator.pathSynopsis;
		this.bracketNodeLoader = new BracketNodeLoaderImpl();
	}

	public BracketNode fromBytes(XTCdeweyID deweyID, byte[] record)
			throws DocumentException {
		int pcr = ElRecordAccess.getPCR(record);
		PSNode psn = pathSynopsis.get(collection.getTX(), pcr);
		int dist = deweyID.getLevel() - psn.getLevel();

		if ((dist == 1)) {
			return new BracketNode(this, deweyID,
					ElRecordAccess.getType(record),
					ElRecordAccess.getValue(record), psn);
		} else if (dist <= 0) {
			while (dist++ < 0) {
				psn = psn.getParent();
			}
			return new BracketNode(this, deweyID, Kind.ELEMENT.ID, null, psn);
		} else {
			throw new DocumentException(
					"Node %s has level %s but PCR %s has level %s", deweyID,
					deweyID.getLevel(), pcr, psn.getLevel());
		}
	}
}
