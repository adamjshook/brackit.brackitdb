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
import java.util.BitSet;
import java.util.List;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.NsMapping;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr.SubtreeCopyResult;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketIter;
import org.brackit.server.store.index.bracket.HintPageInformation;
import org.brackit.server.store.index.bracket.InsertController;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.store.index.bracket.StreamIterator;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.store.index.bracket.filter.ElementFilter;
import org.brackit.server.store.index.bracket.filter.NodeTypeFilter;
import org.brackit.server.store.index.bracket.filter.PSNodeFilter;
import org.brackit.server.store.index.bracket.filter.TextFilter;
import org.brackit.server.store.page.bracket.RecordInterpreter;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.services.MetaLockService;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.NavigationalSubtreeParser;
import org.brackit.xquery.node.parser.SubtreeHandler;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.node.stream.AtomStream;
import org.brackit.xquery.util.Cfg;
import org.brackit.xquery.xdm.Axis;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Scope;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.type.NodeType;

/**
 * @author Martin Hiller
 * 
 */
public class BracketNode extends TXNode<BracketNode> {

	public static final boolean OPTIMIZE = Cfg.asBool(
			"org.brackit.server.node.bracket.optimize", false);

	public static final int NODE_CLASS_ID = 2;

	protected final BracketLocator locator;

	protected Atomic value;

	protected PSNode psNode;

	private BracketScope scope;

	public HintPageInformation hintPageInfo;

	public String[][] outputPrefetch = null;

	public BracketNode(BracketLocator locator) {
		super(new XTCdeweyID(locator.docID), Kind.DOCUMENT.ID);
		this.locator = locator;
	}

	public BracketNode(BracketCollection collection, int docNumber) {
		super(new XTCdeweyID(new DocID(collection.getID(), docNumber)),
				Kind.DOCUMENT.ID);
		this.locator = new BracketLocator(collection, deweyID.getDocID());
	}

	public BracketNode(BracketLocator locator, XTCdeweyID deweyID, byte type,
			Atomic value, PSNode psNode) {
		super(deweyID, type);
		this.locator = locator;
		this.value = value;
		this.psNode = psNode;
	}

	@Override
	public BracketNode copyFor(Tx tx) {
		BracketCollection copyCol = locator.collection.copyFor(tx);
		if (copyCol == locator.collection) {
			return this;
		}
		BracketLocator copyLoc = new BracketLocator(copyCol, locator.docID);
		BracketNode copyNode = new BracketNode(copyLoc, deweyID, type, value,
				psNode);
		return copyNode;
	}

	@Override
	public int getNodeClassID() {
		return NODE_CLASS_ID;
	}

	public BracketLocator getLocator() {
		return locator;
	}

	public PathSynopsisMgr getPathSynopsis() {
		return locator.pathSynopsis;
	}

	@Override
	public Tx getTX() {
		return locator.collection.getTX();
	}

	@Override
	public TXCollection<BracketNode> getCollection() {
		return locator.collection;
	}

	@Override
	public MetaLockService<?> getNls() {
		return locator.collection.store.mls;
	}

	public int getPCR() {
		return (psNode != null) ? psNode.getPCR() : -1;
	}

