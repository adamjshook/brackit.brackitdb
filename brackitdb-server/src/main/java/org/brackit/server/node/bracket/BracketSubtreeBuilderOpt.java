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
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.node.bracket;

import org.brackit.server.metadata.pathSynopsis.NsMapping;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.InsertController;
import org.brackit.server.store.page.bracket.BracketKey;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.SubtreeHandler;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * Optimized BracketSubtreeBuilder with automatic BracketIndex insertions. This
 * implementation should be preferred if the only listener is the
 * BracketDocIndexListener. It allows this class to skip constructing
 * unnecessary BracketNode objects.
 * 
 * @author Martin Hiller
 * 
 */
public class BracketSubtreeBuilderOpt implements SubtreeHandler {

	private InsertController insertCtrl;
	private int ancestorsToInsert;
	private final XTCdeweyID rootDeweyID;
	private final boolean docMode;

	private final DeweyIDBuffer currentDeweyID;
	private final BracketKey currentKey;
	private boolean updateDeweyID;

	private final DictionaryMgr dictionary;
	private final PathSynopsisMgr psMgr;
	private final Tx tx;
	private NsMapping nsMapping;

	private PSNode parentPSNode;

	private boolean newSubtree = true;

	public BracketSubtreeBuilderOpt(BracketCollection collection,
			InsertController insertCtrl, int parentPCR)
			throws DocumentException {
		this.insertCtrl = insertCtrl;
		this.ancestorsToInsert = 0;
		this.rootDeweyID = insertCtrl.getStartInsertKey();
		this.docMode = rootDeweyID.isDocument();
		this.currentDeweyID = new DeweyIDBuffer(rootDeweyID);
		this.currentKey = new BracketKey();
		this.updateDeweyID = false;

		this.tx = collection.getTX();
		this.dictionary = collection.getDictionary();
		this.psMgr = collection.pathSynopsis.spawnBulkPsManager();
		if (parentPCR != -1) {
			this.parentPSNode = psMgr.get(parentPCR);
		}
	}

	@Override
	public void startDocument() throws DocumentException {
	}

	@Override
	public void endDocument() throws DocumentException {
	}

	@Override
	public void text(Atomic content) throws DocumentException {
		textOrComment(true, content);
	}

	@Override
	public void comment(Atomic content) throws DocumentException {
		textOrComment(false, content);
	}

	private void textOrComment(boolean text, Atomic content)
			throws DocumentException {

		// update DeweyID
		if (newSubtree) {
			currentKey.set(0, 0, 0, BracketKey.Type.DATA);
		} else {
			currentKey.set(1, 0, 0, BracketKey.Type.DATA);
		}
		updateDeweyID();

		// create record
		byte[] textRecord = ElRecordAccess.createRecord(getParentPCR(),
				text ? Kind.TEXT.ID : Kind.COMMENT.ID, content.stringValue());

		// insert
		try {
			insertCtrl.insert(currentDeweyID.getDeweyID(), textRecord,
					ancestorsToInsert);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		ancestorsToInsert = 0;
		newSubtree = false;
	}

	@Override
	public void processingInstruction(QNm target, Atomic content)
			throws DocumentException {

		// update DeweyID
		if (newSubtree) {
			currentKey.set(0, 0, 0, BracketKey.Type.DATA);
		} else {
			currentKey.set(1, 0, 0, BracketKey.Type.DATA);
		}

		// create PS node
		if (nsMapping != null) {
			throw new RuntimeException();
		}
		PSNode psNode = psMgr.getChild(getParentPCR(), target,
				Kind.PROCESSING_INSTRUCTION.ID, null);

		// create record
		byte[] record = ElRecordAccess.createRecord(psNode.getPCR(),
				Kind.PROCESSING_INSTRUCTION.ID, content.stringValue());

		// insert
		try {
			insertCtrl.insert(currentDeweyID.getDeweyID(), record,
					ancestorsToInsert);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		ancestorsToInsert = 0;
		newSubtree = false;
	}

	@Override
	public void startElement(QNm name) throws DocumentException {

		// update DeweyID
		if (newSubtree) {
			currentKey.set(0, 0, 0, BracketKey.Type.DATA);
		} else {
			currentKey.set(1, 0, 0, BracketKey.Type.DATA);
		}
		updateDeweyID();

		// create PS node
		parentPSNode = psMgr.getChild(getParentPCR(), name, Kind.ELEMENT.ID,
				nsMapping);
		nsMapping = null;

		ancestorsToInsert++;
		newSubtree = true;
	}

	@Override
	public void endElement(QNm name) throws DocumentException {

		if (ancestorsToInsert > 0) {
			// insert empty element
			byte[] emptyElement = ElRecordAccess.createRecord(getParentPCR(),
					Kind.ELEMENT.ID, null);

			try {
				insertCtrl.insert(currentDeweyID.getDeweyID(), emptyElement,
						ancestorsToInsert - 1);
			} catch (IndexAccessException e) {
				throw new DocumentException(e);
			}
		}

		// update DeweyID
		currentDeweyID.setAttributeToRelatedElement();
		if (!newSubtree) {
			currentDeweyID.setToParent();
		}

		// set parent PSNode
		parentPSNode = parentPSNode.getParent();

		ancestorsToInsert = 0;
		newSubtree = false;
	}

	@Override
	public void attribute(QNm name, Atomic value) throws DocumentException {

		// update DeweyID
		currentKey.set(0, 0, 0, BracketKey.Type.ATTRIBUTE);
		updateDeweyID();

		// create PS node
		if (nsMapping != null) {
			throw new RuntimeException();
		}
		PSNode psNode = psMgr.getChild(getParentPCR(), name, Kind.ATTRIBUTE.ID,
				null);

		// create record
		byte[] attributeRecord = ElRecordAccess.createRecord(psNode.getPCR(),
				Kind.ATTRIBUTE.ID, value.stringValue());

		// insert
		try {
			insertCtrl.insert(currentDeweyID.getDeweyID(), attributeRecord,
					ancestorsToInsert);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		ancestorsToInsert = 0;
	}

	@Override
	public void begin() throws DocumentException {
	}

	@Override
	public void end() throws DocumentException {
	}

	@Override
	public void fail() throws DocumentException {
	}

	@Override
	public void beginFragment() throws DocumentException {

		ancestorsToInsert = 0;
		newSubtree = true;

		if (docMode) {
			// store each fragment as new document
			currentKey.set(0, 0, 0, BracketKey.Type.DOCUMENT);
			updateDeweyID();
			ancestorsToInsert++;
		}
	}

	@Override
	public void endFragment() throws DocumentException {
	}

	private final void updateDeweyID() {
		if (updateDeweyID) {
			currentDeweyID.update(currentKey, false);
		}
		updateDeweyID = true;
	}

	@Override
	public void startMapping(String prefix, String uri)
			throws DocumentException {

		int prefixVocID = (prefix == null || prefix.isEmpty() ? -1 : dictionary
				.translate(tx, prefix));
		int uriVocID = (uri.isEmpty() ? -1 : dictionary.translate(tx, uri));

		if (nsMapping == null) {
			nsMapping = new NsMapping(prefixVocID, uriVocID);
		} else {
			nsMapping.addPrefix(prefixVocID, uriVocID);
		}
	}

	@Override
	public void endMapping(String prefix) throws DocumentException {
	}

	private int getParentPCR() {
		return parentPSNode != null ? parentPSNode.getPCR() : -1;
	}
}
