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
package org.brackit.server.metaData.pathSynopsis.manager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsis;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisNode;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.xquery.xdm.Kind;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class PathSynopsisTest {

	private static final String DOCFILE = "/docs/ps/sampleVOC.xml";
	static PathSynopsis ps;
	static DictionaryMgr dmgr;
	private final static int ITERATIONS = 50000;

	static final String queries[] = { // Positive test cases

	"/*/*/*", "/8/*/*", "/8/*/@*", "/8/9/10", "/8/*/10", "/*/9/10", "/8/9/@11",
			"/8/*/@11", "/*/9/@11", "//9/10", "//*/10", "//10", "//9/@11",
			"//*/@11", "//@11", "/8//10", "/*//10", "/8//@11", "/*//@11",
			"//8/9//10",
			"//8//9//10",

			// Negative test cases
			"/6/9/10", "/6/*/10", "/*/6/10", "/6/9/@11", "/6/*/@11",
			"/*/6/@11", "//6/10", "//*/6", "//6", "//6/@11", "//*/@6", "//@6",
			"/6//10", "/*//6", "/6//@11", "/*//@6", "//6/9//10", "//6//9//10" };

	public static void main(String[] args) throws Exception {
		System.out.println("Starting PathSynopsisTest on '" + DOCFILE
				+ "' with " + ITERATIONS + " iterations each");
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.S");
		long startMillis;
		long endMillis;
		ps = new PathSynopsis(5);
		buildPathSynopsis(DOCFILE);
		List<Float> msr = new ArrayList<Float>();

		for (String query : queries) {
			Set<Integer> result = null;
			System.out.println("Testing query #" + (msr.size() + 1) + ": '"
					+ query + "'");
			startMillis = System.currentTimeMillis();
			System.out.println("getPCRsForPathMax invoked at "
					+ sdf.format(new Date(startMillis)) + "...");

			for (int i = 0; i < ITERATIONS; i++) {
				ps.clearCache();
				result = ps.getPCRsForPathMax(null, query);
			}

			endMillis = System.currentTimeMillis();
			long optTime = endMillis - startMillis;
			System.out.println("Finished in " + optTime + " ms.");
			System.out.println("Returned PCRs: " + result);

			startMillis = System.currentTimeMillis();
			System.out.println("getPCRsForPathSebastian invoked at "
					+ sdf.format(new Date(startMillis)) + "...");

			for (int i = 0; i < ITERATIONS; i++) {
				ps.clearCache();
				result = ps.getPCRsForPath(null, query);
			}

			endMillis = System.currentTimeMillis();
			long sebTime = endMillis - startMillis;
			System.out.println("Finished in " + sebTime + " ms.");
			System.out.println("Returned PCRs: " + result);
		}

		System.out.println("_");
		float sum = 0f;
		for (Float f : msr) {
			sum += f;
		}
		System.out
				.println("Average speedup: " + (sum / queries.length));

	}

	private static void buildPathSynopsis(String docFile)
			throws ParserConfigurationException, SAXException, IOException {
		SAXParserFactory spf = SAXParserFactory.newInstance();
		SAXParser parser = spf.newSAXParser();
		parser.parse(docFile, new PathSynopsisHandler(ps));

	}

}

class PathSynopsisHandler extends org.xml.sax.helpers.DefaultHandler {
	private PathSynopsis ps;
	private PathSynopsisNode root;
	private PathSynopsisNode contextNode;

	public PathSynopsisHandler(PathSynopsis ps) {
		this.ps = ps;
		root = this.ps.getRoots()[0];
		contextNode = root;
	}

	@Override
	public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		PathSynopsisNode newNode = ps.getNewNode(name.substring(1), Integer
				.parseInt(name.substring(1)), Kind.ELEMENT.ID, contextNode, 0);
		contextNode = newNode;

		for (int i = 0; i < attributes.getLength(); i++) {
			String aName = attributes.getQName(i);
			ps.getNewNode(aName.substring(1), Integer.parseInt(aName
					.substring(1)), Kind.ATTRIBUTE.ID, contextNode, 0);
		}
	}

	@Override
	public void endElement(String uri, String localName, String name)
			throws SAXException {
		contextNode = contextNode.getParent();
	}

}
