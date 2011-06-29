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

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.node.txnode.DebugListener;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketIter;
import org.brackit.server.store.index.bracket.HintPageInformation;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.services.MetaLockService;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.node.stream.EmptyStream;
import org.brackit.xquery.node.stream.IteratorStream;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Martin Hiller
 * 
 */
public class BracketNode extends TXNode<BracketNode> {

	private static final Logger log = Logger.getLogger(BracketNode.class);

	protected final BracketLocator locator;

	protected String value;

	protected PSNode psNode;

	public HintPageInformation hintPageInfo;

	public BracketNode(BracketLocator locator) {
		super(new XTCdeweyID(locator.docID), Kind.DOCUMENT.ID);
		this.locator = locator;
	}

	public BracketNode(BracketCollection collection, PageID rootPageID) {
		super(new XTCdeweyID(new DocID(rootPageID.value())), Kind.DOCUMENT.ID);
		this.locator = new BracketLocator(collection, deweyID.getDocID(),
				rootPageID);
	}

	public BracketNode(BracketLocator locator, XTCdeweyID deweyID, byte type,
			String value, PSNode psNode) {
		super(deweyID, type);
		this.locator = locator;
		this.value = value;
		this.psNode = psNode;
	}

	public BracketNode(BracketCollection collection, BracketNode document) {
		super(document.deweyID, document.type);
		this.locator = new BracketLocator(collection, document.locator);
	}

	@Override
	public BracketNode copyFor(Tx tx) {
		BracketCollection copyCol = locator.collection.copyFor(tx);
		if (copyCol == locator.collection) {
			return this;
		}
		BracketLocator copyLoc = new BracketLocator(copyCol, locator.docID,
				locator.rootPageID);
		BracketNode copyNode = new BracketNode(copyLoc, deweyID, type, value,
				psNode);
		return copyNode;
	}

