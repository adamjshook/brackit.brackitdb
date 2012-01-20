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
package org.brackit.server.node.bracket;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;

import org.brackit.server.node.el.ElMockup;
import org.brackit.server.node.el.ElNode;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.xquery.ErrorCode;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.XQuery;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.Item;
import org.brackit.xquery.xdm.Iter;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Martin Hiller
 *
 */
public class XMarkComparison {
	
	private static final int WARMUP_COUNT = 3;
	private static final int QUERY_COUNT = 5;
	
	protected static final String QUERY_DIR = "/xmark/queries/orig/";

	protected static final String RESULT_DIR = "/xmark/results/";

	protected static TXCollection<BracketNode> bracketColl;
	
	protected static TXCollection<ElNode> elColl;
	
	protected static BracketMockup bracketStore;
	
	protected static ElMockup elStore;
	
//	protected Collection<?> coll;
//	protected QueryContext ctx;

	protected String readQuery(String dirname, String filename)
			throws IOException {
		StringBuilder query = new StringBuilder();
		URL url = getClass().getResource(dirname + filename);
		if (url == null) {
			throw new RuntimeException("Resource not found: " + dirname + filename);
		}
		BufferedReader file = new BufferedReader(new FileReader(url.getFile().replaceAll("%20", " ")));
		boolean first = true;

		String line;
		while ((line = file.readLine()) != null) {
			if (!first)
				query.append(' ');
			query.append(line);
			first = false;
		}
		file.close();
		//System.out.println("Read query:\n" + query);
		return query.toString();
	}
	
	protected XQuery xquery(String query) throws QueryException {
		return new XQuery(query);
	}
	
	protected String readFile(String dirname, String filename)
	throws IOException {
		StringBuilder read = new StringBuilder();
		URL url = getClass().getResource(dirname + filename);
		BufferedReader file = new BufferedReader(new FileReader(url.getFile().replaceAll("%20", " ")));
		boolean first = true;

		String line;
		while ((line = file.readLine()) != null) {
			if (!first)
				read.append('\n');
			read.append(line);
			first = false;
		}
		file.close();
		return read.toString();
	}
	
	private void query(String queryName) throws Exception {
		query(queryName, WARMUP_COUNT);
	}
	
	private void query(String queryName, int warmupCount) throws Exception {
		
		XQuery query = xquery(readQuery(QUERY_DIR, queryName));
		long elResult = queryDoc(elColl, query, warmupCount);
		System.out.println(String.format("EL:      %5d", elResult));
		query = xquery(readQuery(QUERY_DIR, queryName));
		long bracketResult = queryDoc(bracketColl, query, warmupCount);
		System.out.println(String.format("Bracket: %5d", bracketResult));
	}
	
	private long queryDoc(TXCollection<?> coll, XQuery query, int warmupCount) throws Exception {
		
		long start = 0;
		long end = 0;
		
		QueryContext ctx = createContext();
		ctx.setContextItem(coll.getDocument());
		
		for (int i = 0; i < warmupCount; i++) {
			pseudoSerialize(query.execute(ctx));
		}
		
		start = System.currentTimeMillis();
		for (int i = 0; i < QUERY_COUNT; i++) {
			pseudoSerialize(query.execute(ctx));
		}
		end = System.currentTimeMillis();
		
		return (end - start) / QUERY_COUNT;		
	}

	@Test
	public void xmark01() throws Exception, IOException {
		System.out.println("\nXMark01\n--------------------");
		query("q01.xq", 10);
	}

	@Test
	public void xmark02() throws Exception, IOException {		
		System.out.println("\nXMark02\n--------------------");
		query("q02.xq");
	}

	@Test
	public void xmark03() throws Exception, IOException {		
		System.out.println("\nXMark03\n--------------------");
		query("q03.xq");
	}

	@Test
	public void xmark04() throws Exception, IOException {		
		System.out.println("\nXMark04\n--------------------");
		query("q04.xq");
	}

	@Test
	public void xmark05() throws Exception, IOException {		
		System.out.println("\nXMark05\n--------------------");
		query("q05.xq");
	}

	@Test
	public void xmark06() throws Exception, IOException {		
		System.out.println("\nXMark06\n--------------------");
		query("q06.xq");
	}

	@Test
	public void xmark07() throws Exception, IOException {		
		System.out.println("\nXMark07\n--------------------");
		query("q07.xq");
	}

	@Test
	public void xmark08() throws Exception, IOException {		
		System.out.println("\nXMark08\n--------------------");
		query("q08.xq");
	}

