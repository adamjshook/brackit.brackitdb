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

public class XMLDoc {
	
	public static class Record {
		public final XTCdeweyID deweyID;
		public final int numberOfAncestors;
		public final String value;

		public Record(XTCdeweyID deweyID, int numberOfAncestors, String value) {
			super();
			this.deweyID = deweyID;
			this.numberOfAncestors = numberOfAncestors;
			this.value = value;
		}
	}
	
	private static XTCdeweyID getAncestorKey(XTCdeweyID key,
			int ancestorsToInsert) {
		return (ancestorsToInsert == 0) ? key : key.getAncestor(key.getLevel()
				- ancestorsToInsert - (key.isAttribute() ? 1 : 0));
	}
	
	private List<XMLNode> nodes;
	
	public XMLDoc() {
		this.nodes = new ArrayList<XMLNode>();
	}
	
	public void addRecord(Record record) {
		
		// generate XMLNodes from the record
		for (int i = record.numberOfAncestors; i >= 0; i--) {
			
			XTCdeweyID deweyID = getAncestorKey(record.deweyID, i);
			if (!deweyID.isDocument()) {
				String value = (i == 0) ? record.value : null;
				
				XMLNode node = new XMLNode(deweyID, value);
				addNode(node);
			}
		}
		
	}
	
	private void addNode(XMLNode node) {
		
		XTCdeweyID parent = node.deweyID.getParent();
		
		// set references
		for (XMLNode current : nodes) {
			
			// look for parent
			if (node.deweyID.isAttributeOf(current.deweyID)) {
				node.setParent(current);
				
				// set previous sibling/attribute
				if (!current.getAttributes().isEmpty()) {
					XMLNode prevAttr = current.getAttributes().get(current.getAttributes().size() - 1);
					node.setPreviousSibling(prevAttr);
					prevAttr.setNextSibling(node);
				}
				
				current.addAttribute(node);
				break;
			} else if (current.deweyID.equals(parent)) {
				node.setParent(current);
				
				// set previous sibling
				if (!current.getChildren().isEmpty()) {
					XMLNode prevSibling = current.getChildren().get(current.getChildren().size() - 1);
					node.setPreviousSibling(prevSibling);
					prevSibling.setNextSibling(node);
				}
				
				current.addChild(node);
				break;
			}
		}
		
		nodes.add(node);
	}
	
	public XMLNode getRoot() {
		if (nodes.isEmpty()) {
			return null;
		} else {
			return nodes.get(0);
		}
	}
	
	public XMLNode getNext(XMLNode node) {
		
		if (!node.getAttributes().isEmpty()) {
			return node.getAttributes().get(0);
		}
		
		if (!node.getChildren().isEmpty()) {
			return node.getChildren().get(0);
		}
		
		boolean found = false;
		for (XMLNode current : nodes) {
			if (found) {
				return current;
			}
			
			if (current == node) {
				found = true;
			}
		}
		
		return null;
	}

	public List<XMLNode> getNodes() {
		return nodes;
	}

}