	public DocID getID() {
		return locator.docID;
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

	public int getVocID() {
		return ((type == Kind.ELEMENT.ID) || (type == Kind.ATTRIBUTE.ID)) ? psNode
				.getVocID() : -1;
	}

	private BracketNode getNodeGeneric(NavigationMode navMode,
			XTCdeweyID referenceDeweyID) throws DocumentException {

		try {
			BracketIter iterator = locator.collection.store.index.open(getTX(),
					locator.rootPageID, navMode, referenceDeweyID,
					OpenMode.READ, hintPageInfo);
			if (iterator == null) {
				return null;
			}

			XTCdeweyID key = iterator.getKey();
			byte[] value = iterator.getValue();
			HintPageInformation pageInfo = iterator.getPageInformation();
			iterator.close();

			BracketNode result = locator.fromBytes(key, value);
			result.hintPageInfo = pageInfo;
			return result;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

	}

	@Override
	public BracketNode getNodeInternal(XTCdeweyID deweyID)
			throws DocumentException {

		try {
			BracketIter iterator = locator.collection.store.index.open(getTX(),
					locator.rootPageID, NavigationMode.TO_KEY, deweyID,
					OpenMode.READ);
			if (iterator == null) {
				throw new DocumentException(String.format(
						"No record found with key %s.", deweyID));
			}

			byte[] value = iterator.getValue();
			HintPageInformation pageInfo = iterator.getPageInformation();
			iterator.close();

			BracketNode result = locator.fromBytes(deweyID, value);
			result.hintPageInfo = pageInfo;
			return result;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public BracketNode insertRecord(XTCdeweyID childDeweyID, Kind kind,
			String value) throws DocumentException {
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
			int vocID = coll.getDictionary().translate(getTX(), value);
			PSNode childPsNode = pathSynopsisMgr.getChild(getTX(),
					psNode.getPCR(), vocID, Kind.ELEMENT.ID);
			physicalRecord = ElRecordAccess.createRecord(childPsNode.getPCR(),
					Kind.ELEMENT.ID, null);
			node = new BracketNode(locator, childDeweyID, kind.ID, null,
					childPsNode);
			value = null;
		} else {
			physicalRecord = ElRecordAccess.createRecord(psNode.getPCR(),
					kind.ID, value);
			node = new BracketNode(locator, childDeweyID, type, value, psNode);
		}

		notifyUpdate(locator, ListenMode.INSERT, node);

		try {
			BracketIter iterator = r.index
					.open(getTX(), locator.rootPageID,
							NavigationMode.TO_INSERT_POS, childDeweyID,
							OpenMode.UPDATE);
			iterator.insert(childDeweyID, physicalRecord, 0);
			iterator.close();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		return node;
	}

	@Override
	public BracketNode insertSubtree(XTCdeweyID deweyID, SubtreeParser parser)
			throws DocumentException {
		return store(deweyID, parser, false, true);
	}

	@Override
	public Stream<? extends BracketNode> getSubtreeInternal()
			throws DocumentException {
		return new BracketSubtreeStream(locator, deweyID);
	}

	@Override
	public String getNameInternal() throws DocumentException {
		return ((type == Kind.ELEMENT.ID) || (type == Kind.ATTRIBUTE.ID)) ? psNode
				.getName() : null;
	}

	@Override
	public String getValueInternal() throws DocumentException {
		return ((type != Kind.DOCUMENT.ID) && (type != Kind.ELEMENT.ID)) ? value
				: getText();
	}

	@Override
	public void setNameInternal(String name)
			throws OperationNotSupportedException, DocumentException {
		throw new OperationNotSupportedException();
	}

	@Override
	public void setValueInternal(String value)
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
			byte[] oldPhysicalRecord = iterator.getValue();

			int PCR = ElRecordAccess.getPCR(oldPhysicalRecord);

			byte[] physicalRecord = ElRecordAccess.createRecord(PCR, type,
					value);

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
	public boolean deleteAttributeInternal(String name)
			throws OperationNotSupportedException, DocumentException {

		BracketNode attribute = getAttribute(name);

		if (attribute == null) {
			return false;
		}

		attribute.deleteInternal();
		return true;
	}

	@Override
	public BracketNode setAttributeInternal(String name, String value)
			throws OperationNotSupportedException, DocumentException {

		BracketStore r = locator.collection.store;
		XTCdeweyID elementDeweyID = deweyID;
		XTCdeweyID attributeDeweyID = null;
		boolean update = false;

		PSNode elementPsNode = psNode;
		int vocID = locator.collection.getDictionary().translate(getTX(), name);
		PSNode attributePsNode = locator.pathSynopsis.getChild(getTX(),
				elementPsNode.getPCR(), vocID, Kind.ATTRIBUTE.ID);
		byte[] physicalRecord = ElRecordAccess.createRecord(
				attributePsNode.getPCR(), Kind.ATTRIBUTE.ID, value);

		try {
			// Scan all attributes of this element and check if attribute with
			// specified name is already available
			BracketIter iterator = r.index.open(getTX(), locator.rootPageID,
					NavigationMode.TO_KEY, deweyID, OpenMode.UPDATE,
					hintPageInfo);

			while (iterator.navigate(NavigationMode.NEXT_ATTRIBUTE)) {

				attributeDeweyID = iterator.getKey();
				byte[] oldPhysicalRecord = iterator.getValue();
				if (ElRecordAccess.getPCR(oldPhysicalRecord) == attributePsNode
						.getPCR()) {
					// remove old entry from all indexes
					BracketNode oldAttribute = locator.fromBytes(
							attributeDeweyID, oldPhysicalRecord);
					notifyUpdate(locator, ListenMode.DELETE, oldAttribute);

					// we will reuse current deweyID and only current record
					update = true;
					break;
				}
			}

			if (update) {
				iterator.update(physicalRecord);
			} else {
				attributeDeweyID = (attributeDeweyID == null) ? elementDeweyID
						.getNewAttributeID() : XTCdeweyID.newBetween(
						attributeDeweyID, null);
				iterator.insertPrefixAware(attributeDeweyID, physicalRecord, 0);
			}
			iterator.close();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		// insert new entry in all indexes
		BracketNode newNode = new BracketNode(locator, attributeDeweyID,
				Kind.ATTRIBUTE.ID, value, attributePsNode);
		notifyUpdate(locator, ListenMode.INSERT, newNode);

		return newNode;
	}

	@Override
	public void deleteInternal() throws DocumentException {

		BracketStore r = locator.collection.store;
		PageID rootPageID = locator.rootPageID;
		List<SubtreeListener<? super BracketNode>> listeners = getListener(ListenMode.DELETE);

		try {
			BracketIter iterator = r.index.open(getTX(), rootPageID,
					NavigationMode.TO_KEY, deweyID, OpenMode.UPDATE,
					hintPageInfo);
			iterator.deleteSubtree(new SubtreeDeleteListenerImpl(locator,
					listeners.toArray(new SubtreeListener[listeners.size()])));
			iterator.close();

			hintPageInfo = null;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public BracketNode getAttributeInternal(String name)
			throws DocumentException {
		try {
			BracketIter iterator = locator.collection.store.index.open(getTX(),
					locator.rootPageID, NavigationMode.NEXT_ATTRIBUTE, deweyID,
					OpenMode.READ, hintPageInfo);
			if (iterator == null) {
				return null;
			}

			BracketNode attribute = null;

			try {

				do {
					XTCdeweyID currentDeweyID = iterator.getKey();
					byte[] record = iterator.getValue();

					BracketNode currentAttribute = locator.fromBytes(
							currentDeweyID, record);
					currentAttribute.hintPageInfo = iterator
							.getPageInformation();

					if (currentAttribute.psNode.getName().equals(name)) {
						attribute = currentAttribute;
						break;
					}
				} while (iterator.navigate(NavigationMode.NEXT_ATTRIBUTE));

			} catch (DocumentException e) {
				iterator.close();
				throw e;
			}

			iterator.close();
			return attribute;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public Stream<? extends BracketNode> getAttributesInternal()
			throws OperationNotSupportedException, DocumentException {

		try {
			BracketIter iterator = locator.collection.store.index.open(getTX(),
					locator.rootPageID, NavigationMode.NEXT_ATTRIBUTE, deweyID,
					OpenMode.READ, hintPageInfo);
			if (iterator == null) {
				return new EmptyStream<BracketNode>();
			}

			ArrayList<BracketNode> attributes = new ArrayList<BracketNode>();

			try {

				do {
					XTCdeweyID currentDeweyID = iterator.getKey();
					byte[] record = iterator.getValue();

					BracketNode attribute = locator.fromBytes(currentDeweyID,
							record);
					attribute.hintPageInfo = iterator.getPageInformation();
					attributes.add(attribute);

				} while (iterator.navigate(NavigationMode.NEXT_ATTRIBUTE));

			} catch (DocumentException e) {
				iterator.close();
				throw e;
			}

			iterator.close();
			return new IteratorStream<BracketNode>(attributes);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
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
		XTCdeweyID parentDeweyID = deweyID.getParent();
		if (parentDeweyID.isDocument()) {
			return new BracketNode(locator);
		}
		
		return getNodeGeneric(NavigationMode.TO_KEY, deweyID.getParent());
	}

	@Override
	public BracketNode getPreviousSiblingInternal() throws DocumentException {
		return getNodeGeneric(NavigationMode.PREVIOUS_SIBLING, deweyID);
	}

	private List<SubtreeListener<? super BracketNode>> getListener(
			ListenMode mode) throws DocumentException {
		ArrayList<SubtreeListener<? super BracketNode>> listeners = new ArrayList<SubtreeListener<? super BracketNode>>();
		listeners.add(new DebugListener());
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

	BracketNode store(XTCdeweyID rootDeweyID, SubtreeParser parser,
			boolean exclusive, boolean updateIndexes) throws DocumentException {
		OpenMode openMode = (exclusive) ? OpenMode.LOAD : OpenMode.BULK;

		ArrayList<SubtreeListener<? super BracketNode>> listener = new ArrayList<SubtreeListener<? super BracketNode>>(
				5);
		listener.add(new DebugListener());
		listener.add(new BracketDocIndexListener(locator, ListenMode.INSERT,
				openMode));

		if (updateIndexes) {
			listener.addAll(getListener(ListenMode.INSERT));
		}

		BracketSubtreeHandler subtreeHandler = new BracketSubtreeHandler(
				locator, this, rootDeweyID,
				listener.toArray(new SubtreeListener[listener.size()]));
		parser.parse(subtreeHandler);

		BracketNode root = getNode(rootDeweyID);

		return root;
	}

	private String getText() throws DocumentException {

		BracketStore r = locator.collection.store;
		StringBuilder text = new StringBuilder();

		try {

			XTCdeweyID openDeweyID = (type == Kind.DOCUMENT.ID) ? XTCdeweyID
					.newRootID(locator.docID) : deweyID;
			BracketIter iterator = r.index.open(getTX(), locator.rootPageID,
					NavigationMode.TO_KEY, openDeweyID, OpenMode.READ,
					hintPageInfo);

			if (iterator != null) {

				if (type == Kind.ELEMENT.ID) {
					// move iterator to next node
					if (!iterator.next()) {
						return "";
					}
				}

				do {
					XTCdeweyID currentDeweyID = iterator.getKey();

					if (deweyID.isPrefixOf(currentDeweyID)) {
						if (!currentDeweyID.isAttribute()) {
							byte[] record = iterator.getValue();
							String textValue = ElRecordAccess.getValue(record);

							if (textValue != null) {
								text.append(textValue);
							}
						}
					} else {
						break;
					}
				} while (iterator.next());

				iterator.close();
			}

			return text.toString();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public String toString() {
		String name = ((type == Kind.ATTRIBUTE.ID) || (type == Kind.ELEMENT.ID)) ? psNode
				.getName() : null;
		int pcr = (psNode != null) ? psNode.getPCR() : -1;
		return String
				.format("%s(doc='%s', docID='%s', type='%s', name='%s', value='%s', pcr='%s')",
						deweyID, locator.collection.getName(),
						deweyID.getDocID(), getKind(), name, value, pcr);
	}

	@Override
	public Stream<BracketNode> getChildren() throws DocumentException {
		if (getKind() == Kind.ATTRIBUTE) {
			return new EmptyStream<BracketNode>();
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockTreeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}

		return locator.collection.store.index.openChildStream(locator, deweyID, hintPageInfo);
	}

}
