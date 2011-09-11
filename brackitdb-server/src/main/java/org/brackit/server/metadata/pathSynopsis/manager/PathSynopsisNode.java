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
package org.brackit.server.metadata.pathSynopsis.manager;

import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.Kind;

/**
 * This type of node represents a node of the pathsynopsis. Besides the node
 * type, its PCR value (path class reference), vocIDs (vocabulary IDs), level,
 * XML-tag-"name", occurrences ( not up2date), and average content length
 * avgCntLength is represented.
 * 
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 * 
 */
public class PathSynopsisNode implements PSNode {
	protected final byte kind;

	protected final int pcr;

	protected final int uriVocID;
	protected final int prefixVocID;
	protected final int localNameVocID;

	protected final int level;

	protected final QNm name;

	protected final PathSynopsisNode parent;

	protected final PathSynopsis ps;

	// if true, the node is stored on external storage
	private boolean stored = false;

	private boolean visible = false;

	protected PathSynopsisNode[] children;

	public PathSynopsisNode(int uriVocID, int prefixVocID, int localNameVocID,
			int pcr, QNm name, byte kind, PathSynopsisNode parent,
			PathSynopsis ps) {
		this.uriVocID = uriVocID;
		this.prefixVocID = prefixVocID;
		this.localNameVocID = localNameVocID;
		this.pcr = pcr;
		this.name = name;
		this.kind = kind;
		this.parent = parent;
		this.ps = ps;
		this.children = new PathSynopsisNode[0];

		if (parent != null) {
			parent.addChild(this);
			level = parent.getLevel() + 1;
		} else {
			level = 1;
		}
	}

	/**
	 * Add a given node as child to this node.
	 * 
	 * @param newNode
	 *            - an existing node
	 */
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

	/**
	 * Returns a child of the node, if a child exists with this information.
	 * 
	 * @param vocId
	 *            - vocabulary id
	 * @param nodeType
	 *            - type of node
	 * @return found node
	 */
	public PathSynopsisNode hasChild(int uriVocID, int prefixVocID,
			int localNameVocID, byte nodeType) {
		for (PathSynopsisNode child : children) {
			if (child.getKind() == nodeType && child.getURIVocID() == uriVocID
					&& child.getPrefixVocID() == prefixVocID
					&& child.getLocalNameVocID() == localNameVocID) {
				return child;
			}
		}

		return null;
	}

	/**
	 * @return all children in a List.
	 */
	public PathSynopsisNode[] getChildren() {
		return children;
	}

	/**
	 * @return the distance between the root and this node
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * @return the type of the node.
	 */
	public byte getKind() {
		return kind;
	}

	public PathSynopsisNode getParent() {
		return parent;
	}

	public int getPCR() {
		return pcr;
	}

	public QNm getName() {
		return name;
	}

	public PathSynopsis getPathSynopsis() {
		return this.ps;
	}

	public void setStored(boolean stored) {
		this.stored = stored;
	}

	public boolean isStored() {
		return this.stored;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public Path<QNm> getPath() {
		PathSynopsisNode node = this;
		PathSynopsisNode[] path = new PathSynopsisNode[level];
		for (int i = level - 1; i >= 0; i--) {
			path[i] = node;
			node = node.getParent();
		}

		Path<QNm> p = new Path<QNm>();
		for (PathSynopsisNode n : path) {
			if (n.getKind() == Kind.ELEMENT.ID) {
				p.child(n.getName());
			} else {
				p.attribute(n.getName());
			}
		}

		return p;
	}

	@Override
	public String toString() {
		StringBuffer sBuff = new StringBuffer();
		int parentPcr = 0;
		if (this.getParent() != null) {
			parentPcr = this.getParent().getPCR();
		}
		sBuff.append("PSNode " + this.pcr + "; VocID (URI): " + this.uriVocID
				+ "; VocID (Prefix): " + this.prefixVocID
				+ "; VocID (LocalName): " + this.localNameVocID + "; NT: "
				+ this.kind + "; ");
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

	@Override
	public int getURIVocID() {
		return uriVocID;
	}

	@Override
	public int getPrefixVocID() {
		return prefixVocID;
	}

	@Override
	public int getLocalNameVocID() {
		return localNameVocID;
	}
}
