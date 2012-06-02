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
package org.brackit.server.store.page.bracket;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.node.XTCdeweyID;

public class XMLNode {
	
	public final XTCdeweyID deweyID;
	public final String value;
	
	private List<XMLNode> attributes;
	private List<XMLNode> children;
	
	private XMLNode parent;
	private XMLNode previousSibling;
	private XMLNode nextSibling;
	
	public XMLNode(XTCdeweyID deweyID, String value) {
		super();
		this.deweyID = deweyID;
		this.value = value;
		
		this.attributes = new ArrayList<XMLNode>();
		this.children = new ArrayList<XMLNode>();
	}

	public void addAttribute(XMLNode attribute) {
		attributes.add(attribute);
	}
	
	public void addChild(XMLNode child) {
		children.add(child);
	}
	
	public XMLNode getParent() {
		return parent;
	}

	public void setParent(XMLNode parent) {
		this.parent = parent;
	}

	public XMLNode getPreviousSibling() {
		return previousSibling;
	}

	public void setPreviousSibling(XMLNode previousSibling) {
		this.previousSibling = previousSibling;
	}

	public XMLNode getNextSibling() {
		return nextSibling;
	}
	
	public XMLNode getNextSiblingOrNext() {
		
		XMLNode current = this;
		while (current != null) {
			if (current.nextSibling != null) {
				return current.nextSibling;
			}
			current = current.parent;
		}
		
		// no next node availabe		
		return null;
	}

	public void setNextSibling(XMLNode nextSibling) {
		this.nextSibling = nextSibling;
	}

	public List<XMLNode> getAttributes() {
		return attributes;
	}

	public List<XMLNode> getChildren() {
		return children;
	}
	
	public XMLNode getFirstChildOrNext() {
		
		if (children.size() > 0) {
			return children.get(0);
		}
		
		XMLNode current = this;
		while (current != null) {
			if (current.nextSibling != null) {
				return current.nextSibling;
			}
			current = current.parent;
		}
		
		// no next node availabe		
		return null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append(String.format("DeweyID: %s\n", deweyID));
		sb.append(String.format("Value: %s\n\n", value));
		
		sb.append(String.format("Attributes:\n"));
		for (XMLNode node : attributes) {
			sb.append(String.format("\t%s\n", node.deweyID));
		}
		
		sb.append(String.format("Children:\n"));
		for (XMLNode node : children) {
			sb.append(String.format("\t%s\n", node.deweyID));
		}
		
		sb.append(String.format("Parent: %s\n", parent != null ? parent.deweyID : null));
		sb.append(String.format("Previous Sibling: %s\n", previousSibling != null ? previousSibling.deweyID : null));
		sb.append(String.format("Next Sibling: %s\n", nextSibling != null ? nextSibling.deweyID : null));
		
		return sb.toString();
	}
}
