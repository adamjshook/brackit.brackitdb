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
package org.brackit.server.xquery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;

import org.brackit.server.XQueryBaseTest;
import org.brackit.server.metadata.manager.impl.ItemNotFoundException;
import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.store.SearchMode;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.XQuery;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Stream;
import org.junit.Test;

/**
 * @author Max Bechtold
 *
 */
public class FunctionTest extends XQueryBaseTest {

	private DBCompileChain chain = new DBCompileChain(metaDataMgr, tx);

	@Test
	public void createPathIdxTestOneArg() throws QueryException {
		Sequence result = new XQuery(chain,
				"bdb:create-path-index('test.xml')").execute(ctx);
		assertTrue(result != null);
		checkPathIndex();
	}
	
	@Test
	public void createPathIdxTestTwoArg() throws QueryException {
		Sequence result = new XQuery(chain, 
				"bdb:create-path-index('test.xml', ('//Member', '//Title'))")
		.execute(ctx);
		assertTrue(result != null);
		checkPathIndex();
	}
	
	@Test
	public void createNameIdxTestOneArg() throws QueryException {
		Sequence result = new XQuery(chain,
				"bdb:create-name-index('test.xml')").execute(ctx);
		assertTrue(result != null);
		checkNameIndex();
	}
	
	@Test
	public void createNameIdxTestTwoArg() throws QueryException {
		XQuery.DEBUG = true;
		Sequence result = new XQuery(chain, 
				"bdb:create-name-index('test.xml', " +
				"(fn:QName((), 'Title'), " +
				"fn:QName('http://brackit.org', 'Title')))")
		.execute(ctx);
		assertTrue(result != null);
		checkNameIndex();
	}
	
	@Test
	public void createCASIdxTestOneArg() throws QueryException {
		Sequence result = new XQuery(chain,
				"bdb:create-cas-index('test.xml')").execute(ctx);
		assertTrue(result != null);
		checkCASIndex();
	}
	
	@Test
	public void createCASIdxTestTwoArg() throws QueryException {
		Sequence result = new XQuery(chain, 
				"bdb:create-cas-index('test.xml', 'string')").execute(ctx);
		assertTrue(result != null);
		checkCASIndex();
	}
	
	@Test
	public void createCASIdxTestThreeArg() throws QueryException {
		Sequence result = new XQuery(chain, 
				"bdb:create-cas-index('test.xml', 'string'," +
				"('//Member', '//Title'))")
		.execute(ctx);
		assertTrue(result != null);
		checkCASIndex();
	}
	
	@Test
	public void createCASIdxTestTwoAndAHalfArg() throws QueryException {
		Sequence result = new XQuery(chain, 
				"bdb:create-cas-index('test.xml', ()," +
				"('//Member', '//Title'))")
		.execute(ctx);
		assertTrue(result != null);
		checkCASIndex();
	}
	
	protected void checkCASIndex() throws DocumentException,
	ItemNotFoundException, QueryException {
		IndexDef index = metaDataMgr.lookup(tx, "test.xml").get(Indexes.class)
				.findCASIndex(Path.parse("//Title"));
		Una key = new Una("XML-DB");
		Filter<Node<?>> f = new Filter<Node<?>>() {
			@Override
			public boolean filter(Node<?> element) throws DocumentException {
				return !element.getName().equals(new QNm("Title"));
			}
		};
		Stream<?> stream = metaDataMgr.lookup(tx, "test.xml")
				.getIndexController().openCASIndex(index.getID(), f, key,
						null, true, false, SearchMode.FIRST);
		Object o = stream.next();
		int c = 0;
		while (o != null) {
			o = stream.next();
			c++;
		}
		assertEquals(1, c);
		
		f = new Filter<Node<?>>() {
			@Override
			public boolean filter(Node<?> element) throws DocumentException {
				QNm qnm;
				try {
					qnm = new QNm("http://brackit.org", "Title");
				} catch (QueryException e) {
					qnm = null;
				}
				return !element.getName().equals(qnm);
			}
		};
		stream = metaDataMgr.lookup(tx, "test.xml")
				.getIndexController().openCASIndex(index.getID(), f, key,
						null, true, false, SearchMode.GREATER_OR_EQUAL);
		o = stream.next();
		c = 0;
		while (o != null) {
			o = stream.next();
			c++;
		}
		assertEquals(0, c);
	}

	protected void checkNameIndex() throws DocumentException,
			ItemNotFoundException, QueryException {
		QNm title = new QNm("Title");
		IndexDef index = metaDataMgr.lookup(tx, "test.xml").get(Indexes.class)
				.findNameIndex(title);
		Stream<?> stream = metaDataMgr.lookup(tx, "test.xml")
				.getIndexController().openNameIndex(index.getID(), 
						title, SearchMode.FIRST);
		Object o = stream.next();
		int c = 0;
		while (o != null) {
			o = stream.next();
			c++;
		}
		assertEquals(4, c);
		
		stream = metaDataMgr.lookup(tx, "test.xml").getIndexController()
				.openNameIndex(index.getID(), new QNm("http://brackit.org", 
						"Title"), SearchMode.FIRST);
		o = stream.next();
		c = 0;
		while (o != null) {
			o = stream.next();
			c++;
		}
		assertEquals(0, c);
	}
	
	protected void checkPathIndex() throws DocumentException,
	ItemNotFoundException, QueryException {
		IndexDef index = metaDataMgr.lookup(tx, "test.xml").get(Indexes.class)
				.findPathIndex(Path.parse("//Title"));
		Filter<Node<?>> f = new Filter<Node<?>>() {
			@Override
			public boolean filter(Node<?> element) throws DocumentException {
				return !element.getName().equals(new QNm("Title"));
			}
		};
		Stream<?> stream = metaDataMgr.lookup(tx, "test.xml")
				.getIndexController().openPathIndex(index.getID(), f,
						SearchMode.FIRST);
		Object o = stream.next();
		int c = 0;
		while (o != null) {
			o = stream.next();
			c++;
		}
		assertEquals(4, c);
		
		f = new Filter<Node<?>>() {
			@Override
			public boolean filter(Node<?> element) throws DocumentException {
				QNm qnm;
				try {
					qnm = new QNm("http://brackit.org", "Title");
				} catch (QueryException e) {
					qnm = null;
				}
				return !element.getName().equals(qnm);
			}
		};
		stream = metaDataMgr.lookup(tx, "test.xml")
				.getIndexController().openPathIndex(index.getID(), f,
						SearchMode.FIRST);
		o = stream.next();
		c = 0;
		while (o != null) {
			o = stream.next();
			c++;
		}
		assertEquals(0, c);
	}
	
	@Override
	public void setUp() throws Exception, FileNotFoundException {
		super.setUp();
		XQuery.DEBUG = false;
		storeFile("test.xml", "/docs/orga.xml");
	}
}
