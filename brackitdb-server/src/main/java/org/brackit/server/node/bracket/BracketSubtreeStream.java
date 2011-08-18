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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketIndex;
import org.brackit.server.store.index.bracket.BracketIter;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Martin Hiller
 * 
 */
public class BracketSubtreeStream implements Stream<BracketNode> {

	private static final Logger log = Logger
			.getLogger(BracketSubtreeStream.class);
	private final XTCdeweyID subtreeRootDeweyID;
	private final BracketIndex index;
	private BracketIter iterator;
	private BracketNode next;
	private BracketLocator locator;
	private boolean first;
	private final boolean isDocument;

	public BracketSubtreeStream(BracketLocator locator, XTCdeweyID subtreeRoot)
			throws DocumentException {
		this.subtreeRootDeweyID = subtreeRoot;
		this.isDocument = subtreeRootDeweyID.isDocument();
		this.locator = locator;
		this.index = locator.collection.store.index;

		open();
	}

	private void open() throws DocumentException {
		XTCdeweyID openDeweyID = isDocument ? XTCdeweyID
				.newRootID(subtreeRootDeweyID.getDocID()) : subtreeRootDeweyID;

		try {
			iterator = index.open(locator.collection.getTX(),
					locator.rootPageID, NavigationMode.TO_KEY, openDeweyID,
					OpenMode.READ);
			first = true;

			if (iterator == null) {
				throw new DocumentException("No record found with key %s.",
						openDeweyID);
			}

			if (isDocument) {
				next = locator.collection.getDocument(locator.docID);
			} else {
				readNext();
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public boolean hasNext() throws DocumentException {
		try {
			if (next != null) {
				return true;
			}

			if (iterator == null) {
				return false;
			}

			if (first || iterator.next()) {
				readNext();
				return (next != null);
			} else {
				close();
				return false;
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private void readNext() throws IndexAccessException, DocumentException {
		XTCdeweyID currentDeweyID = iterator.getKey();

		if (subtreeRootDeweyID.isPrefixOf(currentDeweyID)) {
			byte[] physicalRecord = iterator.getValue();
			next = locator.fromBytes(currentDeweyID, physicalRecord);
		}

		first = false;
	}

	@Override
	public void close() {
		if (iterator != null) {
			try {
				iterator.close();
				iterator = null;
			} catch (IndexAccessException e) {
				log.error(e);
			}
		}
	}

	@Override
	public BracketNode next() throws DocumentException {
		if ((next == null) && (!hasNext())) {
			close();
			return null;
		}

		BracketNode out = next;
		next = null;
		return out;
	}

}
