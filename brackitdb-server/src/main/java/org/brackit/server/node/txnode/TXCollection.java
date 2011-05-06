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
	public static final String COLLECTION_FLAG_ATTRIBUTE = "collection";

	protected final Tx tx;

	protected E document;

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

	public abstract E store(SubtreeParser parser) throws DocumentException;

	public abstract void delete(DocID docID) throws DocumentException;

	public abstract E getDocument(DocID decodeDocID) throws DocumentException;

	public abstract Index getIndex();

	@Override
	public void serialize(OutputStream out) throws DocumentException {
		throw new DocumentException("Not implemented yet");
	}

	@Override
	public Stream<? extends E> getDocuments() throws DocumentException {
		if (document != null) {
			return new AtomStream<E>(document);
		}

		return new TransformerStream<DocID, E>(
				getDocumentReferenceIndexStream()) {
			@Override
			protected E transform(DocID next) throws DocumentException {
				return getDocument(next);
			}
		};
	}

	public void setDocument(E document) throws DocumentException {
		if (this.document != null) {
			throw new DocumentException("Document node already set.");
		}
		this.document = document;
	}

	@Override
	public E getDocument() throws DocumentException {
		if (document == null) {
			throw new DocumentException(
					"Operation not allowed for collections.");
		}

		return document;
	}

	@Override
	public E add(SubtreeParser parser) throws DocumentException {
		if (document != null) {
			throw new DocumentException(
					"Adding documents to a single collection not allowed.");
		}

		E document = store(parser);
		addDocument(document);
		return document;
	}

	@Override
	public void remove(long documentID) throws DocumentException {
		if (document != null) {
			throw new DocumentException(
					"Removing documents from a single collection not allowed.");
		}

		DocID docID = new DocID((int) documentID);
		deleteDocument(docID);
		delete(docID);
	}

	@Override
	public void calculateStatistics() throws DocumentException {
		super.calculateStatistics();
	}

	@Override
	public void delete() throws DocumentException {
		super.delete();

		if (document != null) {
			delete(document.getDeweyID().getDocID());
		} else {
			Stream<DocID> documentPageIDs = getDocumentReferenceIndexStream();

			try {
				DocID docID;
				while ((docID = documentPageIDs.next()) != null) {
					delete(docID);
				}
			} finally {
				documentPageIDs.close();
			}

			deleteDocumentReferenceIndex(new PageID(docID.value()));
		}
	}

	private void deleteDocumentReferenceIndex(PageID rootPageID)
			throws DocumentException {
		try {
			getIndex().dropIndex(tx, rootPageID);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	protected PageID createDocumentReferenceIndex(Tx transaction,
			int containerNo) throws DocumentException {
		try {
			return getIndex().createIndex(tx, containerNo, Field.PAGEID,
					Field.NULL, true);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private void addDocument(E document) throws DocumentException {
		try {
			getIndex().insert(tx, new PageID(docID.value()),
					document.getDeweyID().getDocID().getBytes(), new byte[0]);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private void deleteDocument(DocID docID) throws DocumentException {
		try {
			getIndex().delete(tx, new PageID(docID.value()), docID.getBytes(),
					new byte[0]);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private Stream<DocID> getDocumentReferenceIndexStream()
			throws DocumentException {
		try {
			IndexIterator iterator = getIndex().open(tx,
					new PageID(docID.value()), SearchMode.FIRST, null, null,
					OpenMode.READ);
			return new DocRefIndexStream(iterator);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public Node<?> materialize() throws DocumentException {
		Node<?> root = super.materialize();
		root.setAttribute(COLLECTION_FLAG_ATTRIBUTE, Boolean
				.toString(document == null));
		return root;
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
		return String.format("%s(%s)", docID, name);
	}
}
