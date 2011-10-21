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

import java.util.Random;

import org.brackit.server.SysMockup;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;
import org.junit.Before;
import org.junit.Test;

public abstract class TXNodeCollectionTest<E extends TXNode<E>> {

	protected Tx tx;

	protected Random rand;

	protected SysMockup sm;

	@Test
	public void testStoreDocuments() throws Exception {
		TXCollection<E> collection = createCollection();
		int documentCount = 10;

		for (int i = 0; i < documentCount; i++) {
			String document = String
					.format("<a%s>Collection Document <b><c>%s</c></b>!</a%s>",
							i, i, i);
			collection.add(new DocumentParser(document));
		}

		int i = 0;
		Stream<? extends E> documents = collection.getDocuments();
		try {
			E document;
			while ((document = documents.next()) != null) {
				System.out.print("Doc " + i++);
				// System.out.println(document.getDeweyID());
				// IndexPrinter.print(tx,
				// document.getDeweyID().getDocID().value(), System.out);
				// SubtreePrinter.print(document, System.out);
			}
		} finally {
			documents.close();
		}

		tx.commit();
	}

	@Before
	public void setUp() throws Exception {
		sm = new SysMockup();
		tx = sm.taMgr.begin();
	}

	protected abstract TXCollection<E> createCollection()
			throws DocumentException;
}