	private BracketNode getNodeGeneric(NavigationMode navMode,
			XTCdeweyID referenceDeweyID) throws DocumentException {

		try {
			BracketIter iterator = locator.collection.store.index.open(getTX(),
					locator.rootPageID, navMode, referenceDeweyID,
					OpenMode.READ,
					navMode != NavigationMode.TO_KEY ? hintPageInfo : null);
			if (iterator == null) {
				return null;
			}

			BracketNode result = iterator.load(locator.bracketNodeLoader);
			iterator.close();

			return result;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

	}

	@Override
	public BracketNode getNodeInternal(XTCdeweyID deweyID)
			throws DocumentException {

		if (deweyID.equals(this.deweyID)) {
			return this;
		}

		try {
			BracketIter iterator = locator.collection.store.index.open(getTX(),
					locator.rootPageID, NavigationMode.TO_KEY, deweyID,
					OpenMode.READ);
			if (iterator == null) {
				throw new DocumentException(String.format(
						"No record found with key %s.", deweyID));
			}

			BracketNode result = iterator.load(locator.bracketNodeLoader);
			iterator.close();

			return result;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public BracketNode insertRecord(XTCdeweyID childDeweyID, Kind kind,
			QNm name, Atomic value) throws DocumentException {
		if ((kind != Kind.ELEMENT) && (kind != Kind.TEXT)
				&& (kind != Kind.COMMENT)
				&& (kind != Kind.PROCESSING_INSTRUCTION)) {
			throw new DocumentException(
					"Operation not allowed for nodes of type: %s", kind);
		}

		BracketCollection coll = locator.collection;
		BracketStore r = coll.store;
		PathSynopsisMgr pathSynopsisMgr = locator.pathSynopsis;

		byte[] physicalRecord;
		BracketNode node;

		if (kind == Kind.ELEMENT) {
			PSNode childPsNode = pathSynopsisMgr.getChild(psNode.getPCR(),
					name, Kind.ELEMENT.ID, null);
			physicalRecord = ElRecordAccess.createRecord(childPsNode.getPCR(),
					Kind.ELEMENT.ID, null);
			;
			node = new BracketNode(locator, childDeweyID, kind.ID, null,
					childPsNode);
			value = null;
		} else {
			physicalRecord = ElRecordAccess.createRecord(psNode.getPCR(),
					kind.ID, value.stringValue());
			node = new BracketNode(locator, childDeweyID, type, value, psNode);
		}

		notifyUpdate(locator, ListenMode.INSERT, node);

		try {
			InsertController insertCtrl = r.index.openForInsert(
					locator.collection.getTX(), locator.rootPageID,
					OpenMode.UPDATE, childDeweyID);
			insertCtrl.insert(childDeweyID, physicalRecord, 0);
			insertCtrl.close();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		return node;
	}

	@Override
	public BracketNode insertSubtree(XTCdeweyID deweyID, SubtreeParser parser)
			throws DocumentException {
		storeSubtree(deweyID, parser, false, true);
		return getNode(deweyID);
	}

	@Override
	public Stream<? extends BracketNode> getSubtreeInternal()
			throws DocumentException {
		return locator.collection.store.index.openSubtreeStream(locator,
				deweyID, hintPageInfo, null, true, false);
	}

	@Override
	public QNm getNameInternal() throws DocumentException {
		return psNode.getName();
	}

	@Override
	public Atomic getValueInternal() throws DocumentException {
		return ((type != Kind.DOCUMENT.ID) && (type != Kind.ELEMENT.ID)) ? value
				: new Una(getText());
	}

	@Override
	public void setNameInternal(QNm name)
			throws OperationNotSupportedException, DocumentException {
		// change name, leave NS mapping unchanged
		setNsMappingAndName(psNode.getNsMapping(), name);
	}

	@Override
	public void setValueInternal(Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if (type == Kind.ELEMENT.ID) {
			throw new DocumentException(
					"Set value of elements not implemented yet.");
		}

		BracketStore r = locator.collection.store;
		PageID rootPageID = locator.rootPageID;

		try {
			BracketIter iterator = r.index.open(getTX(), rootPageID,
					NavigationMode.TO_KEY, deweyID, OpenMode.UPDATE,
					hintPageInfo);

			RecordInterpreter oldRecord = iterator.getRecord();
			int PCR = oldRecord.getPCR();

			byte[] physicalRecord = ElRecordAccess.createRecord(PCR, type,
					value.stringValue());

			// delete old entry from all indexes
			notifyUpdate(locator, ListenMode.DELETE, this);

			iterator.update(physicalRecord);
			this.value = value;
			this.hintPageInfo = iterator.getPageInformation();
			iterator.close();

			// insert new entry in all indexes
			notifyUpdate(locator, ListenMode.INSERT, this);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public boolean deleteAttributeInternal(QNm name)
			throws OperationNotSupportedException, DocumentException {

		BracketNode attribute = getAttribute(name);

		if (attribute == null) {
			return false;
		}

		attribute.deleteInternal();
		return true;
	}

	@Override
	public void parse(SubtreeHandler handler) throws DocumentException {
		SubtreeParser parser = new NavigationalSubtreeParser(this);
		parser.parse(handler);
	}

	@Override
	public BracketNode setAttributeInternal(QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {

		BracketStore r = locator.collection.store;

		try {

			BracketAttributeTuple attributeTuple = r.index.setAttribute(this,
					name, value);

			// notify indexes
			if (attributeTuple.oldAttribute != null) {
				notifyUpdate(locator, ListenMode.DELETE,
						attributeTuple.oldAttribute);
			}
			notifyUpdate(locator, ListenMode.INSERT,
					attributeTuple.newAttribute);

			return attributeTuple.newAttribute;

		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void deleteInternal() throws DocumentException {

		BracketStore r = locator.collection.store;
		List<SubtreeListener<? super BracketNode>> listeners = getListener(ListenMode.DELETE);
		// listeners.add(new DebugListener());

		r.index.deleteSubtree(
				locator,
				deweyID,
				hintPageInfo,
				new SubtreeDeleteListenerImpl(locator, listeners
						.toArray(new SubtreeListener[listeners.size()])));

		hintPageInfo = null;
	}

	@Override
	public BracketNode getAttributeInternal(QNm name) throws DocumentException {

		// look for the requested attribute PSNode
		PSNode attributePSNode = locator.pathSynopsis.getChildIfExists(
				psNode.getPCR(), name, Kind.ATTRIBUTE.ID, null);

		if (attributePSNode == null) {
			return null;
		}

		Stream<BracketNode> aStream = locator.collection.store.index
				.openAttributeStream(locator, deweyID, hintPageInfo,
						new PSNodeFilter(locator.pathSynopsis, attributePSNode,
								true));

		BracketNode attribute = aStream.next();
		aStream.close();

		return attribute;
	}

	@Override
	public Stream<? extends BracketNode> getAttributesInternal()
			throws OperationNotSupportedException, DocumentException {
		return locator.collection.store.index.openAttributeStream(locator,
				deweyID, hintPageInfo, null);
	}

	@Override
	public BracketNode getFirstChildInternal() throws DocumentException {
		if (type == Kind.DOCUMENT.ID) {
			return getNodeGeneric(NavigationMode.TO_KEY,
					XTCdeweyID.newRootID(locator.docID));
		} else {
			return getNodeGeneric(NavigationMode.FIRST_CHILD, deweyID);
		}
	}

	@Override
	public BracketNode getLastChildInternal() throws DocumentException {
		if (type == Kind.DOCUMENT.ID) {
			return getNodeGeneric(NavigationMode.TO_KEY,
					XTCdeweyID.newRootID(locator.docID));
		} else {
			return getNodeGeneric(NavigationMode.LAST_CHILD, deweyID);
		}
	}

	@Override
	public BracketNode getNextSiblingInternal() throws DocumentException {
		return getNodeGeneric(NavigationMode.NEXT_SIBLING, deweyID);
	}

	@Override
	public BracketNode getParentInternal() throws DocumentException {

		// TODO: remove
		if (deweyID.isRoot()) {
			return new BracketNode(locator);
		}

		return getNodeGeneric(NavigationMode.PARENT, deweyID);
	}

	@Override
	public BracketNode getPreviousSiblingInternal() throws DocumentException {
		return getNodeGeneric(NavigationMode.PREVIOUS_SIBLING, deweyID);
	}

	private List<SubtreeListener<? super BracketNode>> getListener(
			ListenMode mode) throws DocumentException {
		ArrayList<SubtreeListener<? super BracketNode>> listeners = new ArrayList<SubtreeListener<? super BracketNode>>();
		listeners.addAll(locator.collection.indexController
				.getIndexListener(mode));
		return listeners;
	}

	private void notifyUpdate(BracketLocator locator, ListenMode listenMode,
			BracketNode node) throws DocumentException {
		byte type = node.type;

		if (type == Kind.ELEMENT.ID) {
			for (SubtreeListener<? super BracketNode> listener : getListener(listenMode)) {
				listener.begin();
				listener.beginFragment();
				listener.startElement(node);
				listener.endElement(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.TEXT.ID) {
			for (SubtreeListener<? super BracketNode> listener : getListener(listenMode)) {
				listener.begin();
				listener.beginFragment();
				listener.text(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.ATTRIBUTE.ID) {
			for (SubtreeListener<? super BracketNode> listener : getListener(listenMode)) {
				listener.begin();
				listener.beginFragment();
				listener.attribute(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.COMMENT.ID) {
			for (SubtreeListener<? super BracketNode> listener : getListener(listenMode)) {
				listener.begin();
				listener.beginFragment();
				listener.comment(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.PROCESSING_INSTRUCTION.ID) {
			for (SubtreeListener<? super BracketNode> listener : getListener(listenMode)) {
				listener.begin();
				listener.beginFragment();
				listener.processingInstruction(node);
				listener.endFragment();
				listener.end();
			}
		}
	}

	void storeSubtree(XTCdeweyID rootDeweyID, SubtreeParser parser,
			boolean exclusive, boolean updateIndexes) throws DocumentException {
		OpenMode openMode = (exclusive) ? OpenMode.LOAD : OpenMode.BULK;

		ArrayList<SubtreeListener<? super BracketNode>> listener = new ArrayList<SubtreeListener<? super BracketNode>>(
				5);
		listener.add(new BracketDocIndexListener(locator, rootDeweyID,
				ListenMode.INSERT, openMode));
		// listener.add(new DebugListener());

		if (updateIndexes) {
			listener.addAll(getListener(ListenMode.INSERT));
		}

		BracketSubtreeBuilder subtreeHandler = new BracketSubtreeBuilder(
				locator, this, rootDeweyID,
				listener.toArray(new SubtreeListener[listener.size()]));
		parser.parse(subtreeHandler);
	}

	private String getText() throws DocumentException {
		TextFilter filter = new TextFilter();
		StreamIterator it = locator.collection.store.index.openSubtreeStream(
				locator, deweyID, hintPageInfo, filter, false, true);

		try {
			while (it.next() != null)
				;
		} finally {
			it.close();
		}
		return filter.getString();

	}

	@Override
	public String toString() {
		QNm name = ((type == Kind.ATTRIBUTE.ID) || (type == Kind.ELEMENT.ID)) ? psNode
				.getName() : null;
		int pcr = (psNode != null) ? psNode.getPCR() : -1;
		return String
				.format("%s(collection='%s', docID='%s', type='%s', name='%s', value='%s', pcr='%s')",
						deweyID, locator.collection.getName(),
						deweyID.getDocID(), getKind(), name, value, pcr);
	}

	@Override
	protected Stream<BracketNode> getChildrenInternal()
			throws DocumentException {
		return locator.collection.store.index.openChildStream(locator, deweyID,
				hintPageInfo, null);
	}

	@Override
	public Stream<? extends BracketNode> getDescendantOrSelf()
			throws DocumentException {

		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			if (getKind() != Kind.ATTRIBUTE) {
				return getDescendants(true, null);
			} else {
				return new AtomStream<BracketNode>(this);
			}
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockTreeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}

		Stream<? extends BracketNode> subtree;
		if (getKind() != Kind.ATTRIBUTE) {
			subtree = getDescendants(true, null);
		} else {
			subtree = new AtomStream<BracketNode>(this);
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			// TODO unlock tree when stream is closed
		}

		return subtree;
	}

	public Stream<? extends BracketNode> getDescendants(boolean self,
			BracketFilter filter) {
		return locator.collection.store.index.openSubtreeStream(locator,
				deweyID, hintPageInfo, filter, self, true);
	}

	@Override
	public Stream<? extends Node<?>> performStep(Axis axis, NodeType test)
			throws DocumentException {
		if (!OPTIMIZE) {
			return null;
		}
		if ((axis == Axis.DESCENDANT) && (test.getNodeKind() == Kind.ELEMENT)) {
			QNm name = test.getQName();
			PathSynopsisMgr ps = getPathSynopsis();
			BitSet matches = ps.match(name, deweyID.getLevel());
			ElementFilter filter = new ElementFilter(ps, name, matches);
			if (matches.cardinality() == 1) {
				int pcr = matches.nextSetBit(0);
				PSNode targetPSN = ps.get(pcr);
				if (targetPSN.getLevel() == deweyID.getLevel() + 1) {
					return locator.collection.store.index.openChildStream(
							locator, deweyID, hintPageInfo, filter);
				}
			}
			return locator.collection.store.index.openSubtreeStream(locator,
					deweyID, hintPageInfo, filter, false, true);
		}
		if ((axis == Axis.DESCENDANT_OR_SELF)
				&& (test.getNodeKind() == Kind.ELEMENT)) {
			return locator.collection.store.index.openSubtreeStream(locator,
					deweyID, hintPageInfo, createFilter(test.getQName()), true,
					true);
		}
		if ((axis == Axis.CHILD) && (test.getNodeKind() == Kind.ELEMENT)) {
			return locator.collection.store.index.openChildStream(locator,
					deweyID, hintPageInfo, createFilter(test.getQName()));
		}
		return null;
	}

	public Stream<? extends Node<?>> getChildPath(NodeType[] tests)
			throws QueryException {
		BracketFilter[] filters = new BracketFilter[tests.length];
		PathSynopsisMgr ps = getPathSynopsis();
		for (int i = 0; i < tests.length; i++) {
			filters[i] = new NodeTypeFilter(ps, tests[i]);
		}
		return locator.collection.store.index.openMultiChildStream(locator,
				deweyID, hintPageInfo, filters);
	}

	private BracketFilter createFilter(QNm name) throws DocumentException {
		PathSynopsisMgr ps = getPathSynopsis();
		BitSet matches = ps.match(name, deweyID.getLevel());
		if (matches.cardinality() == 1) {

		}
		return new ElementFilter(ps, name, matches);
	}

	protected void setNsMappingAndName(NsMapping nsMapping, QNm name)
			throws DocumentException {

		SubtreeCopyResult res = locator.collection.pathSynopsis.copySubtree(
				psNode.getPCR(), nsMapping, name);

		// iterate over subtree, change PCRs of decendants
		// TODO
		throw new OperationNotSupportedException();
	}

	@Override
	public Scope getScope() {

		if (scope == null && type == Kind.ELEMENT.ID) {
			scope = new BracketScope(this);
		}

		return scope;
	}
}
