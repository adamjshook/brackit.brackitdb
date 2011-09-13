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
package org.brackit.server.node.txnode;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.SubtreeHandler;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.parser.SubtreeProcessor;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;

/**
 * @author Sebastian Baechle
 * 
 */
public abstract class SubtreeBuilder<E extends TXNode<E>> extends
		SubtreeProcessor<E> implements SubtreeHandler {
	static final Logger log = Logger.getLogger(SubtreeBuilder.class);

	protected final E parent;

	protected final XTCdeweyID rootDeweyID;

	protected TXNode[] stack;

	protected int stackSize;

	protected int level;

	protected E subtreeRoot;

	protected XTCdeweyID lastAttributeDeweyID;

	public SubtreeBuilder(E parent, XTCdeweyID rootDeweyID,
			SubtreeListener<? super E>[] listeners) throws DocumentException {
		super(listeners);
		this.parent = parent;
		this.rootDeweyID = rootDeweyID;
	}

	protected abstract E buildElement(E parent, QNm name, XTCdeweyID deweyID)
			throws DocumentException;

	protected abstract E buildAttribute(E parent, QNm name, Atomic value,
			XTCdeweyID deweyID) throws DocumentException;

	protected abstract E buildText(E parent, Atomic text, XTCdeweyID deweyID)
			throws DocumentException;

	protected abstract E buildComment(E parent, Atomic text, XTCdeweyID deweyID)
			throws DocumentException;

	// TODO check params for PI's
	protected abstract E buildProcessingInstruction(E parent, Atomic text,
			XTCdeweyID deweyID) throws DocumentException;

	public E getSubtreeRoot() throws DocumentException {
		if (subtreeRoot == null) {
			throw new DocumentException("No subtree has been build.");
		}

		return subtreeRoot;
	}

	@Override
	public void startDocument() throws DocumentException {
		try {
			notifyBeginDocument();
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void endDocument() throws DocumentException {
		try {
			notifyEndDocument();
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void beginFragment() throws DocumentException {
		try {
			notifyEndFragment();
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void endFragment() throws DocumentException {
		try {
			notifyEndFragment();
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void begin() throws DocumentException {
		try {
			stack = new TXNode[20];
			stackSize = 0;
			level = 0;
			notifyBegin();
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void text(Atomic content) throws DocumentException {
		try {
			level++;
			lastAttributeDeweyID = null;
			E node = pushNode(Kind.TEXT, null, content);
			notifyText(node);

			if (level < (stackSize - 1)) {
				stack[--stackSize] = null;
			}
			level--;
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void comment(Atomic content) throws DocumentException {
		try {
			// root-level comments are not supported yet
			if (level == 0) {
				return;
			}

			level++;
			lastAttributeDeweyID = null;
			E node = pushNode(Kind.COMMENT, null, content);
			notifyComment(node);

			if (level < (stackSize - 1)) {
				stack[--stackSize] = null;
			}
			level--;
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void processingInstruction(Atomic content) throws DocumentException {
		try {
			// processing instructions not working yet
			if (true) {
				return;
			}

			level++;
			lastAttributeDeweyID = null;
			E node = pushNode(Kind.PROCESSING_INSTRUCTION, null, content);
			notifyProcessingInstruction(node);

			if (level < (stackSize - 1)) {
				stack[--stackSize] = null;
			}
			level--;
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void startElement(QNm name) throws DocumentException {
		try {
			level++;
			lastAttributeDeweyID = null;
			// System.err.println("start element: Incremented level to " +
			// level);
			E node = pushNode(Kind.ELEMENT, name, null);

			notifyStartElement(node);
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	public void attribute(QNm name, Atomic value) throws DocumentException {
		XTCdeweyID deweyID = lastAttributeDeweyID;

		if (rootDeweyID != null) {
			if (deweyID == null) {
				XTCdeweyID elementDeweyID = stack[stackSize - 1].getDeweyID();
				deweyID = elementDeweyID.getAttributeRootID().getNewChildID();
			} else {
				deweyID = XTCdeweyID.newBetween(deweyID, null);
			}
			lastAttributeDeweyID = deweyID;
		}

		E node = buildAttribute((E) ((stackSize > 0) ? stack[stackSize - 1]
				: null), name, value, deweyID);

		notifyAttribute(node);
	}

	private E pushNode(Kind kind, QNm name, Atomic text)
			throws DocumentException {
		E node = null;
		XTCdeweyID deweyID = null;

		if (rootDeweyID != null) {
			if (stackSize == 0) {
				deweyID = rootDeweyID;
			} else {
				if (stackSize == level) // new sibling at this level
				{
					deweyID = XTCdeweyID.newBetween(stack[--stackSize]
							.getDeweyID(), null);
					stack[stackSize] = null;
				} else // first child at this level
				{
					deweyID = stack[stackSize - 1].getDeweyID().getNewChildID();
				}
			}
		} else if (stackSize == level) {
			stack[--stackSize] = null;
		}

		E parent = (E) ((stackSize > 0) ? stack[stackSize - 1] : this.parent);

		if (kind == Kind.ELEMENT) {
			node = buildElement(parent, name, deweyID);
		} else if (kind == Kind.TEXT) {
			node = buildText(parent, text, deweyID);
		} else if (kind == Kind.COMMENT) {
			node = buildComment(parent, text, deweyID);
		} else if (kind == Kind.PROCESSING_INSTRUCTION) {
			node = buildProcessingInstruction(parent, text, deweyID);
		}

		if (stackSize == 0) {
			subtreeRoot = node;
		}

		if (++stackSize == stack.length) {
			E[] newStack = (E[]) new Node[(stackSize * 3) / 2 + 1];
			System.arraycopy(stack, 0, newStack, 0, stack.length);
			stack = newStack;
		}
		stack[stackSize - 1] = node;

		return node;
	}

	@Override
	public void endElement(QNm name) throws DocumentException {
		try {
			notifyEndElement((E) stack[stackSize - 1]);

			level--;
			lastAttributeDeweyID = null;
			// System.err.println("end element: Decremented level to " + level);

			if (level < (stackSize - 1)) {
				stack[--stackSize] = null;
			}
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void end() throws DocumentException {
		try {
			notifyEnd();
		} catch (DocumentException e) {
			notifyFail();
			throw e;
		}
	}

	@Override
	public void fail() throws DocumentException {
		notifyFail();
	}
}