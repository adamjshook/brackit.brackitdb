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

import java.io.OutputStream;
import java.io.PrintStream;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.BaseCollection;
import org.brackit.server.node.DocID;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.SubtreePrinter;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.node.stream.AtomStream;
import org.brackit.xquery.node.stream.TransformerStream;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Sebastian Baechle
 * 
 */
public abstract class TXCollection<E extends TXNode<E>> extends
		BaseCollection<E> {

	protected final Tx tx;

	protected Persistor persistor;

	/**
	 * Incomplete constructor to initialize locator from materialized tree.
	 */
	public TXCollection(Tx tx) {
		this.tx = tx;
	}

	public TXCollection(TXCollection<E> collection, Tx tx) {
		super(collection);
		this.tx = tx;
		this.persistor = collection.persistor;
	}

	public Tx getTX() {
		return tx;
	}

	@Override
	public void serialize(OutputStream out) throws DocumentException {

		Stream<? extends E> docs = getDocuments();
		E doc;
		try {
			while ((doc = docs.next()) != null) {
				new SubtreePrinter(new PrintStream(out)).print(doc);
			}
		} finally {
			docs.close();
		}
	}

	@Override
	public void calculateStatistics() throws DocumentException {
		super.calculateStatistics();
	}

	public Persistor getPersistor() {
		return persistor;
	}

	public void setPersistor(Persistor persistor) {
		this.persistor = persistor;
	}

	public void persist() throws OperationNotSupportedException,
			DocumentException {
		if (persistor == null) {
			throw new DocumentException(
					"Collection %s is not assigned to a persistor", toString());
		}

		persistor.persist(tx, this);
	}

	@Override
	public String toString() {
		return String.format("%s(%s)", collID, name);
	}
}
