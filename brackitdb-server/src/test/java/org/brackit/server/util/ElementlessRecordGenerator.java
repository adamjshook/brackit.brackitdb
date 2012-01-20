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
package org.brackit.server.util;

import java.util.ArrayList;
import java.util.HashMap;

import org.brackit.server.metadata.vocabulary.ConcurrentVocIDMapping;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.OperationNotSupportedException;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElementlessRecordGenerator extends RecordGenerator {
	private static final byte NODETYPE_ATTRIBUTE = 0;

	private static final byte NODETYPE_ELEMENT = 1;

	private interface PsNode {
		public int getPCR();

		public int getVocID();

		public String getName();

		public int getLevel();

		public PsNode getParent();
	}

	private class PathSynopsisNode implements PsNode {
		protected final byte nodeType;
		protected final int pcr;
		protected final int vocId;
		protected final int level;
		protected final String name;
		protected final PathSynopsisNode parent;
		protected final PathSynopsis ps;
		protected PathSynopsisNode[] children;

		public PathSynopsisNode(int vocId, int pcr, String name, byte nodeType,
				PathSynopsisNode parent, PathSynopsis ps) {
			this.vocId = vocId;
			this.pcr = pcr;
			this.name = name;
			this.nodeType = nodeType;
			this.parent = parent;
			this.ps = ps;
			this.children = new PathSynopsisNode[0];

			if (parent != null) {
				parent.addChild(this);
				level = parent.getLevel() + 1;
			} else {
				level = 0;
			}
		}

		private void addChild(PathSynopsisNode newNode) {
			if (children.length == 0) {
				children = new PathSynopsisNode[] { newNode };
			} else {
				PathSynopsisNode[] newChildren = new PathSynopsisNode[children.length + 1];
				System.arraycopy(children, 0, newChildren, 0, children.length);
				newChildren[children.length] = newNode;
				children = newChildren;
			}
		}

		public PathSynopsisNode hasChild(int vocId, byte nodeType) {
			for (PathSynopsisNode child : children) {
				if (child.getNodeType() == nodeType
						&& child.getVocID() == vocId) {
					return child;
				}
			}

			return null;
		}

		public PathSynopsisNode[] getChildren() {
			return children;
		}

		public int getLevel() {
			return level;
		}

		public byte getNodeType() {
			return nodeType;
		}

		public PathSynopsisNode getParent() {
			return parent;
		}

		public int getPCR() {
			return pcr;
		}

		public String getName() {
			return name;
		}

		public int getVocID() {
			return vocId;
		}

		public PathSynopsis getPathSynopsis() {
			return this.ps;
		}

		@Override
		public String toString() {
			StringBuffer sBuff = new StringBuffer();
			int parentPcr = 0;
			if (this.getParent() != null) {
				parentPcr = this.getParent().getPCR();
			}
			sBuff.append("PSNode " + this.pcr + "; VocID: " + this.vocId
					+ "; NT: " + this.nodeType);
			if (parentPcr > 0)
				sBuff.append("Parent: " + parentPcr + "; ");
			else
				sBuff.append("Rootnode; ");
			sBuff.append("Level: " + this.getLevel() + "; ");
			if (children.length > 0)
				sBuff.append("Child(ren): ");
			for (int i = 0; i < children.length; i++) {
				sBuff.append(children[i]);

				if (children.length > i + 1) {
					sBuff.append(", ");
				}
			}
			return sBuff.toString();
		}
	}

	private class PathSynopsis {
		private PathSynopsisNode root;
		private HashMap<Integer, PathSynopsisNode> pcr_hash = new HashMap<Integer, PathSynopsisNode>();
		private int pcr = 0;

		public PathSynopsis() {
			this.pcr_hash.clear();
		}

		public PathSynopsisNode getNewNode(int pcr, String name, int vocId,
				byte nodeType, PathSynopsisNode parent) {
			PathSynopsisNode psN = new PathSynopsisNode(vocId, pcr, name,
					nodeType, parent, this);
			pcr_hash.put(pcr, psN);

			if (parent == null) {
				root = psN;
			}

			return psN;
		}

		public PathSynopsisNode getNewNode(String name, int vocId,
				byte nodeType, PathSynopsisNode parent) {
			return getNewNode(++pcr, name, vocId, nodeType, parent);
		}

		public PathSynopsisNode getNodeByPcr(int pcr) {
			return pcr_hash.get(pcr);
		}

		@Override
		public String toString() {
			StringBuffer buffer = new StringBuffer(
					"path synopsis (Format [PCR:VocID:Count] ):");

			ArrayList<StringBuffer> levelBuffers = new ArrayList<StringBuffer>();
			printNode(root, levelBuffers);

			for (StringBuffer buf : levelBuffers) {
				buffer.append("\n");
				buffer.append(buf);
			}
			buffer.append("\n Max PCR=" + this.pcr);
			return buffer.toString();
		}

		private void printNode(PathSynopsisNode node,
				ArrayList<StringBuffer> levelBuffers) {
			if (levelBuffers.size() - 1 < node.getLevel()) {
				levelBuffers.add(new StringBuffer());
			}

			for (PathSynopsisNode child : node.getChildren()) {
				printNode(child, levelBuffers);
			}

			StringBuffer buf = levelBuffers.get(node.getLevel());

			String str = String.format("[%s:%s]  ", node.getPCR(), (node
					.getNodeType() == NODETYPE_ATTRIBUTE) ? "@"
					+ node.getVocID() : node.getVocID());
			buf.append(str);

			if (levelBuffers.size() - 1 > node.getLevel()) // child level exists
			{
				int indent = levelBuffers.get(node.getLevel() + 1).length()
						- buf.length();
				for (int i = indent + 2; i > 0; i--)
					buf.append(" ");
			}
		}

		public int getChild(int parentPCR, int vocID, byte nodeType) {
			if (parentPCR != -1) {
				PathSynopsisNode parent = pcr_hash.get(parentPCR);

				for (PathSynopsisNode node : parent.getChildren()) {
					if ((node.getVocID() == vocID)
							&& (node.getNodeType() == nodeType)) {
						return node.getPCR();
					}

				}
			}

			return appendNode(parentPCR, vocID, nodeType);
		}

		private int appendNode(int parentPCR, int vocID, byte nodeType) {
			PathSynopsisNode node = null;
			boolean newNode = false;
			PathSynopsisNode parent = pcr_hash.get(parentPCR);

			if (parent != null) {
				// check if node already exists
				node = parent.hasChild(vocID, nodeType);
			} else if ((root != null)
					&& (root.getVocID() == vocID && root.getNodeType() == nodeType)) {
				node = root;
			}

			if (node == null) {
				String name = dictionary.resolve(vocID);
				node = getNewNode(name, vocID, nodeType, parent);

				if (parent == null && root == null) {
					root = node;
				}
			}

			return node.getPCR();
		}
	}

	private final ConcurrentVocIDMapping dictionary;

	private final PathSynopsis psMgr;

	private int stackSize;

	private int[] stack;

	public ElementlessRecordGenerator() {
		dictionary = new ConcurrentVocIDMapping(1000);
		psMgr = new PathSynopsis();
		stack = new int[10];
	}

	@Override
	public Record buildAttribute(XTCdeweyID deweyID, QNm name, Atomic value)
			throws DocumentException {
//		int vocID = dictionary.translate(name);
//		int pcr = (stackSize == 0) ? psMgr.getChild(-1, vocID,
//				NODETYPE_ATTRIBUTE) : psMgr.getChild(stack[stackSize - 1],
//				vocID, NODETYPE_ATTRIBUTE);
//		return insertRecord(deweyID, ElRecordAccess.createRecord(pcr,
//				Kind.ATTRIBUTE.ID, value));
		
		throw new OperationNotSupportedException();
	}

	@Override
	public void endElement(QNm name) throws DocumentException {
		super.endElement(name);
		stackSize--;
	}

	@Override
	public Record buildElement(XTCdeweyID deweyID, QNm name)
			throws DocumentException {
//		int vocID = dictionary.translate(name);
//		int pcr = (stackSize == 0) ? psMgr
//				.getChild(-1, vocID, NODETYPE_ELEMENT) : psMgr.getChild(
//				stack[stackSize - 1], vocID, NODETYPE_ELEMENT);
//
//		if (stackSize == stack.length) {
//			int[] newStack = new int[(stack.length * 3) / 2 + 1];
//			System.arraycopy(stack, 0, newStack, 0, stack.length);
//			stack = newStack;
//		}
//
//		stack[stackSize++] = pcr;
//
//		return insertRecord(deweyID, ElRecordAccess.createRecord(pcr,
//				Kind.ELEMENT.ID, null));
		
		throw new OperationNotSupportedException();
	}

	@Override
	public Record buildText(XTCdeweyID deweyID, Atomic value)
			throws DocumentException {
		return insertRecord(deweyID, ElRecordAccess.createRecord(
				stack[stackSize - 1], Kind.TEXT.ID, value.stringValue()));
	}

	@Override
	public Record buildComment(XTCdeweyID deweyID, Atomic value)
			throws DocumentException {
		return insertRecord(deweyID, ElRecordAccess.createRecord(
				stack[stackSize - 1], Kind.COMMENT.ID, value.stringValue()));
	}

	@Override
	public Record buildProcessingInstruction(XTCdeweyID deweyID, QNm name, 
			Atomic value) throws DocumentException {
		return insertRecord(deweyID, ElRecordAccess.createRecord(
				stack[stackSize - 1], Kind.PROCESSING_INSTRUCTION.ID, value.stringValue()));
	}
}