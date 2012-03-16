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
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.SubtreeDeleteListener;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Martin Hiller
 *
 */
public class SubtreeDeleteListenerImpl implements SubtreeDeleteListener {

	private final int OPEN_ELEMENTS_INITIAL_SIZE = 16;
	private final int PENDING_ELEMENTS_INITIAL_SIZE = 16;
	
	private final BracketLocator locator;
	private final SubtreeListener<? super BracketNode>[] listener;
	
	private XTCdeweyID subtreeRoot = null;
	
	private BracketNode[] openElements = new  BracketNode[OPEN_ELEMENTS_INITIAL_SIZE];
	private int openElementsLength = 0;
	
	private XTCdeweyID[] pendingElements = new XTCdeweyID[PENDING_ELEMENTS_INITIAL_SIZE];
	private int pendingElementsLevel = 0;
	private int pendingElementsLength = 0;
	
	public SubtreeDeleteListenerImpl(BracketLocator locator, SubtreeListener<? super BracketNode>[] listener) {
		this.locator = locator;
		this.listener = listener;
	}
	
	@Override
	public void deleteNode(XTCdeweyID deweyID, byte[] value, int level) throws IndexOperationException
	{
		try {
		
			if (value == null) {
				// add current node to pending elements
				addPendingElement(deweyID, level);
			} else {
				processPendingElements(value);
				deleteNodeInternal(deweyID, value, level);
			}
		
		} catch (DocumentException e) {
			throw new IndexOperationException(e, "Error notifying listeners about node deletion.");
		}
	}
	
	private void deleteNodeInternal(XTCdeweyID deweyID, byte[] value, int level) throws DocumentException {
		// assert(value != null)
		
		if (subtreeRoot == null) {
			// first node to delete -> subtreeRoot
			subtreeRoot = deweyID;
			for (SubtreeListener<? super BracketNode> subLis : listener) {
				subLis.begin();
				subLis.beginFragment();
			}
		}
		
		BracketNode node = locator.bracketNodeLoader.load(deweyID, value);
		
		Kind kind = node.getKind();
		
		if (kind == Kind.ATTRIBUTE) {
			for (SubtreeListener<? super BracketNode> subLis : listener) {
				subLis.attribute(node);
			}
		} else {
			// close elements
			endElements(openElementsLength - level);
			
			if (kind == Kind.ELEMENT) {
				startElement(node);
				for (SubtreeListener<? super BracketNode> subLis : listener) {
					subLis.startElement(node);
				}
			} else if (kind == Kind.TEXT) {
				for (SubtreeListener<? super BracketNode> subLis : listener) {
					subLis.text(node);
				}
			} else if (kind == Kind.COMMENT) {
				for (SubtreeListener<? super BracketNode> subLis : listener) {
					subLis.comment(node);
				}
			} else if (kind == Kind.PROCESSING_INSTRUCTION) {
				for (SubtreeListener<? super BracketNode> subLis : listener) {
					subLis.processingInstruction(node);
				}
			}
		}		
	}
	
	private void addPendingElement(XTCdeweyID deweyID, int level) {
		
		if (pendingElementsLength == pendingElements.length) {
			// increase buffer
			XTCdeweyID[] temp = new XTCdeweyID[(pendingElementsLength * 3) / 2];
			System.arraycopy(pendingElements, 0, temp, 0, pendingElementsLength);
			pendingElements = temp;
		}
		
		if (pendingElementsLength == 0) {
			// store only level of first pending element (-> levels of subsequent pending elements are just incremented)
			pendingElementsLevel = level;
		}
		
		pendingElements[pendingElementsLength] = deweyID;
		pendingElementsLength++;
	}
	
	private void processPendingElements(byte[] value) throws DocumentException {
		
		for (int i = 0; i < pendingElementsLength; i++) {
			deleteNodeInternal(pendingElements[i], value, pendingElementsLevel);
			pendingElements[i] = null;
			pendingElementsLevel++;
		}
		
		// clear pendingElements
		pendingElementsLength = 0;
		pendingElementsLevel = 0;
	}
	
	private void startElement(BracketNode node) {
		
		if (openElementsLength == openElements.length) {
			// increase buffer
			BracketNode[] temp = new BracketNode[(openElementsLength * 3) / 2];
			System.arraycopy(openElements, 0, temp, 0, openElementsLength);
			openElements = temp;
		}
		
		openElements[openElementsLength] = node;
		openElementsLength++;
	}
	
	private void endElements(int number) throws DocumentException {
		
		for (int i = 0; i < number; i++) {
			openElementsLength--;
			for (SubtreeListener<? super BracketNode> subLis : listener) {
				subLis.endElement(openElements[openElementsLength]);
			}
			openElements[openElementsLength] = null;
		}
	}

	@Override
	public void subtreeEnd() throws IndexOperationException
	{
		try {
		
			if (subtreeRoot == null) {
				// subtree deletion did not start yet
				return;
			}
			
			// close opened elements
			endElements(openElementsLength);
			
			// end subtree
			for (SubtreeListener<? super BracketNode> subLis : listener) {
				subLis.endFragment();
				subLis.end();
			}
			
			subtreeRoot = null;
		} catch (DocumentException e) {
			throw new IndexOperationException(e, "Error notifying listeners about node deletion.");
		}
	}
}
