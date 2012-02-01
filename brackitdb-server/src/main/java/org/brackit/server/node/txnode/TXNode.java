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
package org.brackit.server.node.txnode;

import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.FIRST_CHILD;
import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.LAST_CHILD;
import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.NEXT_SIBLING;
import static org.brackit.server.tx.locking.services.EdgeLockService.Edge.PREV_SIBLING;

import org.brackit.server.metadata.TXObject;
import org.brackit.server.node.NodeNotFoundException;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.services.MetaLockService;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.AbstractNode;
import org.brackit.xquery.node.d2linked.D2NodeFactory;
import org.brackit.xquery.node.parser.NavigationalSubtreeParser;
import org.brackit.xquery.node.parser.StreamSubtreeParser;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.node.stream.AtomStream;
import org.brackit.xquery.node.stream.EmptyStream;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Sebastian Baechle
 * 
 */
public abstract class TXNode<E extends TXNode<E>> extends AbstractNode<E>
		implements TXObject<E> {
	protected final XTCdeweyID deweyID;

	protected final byte type;

	public TXNode(XTCdeweyID deweyID, byte type) {
		this.deweyID = deweyID;
		this.type = type;
	}

	public final XTCdeweyID getDeweyID() {
		return deweyID;
	}

	public Kind getKind() {
		return Kind.map[type];
	}

	@Override
	public long getFragmentID() {
		return (((long) deweyID.docID.getCollectionID()) << 32);
	}

	@Override
	protected int cmpInternal(E other) {
		return deweyID.compareTo(other.getDeweyID());
	}

	private LockClass lockClass(Tx transaction, boolean write) {
		if (!write) {
			return (transaction.getIsolationLevel().longReadLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
		} else {
			return (transaction.getIsolationLevel().longWriteLocks()) ? LockClass.COMMIT_DURATION
					: LockClass.SHORT_DURATION;
		}
	}

	public abstract Tx getTX();

	public abstract TXCollection<E> getCollection();

	public abstract MetaLockService<?> getNls();

	protected abstract E getNodeInternal(XTCdeweyID deweyID)
			throws DocumentException;

	protected abstract QNm getNameInternal() throws DocumentException;

	protected abstract void setNameInternal(QNm name)
			throws OperationNotSupportedException, DocumentException;

	protected abstract Atomic getValueInternal() throws DocumentException;

	protected abstract void setValueInternal(Atomic value)
			throws OperationNotSupportedException, DocumentException;

	protected abstract void deleteInternal() throws DocumentException;

	protected abstract E getParentInternal() throws DocumentException;

	protected abstract E getFirstChildInternal() throws DocumentException;

	protected abstract E getLastChildInternal() throws DocumentException;

	protected abstract E getNextSiblingInternal() throws DocumentException;

	protected abstract E getPreviousSiblingInternal() throws DocumentException;

	protected abstract Stream<? extends E> getSubtreeInternal()
			throws DocumentException;

	protected abstract E getAttributeInternal(QNm name)
			throws DocumentException;

	protected abstract Stream<? extends E> getAttributesInternal()
			throws DocumentException;

	protected abstract E setAttributeInternal(QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException;

	protected abstract boolean deleteAttributeInternal(QNm name)
			throws DocumentException;

	protected abstract E insertRecord(XTCdeweyID deweyID, Kind kind, QNm name,
			Atomic value) throws OperationNotSupportedException,
			DocumentException;

	protected abstract E insertSubtree(XTCdeweyID deweyID, SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException;

	protected Stream<E> getChildrenInternal() throws DocumentException {
		return (getKind() != Kind.ATTRIBUTE) ? new ChildrenStream<E>(
				getFirstChild()) : new EmptyStream<E>();
	}

	@Override
	public boolean isSelfOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isSelfOf(oDeweyID));
	}

	@Override
	public final boolean isAncestorOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isAncestorOf(oDeweyID));
	}

	@Override
	public final boolean isAncestorOrSelfOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isAncestorOrSelfOf(oDeweyID));
	}

	@Override
	public final boolean isAttributeOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isAttributeOf(oDeweyID));
	}

	@Override
	public final boolean isChildOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isChildOf(oDeweyID))
				&& (getKind() != Kind.ATTRIBUTE);
	}

	@Override
	public final boolean isDescendantOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isDescendantOf(oDeweyID))
				&& (getKind() != Kind.ATTRIBUTE);
	}

	@Override
	public final boolean isDescendantOrSelfOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isDescendantOrSelfOf(oDeweyID))
				&& ((getKind() != Kind.ATTRIBUTE) || (isSelfOf(node)));
	}

	@Override
	public final boolean isDocumentOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isDocumentOf(oDeweyID));
	}

	@Override
	public final boolean isFollowingOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isFollowingOf(oDeweyID))
				&& (getKind() != Kind.ATTRIBUTE);
	}

	@Override
	public final boolean isFollowingSiblingOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isFollowingSiblingOf(oDeweyID))
				&& (getKind() != Kind.ATTRIBUTE);
	}

	@Override
	public final boolean isParentOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isParentOf(oDeweyID));
	}

	@Override
	public final boolean isPrecedingOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isPrecedingOf(oDeweyID))
				&& (getKind() != Kind.ATTRIBUTE);
	}

	@Override
	public final boolean isPrecedingSiblingOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (oDeweyID != null) && (deweyID.isPrecedingSiblingOf(oDeweyID))
				&& (getKind() != Kind.ATTRIBUTE);
	}

	@Override
	public final boolean isRoot() {
		return ((type == Kind.ELEMENT.ID) && (deweyID.isRoot()));
	}

	@Override
	public final boolean isSiblingOf(Node<?> node) {
		XTCdeweyID oDeweyID = (node instanceof TXNode<?>) ? ((TXNode<?>) node)
				.getDeweyID() : null;
		return (deweyID.isSiblingOf(oDeweyID)) && (getKind() != Kind.ATTRIBUTE);
	}

	@Override
	public final boolean hasAttributes() throws DocumentException {
		return (getAttributes() != null);
	}

	@Override
	public final boolean hasChildren() throws DocumentException {
		return (getFirstChild() != null);
	}

	@Override
	public final Stream<E> getChildren() throws DocumentException {
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			if (getKind() != Kind.ATTRIBUTE) {
				return getChildrenInternal();
			} else {
				return new EmptyStream<E>();
			}
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockLevelShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}

		Stream<E> children;
		if (getKind() != Kind.ATTRIBUTE) {
			children = getChildrenInternal();
		} else {
			children = new EmptyStream<E>();
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			// TODO unlock level when stream is closed
		}

		return children;
	}

	public final E setAttribute(Node<?> attribute)
			throws OperationNotSupportedException, DocumentException {
		return setAttribute(attribute.getName(), attribute.getValue());
	}

	@Override
	public final void delete() throws DocumentException {

		// TODO cleanup locking
		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		if (nls.supportsExclusiveTreeLock(tx)) {
			nls.lockTreeUpdate(tx, deweyID,
					tx.getIsolationLevel().lockClass(true), false);
			nls.lockTreeExclusive(tx, deweyID, tx.getIsolationLevel()
					.lockClass(true), false);
		} else {
			throw new DocumentException("TODO");
			// lockFragmentExclusive(transaction, node);
		}

		deleteInternal();
	}

	@Override
	public final E getAttribute(QNm name) throws DocumentException {
		if (type != Kind.ELEMENT.ID) {
			return null;
		}
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getAttributeInternal(name);
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.lockNodeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockEdgeShared(tx, deweyID, name.stringValue());
		}

		E attribute = getAttributeInternal(name);

		if (attribute != null) {
			if (tx.getIsolationLevel().longReadLocks()) {
				nls.lockNodeShared(tx, attribute.getDeweyID(), tx
						.getIsolationLevel().lockClass(false), false);
			}
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockEdge(tx, deweyID, name.stringValue());
			nls.unlockNode(tx, deweyID);
		}
		return attribute;
	}

	@Override
	public final Stream<? extends E> getAttributes()
			throws OperationNotSupportedException, DocumentException {
		if (type != Kind.ELEMENT.ID) {
			return new EmptyStream<E>();
		}
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getAttributesInternal();
		}

		MetaLockService<?> nls = getNls();
		XTCdeweyID attributeRootDeweyID = deweyID.getAttributeRootID();

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.lockNodeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}

		if (tx.getIsolationLevel().longReadLocks()) {
			if (nls.supportsSharedLevelLock(tx)) {
				nls.lockLevelShared(tx, attributeRootDeweyID, tx
						.getIsolationLevel().lockClass(false), false);
			} else {
				nls.lockTreeShared(tx, attributeRootDeweyID, tx
						.getIsolationLevel().lockClass(false), false);
			}
		}

		Stream<? extends E> attributes = getAttributesInternal();

		if (tx.getIsolationLevel().shortReadLocks()) {
			// TODO unlock attribute subtree when stream is closed
			nls.unlockNode(tx, deweyID);
		}

		return attributes;
	}

	@Override
	public final E getFirstChild() throws DocumentException {
		if ((type != Kind.ELEMENT.ID) && (type != Kind.DOCUMENT.ID)) {
			return null;
		}
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getFirstChildInternal();
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.lockNodeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockEdgeShared(tx, deweyID, FIRST_CHILD);
		}

		E firstChild = getFirstChildInternal();

		if (firstChild != null) {
			if (tx.getIsolationLevel().longReadLocks()) {
				nls.lockNodeShared(tx, firstChild.getDeweyID(), tx
						.getIsolationLevel().lockClass(false), false);
			}
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockEdge(tx, deweyID, FIRST_CHILD);
			nls.unlockNode(tx, deweyID);
		}

		return firstChild;
	}

	@Override
	public final E getLastChild() throws DocumentException {
		if ((type != Kind.ELEMENT.ID) && (type != Kind.DOCUMENT.ID)) {
			return null;
		}
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getLastChildInternal();
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.lockNodeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockEdgeShared(tx, deweyID, LAST_CHILD);
		}

		E lastChild = getLastChildInternal();

		if (lastChild != null) {
			if (tx.getIsolationLevel().longReadLocks()) {
				nls.lockNodeShared(tx, lastChild.getDeweyID(), tx
						.getIsolationLevel().lockClass(false), false);
			}
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockEdge(tx, deweyID, LAST_CHILD);
			nls.unlockNode(tx, deweyID);
		}

		return lastChild;
	}

	@Override
	public QNm getName() throws DocumentException {
		if ((type != Kind.ELEMENT.ID) && (type != Kind.ATTRIBUTE.ID)) {
			return null;
		}
		return getNameInternal();
		// TODO extremely fine-grained and expensive!
		// should we keep this alive?
		/*
		 * Tx tx = getTX(); boolean shortReadLocks =
		 * tx.getIsolationLevel().shortReadLocks(); if (shortReadLocks) {
		 * getNls().lockNodeShared(tx, deweyID,
		 * tx.getIsolationLevel().lockClass(false), false); }
		 * 
		 * String name = getNameInternal();
		 * 
		 * if (shortReadLocks) { getNls().unlockNode(tx, deweyID); }
		 * 
		 * return name;
		 */
	}

	@Override
	public final E getNextSibling() throws DocumentException {
		if ((type == Kind.DOCUMENT.ID) || (type == Kind.ATTRIBUTE.ID)) {
			return null;
		}
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getNextSiblingInternal();
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.lockNodeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockEdgeShared(tx, deweyID, NEXT_SIBLING);
		}

		E nextSibling = getNextSiblingInternal();

		if (nextSibling != null) {
			if (tx.getIsolationLevel().longReadLocks()) {
				nls.lockNodeShared(tx, nextSibling.getDeweyID(), tx
						.getIsolationLevel().lockClass(false), false);
			}
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockEdge(tx, deweyID, NEXT_SIBLING);
			nls.unlockNode(tx, deweyID);
		}

		return nextSibling;
	}

	public final E getNode(XTCdeweyID deweyID) throws NodeNotFoundException,
			DocumentException {
		Tx tx = getTX();
		if ((tx.getLockDepth() <= 0) && (!deweyID.isDocument())) {
			return getNodeInternal(deweyID);
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockNodeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}

		E node = getNodeInternal(deweyID);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		return node;
	}

	@Override
	public final E getParent() throws DocumentException {
		if (deweyID.isDocument()) {
			return null;
		}
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getParentInternal();
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().longReadLocks()) {
			XTCdeweyID parentDeweyID = deweyID.getParent();
			nls.lockNodeShared(tx, parentDeweyID, tx.getIsolationLevel()
					.lockClass(false), false);
		}

		E parentNode = getParentInternal();

		return parentNode;
	}

	@Override
	public final E getPreviousSibling() throws DocumentException {
		if ((type == Kind.DOCUMENT.ID) || (type == Kind.ATTRIBUTE.ID)) {
			return null;
		}
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getPreviousSiblingInternal();
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.lockNodeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockEdgeShared(tx, deweyID, PREV_SIBLING);
		}

		E prevSibling = getPreviousSiblingInternal();

		if (prevSibling != null) {
			if (tx.getIsolationLevel().longReadLocks()) {
				nls.lockNodeShared(tx, prevSibling.getDeweyID(), tx
						.getIsolationLevel().lockClass(false), false);
			}
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockEdge(tx, deweyID, PREV_SIBLING);
			nls.unlockNode(tx, deweyID);
		}

		return prevSibling;
	}

	@Override
	public final Stream<? extends E> getSubtree() throws DocumentException {
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			if (getKind() != Kind.ATTRIBUTE) {
				return getSubtreeInternal();
			} else {
				return new AtomStream(this);
			}
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().useReadLocks()) {
			nls.lockTreeShared(tx, deweyID,
					tx.getIsolationLevel().lockClass(false), false);
		}

		Stream<? extends E> subtree;
		if (getKind() != Kind.ATTRIBUTE) {
			subtree = getSubtreeInternal();
		} else {
			subtree = new AtomStream(this);
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			// TODO unlock tree when stream is closed
		}

		return subtree;
	}

	@Override
	public final Atomic getValue() throws DocumentException {
		Tx tx = getTX();
		if (tx.getLockDepth() <= 0) {
			return getValueInternal();
		}

		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockTreeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockTreeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		Atomic value = getValueInternal();

		if (tx.getIsolationLevel().shortReadLocks()) {
			getNls().unlockNode(tx, deweyID);
		}

		return value;
	}

	@Override
	public final boolean deleteAttribute(QNm name)
			throws OperationNotSupportedException, DocumentException {
		if (type != Kind.ELEMENT.ID) {
			throw new OperationNotSupportedException(
					"Cannot remove attribute from non-element nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, name.stringValue());
		nls.lockEdgeExclusive(tx, deweyID, name.stringValue());

		return deleteAttributeInternal(name);
	}

	@Override
	public final E setAttribute(QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if (type != Kind.ELEMENT.ID) {
			throw new OperationNotSupportedException(
					"Cannot set attribute for non-element nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, name.stringValue());
		nls.lockEdgeExclusive(tx, deweyID, name.stringValue());

		E attribute = getAttributeInternal(name);

		if (attribute != null) // attribute already exists
		{
			XTCdeweyID attributeDeweyID = attribute.getDeweyID();
			nls.lockTreeExclusive(tx, attributeDeweyID, tx.getIsolationLevel()
					.lockClass(true), false);
		} else {
			// lock level part on attribute root to block getAttributes and
			// getSubtree
			nls.lockTreeExclusive(tx, deweyID.getAttributeRootID(), tx
					.getIsolationLevel().lockClass(true), false);
		}

		// new record is attribute node (if attribute not preexists) or value
		// node (if attribute exists and value is replaced)
		attribute = setAttributeInternal(name, value);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		return attribute;
	}

	@Override
	public final void setName(QNm name) throws OperationNotSupportedException,
			DocumentException {
		if ((type != Kind.ELEMENT.ID) && (type != Kind.ATTRIBUTE.ID)) {
			throw new OperationNotSupportedException(
					"Cannot set name for non-element and non-attribute nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		setNameInternal(name);

		if (!tx.getIsolationLevel().longWriteLocks()) {
			nls.unlockNode(tx, deweyID);
		}
	}

	@Override
	public final void setValue(Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if (type == Kind.DOCUMENT.ID) {
			throw new OperationNotSupportedException(
					"Cannot set value for document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		setValueInternal(value);

		if (!tx.getIsolationLevel().longWriteLocks()) {
			nls.unlockNode(tx, deweyID);
		}
	}

	@Override
	public final E append(Kind kind, QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if ((this.type != Kind.ELEMENT.ID) && (this.type != Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot append nodes to non-element and non-document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newChildDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, LAST_CHILD);
		nls.lockEdgeExclusive(tx, deweyID, LAST_CHILD);

		E lastChild = getLastChildInternal();

		if (lastChild == null) // no last child present
		{
			newChildDeweyID = deweyID.getNewChildID();
			nls.lockEdgeUpdate(tx, deweyID, FIRST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID, FIRST_CHILD);
		} else {
			nls.lockEdgeUpdate(tx, lastChild.getDeweyID(), NEXT_SIBLING);
			nls.lockEdgeExclusive(tx, lastChild.getDeweyID(), NEXT_SIBLING);
			newChildDeweyID = XTCdeweyID.newBetween(lastChild.getDeweyID(),
					null);
		}

		nls.lockTreeUpdate(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);
		nls.lockTreeExclusive(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);

		E result = insertRecord(newChildDeweyID, kind, name, value);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (lastChild == null)
				nls.unlockEdge(tx, deweyID, FIRST_CHILD);
			else
				nls.unlockEdge(tx, lastChild.getDeweyID(), NEXT_SIBLING);

			nls.unlockEdge(tx, deweyID, LAST_CHILD);
		}

		return result;
	}

	@Override
	public final E append(Node<?> child) throws OperationNotSupportedException,
			DocumentException {
		if ((this.type != Kind.ELEMENT.ID) && (this.type != Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot append nodes to non-element and non-document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newChildDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, LAST_CHILD);
		nls.lockEdgeExclusive(tx, deweyID, LAST_CHILD);

		E lastChild = getLastChildInternal();

		if (lastChild == null) // no last child present
		{
			newChildDeweyID = deweyID.getNewChildID();
			nls.lockEdgeUpdate(tx, deweyID, FIRST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID, FIRST_CHILD);
		} else {
			nls.lockEdgeUpdate(tx, lastChild.getDeweyID(), NEXT_SIBLING);
			nls.lockEdgeExclusive(tx, lastChild.getDeweyID(), NEXT_SIBLING);
			newChildDeweyID = XTCdeweyID.newBetween(lastChild.getDeweyID(),
					null);
		}

		nls.lockTreeUpdate(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);
		nls.lockTreeExclusive(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);

		E result = insertSubtree(newChildDeweyID,
				new NavigationalSubtreeParser(child));

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (lastChild == null)
				nls.unlockEdge(tx, deweyID, FIRST_CHILD);
			else
				nls.unlockEdge(tx, lastChild.getDeweyID(), NEXT_SIBLING);

			nls.unlockEdge(tx, deweyID, LAST_CHILD);
		}

		return result;
	}

	@Override
	public final E append(SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		if ((this.type != Kind.ELEMENT.ID) && (this.type != Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot append nodes to non-element and non-document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newChildDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, LAST_CHILD);
		nls.lockEdgeExclusive(tx, deweyID, LAST_CHILD);

		E lastChild = getLastChildInternal();

		if (lastChild == null) // no last child present
		{
			newChildDeweyID = deweyID.getNewChildID();
			nls.lockEdgeUpdate(tx, deweyID, FIRST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID, FIRST_CHILD);
		} else {
			nls.lockEdgeUpdate(tx, lastChild.getDeweyID(), NEXT_SIBLING);
			nls.lockEdgeExclusive(tx, lastChild.getDeweyID(), NEXT_SIBLING);
			newChildDeweyID = XTCdeweyID.newBetween(lastChild.getDeweyID(),
					null);
		}

		nls.lockTreeUpdate(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);
		nls.lockTreeExclusive(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);

		E result = insertSubtree(newChildDeweyID, parser);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (lastChild == null)
				nls.unlockEdge(tx, deweyID, FIRST_CHILD);
			else
				nls.unlockEdge(tx, lastChild.getDeweyID(), NEXT_SIBLING);

			nls.unlockEdge(tx, deweyID, LAST_CHILD);
		}

		return result;
	}

	@Override
	public final E prepend(Kind kind, QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if ((this.type != Kind.ELEMENT.ID) && (this.type != Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot prepend nodes to non-element and non-document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newChildDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		// lock last child edge of passed refNode
		nls.lockEdgeUpdate(tx, deweyID, FIRST_CHILD);
		nls.lockEdgeExclusive(tx, deweyID, FIRST_CHILD);

		E firstChild = getFirstChildInternal();

		if (firstChild == null) {
			nls.lockEdgeUpdate(tx, deweyID, LAST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID, LAST_CHILD);
			newChildDeweyID = deweyID.getNewChildID();
		} else {
			nls.lockEdgeUpdate(tx, firstChild.getDeweyID(), PREV_SIBLING);
			nls.lockEdgeExclusive(tx, firstChild.getDeweyID(), PREV_SIBLING);
			newChildDeweyID = XTCdeweyID.newBetween(null,
					firstChild.getDeweyID());
		}

		nls.lockTreeUpdate(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);
		nls.lockTreeExclusive(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);

		E result = insertRecord(newChildDeweyID, kind, name, value);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (firstChild == null)
				nls.unlockEdge(tx, deweyID, LAST_CHILD);
			else
				nls.unlockEdge(tx, firstChild.getDeweyID(), PREV_SIBLING);

			nls.unlockEdge(tx, deweyID, FIRST_CHILD);
		}

		return result;
	}

	@Override
	public final E prepend(Node<?> child)
			throws OperationNotSupportedException, DocumentException {
		if ((this.type != Kind.ELEMENT.ID) && (this.type != Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot prepend nodes to non-element and non-document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newChildDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		// lock last child edge of passed refNode
		nls.lockEdgeUpdate(tx, deweyID, FIRST_CHILD);
		nls.lockEdgeExclusive(tx, deweyID, FIRST_CHILD);

		E firstChild = getFirstChildInternal();

		if (firstChild == null) {
			nls.lockEdgeUpdate(tx, deweyID, LAST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID, LAST_CHILD);
			newChildDeweyID = deweyID.getNewChildID();
		} else {
			nls.lockEdgeUpdate(tx, firstChild.getDeweyID(), PREV_SIBLING);
			nls.lockEdgeExclusive(tx, firstChild.getDeweyID(), PREV_SIBLING);
			newChildDeweyID = XTCdeweyID.newBetween(null,
					firstChild.getDeweyID());
		}

		nls.lockTreeUpdate(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);
		nls.lockTreeExclusive(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);

		E result = insertSubtree(newChildDeweyID,
				new NavigationalSubtreeParser(child));

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (firstChild == null)
				nls.unlockEdge(tx, deweyID, LAST_CHILD);
			else
				nls.unlockEdge(tx, firstChild.getDeweyID(), PREV_SIBLING);

			nls.unlockEdge(tx, deweyID, FIRST_CHILD);
		}

		return result;
	}

	@Override
	public final E prepend(SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		if ((this.type != Kind.ELEMENT.ID) && (this.type != Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot prepend nodes to non-element and non-document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newChildDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
			else
				nls.lockNodeShared(tx, deweyID, tx.getIsolationLevel()
						.lockClass(false), false);
		}

		// lock last child edge of passed refNode
		nls.lockEdgeUpdate(tx, deweyID, FIRST_CHILD);
		nls.lockEdgeExclusive(tx, deweyID, FIRST_CHILD);

		E firstChild = getFirstChildInternal();

		if (firstChild == null) {
			nls.lockEdgeUpdate(tx, deweyID, LAST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID, LAST_CHILD);
			newChildDeweyID = deweyID.getNewChildID();
		} else {
			nls.lockEdgeUpdate(tx, firstChild.getDeweyID(), PREV_SIBLING);
			nls.lockEdgeExclusive(tx, firstChild.getDeweyID(), PREV_SIBLING);
			newChildDeweyID = XTCdeweyID.newBetween(null,
					firstChild.getDeweyID());
		}

		nls.lockTreeUpdate(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);
		nls.lockTreeExclusive(tx, newChildDeweyID, tx.getIsolationLevel()
				.lockClass(true), false);

		E result = insertSubtree(newChildDeweyID, parser);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (firstChild == null)
				nls.unlockEdge(tx, deweyID, LAST_CHILD);
			else
				nls.unlockEdge(tx, firstChild.getDeweyID(), PREV_SIBLING);

			nls.unlockEdge(tx, deweyID, FIRST_CHILD);
		}

		return result;
	}

	@Override
	public E insertAfter(Kind kind, QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if ((kind == Kind.ATTRIBUTE) || (kind == Kind.DOCUMENT)) {
			throw new OperationNotSupportedException(
					"Cannot insert nodes after attribute or document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newSiblingDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, lockClass(tx, false), false);
			else
				nls.lockNodeShared(tx, deweyID, lockClass(tx, false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, NEXT_SIBLING);
		nls.lockEdgeExclusive(tx, deweyID, NEXT_SIBLING);
		E nextSibling = getNextSiblingInternal();

		if (nextSibling == null) // prevSibling is last child
		{
			nls.lockEdgeUpdate(tx, deweyID.getParent(), LAST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID.getParent(), LAST_CHILD);
			newSiblingDeweyID = XTCdeweyID.newBetween(deweyID, null);
		} else {
			nls.lockEdgeUpdate(tx, nextSibling.getDeweyID(), PREV_SIBLING);
			nls.lockEdgeExclusive(tx, nextSibling.getDeweyID(), PREV_SIBLING);
			newSiblingDeweyID = XTCdeweyID.newBetween(deweyID,
					nextSibling.getDeweyID());
		}
		nls.lockTreeUpdate(tx, newSiblingDeweyID, lockClass(tx, true), false);
		nls.lockTreeExclusive(tx, newSiblingDeweyID, lockClass(tx, true), false);
		E newSibling = getParent().insertRecord(newSiblingDeweyID, kind, name, value);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (nextSibling == null)
				nls.unlockEdge(tx, deweyID.getParent(), LAST_CHILD);
			else
				nls.unlockEdge(tx, nextSibling.getDeweyID(), PREV_SIBLING);

			nls.unlockEdge(tx, deweyID, NEXT_SIBLING);
		}

		return newSibling;
	}

	@Override
	public E insertAfter(Node<?> child) throws OperationNotSupportedException,
			DocumentException {
		if ((type == Kind.ATTRIBUTE.ID) || (type == Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot insert nodes after attribute or document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newSiblingDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, lockClass(tx, false), false);
			else
				nls.lockNodeShared(tx, deweyID, lockClass(tx, false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, NEXT_SIBLING);
		nls.lockEdgeExclusive(tx, deweyID, NEXT_SIBLING);
		E nextSibling = getNextSiblingInternal();

		if (nextSibling == null) // prevSibling is last child
		{
			nls.lockEdgeUpdate(tx, deweyID.getParent(), LAST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID.getParent(), LAST_CHILD);
			newSiblingDeweyID = XTCdeweyID.newBetween(deweyID, null);
		} else {
			nls.lockEdgeUpdate(tx, nextSibling.getDeweyID(), PREV_SIBLING);
			nls.lockEdgeExclusive(tx, nextSibling.getDeweyID(), PREV_SIBLING);
			newSiblingDeweyID = XTCdeweyID.newBetween(deweyID,
					nextSibling.getDeweyID());
		}
		nls.lockTreeUpdate(tx, newSiblingDeweyID, lockClass(tx, true), false);
		nls.lockTreeExclusive(tx, newSiblingDeweyID, lockClass(tx, true), false);
		E newSibling = getParent().insertSubtree(newSiblingDeweyID,
				new NavigationalSubtreeParser(child));

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (nextSibling == null)
				nls.unlockEdge(tx, deweyID.getParent(), LAST_CHILD);
			else
				nls.unlockEdge(tx, nextSibling.getDeweyID(), PREV_SIBLING);

			nls.unlockEdge(tx, deweyID, NEXT_SIBLING);
		}

		return newSibling;
	}

	@Override
	public E insertAfter(SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		if ((type == Kind.ATTRIBUTE.ID) || (type == Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot insert nodes after attribute or document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newSiblingDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, lockClass(tx, false), false);
			else
				nls.lockNodeShared(tx, deweyID, lockClass(tx, false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, NEXT_SIBLING);
		nls.lockEdgeExclusive(tx, deweyID, NEXT_SIBLING);
		E nextSibling = getNextSiblingInternal();

		if (nextSibling == null) // prevSibling is last child
		{
			nls.lockEdgeUpdate(tx, deweyID.getParent(), LAST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID.getParent(), LAST_CHILD);
			newSiblingDeweyID = XTCdeweyID.newBetween(deweyID, null);
		} else {
			nls.lockEdgeUpdate(tx, nextSibling.getDeweyID(), PREV_SIBLING);
			nls.lockEdgeExclusive(tx, nextSibling.getDeweyID(), PREV_SIBLING);
			newSiblingDeweyID = XTCdeweyID.newBetween(deweyID,
					nextSibling.getDeweyID());
		}
		nls.lockTreeUpdate(tx, newSiblingDeweyID, lockClass(tx, true), false);
		nls.lockTreeExclusive(tx, newSiblingDeweyID, lockClass(tx, true), false);
		E newSibling = getParent().insertSubtree(newSiblingDeweyID, parser);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (nextSibling == null)
				nls.unlockEdge(tx, deweyID.getParent(), LAST_CHILD);
			else
				nls.unlockEdge(tx, nextSibling.getDeweyID(), PREV_SIBLING);

			nls.unlockEdge(tx, deweyID, NEXT_SIBLING);
		}

		return newSibling;
	}

	@Override
	public E insertBefore(Kind kind, QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if ((kind == Kind.ATTRIBUTE) || (kind == Kind.DOCUMENT)) {
			throw new OperationNotSupportedException(
					"Cannot insert nodes before attribute or document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newSiblingDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, lockClass(tx, false), false);
			else
				nls.lockNodeShared(tx, deweyID, lockClass(tx, false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, PREV_SIBLING);
		nls.lockEdgeExclusive(tx, deweyID, PREV_SIBLING);
		E prevSibling = getPreviousSiblingInternal();

		if (prevSibling == null) // nextSibling is first child
		{
			nls.lockEdgeUpdate(tx, deweyID.getParent(), FIRST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID.getParent(), FIRST_CHILD);
			newSiblingDeweyID = XTCdeweyID.newBetween(null, deweyID);
		} else {
			nls.lockEdgeUpdate(tx, prevSibling.getDeweyID(), NEXT_SIBLING);
			nls.lockEdgeExclusive(tx, prevSibling.getDeweyID(), NEXT_SIBLING);
			newSiblingDeweyID = XTCdeweyID.newBetween(prevSibling.getDeweyID(),
					deweyID);
		}

		nls.lockTreeUpdate(tx, newSiblingDeweyID, lockClass(tx, true), false);
		nls.lockTreeExclusive(tx, newSiblingDeweyID, lockClass(tx, true), false);
		E newSibling = getParent().insertRecord(newSiblingDeweyID, kind, name, value);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (prevSibling == null)
				nls.unlockEdge(tx, deweyID.getParent(), FIRST_CHILD);
			else
				nls.unlockEdge(tx, prevSibling.getDeweyID(), NEXT_SIBLING);

			nls.unlockEdge(tx, deweyID, PREV_SIBLING);
		}

		return newSibling;
	}

	@Override
	public E insertBefore(Node<?> child) throws OperationNotSupportedException,
			DocumentException {
		if ((type == Kind.ATTRIBUTE.ID) || (type == Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot insert nodes before attribute or document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newSiblingDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, lockClass(tx, false), false);
			else
				nls.lockNodeShared(tx, deweyID, lockClass(tx, false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, PREV_SIBLING);
		nls.lockEdgeExclusive(tx, deweyID, PREV_SIBLING);
		E prevSibling = getPreviousSiblingInternal();

		if (prevSibling == null) // nextSibling is first child
		{
			nls.lockEdgeUpdate(tx, deweyID.getParent(), FIRST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID.getParent(), FIRST_CHILD);
			newSiblingDeweyID = XTCdeweyID.newBetween(null, deweyID);
		} else {
			nls.lockEdgeUpdate(tx, prevSibling.getDeweyID(), NEXT_SIBLING);
			nls.lockEdgeExclusive(tx, prevSibling.getDeweyID(), NEXT_SIBLING);
			newSiblingDeweyID = XTCdeweyID.newBetween(prevSibling.getDeweyID(),
					deweyID);
		}

		nls.lockTreeUpdate(tx, newSiblingDeweyID, lockClass(tx, true), false);
		nls.lockTreeExclusive(tx, newSiblingDeweyID, lockClass(tx, true), false);
		E newSibling = getParent().insertSubtree(newSiblingDeweyID,
				new NavigationalSubtreeParser(child));

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (prevSibling == null)
				nls.unlockEdge(tx, deweyID.getParent(), FIRST_CHILD);
			else
				nls.unlockEdge(tx, prevSibling.getDeweyID(), NEXT_SIBLING);

			nls.unlockEdge(tx, deweyID, PREV_SIBLING);
		}

		return newSibling;
	}

	@Override
	public E insertBefore(SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		if ((type == Kind.ATTRIBUTE.ID) || (type == Kind.DOCUMENT.ID)) {
			throw new OperationNotSupportedException(
					"Cannot insert nodes before attribute or document nodes.");
		}

		Tx tx = getTX();
		MetaLockService<?> nls = getNls();
		XTCdeweyID newSiblingDeweyID = null;

		if (tx.getIsolationLevel().shortReadLocks()) {
			if (tx.getLockDepth() < deweyID.getLevel())
				nls.lockNodeUpdate(tx, deweyID, lockClass(tx, false), false);
			else
				nls.lockNodeShared(tx, deweyID, lockClass(tx, false), false);
		}

		nls.lockEdgeUpdate(tx, deweyID, PREV_SIBLING);
		nls.lockEdgeExclusive(tx, deweyID, PREV_SIBLING);
		E prevSibling = getPreviousSiblingInternal();

		if (prevSibling == null) // nextSibling is first child
		{
			nls.lockEdgeUpdate(tx, deweyID.getParent(), FIRST_CHILD);
			nls.lockEdgeExclusive(tx, deweyID.getParent(), FIRST_CHILD);
			newSiblingDeweyID = XTCdeweyID.newBetween(null, deweyID);
		} else {
			nls.lockEdgeUpdate(tx, prevSibling.getDeweyID(), NEXT_SIBLING);
			nls.lockEdgeExclusive(tx, prevSibling.getDeweyID(), NEXT_SIBLING);
			newSiblingDeweyID = XTCdeweyID.newBetween(prevSibling.getDeweyID(),
					deweyID);
		}

		nls.lockTreeUpdate(tx, newSiblingDeweyID, lockClass(tx, true), false);
		nls.lockTreeExclusive(tx, newSiblingDeweyID, lockClass(tx, true), false);
		E newSibling = getParent().insertSubtree(newSiblingDeweyID, parser);

		if (tx.getIsolationLevel().shortReadLocks()) {
			nls.unlockNode(tx, deweyID);
		}

		if (tx.getIsolationLevel().longWriteLocks()) {
			if (prevSibling == null)
				nls.unlockEdge(tx, deweyID.getParent(), FIRST_CHILD);
			else
				nls.unlockEdge(tx, prevSibling.getDeweyID(), NEXT_SIBLING);

			nls.unlockEdge(tx, deweyID, PREV_SIBLING);
		}

		return newSibling;
	}

	@Override
	public E replaceWith(Kind kind, QNm name, Atomic value)
			throws OperationNotSupportedException, DocumentException {
		if (this.type == Kind.DOCUMENT.ID) {
			throw new OperationNotSupportedException(
					"Cannot replace document node.");
		}

		if (this.type == Kind.ATTRIBUTE.ID) {
			if (kind != Kind.ATTRIBUTE) {
				throw new DocumentException(
						"Cannot replace attribute with node of type: %s.", kind);
			}
			return getParent().setAttribute(getName(), value);
		}

		if ((isRoot()) && (kind != Kind.ELEMENT)) {
			throw new DocumentException(
					"Cannot replace root node with node of type: %s", kind);
		}

		if ((kind == Kind.ELEMENT) && (kind != Kind.TEXT)
				&& (kind != Kind.COMMENT)
				&& (kind != Kind.PROCESSING_INSTRUCTION)) {
			throw new DocumentException(
					"Cannot replace node with node of type: %s.", kind);
		}

		XTCdeweyID rememberedDeweyID = deweyID;
		E parent = getParentInternal();
		delete();
		return parent.insertRecord(rememberedDeweyID, kind, name, value);
	}

	@Override
	public E replaceWith(Node<?> node) throws OperationNotSupportedException,
			DocumentException {
		if (this.type == Kind.DOCUMENT.ID) {
			throw new OperationNotSupportedException(
					"Cannot replace document node.");
		}

		if (this.type == Kind.ATTRIBUTE.ID) {
			if (type != Kind.ATTRIBUTE.ID) {
				throw new DocumentException(
						"Cannot replace attribute with node of type: %s.", type);
			}
			return getParent().setAttribute(node);
		}

		if ((isRoot()) && (type != Kind.ELEMENT.ID)) {
			throw new DocumentException(
					"Cannot replace root node with node of type: %s", type);
		}

		if ((type != Kind.ELEMENT.ID) && (type != Kind.TEXT.ID)
				&& (type != Kind.COMMENT.ID)
				&& (type != Kind.PROCESSING_INSTRUCTION.ID)) {
			throw new DocumentException(
					"Cannot replace node with node of type: %s.", type);
		}

		XTCdeweyID rememberedDeweyID = deweyID;
		E parent = getParentInternal();
		delete();
		return parent.insertSubtree(rememberedDeweyID, new StreamSubtreeParser(
				node.getSubtree()));
	}

	@Override
	public E replaceWith(SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		if (this.type == Kind.DOCUMENT.ID) {
			throw new OperationNotSupportedException(
					"Cannot replace document node.");
		}

		;
		Node<?> node = (new D2NodeFactory()).build(parser);
		Kind kind = node.getKind();

		if (this.type == Kind.ATTRIBUTE.ID) {
			if (kind != Kind.ATTRIBUTE) {
				throw new DocumentException(
						"Cannot replace attribute with node of type: %s.", kind);
			}
			return getParent().setAttribute(node);
		}

		if ((isRoot()) && (kind != Kind.ELEMENT)) {
			throw new DocumentException(
					"Cannot replace root node with node of type: %s", kind);
		}

		if ((kind != Kind.ELEMENT) && (kind != Kind.TEXT)
				&& (kind != Kind.COMMENT)
				&& (kind != Kind.PROCESSING_INSTRUCTION)) {
			throw new DocumentException(
					"Cannot replace node with node of type: %s.", kind);
		}

		XTCdeweyID rememberedDeweyID = deweyID;
		E parent = getParentInternal();
		delete();
		return parent.insertSubtree(rememberedDeweyID,
				new NavigationalSubtreeParser(node));
	}

	@Override
	public final int hashCode() {
		return deweyID.hashCode();
	}
}
