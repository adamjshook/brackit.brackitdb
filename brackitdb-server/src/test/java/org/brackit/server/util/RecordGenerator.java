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
package org.brackit.server.util;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.xquery.node.parser.DefaultHandler;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Sebastian Baechle
 * 
 */
public abstract class RecordGenerator extends DefaultHandler {

	public final class Record {
		public final XTCdeweyID deweyID;
		public final byte[] record;

		private Record(final XTCdeweyID deweyID, final byte[] record) {
			this.deweyID = deweyID;
			this.record = record;
		}
	}

	private final List<Record> records = new ArrayList<Record>();

	protected final XTCdeweyID subtreeRootDeweyID;

	protected Record[] stack;

	protected int stackSize;

	protected int level;

	protected Record subtreeRoot;

	protected XTCdeweyID lastAttributeDeweyID;

	public RecordGenerator() {
		subtreeRootDeweyID = new XTCdeweyID(new DocID(4711));
		stack = new Record[20];
		stackSize = 0;
		level = 0;
	}

	protected Record insertRecord(XTCdeweyID deweyID, byte[] record) {
		Record r = new Record(deweyID, record);
		records.add(r);
		return r;
	}

	public List<Record> getRecords() {
		return records;
	}

	@Override
	public void text(String content) throws DocumentException {
		level++;
		lastAttributeDeweyID = null;
		Record node = pushNode(Kind.TEXT, null, content);

		if (level < (stackSize - 1)) {
			stack[--stackSize] = null;
		}
		level--;
	}

	@Override
	public void comment(String content) throws DocumentException {
		// root-level comments are not supported yet
		if (level == 0) {
			return;
		}

		level++;
		lastAttributeDeweyID = null;
		Record node = pushNode(Kind.COMMENT, null, content);

		if (level < (stackSize - 1)) {
			stack[--stackSize] = null;
		}
		level--;
	}

	@Override
	public void processingInstruction(String content) throws DocumentException {
		// processing instructions not working yet
		if (true) {
			return;
		}

		level++;
		lastAttributeDeweyID = null;
		Record node = pushNode(Kind.PROCESSING_INSTRUCTION, null, content);

		if (level < (stackSize - 1)) {
			stack[--stackSize] = null;
		}
		level--;
	}

	@Override
	public void startElement(String name) throws DocumentException {
		level++;
		lastAttributeDeweyID = null;
		// System.err.println("start element: Incremented level to " +
		// level);
		Record node = pushNode(Kind.ELEMENT, name, null);
	}

	@Override
	public void attribute(String name, String value) throws DocumentException {
		XTCdeweyID deweyID = lastAttributeDeweyID;

		if (subtreeRootDeweyID != null) {
			if (deweyID == null) {
				XTCdeweyID elementDeweyID = stack[stackSize - 1].deweyID;
				deweyID = elementDeweyID.getAttributeRootID().getNewChildID();
			} else {
				deweyID = XTCdeweyID.newBetween(deweyID, null);
			}
			lastAttributeDeweyID = deweyID;
		}

		Record node = buildAttribute(deweyID, name, value);
	}

	private Record pushNode(Kind kind, String name, String text)
			throws DocumentException {
		Record node = null;
		XTCdeweyID deweyID = null;

		if (subtreeRootDeweyID != null) {
			if (stackSize == 0) {
				deweyID = subtreeRootDeweyID;
			} else {
				if (stackSize == level) // new sibling at this level
				{
					deweyID = XTCdeweyID.newBetween(stack[--stackSize].deweyID,
							null);
					stack[stackSize] = null;
				} else // first child at this level
				{
					deweyID = stack[stackSize - 1].deweyID.getNewChildID();
				}
			}
		} else if (stackSize == level) {
			stack[--stackSize] = null;
		}

		if (kind == Kind.ELEMENT) {
			node = buildElement(deweyID, name);
		} else if (kind == Kind.TEXT) {
			node = buildText(deweyID, text);
		} else if (kind == Kind.COMMENT) {
			node = buildComment(deweyID, text);
		} else if (kind == Kind.PROCESSING_INSTRUCTION) {
			node = buildProcessingInstruction(deweyID, text);
		}

		if (stackSize == 0) {
			subtreeRoot = node;
		}

		if (++stackSize == stack.length) {
			Record[] newStack = new Record[(stackSize * 3) / 2 + 1];
			System.arraycopy(stack, 0, newStack, 0, stack.length);
			stack = newStack;
		}
		stack[stackSize - 1] = node;

		return node;
	}

	@Override
	public void endElement(String name) throws DocumentException {
		level--;
		lastAttributeDeweyID = null;
		// System.err.println("end element: Decremented level to " + level);

		if (level < (stackSize - 1)) {
			stack[--stackSize] = null;
		}
	}

	public abstract Record buildAttribute(XTCdeweyID deweyID, String name,
			String value) throws DocumentException;

	public abstract Record buildElement(XTCdeweyID deweyID, String name)
			throws DocumentException;

	public abstract Record buildText(XTCdeweyID deweyID, String value)
			throws DocumentException;

	public abstract Record buildComment(XTCdeweyID deweyID, String value)
			throws DocumentException;

	public abstract Record buildProcessingInstruction(XTCdeweyID deweyID,
			String value) throws DocumentException;
}
