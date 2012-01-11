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
/**
 * 
 */
package org.brackit.server.store.util;

import java.io.File;
import java.io.FileReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 * @author Sebastian Baechle
 * 
 */
public class DomPerformance {
	private int elementCount;
	private int textCount;
	private int attributeCount;

	private DocumentBuilder builder;

	public DomPerformance() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		builder = factory.newDocumentBuilder();
	}

	public static void main(String[] args) {
		try {
			(new DomPerformance())
					.traverseDocumentInPreorder(new File(args[0]));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void traverseDocumentInPreorder(File file) throws Exception {
		long startParse = System.currentTimeMillis();
		Document document = builder
				.parse(new InputSource(new FileReader(file)));
		long endParse = System.currentTimeMillis();
		System.out.println(String.format("Parsing %s (%s KB): %s ms", file
				.getAbsolutePath(), (file.length() / 1024),
				((endParse - startParse))));
		long startTraverse = System.currentTimeMillis();
		Node domRoot = document.getDocumentElement();
		checkSubtreePreOrder(domRoot);
		long endTraverse = System.currentTimeMillis();
		System.out.println(String.format(
				"Traversal %s elements, %s attributes, %s text: %s ms",
				elementCount, attributeCount, textCount,
				((endTraverse - startTraverse))));
		System.out.println(String.format("Total: %s ms",
				((endParse + endTraverse) - (startParse + startTraverse))));
	}

	private void checkSubtreePreOrder(Node domNode) {
		if (domNode instanceof Element) {
			elementCount++;
			Element element = (Element) domNode;
			String name = element.getNodeName();
			compareAttributes(element);
			NodeList domChildNodes = element.getChildNodes();

			for (int i = 0; i < domChildNodes.getLength(); i++) {
				Node domChild = domChildNodes.item(i);
				checkSubtreePreOrder(domChild);
			}
		} else if (domNode instanceof Text) {
			textCount++;
			Text text = (Text) domNode;
			String value = text.getNodeValue();
		}
	}

	private void compareAttributes(Element element) {
		NamedNodeMap domAttributes = element.getAttributes();
		attributeCount += domAttributes.getLength();

		for (int i = 0; i < domAttributes.getLength(); i++) {
			Attr domAttribute = (Attr) domAttributes.item(i);
			String name = domAttribute.getNodeName();
			String value = domAttribute.getValue();
		}
	}
}
