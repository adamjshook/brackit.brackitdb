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
package org.brackit.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Random;

import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.metadata.manager.impl.MetaDataMgrImpl;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.node.SubtreePrinter;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Item;
import org.brackit.xquery.xdm.Iter;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Store;
import org.junit.Before;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class XQueryBaseTest {
	protected QueryContext ctx;

	protected Random rand;

	protected Store store;

	protected SysMockup sm;

	protected MetaDataMgrImpl metaDataMgr;

	protected Tx tx;

	protected void print(Sequence sequence) throws QueryException {
		if (sequence == null) {
			return;
		}
		Iter it = sequence.iterate();
		Item item;
		try {
			while ((item = it.next()) != null) {
//				System.out.println(item);
				if ((item instanceof Node<?>)
						&& (((Node<?>) item).getKind() != Kind.ATTRIBUTE)) {
					try {
						new SubtreePrinter(System.out, false, false)
								.print((Node<?>) item);
						System.out.println();
					} catch (DocumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		} finally {
			it.close();
		}
	}

	protected PrintStream createBuffer() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		return new PrintStream(out) {
			final OutputStream baos = out;

			@Override
			public String toString() {
				return baos.toString();
			}
		};
	}

	protected String readQuery(String dirname, String filename)
			throws IOException {
		StringBuilder query = new StringBuilder();
		URL url = getClass().getResource(dirname + filename);
		BufferedReader file = new BufferedReader(new FileReader(url.getFile()));
		boolean first = true;

		String line;
		while ((line = file.readLine()) != null) {
			if (!first)
				query.append(' ');
			query.append(line);
			first = false;
		}
		file.close();
		System.out.println("Read query:\n" + query);
		return query.toString();
	}

	protected String readFile(String dirname, String filename)
			throws IOException {
		StringBuilder read = new StringBuilder();
		URL url = getClass().getResource(dirname + filename);
		BufferedReader file = new BufferedReader(new FileReader(url.getFile()));
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

	protected Collection<?> storeFile(String name, String document)
			throws Exception, FileNotFoundException {
		URL url = getClass().getResource(document);
		DocumentParser parser = new DocumentParser(new File(url.getFile()));
		parser.setRetainWhitespace(true);
		return storeDocument(name, parser);
	}

	protected Collection<?> storeDocument(String name, String document)
			throws Exception {
		return storeDocument(name, new DocumentParser(document));
	}

	protected Collection<?> storeDocument(String name, SubtreeParser parser)
			throws Exception {
		Collection<?> collection = store.create(name, parser);
		return collection;
	}

	protected Store createStore() throws Exception {
		return new TXQueryContext.MDMStore(tx, metaDataMgr);
	}

	protected QueryContext createContext() throws Exception {
		return new TXQueryContext(tx, metaDataMgr);
	}

	@Before
	public void setUp() throws Exception, FileNotFoundException {
		sm = new SysMockup();
		tx = sm.taMgr.begin();
		metaDataMgr = new MetaDataMgrImpl(sm.taMgr);
		metaDataMgr.start(tx, true);
		store = createStore();
		ctx = createContext();

		// use same random source to get reproducible results in case of an
		// error
		rand = new Random(12345678);
	}
}
