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
package org.brackit.server.node.bracket;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.index.IndexController;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;

/**
 * @author Martin Hiller
 *
 */
public class BracketCollection extends TXCollection<BracketNode> {
	
	public static final String PATHSYNOPSIS_ID_ATTRIBUTE = "pathSynopsis";

	protected PathSynopsisMgr pathSynopsis;

	protected final BracketStore store;
	
	protected final BracketIndexController indexController;

	public BracketCollection(Tx tx, BracketStore elStore) {
		super(tx);
		this.store = elStore;
		this.indexController = new BracketIndexController(this);
	}

	protected BracketCollection(BracketCollection collection, Tx tx) {
		super(collection, tx);
		this.store = collection.store;
		this.pathSynopsis = collection.pathSynopsis;
		this.indexController = new BracketIndexController(this);
	}

	@Override
	public BracketCollection copyFor(Tx tx) {
		if (this.tx.equals(tx)) {
			return this;
		}
		BracketCollection copyCol = new BracketCollection(this, tx);
		if (document != null) {
			copyCol.document = new BracketNode(copyCol, document);
		}
		return copyCol;
	}

	@Override
	public BracketNode store(SubtreeParser parser) throws DocumentException {
		try {
			PageID rootPageID = store.index.createIndex(tx, new PageID(docID
					.value()).getContainerNo());
			BracketNode document = new BracketNode(this, rootPageID);
			DocID docID = new DocID(rootPageID.value());
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
			store.index.dropIndex(tx, new PageID(docID.value()));
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
				spec.getContainerID()).value());
	}

	public BracketNode create(StorageSpec spec, SubtreeParser parser)
			throws DocumentException {
		try {
			PageID rootPageID = store.index.createIndex(tx, spec
					.getContainerID());

			docID = new DocID(rootPageID.value());
			name = spec.getDocumentName();
			dictionary = spec.getDictionary();
			pathSynopsis = store.pathSynopsisMgrFactory.create(tx, spec
					.getDictionary(), spec.getContainerID());
			document = new BracketNode(this, rootPageID);

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
		docID = new DocID(rootPageID.value());
		this.name = name;
		dictionary = store.dictionary;
		pathSynopsis = store.pathSynopsisMgrFactory.load(tx, dictionary,
				psPageID);
		document = new BracketNode(this, rootPageID);
	}

	@Override
	public Index getIndex() {
		return store.stdIndex;
	}

	public BracketNode getSingleDocument() {
		return document;
	}

	@Override
	public BracketNode getDocument(DocID docID) throws DocumentException {
		return new BracketNode(
				new BracketLocator(this, docID, new PageID(docID.value())),
				new XTCdeweyID(docID), Kind.DOCUMENT.ID, null, null);
	}

	@Override
	public Node<?> materialize() throws DocumentException {
		Node<?> root = super.materialize();
		root.setAttribute(PATHSYNOPSIS_ID_ATTRIBUTE, Integer
				.toString(pathSynopsis.getPathSynopsisNo()));
		return root;
	}

	@Override
	public void init(Node<?> root) throws DocumentException {
		super.init(root);
		dictionary = store.dictionary;

		if (pathSynopsis == null) {
			PageID psID = PageID.fromString(root
					.getAttributeValue(PATHSYNOPSIS_ID_ATTRIBUTE));
			pathSynopsis = store.pathSynopsisMgrFactory.load(tx, dictionary,
					psID);
		}

		if (!Boolean.parseBoolean(root
				.getAttributeValue(COLLECTION_FLAG_ATTRIBUTE))) {
			document = new BracketNode(this, new PageID(docID.value()));
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
	public IndexController<BracketNode> getIndexController()
	{
		return indexController;
	}

	public BracketStore getRecordManager() {
		return store;
	}

	public PathSynopsisMgr getPathSynopsis() {
		return pathSynopsis;
	}
}
