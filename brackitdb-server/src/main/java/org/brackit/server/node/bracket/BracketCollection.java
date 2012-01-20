/*
 * [New BSD License]
 * Copyright (c) 2011-2012, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Brackit Project Team nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
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
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.InsertController;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Martin Hiller
 * 
 */
public class BracketCollection extends TXCollection<BracketNode> {

	public static final QNm PATHSYNOPSIS_ID_ATTRIBUTE = new QNm("pathSynopsis");

	protected PathSynopsisMgr pathSynopsis;

	protected final BracketStore store;

	protected final BracketIndexController indexController;

	public BracketCollection(Tx tx, BracketStore bracketStore) {
		super(tx);
		this.store = bracketStore;
		this.indexController = new BracketIndexController(this);
	}

	protected BracketCollection(BracketCollection collection, Tx tx) {
		super(collection, tx);
		this.store = collection.store;
		this.pathSynopsis = collection.pathSynopsis.copyFor(tx);
		this.indexController = new BracketIndexController(this);
	}

	@Override
	public BracketCollection copyFor(Tx tx) {
		if (this.tx.equals(tx)) {
			return this;
		}
		BracketCollection copyCol = new BracketCollection(this, tx);
		return copyCol;
	}

	public int create(StorageSpec spec) throws DocumentException {
		try {
			name = spec.getDocumentName();
			dictionary = spec.getDictionary();
			// create a path synopsis and document reference container
			pathSynopsis = store.pathSynopsisMgrFactory.create(tx, spec
					.getDictionary(), spec.getContainerID());
			PageID rootPageID = store.index.createIndex(tx, spec
					.getContainerID());
			collID = rootPageID.value();
			return collID;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public BracketNode create(StorageSpec spec, SubtreeParser parser)
			throws DocumentException {
		try {
			PageID rootPageID = store.index.createIndex(tx, spec
					.getContainerID());

			collID = rootPageID.value();
			DocID docID = new DocID(collID, 0);
			name = spec.getDocumentName();
			dictionary = spec.getDictionary();
			pathSynopsis = store.pathSynopsisMgrFactory.create(tx, spec
					.getDictionary(), spec.getContainerID());

			BracketNode document = new BracketNode(this, docID.getDocNumber());

			// write document to document container
			XTCdeweyID rootDeweyID = XTCdeweyID.newRootID(docID);
			document.store(rootDeweyID, parser, true, false, true);
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
	}

	public BracketNode getSingleDocument() {
		// TODO: assumption: docNumber of single document collection is 0
		return new BracketNode(this, 0);
	}

	public BracketNode getDocument(DocID docID) throws DocumentException {
		return new BracketNode(this, docID.getDocNumber());
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
	}

	@Override
	public void delete() throws DocumentException {
		try {
			store.index.dropIndex(tx, new PageID(collID));
			store.index.dropIndex(tx, new PageID(pathSynopsis
					.getPathSynopsisNo()));
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public IndexController<BracketNode> getIndexController() {
		return indexController;
	}

	public BracketStore getRecordManager() {
		return store;
	}

	public PathSynopsisMgr getPathSynopsis() {
		return pathSynopsis;
	}

	@Override
	public BracketNode getDocument() throws DocumentException {
		// TODO: assumption: docNumber of single document collection is 0
		return new BracketNode(this, 0);
	}

	@Override
	public void remove(long documentID) throws OperationNotSupportedException,
			DocumentException {
		// TODO Auto-generated method stub

	}

	@Override
	public Stream<? extends BracketNode> getDocuments()
			throws DocumentException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BracketNode add(SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		
		InsertController insertCtrl = null;
		try {
			// TODO OpenMode.BULK
			insertCtrl = store.index.openForInsert(tx, new PageID(collID), OpenMode.LOAD, null);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
		
		DocID newDocID = insertCtrl.getStartInsertKey().getDocID();
		
		BracketNode document = new BracketNode(this, newDocID.getDocNumber());
		XTCdeweyID rootDeweyID = XTCdeweyID.newRootID(newDocID);
		document.store(rootDeweyID, parser, insertCtrl, false, true);
		return document;
	}
}
