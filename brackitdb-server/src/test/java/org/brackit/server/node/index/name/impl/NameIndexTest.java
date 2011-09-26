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
package org.brackit.server.node.index.name.impl;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.brackit.server.node.index.definition.IndexDefBuilder.*;
import org.brackit.xquery.util.log.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.server.node.el.ElCollection;
import org.brackit.server.node.el.ElNode;
import org.brackit.server.node.el.ElStore;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.txnode.Persistor;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Sebastian Baechle
 * 
 */
public class NameIndexTest {
	private static final Logger log = Logger.getLogger(NameIndexTest.class
			.getName());

	private static final String DOCUMENT = "<?xml version = '1.0' encoding = 'UTF-8'?>"
			+ "<Organization>"
			+ "<Department>"
			+ "<Member key=\"12\" employee=\"true\">"
			+ "<Firstname>Kurt</Firstname>"
			+ "<Lastname>Mayer</Lastname>"
			+ "<DateOfBirth>1.4.1963</DateOfBirth>"
			+ "<Title>Dr.-Ing.</Title>"
			+ "</Member>"
			+ "<Member key=\"40\"  employe=\"false\">"
			+ "<Firstname>Hans</Firstname>"
			+ "<Lastname>Mettmann</Lastname>"
			+ "<DateOfBirth>12.9.1974</DateOfBirth>"
			+ "<Title>Dipl.-Inf</Title>"
			+ "</Member>"
			+ "</Department>"
			+ "<Project id=\"4711\" priority=\"high\">"
			+ "<Title>XML-DB</Title>"
			+ "<Budget>10000</Budget>"
			+ "</Project>"
			+ "<Project id=\"666\" priority=\"evenhigher\">"
			+ "<Title>DISS</Title>"
			+ "<Budget>7000</Budget>"
			+ "<Abstract></Abstract>" + "</Project>" + "</Organization>";

	private BPlusIndex index;

	private Tx t1;

	private SysMockup sm;

	@Test
	public void testCreateSimpleNameIndex() throws DocumentException {
		ElCollection locator = createDocument(t1, 
				new DocumentParser(DOCUMENT));
		locator.getIndexController().createIndexes(createNameIdxDef(null));
	}

	@Test
	public void testGetFromNameIndex() throws IndexAccessException, 
	DocumentException {
		ElCollection locator = createDocument(t1, new DocumentParser(DOCUMENT));
		IndexDef idxDef = createNameIdxDef(null);
		locator.getIndexController().createIndexes(idxDef);
		Stream<? extends ElNode> iterator = locator.getIndexController()
				.openNameIndex(idxDef.getID(), new QNm("Member"), 
						SearchMode.FIRST);

		ElNode n;
		while ((n = iterator.next()) != null) {
			System.out.println(n);
		}
		iterator.close();
	}

	@Test
	public void testOpenNameIndex() throws Exception {
		ElCollection locator = createDocument(t1, new DocumentParser(DOCUMENT));
		IndexDef idxDef = createNameIdxDef(null);
		locator.getIndexController().createIndexes(idxDef);
		Stream<? extends ElNode> elements = locator.getIndexController()
				.openNameIndex(idxDef.getID(), new QNm("Member2"), 
						SearchMode.FIRST);

		ElNode n;
		while ((n = elements.next()) != null) {
			System.out.println(n);
		}
	}

	void printIndex(Tx transaction, String filename, int rootPageNo,
			boolean showValues) throws IndexAccessException {
		try {
			PrintStream printer = new PrintStream(new File(filename));
			index.traverse(transaction, new PageID(rootPageNo),
					new DisplayVisitor(printer, showValues));
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@After
	public void tearDown() throws BufferException {
	}

	@Before
	public void setUp() throws Exception {
		sm = new SysMockup();
		index = new BPlusIndex(sm.bufferManager);
		t1 = sm.taMgr.begin();
	}

	private ElCollection createCollection(Tx tx) throws DocumentException {
		ElStore elStore = new ElStore(sm.bufferManager, sm.dictionary, sm.mls);
		StorageSpec spec = new StorageSpec("", sm.dictionary);
		ElCollection collection = new ElCollection(tx, elStore);
		collection.create(spec);
		collection.setPersistor(new Persistor() {
			@Override
			public void persist(Tx tx, Materializable materializable)
					throws DocumentException {
			}
		});
		return collection;
	}

	private ElCollection createDocument(Tx tx, DocumentParser documentParser)
			throws DocumentException {
		ElStore elStore = new ElStore(sm.bufferManager, sm.dictionary, sm.mls);
		StorageSpec spec = new StorageSpec("", sm.dictionary);
		ElCollection collection = new ElCollection(tx, elStore);
		collection.create(spec, documentParser);
		collection.setPersistor(new Persistor() {
			@Override
			public void persist(Tx tx, Materializable materializable)
					throws DocumentException {
			}
		});
		return collection;
	}
}
