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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Sebastian Baechle
 * 
 */
public class SaxPerformance extends DefaultHandler {
	private int elementCount;
	private int textCount;
	private int attributeCount;

	private SAXParser parser;

	public SaxPerformance() throws ParserConfigurationException, SAXException {
		SAXParserFactory factory = SAXParserFactory.newInstance(); // Use the
		// default
		// (non-validating)
		// parser
		parser = factory.newSAXParser();
		XMLReader xmlReader = parser.getXMLReader();
		xmlReader.setFeature("http://xml.org/sax/features/validation", false);
	}

	public static void main(String[] args) {
		try {
			(new SaxPerformance()).parse(new File(args[0]));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void parse(File file) throws Exception {
		long startParse = System.currentTimeMillis();
		parser.parse(file, this);
		long endParse = System.currentTimeMillis();
		System.out.println(String.format(
				"1. Parse %s elements, %s attributes, %s text: %s ms",
				elementCount, attributeCount, textCount,
				((endParse - startParse))));
		startParse = System.currentTimeMillis();
		parser.parse(file, this);
		endParse = System.currentTimeMillis();
		System.out.println(String.format(
				"2. Parse %s elements, %s attributes, %s text: %s ms",
				elementCount, attributeCount, textCount,
				((endParse - startParse))));
		startParse = System.currentTimeMillis();
		parser.parse(file, this);
		endParse = System.currentTimeMillis();
		System.out.println(String.format(
				"3. Parse %s elements, %s attributes, %s text: %s ms",
				elementCount, attributeCount, textCount,
				((endParse - startParse))));
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		textCount++;
	}

	@Override
	public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		elementCount++;
		attributeCount += attributes.getLength();
	}
}
