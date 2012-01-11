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
package org.brackit.server.util;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.xquery.xdm.DocumentException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class DeweyIDGenerator extends DefaultHandler {
	private final XTCdeweyID subtreeRootDeweyID;

	private StringBuilder currentText;

	private Deque<XTCdeweyID> stack;

	private int level;

	private ArrayList<XTCdeweyID> deweyIDs;

	public DeweyIDGenerator(XTCdeweyID subtreeRootDeweyID) {
		super();
		this.subtreeRootDeweyID = subtreeRootDeweyID;
		this.deweyIDs = new ArrayList<XTCdeweyID>();
		this.currentText = new StringBuilder();
		this.stack = new LinkedList<XTCdeweyID>();
	}

	public List<XTCdeweyID> getDeweyIDs() {
		return deweyIDs;
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		if (currentText.length() == 0) // "begin" of text node
			level++;

		currentText.append(ch, start, length);
	}

	@Override
	public void endElement(String uri, String localName, String name)
			throws SAXException {
		if (currentText.length() > 0) // append waiting text node
			handleText();

		level--;

		if (level < (stack.size() - 1)) {
			stack.pop();
		}
	}

	@Override
	public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		if (currentText.length() > 0) {
			handleText();
		}

		level++;
		XTCdeweyID elementDeweyID = calcNextDeweyID();
		deweyIDs.add(elementDeweyID);

		handleAttributes(attributes, elementDeweyID);
	}

	private void handleAttributes(Attributes attributes,
			XTCdeweyID elementDeweyID) throws SAXException {
		XTCdeweyID deweyID = null;
		for (int i = 0; i < attributes.getLength(); i++) {
			if (deweyID == null) {
				deweyID = elementDeweyID.getAttributeRootID().getNewChildID();
			} else {
				try {
					deweyID = XTCdeweyID.newBetween(deweyID, null);
				} catch (DocumentException e) {
					throw new SAXException();
				}
			}

			deweyIDs.add(deweyID);
		}
	}

	private void handleText() throws SAXException {
		String text = currentText.toString().trim();

		if (text.length() > 0) {
			XTCdeweyID deweyID = calcNextDeweyID();

			deweyIDs.add(deweyID);

			if (level < (stack.size() - 1)) {
				stack.pop();
			}
		}
		level--;
		currentText = new StringBuilder();
	}

	private XTCdeweyID calcNextDeweyID() throws SAXException {
		XTCdeweyID deweyID = null;

		if (stack.isEmpty()) {
			deweyID = subtreeRootDeweyID;
		} else {
			if (stack.size() == level) // new sibling at this level
			{
				try {
					deweyID = XTCdeweyID.newBetween(stack.pop(), null);
				} catch (DocumentException e) {
					throw new SAXException();
				}
			} else // first child at this level
			{
				deweyID = stack.peek().getNewChildID();
			}
		}

		stack.push(deweyID);
		return deweyID;
	}

	public static List<XTCdeweyID> generateDeweyIDs(DocID docID, String filename)
			throws Exception {
		SAXParserFactory factory = SAXParserFactory.newInstance(); // Use the
		// default
		// (non-validating)
		// parser
		SAXParser saxParser = factory.newSAXParser();
		XMLReader xmlReader = saxParser.getXMLReader();
		xmlReader.setFeature("http://xml.org/sax/features/validation", false);

		DeweyIDGenerator generator = new DeweyIDGenerator(XTCdeweyID
				.newRootID(docID));
		saxParser.parse(new InputSource(new FileReader(filename)), generator);

		return generator.getDeweyIDs();
	}
}
