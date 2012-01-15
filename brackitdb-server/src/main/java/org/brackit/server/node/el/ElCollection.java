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
package org.brackit.server.node.el;

import java.io.OutputStream;
import java.io.PrintStream;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.BaseCollection;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.DocRefIndexStream;
import org.brackit.server.node.txnode.Persistor;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.visitor.IndexStatisticsVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.SubtreePrinter;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.node.stream.AtomStream;
import org.brackit.xquery.node.stream.TransformerStream;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ElCollection extends TXCollection<ElNode> {

	public static final QNm COLLECTION_FLAG_ATTRIBUTE = new QNm("collection");

	public static final QNm PATHSYNOPSIS_ID_ATTRIBUTE = new QNm("pathSynopsis");
	
	protected ElNode document;

	protected PathSynopsisMgr pathSynopsis;

	protected final ElStore store;

	protected final ElIndexController indexController;

	@Override
	public void serialize(OutputStream out) throws DocumentException {
		if (document != null) {
			new SubtreePrinter(new PrintStream(out)).print(document);
		} else {
			super.serialize(out);
		}
	}

	@Override
	public Stream<? extends ElNode> getDocuments() throws DocumentException {
		if (document != null) {
			return new AtomStream<ElNode>(document);
		}

		return new TransformerStream<Integer, ElNode>(
				getDocumentReferenceIndexStream()) {
			@Override
			protected ElNode transform(Integer next) throws DocumentException {
				return getDocument(next);
			}
		};
	}

	public void setDocument(ElNode document) throws DocumentException {
		if (this.document != null) {
			throw new DocumentException("Document node already set.");
		}
		this.document = document;
	}

	@Override
	public ElNode getDocument() throws DocumentException {
		if (document == null) {
			throw new DocumentException(
					"Operation not allowed for collections.");
		}

		return document;
	}

	@Override
	public ElNode add(SubtreeParser parser) throws DocumentException {
		if (document != null) {
			throw new DocumentException(
					"Adding documents to a single collection not allowed.");
		}

		ElNode document = store(parser);
		addDocument(document);
		return document;
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

	private void addDocument(ElNode document) throws DocumentException {
		try {
			getIndex().insert(
					tx,
					new PageID(collID),
					Calc.fromInt(document.getDeweyID().getDocID()
							.getDocNumber()), new byte[0]);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private void deleteDocument(DocID docID) throws DocumentException {
		try {
			getIndex().delete(tx, new PageID(docID.getCollectionID()),
					docID.getBytes(), new byte[0]);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private Stream<Integer> getDocumentReferenceIndexStream()
			throws DocumentException {
		try {
			IndexIterator iterator = getIndex().open(tx, new PageID(collID),
					SearchMode.FIRST, null, null, OpenMode.READ);
			return new DocRefIndexStream(iterator);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public ElCollection(Tx tx, ElStore elStore) {
		super(tx);
		this.store = elStore;
		this.indexController = new ElIndexController(this);
	}

	protected ElCollection(ElCollection collection, Tx tx) {
		super(collection, tx);
		this.store = collection.store;
		this.pathSynopsis = collection.pathSynopsis.copyFor(tx);
		this.indexController = new ElIndexController(this);
	}

	@Override
	public ElCollection copyFor(Tx tx) {
		if (this.tx.equals(tx)) {
			return this;
		}
		ElCollection copyCol = new ElCollection(this, tx);
		if (document != null) {
			copyCol.document = new ElNode(copyCol, document);
		}
		return copyCol;
	}

	public ElNode store(SubtreeParser parser) throws DocumentException {
		try {
			PageID rootPageID = store.index.createIndex(tx,
					new PageID(collID).getContainerNo(), Field.DEWEYID,
					Field.EL_REC, true, true, -1);
			ElNode document = new ElNode(this, rootPageID);
			DocID docID = new DocID(collID, rootPageID.value());
			XTCdeweyID rootDeweyID = XTCdeweyID.newRootID(docID);
			document.store(rootDeweyID, parser, true, false);
			return document;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public void delete(int docNumber) throws DocumentException {
		try {
			store.index.dropIndex(tx, new PageID(docNumber));
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public void create(StorageSpec spec) throws DocumentException {
		name = spec.getDocumentName();
		dictionary = spec.getDictionary();
		// create a path synopsis and document reference container
		pathSynopsis = store.pathSynopsisMgrFactory.create(tx,
				spec.getDictionary(), spec.getContainerID());
		collID = createDocumentReferenceIndex(tx,
				spec.getContainerID()).value();
	}

	public ElNode create(StorageSpec spec, SubtreeParser parser)
			throws DocumentException {
		try {
			PageID rootPageID = store.index.createIndex(tx,
					spec.getContainerID(), Field.DEWEYID, Field.EL_REC, true,
					spec.isKeyCompression(), -1);

			collID = rootPageID.value();
			DocID docID = new DocID(collID, rootPageID.value());
			name = spec.getDocumentName();
			dictionary = spec.getDictionary();
			pathSynopsis = store.pathSynopsisMgrFactory.create(tx,
					spec.getDictionary(), spec.getContainerID());
			document = new ElNode(this, rootPageID);

			// write document to document container
			XTCdeweyID rootDeweyID = XTCdeweyID.newRootID(docID);
			document.store(rootDeweyID, parser, true, false);
			return document;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public void init(String name, PageID rootPageID, PageID psPageID)
			throws DocumentException {
		collID = rootPageID.value();
		this.name = name;
		dictionary = store.dictionary;
		pathSynopsis = store.pathSynopsisMgrFactory.load(tx, dictionary,
				psPageID);
		document = new ElNode(this, rootPageID);
	}

	public Index getIndex() {
		return store.index;
	}

	public ElNode getSingleDocument() {
		return document;
	}

	public ElNode getDocument(int docNumber) throws DocumentException {
		return new ElNode(new ElLocator(this, docID, new PageID(
				docID.getCollectionID())), new XTCdeweyID(docID),
				Kind.DOCUMENT.ID, null, null);
	}

	@Override
	public Node<?> materialize() throws DocumentException {
		Node<?> root = super.materialize();
		root.setAttribute(COLLECTION_FLAG_ATTRIBUTE,
				new Una(Boolean.toString(document == null)));
		root.setAttribute(PATHSYNOPSIS_ID_ATTRIBUTE,
				new Una(Integer.toString(pathSynopsis.getPathSynopsisNo())));
		return root;
	}

	@Override
	public void init(Node<?> root) throws DocumentException {
		super.init(root);
		dictionary = store.dictionary;

		if (pathSynopsis == null) {
			PageID psID = PageID.fromString(root
					.getAttribute(PATHSYNOPSIS_ID_ATTRIBUTE).getValue()
					.stringValue());
			pathSynopsis = store.pathSynopsisMgrFactory.load(tx, dictionary,
					psID);
		}

		if (!Boolean.parseBoolean(root.getAttribute(COLLECTION_FLAG_ATTRIBUTE)
				.getValue().stringValue())) {
			document = new ElNode(this, new PageID(docID.getCollectionID()));
		}
	}

	@Override
	public void calculateStatistics() throws DocumentException {
		super.calculateStatistics();
		try {
			IndexStatisticsVisitor visitor = new IndexStatisticsVisitor();
			store.index.traverse(tx, new PageID(docID.getCollectionID()),
					visitor);
			set(visitor.getIndexStatistics());

			indexController.calculateStatistics();

			// TODO
			// visitor = new IndexStatisticsVisitor();
			// recordMgr.index.traverse(ctx, pathSynopsis.getPathSynopsisNo(),
			// visitor);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void delete() throws DocumentException {
		try {
			super.delete();

			if (document != null) {
				delete(document.getDeweyID().getDocID().getDocNumber());
			} else {
				Stream<Integer> documentNumbers = getDocumentReferenceIndexStream();

				try {
					Integer docNumber;
					while ((docNumber = documentNumbers.next()) != null) {
						delete(docNumber);
					}
				} finally {
					documentNumbers.close();
				}

				deleteDocumentReferenceIndex(new PageID(collID));
			}

			store.index.dropIndex(tx,
					new PageID(pathSynopsis.getPathSynopsisNo()));
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public ElIndexController getIndexController() {
		return indexController;
	}

	public ElStore getRecordManager() {
		return store;
	}

	public PathSynopsisMgr getPathSynopsis() {
		return pathSynopsis;
	}

	@Override
	public void remove(long documentID) throws OperationNotSupportedException,
			DocumentException {
		// TODO Auto-generated method stub
		
	}
}
