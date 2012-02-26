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

import java.util.ArrayList;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElNode;
import org.brackit.server.node.index.IndexController;
import org.brackit.server.node.txnode.DebugListener;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.InsertController;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.parser.CollectionParser;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
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

	public int create(StorageSpec spec, SubtreeParser parser)
			throws DocumentException {

		InsertController insertCtrl = null;
		try {
			PageID rootPageID = store.index.createIndex(tx,
					spec.getContainerID());

			collID = rootPageID.value();
			name = spec.getDocumentName();
			dictionary = spec.getDictionary();
			pathSynopsis = store.pathSynopsisMgrFactory.create(tx,
					spec.getDictionary(), spec.getContainerID());

			if (parser != null) {

				// open index in LOAD mode
				insertCtrl = store.index.openForInsert(tx, new PageID(collID),
						OpenMode.LOAD, null);
				
				storeDocuments(0, parser, insertCtrl, false);

				// close index
				insertCtrl.close();
			}

			return collID;

		} catch (IndexAccessException e) {
			if (insertCtrl != null) {
				try {
					insertCtrl.close();
				} catch (Exception e1) {
				}
			}
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
		try {
			return getDocument();
		} catch (DocumentException e) {
			return null;
		}
	}

	public BracketNode getDocument(int docNumber) throws DocumentException {
		return new BracketNode(this, docNumber);
	}

	@Override
	public Node<?> materialize() throws DocumentException {
		Node<?> root = super.materialize();
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
	}

	@Override
	public void delete() throws DocumentException {
		try {
			store.index.dropIndex(tx, new PageID(collID));
			store.index.dropIndex(tx,
					new PageID(pathSynopsis.getPathSynopsisNo()));
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
		// Stream<BracketNode> docs = store.index.openDocumentStream(new
		// BracketLocator(this, new DocID(collID, 0)), null);
		// BracketNode doc = docs.next();
		// docs.close();
		// return doc;
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
		return store.index.openDocumentStream(new BracketLocator(this,
				new DocID(collID, 0)), null);
	}

	@Override
	public BracketNode add(SubtreeParser parser) throws DocumentException {

		try {
			InsertController insertCtrl = store.index.openForInsert(tx,
					new PageID(collID), OpenMode.BULK, null);
			
			int nextDocNumber = insertCtrl.getStartInsertKey().getDocID().getDocNumber();
			storeDocuments(nextDocNumber, parser, insertCtrl, true);
			insertCtrl.close();
			
			return new BracketNode(this, nextDocNumber);

		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private void storeDocuments(int nextDocNumber, SubtreeParser parser,
			InsertController insertCtrl, boolean updateIndexes)
			throws DocumentException {

		ArrayList<SubtreeListener<? super BracketNode>> listener = new ArrayList<SubtreeListener<? super BracketNode>>(
				5);
		listener.add(new BracketDocIndexListener(ListenMode.INSERT, insertCtrl));
		//listener.add(new DebugListener());

		if (updateIndexes) {
			listener.addAll(indexController.getIndexListener(ListenMode.INSERT));
		}
		
		// make sure the CollectionParser is used
		if (!(parser instanceof CollectionParser)) {
			parser = new CollectionParser(parser);
		}

		BracketSubtreeBuilder subtreeHandler = new BracketSubtreeBuilder(this,
				nextDocNumber, listener.toArray(new SubtreeListener[listener
						.size()]));
		parser.parse(subtreeHandler);

		// remark: at this point, the insertCtrl is still open for further
		// inserts
	}
}
