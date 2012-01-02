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
