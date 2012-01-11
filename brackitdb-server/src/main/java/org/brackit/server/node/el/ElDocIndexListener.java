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
package org.brackit.server.node.el;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.xquery.node.parser.DefaultListener;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElDocIndexListener extends DefaultListener<ElNode> implements
		SubtreeListener<ElNode> {
	private final ListenMode listenMode;

	private final OpenMode openMode;

	private final Elndex index;

	private final ElLocator locator;

	private ElIndexIterator iterator;

	private ElNode[] pendingElements;

	private int stackSize;

	public ElDocIndexListener(ElLocator locator, ListenMode listenMode,
			OpenMode openMode) {
		this.locator = locator;
		this.index = locator.collection.store.index;
		this.listenMode = listenMode;
		this.openMode = openMode;
		this.pendingElements = new ElNode[8];
		this.stackSize = 0;
	}

	@Override
	public void text(ElNode node) throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertText(node);
			break;
		default:
			// ignore text
		}
	}

	@Override
	public void comment(ElNode node) throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertText(node);
			break;
		default:
			// ignore text
		}
	}

	@Override
	public void processingInstruction(ElNode node) throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertText(node);
			break;
		default:
			// ignore text
		}
	}

	private void insertText(ElNode node) throws DocumentException {
		checkForPendingElement(node);

		byte[] textRecord = ElRecordAccess.createRecord(node.getPCR(), node
				.getKind().ID, node.getValue().stringValue());
		insertRecord(node, textRecord);
	}

	private void insertRecord(ElNode node, byte[] record)
			throws DocumentException {
		try {
			XTCdeweyID deweyID = node.getDeweyID();

			if (iterator == null) {
				iterator = index.open(locator.collection.getTX(),
						node.locator.rootPageID, SearchMode.GREATER_OR_EQUAL,
						deweyID.toBytes(), null, openMode);
				iterator.insertPrefixAware(deweyID.toBytes(), record, deweyID
						.getLevel() - 1);
			} else {
				iterator.next();
				iterator.insert(deweyID.toBytes(), record);
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void attribute(ElNode node) throws DocumentException {
		switch (listenMode) {
		case INSERT:
			insertAttribute(node);
			break;
		default:
			// ignore attribute
		}
	}

	private void insertAttribute(ElNode node) throws DocumentException {
		checkForPendingElement(node);

		byte[] attributeRecord = ElRecordAccess.createRecord(node.getPCR(),
				Kind.ATTRIBUTE.ID, node.getValue().stringValue());
		insertRecord(node, attributeRecord);
	}

	@Override
	public void startElement(ElNode node) throws DocumentException {
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
	public void beginFragment() throws DocumentException {
		this.pendingElements = new ElNode[8];
		this.stackSize = 0;
	}

	@Override
	public void endFragment() throws DocumentException {
		if (stackSize > 0) {
			writeEmptyElement();
		}

		if (iterator != null) {
			iterator.close();
			iterator = null;
		}
	}

	private void insertElement(ElNode node) {
		if (stackSize == pendingElements.length) {
			ElNode[] newPendingElements = new ElNode[(stackSize * 3) / 2 + 1];
			System.arraycopy(pendingElements, 0, newPendingElements, 0,
					stackSize);
			pendingElements = newPendingElements;
		}
		pendingElements[stackSize++] = node;
	}

	private void checkForPendingElement(ElNode node) throws DocumentException {
		if ((stackSize > 0)
				&& (!pendingElements[stackSize - 1].getDeweyID().isAncestorOf(
						node.getDeweyID()))) {
			writeEmptyElement();
		}

		stackSize = 0;
	}

	private void writeEmptyElement() throws DocumentException {
		ElNode pendingElement = pendingElements[--stackSize];
		byte[] physicalElement = ElRecordAccess.createRecord(pendingElement
				.getPCR(), Kind.ELEMENT.ID, null);
		insertRecord(pendingElement, physicalElement);
	}

	@Override
	public void end() throws DocumentException {
		if (iterator != null) {
			throw new DocumentException("End before endFragment");
		}
	}

	@Override
	public void fail() throws DocumentException {
		if (iterator != null) {
			iterator.close();
		}
	}
}