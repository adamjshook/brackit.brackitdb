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
package org.brackit.server.node.txnode;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.log4j.Logger;
import org.brackit.server.SysMockup;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.node.DocID;
import org.brackit.server.node.NodeTest;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.DocumentException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class TXNodeTest<E extends TXNode<E>> extends NodeTest<E> {
	protected static final Logger log = Logger.getLogger(TXNodeTest.class
			.getName());

	protected static final String DOCUMENT = "<?xml version = '1.0' encoding = 'UTF-8'?>"
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
			+ "<Member>"
			+ "</Member>"
			+ "<Member>"
			+ "</Member>"
			+ "</Department>"
			+ "<Project id=\"4711\" priority=\"high\">"
			+ "<Title>XML-DB</Title>"
			+ "<Budget>10000</Budget>"
			+ "</Project>"
			+ "<Project id=\"666\" priority=\"evenhigher\">"
			+ "<Title>DISS</Title>"
			+ "<Budget>7000</Budget>"
			+ "<Abstract>Native<b>XML</b>-Databases</Abstract>"
			+ "</Project>"
			+ "</Organization>";

	protected static final String ROOT_ONLY_DOCUMENT = "<?xml version = '1.0' encoding = 'UTF-8'?><root/>";

	protected BPlusIndex index;

	protected Tx tx;

	protected SysMockup sm;

	@Override
	@Test
	public void testStoreDocument() throws DocumentException,
			IndexAccessException {
		TXCollection<E> locator = createDocument(new DocumentParser(DOCUMENT));

		printIndex(((TXQueryContext) ctx).getTX(),
				"/media/ramdisk/document.dot", locator.getID(), true);
	}

	protected void printIndex(Tx transaction, String filename, DocID docID,
			boolean showValues) throws IndexAccessException {
		try {
			PrintStream printer = new PrintStream(new File(filename));
			index.traverse(transaction, new PageID(docID.value()),
					new DisplayVisitor(printer, showValues));
			printer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	@After
	public void tearDown() throws DocumentException, BufferException {
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		sm = new SysMockup();
		index = new BPlusIndex(sm.bufferManager);
		ctx = new TXQueryContext(tx, null);

	}

	@Override
	protected abstract TXCollection<E> createDocument(
			DocumentParser documentParser) throws DocumentException;
}
