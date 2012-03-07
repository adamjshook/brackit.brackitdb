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

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketIndex;
import org.brackit.server.store.index.bracket.InsertController;
import org.brackit.xquery.node.parser.DefaultListener;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Martin Hiller
 * 
 */
public class BracketDocIndexListener extends DefaultListener<BracketNode>
		implements SubtreeListener<BracketNode> {

	private final ListenMode listenMode;

	private final OpenMode openMode;

	private final BracketIndex index;

	private final BracketLocator locator;
	
	private final boolean externalInsertCtrl;
	
	private final XTCdeweyID rootDeweyID;

	private InsertController insertCtrl;

	private BracketNode pendingElement;

	private int ancestorsToInsert;

	public BracketDocIndexListener(BracketLocator locator, XTCdeweyID rootDeweyID,
			ListenMode listenMode, OpenMode openMode) {
		this.locator = locator;
		this.index = locator.collection.store.index;
		this.listenMode = listenMode;
		this.openMode = openMode;
		this.ancestorsToInsert = 0;
		this.pendingElement = null;
		this.externalInsertCtrl = false;
		this.rootDeweyID = rootDeweyID;
	}

	public BracketDocIndexListener(ListenMode listenMode,
			InsertController insertCtrl) {
		this.locator = null;
		this.index = null;
		this.listenMode = listenMode;
		this.openMode = null;
		this.insertCtrl = insertCtrl;
		this.ancestorsToInsert = 0;
		this.pendingElement = null;
		this.externalInsertCtrl = true;
		this.rootDeweyID = insertCtrl.getStartInsertKey();
	}

	@Override
	public void end() throws DocumentException {
		if (!externalInsertCtrl && insertCtrl != null) {
			throw new DocumentException("End before endFragment");
		}
	}

	@Override
	public void attribute(BracketNode node) throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertAttribute(node);
			break;
		default:
			// ignore attribute
		}
	}

	@Override
	public void startElement(BracketNode node) throws DocumentException {
		checkForPendingElement(node);

		switch (listenMode) {
		case INSERT:
			insertElement(node);
			break;
		default:
			// ignore element
		}
	}

	@Override
	public void fail() throws DocumentException {
		if (!externalInsertCtrl && insertCtrl != null) {
			try {
				insertCtrl.close();
			} catch (IndexAccessException e) {
				throw new DocumentException(e);
			}
			
			insertCtrl = null;
		}
	}

	@Override
	public void text(BracketNode node) throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertText(node);
			break;
		default:
			// ignore text
		}
	}

	@Override
	public void comment(BracketNode node) throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertText(node);
			break;
		default:
			// ignore text
		}
	}

	@Override
	public void processingInstruction(BracketNode node)
			throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertText(node);
			break;
		default:
			// ignore text
		}
	}

	private void insertText(BracketNode node) throws DocumentException {
		checkForPendingElement(node);

		byte[] textRecord = ElRecordAccess.createRecord(node.getPCR(),
				node.getKind().ID, node.getValue().stringValue());
		insertRecord(node, textRecord);
	}

	private void insertRecord(BracketNode node, byte[] record)
			throws DocumentException {
		try {
			XTCdeweyID deweyID = node.getDeweyID();

			if (insertCtrl == null) {
				if (externalInsertCtrl) {
					throw new DocumentException("External InsertController is null!");
				}
				insertCtrl = index.openForInsert(locator.collection.getTX(),
						locator.rootPageID, openMode, rootDeweyID);
			}
			
			insertCtrl.insert(deweyID, record, ancestorsToInsert);
			ancestorsToInsert = 0;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private void insertAttribute(BracketNode node) throws DocumentException {
		checkForPendingElement(node);

		byte[] attributeRecord = ElRecordAccess.createRecord(node.getPCR(),
				Kind.ATTRIBUTE.ID, node.getValue().stringValue());
		insertRecord(node, attributeRecord);
	}

	private void insertElement(BracketNode node) throws DocumentException {
		pendingElement = node;
	}

	private void checkForPendingElement(BracketNode node)
			throws DocumentException {

		if (pendingElement != null) {
			if (pendingElement.getDeweyID().isAncestorOf(node.getDeweyID())) {
				ancestorsToInsert++;
			} else {
				writeEmptyElement();
			}
		}

		pendingElement = null;
	}

	private void writeEmptyElement() throws DocumentException {
		byte[] physicalElement = ElRecordAccess.createRecord(
				pendingElement.getPCR(), Kind.ELEMENT.ID, null);
		insertRecord(pendingElement, physicalElement);
	}

	public void beginFragment() throws DocumentException {
		this.pendingElement = null;
		this.ancestorsToInsert = 0;
	}

	public void endFragment() throws DocumentException {
		if (pendingElement != null) {
			writeEmptyElement();
			pendingElement = null;
		}

		if (!externalInsertCtrl && insertCtrl != null) {
			try {
				insertCtrl.close();
			} catch (IndexAccessException e) {
				throw new DocumentException(e);
			}

			insertCtrl = null;
		}
	}
	
	@Override
	public void startDocument() throws DocumentException {
		this.ancestorsToInsert++;
	}
	
	@Override
	public void endDocument() throws DocumentException {
		if (pendingElement != null) {
			writeEmptyElement();
			pendingElement = null;
		}
	}
}