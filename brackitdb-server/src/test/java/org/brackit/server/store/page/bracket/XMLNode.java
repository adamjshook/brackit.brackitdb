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

	public void setNextSibling(XMLNode nextSibling) {
		this.nextSibling = nextSibling;
	}

	public List<XMLNode> getAttributes() {
		return attributes;
	}

	public List<XMLNode> getChildren() {
		return children;
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
