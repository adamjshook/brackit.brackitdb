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

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.visitor.IndexStatisticsVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ElCollection extends TXCollection<ElNode> {
	public static final QNm PATHSYNOPSIS_ID_ATTRIBUTE = new QNm("pathSynopsis");

	protected PathSynopsisMgr pathSynopsis;

	protected final ElStore store;

	protected final ElIndexController indexController;

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

	@Override
	public ElNode store(SubtreeParser parser) throws DocumentException {
		try {
			PageID rootPageID = store.index.createIndex(tx, new PageID(docID
					.getCollID()).getContainerNo(), Field.DEWEYID, Field.EL_REC,
					true, true, -1);
			ElNode document = new ElNode(this, rootPageID);
			DocID docID = new DocID(rootPageID.value(), XXX);
			XTCdeweyID rootDeweyID = XTCdeweyID.newRootID(docID);
			document.store(rootDeweyID, parser, true, false);
			return document;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void delete(DocID docID) throws DocumentException {
		try {
			store.index.dropIndex(tx, new PageID(docID.getCollID()));
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public void create(StorageSpec spec) throws DocumentException {
		name = spec.getDocumentName();
		dictionary = spec.getDictionary();
		// create a path synopsis and document reference container
		pathSynopsis = store.pathSynopsisMgrFactory.create(tx, spec
				.getDictionary(), spec.getContainerID());
		docID = new DocID(createDocumentReferenceIndex(tx,
				spec.getContainerID()).value(), XXX);
	}

	public ElNode create(StorageSpec spec, SubtreeParser parser)
			throws DocumentException {
		try {
			PageID rootPageID = store.index.createIndex(tx, spec
					.getContainerID(), Field.DEWEYID, Field.EL_REC, true, spec
					.isKeyCompression(), -1);

			docID = new DocID(rootPageID.value(), XXX);
			name = spec.getDocumentName();
			dictionary = spec.getDictionary();
			pathSynopsis = store.pathSynopsisMgrFactory.create(tx, spec
					.getDictionary(), spec.getContainerID());
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
		docID = new DocID(rootPageID.value(), XXX);
		this.name = name;
		dictionary = store.dictionary;
		pathSynopsis = store.pathSynopsisMgrFactory.load(tx, dictionary,
				psPageID);
		document = new ElNode(this, rootPageID);
	}

	@Override
	public Index getIndex() {
		return store.index;
	}

	public ElNode getSingleDocument() {
		return document;
	}

	@Override
	public ElNode getDocument(DocID docID) throws DocumentException {
		return new ElNode(
				new ElLocator(this, docID, new PageID(docID.getCollID())),
				new XTCdeweyID(docID), Kind.DOCUMENT.ID, null, null);
	}

	@Override
	public Node<?> materialize() throws DocumentException {
		Node<?> root = super.materialize();
		root.setAttribute(PATHSYNOPSIS_ID_ATTRIBUTE, new Una(Integer
				.toString(pathSynopsis.getPathSynopsisNo())));
		return root;
	}

	@Override
	public void init(Node<?> root) throws DocumentException {
		super.init(root);
		dictionary = store.dictionary;

		if (pathSynopsis == null) {
			PageID psID = PageID.fromString(root.getAttribute(
					PATHSYNOPSIS_ID_ATTRIBUTE).getValue().stringValue());
			pathSynopsis = store.pathSynopsisMgrFactory.load(tx, dictionary,
					psID);
		}

		if (!Boolean.parseBoolean(root.getAttribute(COLLECTION_FLAG_ATTRIBUTE)
				.getValue().stringValue())) {
			document = new ElNode(this, new PageID(docID.getCollID()));
		}
	}

	@Override
	public void calculateStatistics() throws DocumentException {
		super.calculateStatistics();
		try {
			IndexStatisticsVisitor visitor = new IndexStatisticsVisitor();
			store.index.traverse(tx, new PageID(docID.getCollID()), visitor);
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
			store.index.dropIndex(tx, new PageID(pathSynopsis
					.getPathSynopsisNo()));
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
}
