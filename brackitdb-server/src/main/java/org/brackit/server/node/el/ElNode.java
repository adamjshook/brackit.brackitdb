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

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.DebugListener;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.services.MetaLockService;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.NavigationalSubtreeParser;
import org.brackit.xquery.node.parser.StreamSubtreeProcessor;
import org.brackit.xquery.node.parser.SubtreeHandler;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Scope;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ElNode extends TXNode<ElNode> {

	private class AttributeStream implements Stream<ElNode> {
		private IndexIterator it;

		@Override
		public ElNode next() throws DocumentException {
			try {
				if (it == null) {
					ElStore r = locator.collection.store;
					it = r.index.open(getTX(), locator.rootPageID,
							SearchMode.LEAST_HAVING_PREFIX, deweyID.toBytes(),
							null, OpenMode.READ, hintPageID, hintPageLSN);
					if (it.getKey() == null) {
						it.close();
						throw new DocumentException(
								"No record found with key having %s as prefix.",
								deweyID);
					}
				} else if (!it.next()) {
					return null;
				}

				XTCdeweyID id = new XTCdeweyID(deweyID.getDocID(), it.getKey());

				if (!id.isAttributeOf(deweyID)) {
					return null;
				} else {
					byte[] record = it.getValue();
					PageID hintPageID = it.getCurrentPageID();
					long hintPageLSN = it.getCurrentLSN();

					ElNode attribute = locator.fromBytes(id, record);
					attribute.hintPageID = hintPageID;
					attribute.hintPageLSN = hintPageLSN;
					return attribute;
				}
			} catch (IndexAccessException e) {
				throw new DocumentException(e);
			}
		}

		public void close() {
			if (it != null) {
				it.close();
			}
		}
	}

	protected final ElLocator locator;

	protected Atomic value;

	protected PSNode psNode;

	protected PageID hintPageID;

	protected long hintPageLSN;

	public ElNode(ElLocator locator) {
		super(new XTCdeweyID(locator.docID), Kind.DOCUMENT.ID);
		this.locator = locator;
	}

	public ElNode(ElCollection collection, PageID rootPageID) {
		super(new XTCdeweyID(new DocID(rootPageID.value())), Kind.DOCUMENT.ID);
		this.locator = new ElLocator(collection, deweyID.getDocID(), rootPageID);
	}

	public ElNode(ElLocator locator, XTCdeweyID deweyID, byte type,
			Atomic value, PSNode psNode) {
		super(deweyID, type);
		this.locator = locator;
		this.value = value;
		this.psNode = psNode;
	}

	public ElNode(ElCollection collection, ElNode document) {
		super(document.deweyID, document.type);
		this.locator = new ElLocator(collection, document.locator);
	}

	@Override
	public ElNode copyFor(Tx tx) {
		ElCollection copyCol = locator.collection.copyFor(tx);
		if (copyCol == locator.collection) {
			return this;
		}
		ElLocator copyLoc = new ElLocator(copyCol, locator.docID,
				locator.rootPageID);
		ElNode copyNode = new ElNode(copyLoc, deweyID, type, value, psNode);
		return copyNode;
	}

	public DocID getID() {
		return locator.docID;
	}

	public ElLocator getLocator() {
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
	public TXCollection<ElNode> getCollection() {
		return locator.collection;
	}

	@Override
	public MetaLockService<?> getNls() {
		return locator.collection.store.mls;
	}

	public int getPCR() {
		return (psNode != null) ? psNode.getPCR() : -1;
	}

	@Override
	public ElNode getNodeInternal(XTCdeweyID deweyID) throws DocumentException {
		ElStore r = locator.collection.store;

		if (deweyID.equals(this.deweyID)) {
			return this;
		}

		if (deweyID.isRoot()) {
			return new ElNode(locator);
		}

		try {
			IndexIterator iterator = r.index.open(getTX(), locator.rootPageID,
					SearchMode.LEAST_HAVING_PREFIX, deweyID.toBytes(), null,
					OpenMode.READ);

			byte[] key = iterator.getKey();
			byte[] value = iterator.getValue();
			iterator.close();
			if (key == null) {
				throw new DocumentException(
						"Could not get record %s because index was opened at EOF.",
						deweyID);
			}
			XTCdeweyID tmp = new XTCdeweyID(deweyID.getDocID(), key);
			if (!(tmp.isDescendantOrSelfOf(deweyID))) {
				throw new DocumentException(
						"Could not get record %s because index was opened at key %s.",
						deweyID, tmp);
			}
			return locator.fromBytes(deweyID, value);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public ElNode insertRecord(XTCdeweyID childDeweyID, Kind kind, QNm name,
			Atomic value) throws DocumentException {
		if ((kind != Kind.ELEMENT) && (kind != Kind.TEXT)
				&& (kind != Kind.COMMENT)
				&& (kind != Kind.PROCESSING_INSTRUCTION)) {
			throw new DocumentException(
					"Operation not allowed for nodes of type: %s", kind);
		}

		ElCollection coll = locator.collection;
		ElStore r = coll.store;
		PathSynopsisMgr pathSynopsisMgr = locator.pathSynopsis;

		byte[] record;
		ElNode node;

		if (kind == Kind.ELEMENT) {
			int uriVocID = (name.nsURI.isEmpty() ? -1 : coll.getDictionary()
					.translate(getTX(), name.nsURI));
			int prefixVocID = (name.prefix == null ? -1 : coll.getDictionary()
					.translate(getTX(), name.prefix));
			int localNameVocID = coll.getDictionary().translate(getTX(),
					name.localName);
			PSNode childPsNode = pathSynopsisMgr.getChild(getTX(),
					psNode.getPCR(), uriVocID, prefixVocID, localNameVocID,
					Kind.ELEMENT.ID);
			record = ElRecordAccess.createRecord(childPsNode.getPCR(),
					Kind.ELEMENT.ID, null);
			node = new ElNode(locator, childDeweyID, kind.ID, null, childPsNode);
			value = null;
		} else {
			record = ElRecordAccess.createRecord(psNode.getPCR(), kind.ID,
					value.stringValue());
			node = new ElNode(locator, childDeweyID, kind.ID, value, psNode);
		}

		notifyUpdate(locator, ListenMode.INSERT, node);

		try {
			ElIndexIterator iterator = r.index.open(getTX(),
					locator.rootPageID, SearchMode.GREATER_OR_EQUAL,
					childDeweyID.toBytes(), null, OpenMode.UPDATE);
			iterator.insertPrefixAware(childDeweyID.toBytes(), record,
					deweyID.getLevel());
			iterator.close();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		return node;
	}

	@Override
	public ElNode insertSubtree(XTCdeweyID deweyID, SubtreeParser parser)
			throws DocumentException {
		return store(deweyID, parser, false, true);
	}

	@Override
	public Stream<? extends ElNode> getSubtreeInternal()
			throws DocumentException {
		return createScanner(locator, deweyID, deweyID, false);
	}

	@Override
	public QNm getNameInternal() throws DocumentException {
		return psNode.getName();
	}

	@Override
	public Atomic getValueInternal() throws DocumentException {
		return ((type != Kind.DOCUMENT.ID) && (type != Kind.ELEMENT.ID)) ? value
				: new Una(getText(this));
	}

	@Override
	public void setNameInternal(QNm name)
			throws OperationNotSupportedException, DocumentException {
		throw new OperationNotSupportedException();
	}

	@Override
	public void setValueInternal(Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if (type == Kind.ELEMENT.ID) {
			throw new DocumentException(
					"Set value of elements not implemented yet.");
		}

		ElStore r = locator.collection.store;
		PageID rootPageID = locator.rootPageID;

		try {
			IndexIterator iterator = r.index.open(getTX(), rootPageID,
					SearchMode.GREATER_OR_EQUAL, deweyID.toBytes(), null,
					OpenMode.UPDATE);
			byte[] oldRecord = iterator.getValue();
			int PCR = ElRecordAccess.getPCR(oldRecord);
			byte[] record = ElRecordAccess.createRecord(PCR, type, value.stringValue());

			// delete old entry from all indexes
			notifyUpdate(locator, ListenMode.DELETE, this);

			iterator.update(record);
			this.value = value;
			this.hintPageID = iterator.getCurrentPageID();
			this.hintPageLSN = iterator.getCurrentLSN();
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
		ElNode attribute = getAttribute(name);

		if (attribute == null) {
			return false;
		}

		attribute.deleteInternal();
		return true;
	}

	@Override
	public ElNode setAttributeInternal(QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {
		ElCollection coll = locator.collection;
		ElStore r = coll.store;
		XTCdeweyID elementDeweyID = deweyID;
		XTCdeweyID attributeDeweyID = null;
		boolean update = false;

		PSNode elementPsNode = psNode;
		
		int uriVocID = (name.nsURI.isEmpty() ? -1 : coll.getDictionary()
				.translate(getTX(), name.nsURI));
		int prefixVocID = (name.prefix == null ? -1 : coll.getDictionary()
				.translate(getTX(), name.prefix));
		int localNameVocID = coll.getDictionary().translate(getTX(),
				name.localName);
		PSNode attributePsNode = locator.pathSynopsis.getChild(getTX(),
				elementPsNode.getPCR(), uriVocID, prefixVocID, localNameVocID, Kind.ATTRIBUTE.ID);
		byte[] record = ElRecordAccess.createRecord(attributePsNode.getPCR(),
				Kind.ATTRIBUTE.ID, value.stringValue());

		try {
			// Scan all attributes of this element and check if attribute with
			// specified name is already available
			ElIndexIterator iterator = r.index.open(getTX(),
					locator.rootPageID, SearchMode.GREATER_OR_EQUAL,
					elementDeweyID.toAttributeRootBytes(), null,
					OpenMode.UPDATE);

			if (iterator.getKey() != null) {
				do {
					XTCdeweyID currentDeweyID = new XTCdeweyID(
							elementDeweyID.getDocID(), iterator.getKey());
					byte[] oldRecord = iterator.getValue();

					if (currentDeweyID.isAttributeOf(elementDeweyID)) {
						// remember last seen attribute deweyID
						attributeDeweyID = currentDeweyID;

						if (ElRecordAccess.getPCR(oldRecord) == attributePsNode
								.getPCR()) {
							// remove old entry from all indexes
							ElNode oldAttribute = locator.fromBytes(
									attributeDeweyID, oldRecord);
							notifyUpdate(locator, ListenMode.DELETE,
									oldAttribute);

							// we will reuse current deweyID and only current
							// record
							update = true;
							break;
						}
					} else {
						break;
					}
				} while (iterator.next());
			}

			if (update) {
				iterator.update(record);
			} else {
				attributeDeweyID = (attributeDeweyID == null) ? elementDeweyID
						.getNewAttributeID() : XTCdeweyID.newBetween(
						attributeDeweyID, null);
				iterator.insertPrefixAware(attributeDeweyID.toBytes(), record,
						elementDeweyID.getLevel());
			}
			iterator.close();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}

		// insert new entry in all indexes
		ElNode newNode = new ElNode(locator, attributeDeweyID,
				Kind.ATTRIBUTE.ID, value, attributePsNode);
		notifyUpdate(locator, ListenMode.INSERT, newNode);

		return newNode;
	}

	@Override
	public void deleteInternal() throws DocumentException {
		List<SubtreeListener<? super ElNode>> listeners = getListener(
				ListenMode.DELETE, locator);
		Stream<ElNode> scanner = createScanner(locator, deweyID, deweyID, true);
		StreamSubtreeProcessor<ElNode> parser = new StreamSubtreeProcessor<ElNode>(
				scanner,
				listeners.toArray(new SubtreeListener[listeners.size()]));
		parser.process();
	}

	@Override
	public ElNode getAttributeInternal(QNm name) throws DocumentException {
		ElStore r = locator.collection.store;
		PageID rootPageID = locator.rootPageID;

		try {
			IndexIterator iterator = r.index.open(getTX(), rootPageID,
					SearchMode.LEAST_HAVING_PREFIX, deweyID.toBytes(), null,
					OpenMode.READ);

			if (iterator.getKey() == null) {
				iterator.close();
				throw new DocumentException(String.format(
						"No record found with key having %s as prefix.",
						deweyID));
			}

			do {
				XTCdeweyID currentDeweyID = new XTCdeweyID(deweyID.getDocID(),
						iterator.getKey());

				if (!currentDeweyID.isAttributeOf(deweyID)) {
					iterator.close();
					return null;
				} else {
					byte[] record = iterator.getValue();

					ElNode attribute = locator
							.fromBytes(currentDeweyID, record);

					if (name.equals(attribute.getName())) {
						attribute.hintPageID = iterator.getCurrentPageID();
						attribute.hintPageLSN = iterator.getCurrentLSN();
						iterator.close();
						return attribute;
					}
				}
			} while (iterator.next());

			iterator.close();
			return null;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public Stream<? extends ElNode> getAttributesInternal()
			throws OperationNotSupportedException, DocumentException {
		return new AttributeStream();
	}

	@Override
	public ElNode getFirstChildInternal() throws DocumentException {
		try {
			ElStore r = locator.collection.store;
			XTCdeweyID attributeRootID = deweyID.getAttributeRootID();
			SearchMode searchMode = (type == Kind.ELEMENT.ID) ? SearchMode.GREATEST_HAVING_PREFIX_RIGHT
					: SearchMode.FIRST;
			IndexIterator iterator = r.index.open(getTX(), locator.rootPageID,
					searchMode, attributeRootID.toBytes(), null, OpenMode.READ,
					hintPageID, hintPageLSN);

			if (iterator.getKey() != null) {
				XTCdeweyID currentDeweyID = new XTCdeweyID(deweyID.getDocID(),
						iterator.getKey());

				if (currentDeweyID.isDescendantOf(deweyID)) {
					byte[] record = iterator.getValue();
					PageID hintPageID = iterator.getCurrentPageID();
					long hintPageLSN = iterator.getCurrentLSN();
					iterator.close();

					XTCdeweyID fcDeweyID = currentDeweyID.getAncestor(deweyID
							.getLevel() + 1);

					ElNode firstChild = locator.fromBytes(fcDeweyID, record);
					firstChild.hintPageID = hintPageID;
					firstChild.hintPageLSN = hintPageLSN;
					return firstChild;
				}
			}

			iterator.close();
			return null;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public ElNode getLastChildInternal() throws DocumentException {
		ElStore r = locator.collection.store;

		try {
			SearchMode searchMode = (type == Kind.ELEMENT.ID) ? SearchMode.GREATEST_HAVING_PREFIX
					: SearchMode.LAST;
			IndexIterator iterator = r.index.open(getTX(), locator.rootPageID,
					searchMode, deweyID.toBytes(), null, OpenMode.READ,
					hintPageID, hintPageLSN);

			if (iterator.getKey() == null) {
				iterator.close();
				throw new DocumentException(String.format(
						"No record found with key having %s as prefix.",
						deweyID));
			}

			XTCdeweyID currentDeweyID = new XTCdeweyID(deweyID.getDocID(),
					iterator.getKey());
			XTCdeweyID lcDeweyID = currentDeweyID.getAncestor(deweyID
					.getLevel() + 1);

			if ((lcDeweyID != null)
					&& (lcDeweyID.isChildOf(deweyID) && (!lcDeweyID
							.isAttributeRoot()))) {
				byte[] record = iterator.getValue();
				PageID hintPageID = iterator.getCurrentPageID();
				long hintPageLSN = iterator.getCurrentLSN();
				iterator.close();

				ElNode lastChild = locator.fromBytes(lcDeweyID, record);
				lastChild.hintPageID = hintPageID;
				lastChild.hintPageLSN = hintPageLSN;

				return lastChild;
			}

			iterator.close();
			return null;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public ElNode getNextSiblingInternal() throws DocumentException {
		try {
			ElStore r = locator.collection.store;
			IndexIterator iterator = r.index.open(getTX(), locator.rootPageID,
					SearchMode.GREATEST_HAVING_PREFIX_RIGHT, deweyID.toBytes(),
					null, OpenMode.READ, hintPageID, hintPageLSN);

			if (iterator.getKey() == null) {
				iterator.close();
				return null;
			}

			XTCdeweyID currentDeweyID = new XTCdeweyID(deweyID.getDocID(),
					iterator.getKey());
			XTCdeweyID nsDeweyID = currentDeweyID.getAncestor(deweyID
					.getLevel());

			if ((nsDeweyID != null) && deweyID.isSiblingOf(nsDeweyID)) {
				byte[] record = iterator.getValue();
				PageID hintPageID = iterator.getCurrentPageID();
				long hintPageLSN = iterator.getCurrentLSN();
				iterator.close();

				ElNode nextSibling = locator.fromBytes(nsDeweyID, record);
				nextSibling.hintPageID = hintPageID;
				nextSibling.hintPageLSN = hintPageLSN;
				return nextSibling;
			}

			iterator.close();
			return null;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public ElNode getParentInternal() throws DocumentException {
		XTCdeweyID parentDeweyID = deweyID.getParent();

		if (parentDeweyID != null) {
			if (parentDeweyID.isAttributeRoot()) {
				parentDeweyID = parentDeweyID.getParent();
			}

			PSNode parentPsNode = psNode.getParent();
			byte parentType = (parentDeweyID.isDocument()) ? Kind.DOCUMENT.ID
					: Kind.ELEMENT.ID;
			ElNode parent = new ElNode(locator, parentDeweyID, parentType,
					null, parentPsNode);
			return parent;
		} else if (isRoot()) {
			return new ElNode(locator);
		}

		return null;
	}

	@Override
	public ElNode getPreviousSiblingInternal() throws DocumentException {
		final PageID rootPageID = locator.rootPageID;
		ElStore r = locator.collection.store;

		try {
			IndexIterator iterator = r.index.open(getTX(), rootPageID,
					SearchMode.LEAST_HAVING_PREFIX_LEFT, deweyID.toBytes(),
					null, OpenMode.READ, hintPageID, hintPageLSN);

			if (iterator.getKey() == null) {
				iterator.close();
				return null;
			}

			XTCdeweyID currentDeweyID = new XTCdeweyID(deweyID.getDocID(),
					iterator.getKey());
			XTCdeweyID psDeweyID = currentDeweyID.getAncestor(deweyID
					.getLevel());

			if ((psDeweyID != null) && (deweyID.isSiblingOf(psDeweyID))) {
				byte[] record = iterator.getValue();
				PageID hintPageID = iterator.getCurrentPageID();
				long hintPageLSN = iterator.getCurrentLSN();
				iterator.close();

				ElNode prevSibling = locator.fromBytes(psDeweyID, record);
				prevSibling.hintPageID = hintPageID;
				prevSibling.hintPageLSN = hintPageLSN;
				return prevSibling;
			}

			iterator.close();
			return null;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private Stream<ElNode> createScanner(ElLocator locator,
			XTCdeweyID subtreeRootDeweyID, XTCdeweyID startDeweyID,
			boolean delete) throws DocumentException {
		return new ElRecordScanner(locator.collection.store.index,
				subtreeRootDeweyID, startDeweyID, locator, delete);
	}

	private List<SubtreeListener<? super ElNode>> getListener(ListenMode mode,
			ElLocator locator) throws DocumentException {
		ArrayList<SubtreeListener<? super ElNode>> listeners = new ArrayList<SubtreeListener<? super ElNode>>();
		listeners.addAll(locator.collection.indexController
				.getIndexListener(mode));
		return listeners;
	}

	private void notifyUpdate(ElLocator locator, ListenMode listenMode,
			ElNode node) throws DocumentException {
		byte type = node.type;

		if (type == Kind.ELEMENT.ID) {
			for (SubtreeListener<? super ElNode> listener : getListener(
					listenMode, locator)) {
				listener.begin();
				listener.beginFragment();
				listener.startElement(node);
				listener.endElement(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.TEXT.ID) {
			for (SubtreeListener<? super ElNode> listener : getListener(
					listenMode, locator)) {
				listener.begin();
				listener.beginFragment();
				listener.text(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.ATTRIBUTE.ID) {
			for (SubtreeListener<? super ElNode> listener : getListener(
					listenMode, locator)) {
				listener.begin();
				listener.beginFragment();
				listener.attribute(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.COMMENT.ID) {
			for (SubtreeListener<? super ElNode> listener : getListener(
					listenMode, locator)) {
				listener.begin();
				listener.beginFragment();
				listener.comment(node);
				listener.endFragment();
				listener.end();
			}
		} else if (type == Kind.PROCESSING_INSTRUCTION.ID) {
			for (SubtreeListener<? super ElNode> listener : getListener(
					listenMode, locator)) {
				listener.begin();
				listener.beginFragment();
				listener.processingInstruction(node);
				listener.endFragment();
				listener.end();
			}
		}
	}

	ElNode store(XTCdeweyID rootDeweyID, SubtreeParser parser,
			boolean exclusive, boolean updateIndexes) throws DocumentException {
		OpenMode openMode = (exclusive) ? OpenMode.LOAD : OpenMode.BULK;

		ArrayList<SubtreeListener<? super ElNode>> listener = new ArrayList<SubtreeListener<? super ElNode>>(
				5);
		listener.add(new DebugListener());
		listener.add(new ElDocIndexListener(locator, ListenMode.INSERT,
				openMode));

		if (updateIndexes) {
			listener.addAll(getListener(ListenMode.INSERT, locator));
		}

		ElSubtreeHandler subtreeHandler = new ElSubtreeHandler(locator, this,
				rootDeweyID, listener.toArray(new SubtreeListener[listener
						.size()]));
		parser.parse(subtreeHandler);

		ElNode root = getNode(rootDeweyID);

		return root;
	}

	private String getText(ElNode node) throws DocumentException {
		ElStore r = locator.collection.store;
		StringBuilder text = new StringBuilder();

		try {
			SearchMode searchMode = (type == Kind.ELEMENT.ID) ? SearchMode.GREATEST_HAVING_PREFIX_RIGHT
					: SearchMode.FIRST;
			IndexIterator iterator = r.index.open(getTX(), locator.rootPageID,
					searchMode, deweyID.toAttributeRootBytes(), null,
					OpenMode.READ, hintPageID, hintPageLSN);

			if (iterator.getKey() != null) {
				do {
					XTCdeweyID currentDeweyID = new XTCdeweyID(
							deweyID.getDocID(), iterator.getKey());

					if (currentDeweyID.isDescendantOf(deweyID)) {
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
			}

			iterator.close();

			return text.toString();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}
	
	@Override
	public void parse(SubtreeHandler handler) throws DocumentException {
		SubtreeParser parser = new NavigationalSubtreeParser(this);
		parser.parse(handler);		
	}

	@Override
	public Scope getScope() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String toString() {
		QNm name = ((type == Kind.ATTRIBUTE.ID) || (type == Kind.ELEMENT.ID)) ? psNode
				.getName() : null;
		int pcr = (psNode != null) ? psNode.getPCR() : -1;
		return String
				.format("%s(doc='%s', docID='%s', type='%s', name='%s', value='%s', pcr='%s')",
						deweyID, locator.collection.getName(),
						deweyID.getDocID(), getKind(), name, value, pcr);
	}
}