	@Test
	public void xmark09() throws Exception, IOException {		
		System.out.println("\nXMark09\n--------------------");
		query("q09.xq");
	}

	@Test
	public void xmark10() throws Exception, IOException {		
		System.out.println("\nXMark10\n--------------------");
		query("q10.xq", 0);
	}

	@Test
	public void xmark11() throws Exception, IOException {		
		System.out.println("\nXMark11\n--------------------");
		query("q11.xq");
	}

	@Test
	public void xmark12() throws Exception, IOException {		
		System.out.println("\nXMark12\n--------------------");
		query("q12.xq");
	}

	@Test
	public void xmark13() throws Exception, IOException {		
		System.out.println("\nXMark13\n--------------------");
		query("q13.xq");
	}

	@Test
	public void xmark14() throws Exception, IOException {		
		System.out.println("\nXMark14\n--------------------");
		query("q14.xq");
	}

	@Test
	public void xmark15() throws Exception, IOException {		
		System.out.println("\nXMark15\n--------------------");
		query("q15.xq");
	}

	@Test
	public void xmark16() throws Exception, IOException {		
		System.out.println("\nXMark16\n--------------------");
		query("q16.xq");
	}

	@Test
	public void xmark17() throws Exception, IOException {		
		System.out.println("\nXMark17\n--------------------");
		query("q17.xq");
	}

	@Test
	public void xmark18() throws Exception, IOException {		
		System.out.println("\nXMark18\n--------------------");
		query("q18.xq");
	}

	@Test
	public void xmark19() throws Exception, IOException {		
		System.out.println("\nXMark19\n--------------------");
		query("q19.xq");
	}

	@Test
	public void xmark20() throws Exception, IOException {		
		System.out.println("\nXMark20\n--------------------");
		query("q20.xq");
	}
	
	protected PrintStream createBuffer() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		return new PrintStream(out) {
			final OutputStream baos = out;

			public String toString() {
				return baos.toString();
			}
		};
	}

	@BeforeClass
	public static void setUpClass() throws Exception, FileNotFoundException {
		bracketStore = new BracketMockup(); 
		elStore = new ElMockup();
//		URL url = XMarkComparison.class.getResource("/xmark/auction.xml");
//		File file = new File(url.getFile().replaceAll("%20", " "));
		File file = new File("xmark10.xml");
		
		System.out.println("Putting document into ELStore...");		
		DocumentParser parser = new DocumentParser(file);
		parser.setRetainWhitespace(true);
		elColl = elStore.createDocument("test", parser);
		elColl.getTX().commit();
		
		System.out.println("Putting document into BracketStore...");		
		parser = new DocumentParser(file);
		parser.setRetainWhitespace(true);
		bracketColl = bracketStore.createDocument("test", parser);
		bracketColl.getTX().commit();
		
		System.out.println("\nStart queries...");
	}
	
	@Before
	public void setUp() throws Exception {
		bracketColl = bracketStore.newTXforDocument(bracketColl, true);
		elColl = elStore.newTXforDocument(elColl, true);
//		coll = bracketColl;
//		ctx = new QueryContext();
	}
	
	@After
	public void tearDown() throws Exception {
		bracketColl.getTX().commit();
		elColl.getTX().commit();
	}
	
	@AfterClass
	public static void tearDownClass() throws Exception {
		bracketStore.shutdown();
		elStore.shutdown();
	}
	
	protected QueryContext createContext() throws Exception {
		return new QueryContext();
	}
	
	private void pseudoSerialize(Sequence result) throws Exception {
		
		Item item;
		Iter it = result.iterate();
		try {
			while ((item = it.next()) != null) {
				if (item instanceof Node<?>) {
					Node<?> node = (Node<?>) item;
					Kind kind = node.getKind();

					if ((kind == Kind.ATTRIBUTE) || (kind == Kind.NAMESPACE)) {
						throw new QueryException(
								ErrorCode.ERR_SERIALIZE_ATTRIBUTE_OR_NAMESPACE_NODE);
					}
					if (kind == Kind.DOCUMENT) {
						node = node.getFirstChild();
						while (node.getKind() != Kind.ELEMENT) {
							node = node.getNextSibling();
						}
					}
				}
			}
		} finally {
			it.close();
		}
	}
	
	public static void main(String[] args) throws Exception {
		setUpClass();
		XMarkComparison xmark = new XMarkComparison();
		xmark.setUp();
		xmark.xmark06();
		xmark.tearDown();
		tearDownClass();
	}
}
