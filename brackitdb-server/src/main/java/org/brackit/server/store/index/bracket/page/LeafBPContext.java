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
package org.brackit.server.store.index.bracket.page;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.store.index.bracket.SubtreeDeleteListener;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.bracket.BracketKey;
import org.brackit.server.store.page.bracket.BracketNodeSequence;
import org.brackit.server.store.page.bracket.BracketPage;
import org.brackit.server.store.page.bracket.BracketPageException;
import org.brackit.server.store.page.bracket.BracketValue;
import org.brackit.server.store.page.bracket.DeletePreparation;
import org.brackit.server.store.page.bracket.DeletePrepareListener;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.ExternalValueLoader;
import org.brackit.server.store.page.bracket.navigation.NavigationResult;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.server.tx.Tx;

/**
 * @author Martin Hiller
 * 
 */
public class LeafBPContext extends AbstractBPContext implements Leaf {

	private final float OCCUPANCY_RATE_DEFAULT = 0.5f;
	private final float OCCUPANCY_RATE_COMPACT = 0.5f; // change if needed

	public static final int NEXT_PAGE_FIELD_NO = AbstractBPContext.RESERVED_SIZE;
	public static final int RESERVED_SIZE = NEXT_PAGE_FIELD_NO
			+ PageID.getSize();

	private class DeletePrepareListenerImpl implements DeletePrepareListener {

		private final List<PageID> externalPageIDs;
		private final SubtreeDeleteListener deleteListener;

		public DeletePrepareListenerImpl(List<PageID> externalPageIDs,
				SubtreeDeleteListener deleteListener) {
			this.externalPageIDs = externalPageIDs;
			this.deleteListener = deleteListener;
		}

		@Override
		public void externalNode(XTCdeweyID deweyID, PageID externalPageID,
				int level) throws BracketPageException {
			externalPageIDs.add(externalPageID);
			byte[] value = null;
			try {
				value = read(tx, externalPageID);
			} catch (BlobStoreAccessException e) {
				throw new BracketPageException(
						e,
						"Error reading externalized value at offset %s from blob %s",
						currentOffset, externalPageID);
			}

			try {
				deleteListener.deleteNode(deweyID, value, level);
			} catch (IndexOperationException e) {
				throw new BracketPageException(e,
						"Error notifying listeners about node deletion.");
			}
		}

		@Override
		public void node(XTCdeweyID deweyID, byte[] value, int level)
				throws BracketPageException {
			try {
				deleteListener.deleteNode(deweyID, value, level);
			} catch (IndexOperationException e) {
				throw new BracketPageException(e,
						"Error notifying listeners about node deletion.");
			}
		}

		@Override
		public void subtreeEnd() throws BracketPageException {
			try {
				deleteListener.subtreeEnd();
			} catch (IndexOperationException e) {
				throw new BracketPageException(e,
						"Error notifying listeners about node deletion.");
			}
		}
	}

	private class ExternalValueLoaderImpl implements ExternalValueLoader {

		@Override
		public byte[] loadExternalValue(PageID externalPageID)
				throws BracketPageException {
			try {
				return read(tx, externalPageID);
			} catch (BlobStoreAccessException e) {
				throw new BracketPageException(
						e,
						"Error reading externalized value at offset %s from blob %s",
						currentOffset, externalPageID);
			}
		}
	}

	private final ExternalValueLoader externalValueLoader = new ExternalValueLoaderImpl();

	private final BracketPage page;

	private int currentOffset;
	private LeafBuffers deweyIDBuffers;
	private DeweyIDBuffer currentDeweyID;
	private DeweyIDBuffer tempDeweyID;

	private byte[] bufferedValue;
	private BracketKey.Type bufferedKeyType;

	public LeafBPContext(BufferMgr bufferMgr, Tx tx, BracketPage page) {
		super(bufferMgr, tx, page);
		this.page = page;
	}

	private void initBuffers() {
		if (deweyIDBuffers == null) {
			deweyIDBuffers = new LeafBuffers();
			currentDeweyID = deweyIDBuffers.currentDeweyID;
			tempDeweyID = deweyIDBuffers.tempDeweyID;
		}
	}

	@Override
	public void setContext(XTCdeweyID deweyID, int offset) {

		if (deweyIDBuffers == null) {
			deweyIDBuffers = new LeafBuffers();
			currentDeweyID = deweyIDBuffers.currentDeweyID;
			tempDeweyID = deweyIDBuffers.tempDeweyID;
		}

		currentDeweyID.setTo(deweyID);

		bufferedValue = null;
		currentOffset = offset;
	}

	@Override
	public void setContext(BracketContext context) {
		setContext(context.key, context.keyOffset);
	}

	@Override
	public BracketContext getContext() {
		return new BracketContext(currentOffset,
				currentOffset == BracketPage.BEFORE_LOW_KEY_OFFSET ? null
						: currentDeweyID.getDeweyID());
	}

	@Override
	public void useBuffersFrom(Leaf otherLeaf) {
		LeafBPContext other = (LeafBPContext) otherLeaf;
		this.deweyIDBuffers = other.getDeweyIDBuffers();
		if (deweyIDBuffers != null) {
			currentDeweyID = deweyIDBuffers.currentDeweyID;
			tempDeweyID = deweyIDBuffers.tempDeweyID;
		}
	}

	@Override
	public PageID getNextPageID() {
		byte[] value = page.getHandle().page;
		return PageID.fromBytes(value, BasePage.BASE_PAGE_START_OFFSET
				+ NEXT_PAGE_FIELD_NO);
	}

	@Override
	public void setNextPageID(PageID nextPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		// LogOperation operation = null;
		//
		// if (logged)
		// {
		// operation =
		// BlinkIndexLogOperationHelper.createrPointerLogOperation(BlinkIndexLogOperation.PREV_PAGE,
		// getPageID(), getRootPageID(), getLowPageID(), prevPageID);
		// }

		byte[] value = page.getHandle().page;
		if (nextPageID != null) {
			nextPageID.toBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ NEXT_PAGE_FIELD_NO);
		} else {
			PageID.noPageToBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ NEXT_PAGE_FIELD_NO);
		}

		page.getHandle().setModified(true); // not covered by pageID to

		// if (logged)
		// {
		// log(tx, operation, undoNextLSN);
		// }
		// else
		// {
		// page.getHandle().setAssignedTo(tx);
		// }
	}

	@Override
	public void format(int unitID, PageID rootPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		this.format(true, unitID, rootPageID, 0, true, logged, undoNextLSN);
	}

	@Override
	public XTCdeweyID getKey() {
		if (currentOffset == BracketPage.BEFORE_LOW_KEY_OFFSET) {
			return null;
		} else {
			return currentDeweyID.getDeweyID();
		}
	}

	@Override
	public byte[] getValue() throws IndexOperationException {
		return getValue(currentOffset);
	}

	private byte[] getValue(int keyOffset) throws IndexOperationException {

		if (bufferedValue != null) {
			return bufferedValue;
		}

		BracketValue returnValue = page.getValue(keyOffset);
		byte[] value = returnValue.value;

		if (returnValue.externalized) {
			PageID blobPageID = PageID.fromBytes(value);

			try {
				value = read(tx, blobPageID);
			} catch (BlobStoreAccessException e) {
				throw new IndexOperationException(
						e,
						"Error reading externalized value at offset %s from blob %s",
						currentOffset, blobPageID);
			}
		}

		bufferedValue = value;
		return value;
	}

	@Override
	public boolean moveFirst() {
		initBuffers();

		NavigationResult navRes = page.navigateFirstCF(currentDeweyID,
				tempDeweyID);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			bufferedValue = null;
			bufferedKeyType = navRes.keyType;
			currentOffset = navRes.keyOffset;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveLast() {
		initBuffers();

		NavigationResult navRes = page.navigateLastCF(currentDeweyID,
				tempDeweyID);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			bufferedValue = null;
			bufferedKeyType = navRes.keyType;
			currentOffset = navRes.keyOffset;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveNext() {
		initBuffers();

		NavigationResult navRes = page.navigateNext(currentOffset,
				currentDeweyID, tempDeweyID, bufferedKeyType);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			if (navRes.keyOffset != currentOffset + 3) {
				bufferedValue = null;
			}
			bufferedKeyType = navRes.keyType;
			currentOffset = navRes.keyOffset;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean setValue(byte[] value, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException {
		BracketValue oldValue = page.getValue(currentOffset);

		boolean externalize = externalizeValue(value);

		if (externalize) {
			value = externalize(value);
		}

		if (!page.update(value, externalize, currentOffset)) {
			return false;
		}

		if (oldValue.externalized) {
			deleteExternalized(oldValue.value);
		}

		return true;
	}

	@Override
	public String dump(String pageTitle) {
		if (pageTitle == null) {
			return page.toString();
		} else {
			return pageTitle + ":\n\n" + page;
		}
	}

	@Override
	public boolean insertAfter(XTCdeweyID deweyID, byte[] record,
			int ancestorsToInsert, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException {
		initBuffers();

		boolean externalize = externalizeValue(record);

		if (externalize) {
			record = externalize(record);
		}

		int returnVal = page.insertAfter(deweyID, record, ancestorsToInsert,
				externalize, currentOffset, currentDeweyID, tempDeweyID);

		if (returnVal != BracketPage.INSERTION_NO_SPACE) {
			currentOffset = returnVal;
			bufferedKeyType = null;
			bufferedValue = null;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public NavigationStatus navigate(NavigationMode navMode) {
		// assert(currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET);

		bufferedValue = null;
		NavigationResult navRes = null;

		// call corresponding bracket page method
		switch (navMode) {
		case NEXT_SIBLING:
			navRes = page.navigateNextSibling(currentOffset, currentDeweyID,
					tempDeweyID);
			break;
		case FIRST_CHILD:
			navRes = page.navigateFirstChild(currentOffset, currentDeweyID,
					tempDeweyID);
			break;
		case LAST_CHILD:
			navRes = page.navigateLastChild(currentOffset, currentDeweyID,
					tempDeweyID);
			break;
		case PARENT:
			navRes = page.navigateParent(currentDeweyID, tempDeweyID);
			break;
		case PREVIOUS_SIBLING:
			navRes = page.navigatePreviousSibling(currentOffset,
					currentDeweyID, tempDeweyID);
			break;
		case NEXT_ATTRIBUTE:
			navRes = page.navigateNextAttribute(currentOffset, currentDeweyID,
					tempDeweyID);
			break;
		case TO_INSERT_POS:
		case TO_KEY:
			throw new IllegalArgumentException(String.format(
					"The NavigationMode %s is a context free operation!",
					navMode));
		default:
			throw new RuntimeException("Navigation Mode not supported!");
		}

		if (navRes.status == NavigationStatus.FOUND
				|| navRes.status == NavigationStatus.POSSIBLY_FOUND) {
			// adjust current offset
			bufferedKeyType = navRes.keyType;
			currentOffset = navRes.keyOffset;
		}

		return navRes.status;
	}

	@Override
	public NavigationStatus navigateContextFree(XTCdeweyID referenceDeweyID,
			NavigationMode navMode) {
		// // check whether the invocation of this navigation is allowed
		// if (currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET) {
		// throw new
		// RuntimeException("This is a context free operation! In order to remove the current context, move the pointer before the first key.");
		// }

		bufferedValue = null;
		initBuffers();

		NavigationResult navRes = null;

		// call corresponding bracket page method
		switch (navMode) {
		case TO_KEY:
			navRes = page.navigateToKey(referenceDeweyID, currentDeweyID,
					tempDeweyID);
			break;
		case TO_INSERT_POS:
			navRes = page.navigateToInsertPos(referenceDeweyID, currentDeweyID,
					tempDeweyID);
			break;
		case NEXT_SIBLING:
			navRes = page.navigateNextSiblingCF(referenceDeweyID,
					currentDeweyID, tempDeweyID);
			break;
		case FIRST_CHILD:
			navRes = page.navigateFirstChildCF(referenceDeweyID,
					currentDeweyID, tempDeweyID);
			break;
		case LAST_CHILD:
			navRes = page.navigateLastChildCF(referenceDeweyID, currentDeweyID,
					tempDeweyID);
			break;
		case PARENT:
			navRes = page.navigateParentCF(referenceDeweyID, currentDeweyID,
					tempDeweyID);
			break;
		case PREVIOUS_SIBLING:
			navRes = page.navigatePreviousSiblingCF(referenceDeweyID,
					currentDeweyID, tempDeweyID);
			break;
		case NEXT_ATTRIBUTE:
			navRes = page.navigateNextAttributeCF(referenceDeweyID,
					currentDeweyID, tempDeweyID);
			break;
		default:
			throw new RuntimeException("Navigation Mode not supported!");
		}

		if (navRes.status == NavigationStatus.FOUND
				|| navRes.status == NavigationStatus.POSSIBLY_FOUND) {
			// adjust current offset
			bufferedKeyType = navRes.keyType;
			currentOffset = navRes.keyOffset;
		}

		return navRes.status;
	}

	@Override
	public boolean isBeforeFirst() {
		return (currentOffset == BracketPage.BEFORE_LOW_KEY_OFFSET);
	}

	@Override
	public boolean isLast() {
		return page.isLast(currentOffset);
	}

	@Override
	public void moveBeforeFirst() {
		currentOffset = BracketPage.BEFORE_LOW_KEY_OFFSET;
		bufferedValue = null;
		bufferedKeyType = null;
	}

	@Override
	public boolean split(Leaf rightLeaf, XTCdeweyID key,
			boolean forUpdate, boolean compact, boolean splitAfterCurrent,
			boolean logged, long undoNextLSN) throws IndexOperationException {

		try {
			LeafBPContext rightPage = (LeafBPContext) rightLeaf;

			this.initBuffers();
			rightPage.initBuffers();

			bufferedValue = null;
			bufferedKeyType = null;

			// prepare split
			DeletePreparation delPrep = splitAfterCurrent ? page
					.splitAfterCurrentPrepare(currentOffset, currentDeweyID,
							tempDeweyID) : page.splitPrepare(
					compact ? OCCUPANCY_RATE_COMPACT : OCCUPANCY_RATE_DEFAULT,
					tempDeweyID);

			boolean returnLeftPage = (currentOffset < delPrep.startDeleteOffset);
			// buffer current DeweyID
			XTCdeweyID originalDeweyID = null;
			if (returnLeftPage && !splitAfterCurrent
					&& currentOffset != BracketPage.BEFORE_LOW_KEY_OFFSET) {
				originalDeweyID = currentDeweyID.getDeweyID();
			}

			// get nodes that have to be transferred to the right page
			BracketNodeSequence nodes = page.getBracketNodeSequence(delPrep);

			// delete nodes from left page
			int oldOffset = currentOffset;
			currentOffset = page.delete(currentDeweyID, tempDeweyID, delPrep,
					externalValueLoader);

			// set highKey/separator
			XTCdeweyID highKey = this.getHighKey();
			this.setHighKey(delPrep.startDeleteDeweyID);
			rightPage.setHighKey(highKey);

			// insert nodes into right page
			rightPage.page.insertAfter(nodes, rightPage.currentOffset,
					rightPage.currentDeweyID, rightPage.tempDeweyID);

			// set correct context(s)
			if (returnLeftPage) {
				// left page
				if (oldOffset < delPrep.previousOffset) {
					currentOffset = oldOffset;

					if (originalDeweyID != null) {
						currentDeweyID.setTo(originalDeweyID);
					}
				}

				// right page
				rightPage.moveBeforeFirst();
			} else {

				// right page
				if (forUpdate) {
					if (rightPage.navigateContextFree(key,
							NavigationMode.TO_KEY) != NavigationStatus.FOUND) {
						throw new RuntimeException("Error during leaf split!");
					}
				} else {
					if (rightPage.navigateContextFree(key,
							NavigationMode.TO_INSERT_POS) != NavigationStatus.FOUND) {
						throw new RuntimeException("Error during leaf split!");
					}
				}
			}

			return returnLeftPage;

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

	@Override
	public void copyContentAndContextTo(Leaf otherLeaf, boolean logged, long undoNextLSN)
	{
		LeafBPContext other = (LeafBPContext) otherLeaf;
		
		other.initBuffers();
		
		// copy content
		BracketNodeSequence content = page.getBracketNodeSequence(getLowKey(), BracketPage.LOW_KEY_OFFSET, BracketPage.KEY_AREA_END_OFFSET);
		other.page.insertAfter(content, BracketPage.BEFORE_LOW_KEY_OFFSET, other.currentDeweyID, other.tempDeweyID);
		
		// copy context
		otherLeaf.setContext(this.getContext());
	}

	@Override
	public int getOffset() {
		return currentOffset;
	}

	@Override
	public boolean isFirst() {
		return (currentOffset == BracketPage.LOW_KEY_OFFSET);
	}

	@Override
	public LeafBuffers getDeweyIDBuffers() {
		return deweyIDBuffers;
	}

	@Override
	public void setDeweyIDBuffers(LeafBuffers deweyIDBuffers) {
		this.deweyIDBuffers = deweyIDBuffers;
		currentDeweyID = deweyIDBuffers.currentDeweyID;
		tempDeweyID = deweyIDBuffers.tempDeweyID;
	}

	@Override
	public XTCdeweyID getLowKey() {
		return page.getLowKey();
	}

	@Override
	public byte[] getLowKeyBytes() {
		return page.getLowKeyBytes();
	}

	@Override
	public XTCdeweyID getHighKey() {
		return page.getContextDataAsDeweyID();
	}

	@Override
	public byte[] getHighKeyBytes() {
		return page.getContextData();
	}

	@Override
	public void setHighKey(XTCdeweyID highKey) {

		// store highkey as context data
		if (!page.setContextData(highKey.toBytes())) {
			throw new RuntimeException(
					String.format(
							"Not enough space availabe to store the HighKey %s in leaf page %s!",
							highKey, page.getPageID()));
		}
	}

	@Override
	public BPContext createClone() throws IndexOperationException {
		LeafBPContext clone = new LeafBPContext(bufferMgr, tx, page);
		return clone;
	}

	@Override
	public int getEntryCount() {
		return page.getRecordCount();
	}

	@Override
	public int getHeight() {
		return 0;
	}

	@Override
	public boolean hasNext() {
		return !page.isLast(currentOffset);
	}

	@Override
	public boolean hasPrevious() {
		return (currentOffset > BracketPage.LOW_KEY_OFFSET);
	}

	@Override
	public void init() {
		moveBeforeFirst();
	}

	@Override
	public boolean isLastInLevel() {
		return (getNextPageID() == null);
	}

	@Override
	public void setCompressed(boolean compressed) {
	}

	@Override
	protected void setHeight(int height) {
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	@Override
	public boolean delete(SubtreeDeleteListener deleteListener,
			List<PageID> externalPageIDs, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException,
			EmptyLeafException {
		// assert(currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET);

		try {

			// prepare deletion
			boolean deleteExternalValues = (externalPageIDs == null);
			if (deleteExternalValues) {
				externalPageIDs = new ArrayList<PageID>();
			}

			DeletePreparation delPrep = null;

			if (currentOffset == BracketPage.LOW_KEY_OFFSET) {
				// maybe the page has to be deleted completely / unchained
				// -> DeleteListener's callback invocations should not be
				// propagated to the outside yet

				DelayedSubtreeDeleteListener delayedListener = new DelayedSubtreeDeleteListener(
						deleteListener);
				List<PageID> tempExternalPageIDs = deleteExternalValues ? externalPageIDs
						: new ArrayList<PageID>();
				DeletePrepareListener delPrepListener = new DeletePrepareListenerImpl(
						tempExternalPageIDs, delayedListener);
				delPrep = page.deletePrepare(currentOffset, currentDeweyID,
						tempDeweyID, delPrepListener);

				if (delPrep.endDeleteOffset == BracketPage.KEY_AREA_END_OFFSET) {
					// leaf needs to be unchained
					throw EmptyLeafException.EMPTY_LEAF_EXCEPTION;
				}

				// propagate DeleteListener's callback methods
				delayedListener.flush();
				if (!deleteExternalValues) {
					externalPageIDs.addAll(tempExternalPageIDs);
				}

			} else {
				DeletePrepareListener delPrepListener = new DeletePrepareListenerImpl(
						externalPageIDs, deleteListener);
				delPrep = page.deletePrepare(currentOffset, currentDeweyID,
						tempDeweyID, delPrepListener);
			}

			// delete external values
			if (deleteExternalValues) {
				deleteExternalized(externalPageIDs);
			}

			// delete
			currentOffset = page.delete(currentDeweyID, tempDeweyID, delPrep,
					externalValueLoader);

			return (delPrep.endDeleteOffset != BracketPage.KEY_AREA_END_OFFSET);

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

	@Override
	public boolean deleteRemainingSubtree(XTCdeweyID subtreeRoot,
			SubtreeDeleteListener deleteListener, List<PageID> externalPageIDs,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		try {

			initBuffers();

			// prepare deletion
			boolean deleteExternalValues = (externalPageIDs == null);
			if (deleteExternalValues) {
				externalPageIDs = new ArrayList<PageID>();
			}

			DeletePrepareListener delPrepListener = new DeletePrepareListenerImpl(
					externalPageIDs, deleteListener);
			DeletePreparation delPrep = page.deleteRemainingSubtreePrepare(
					subtreeRoot, tempDeweyID, delPrepListener);

			if (delPrep == null) {
				// nothing to delete
				return true;
			}

			// delete external values
			if (deleteExternalValues) {
				deleteExternalized(externalPageIDs);
			}

			if (delPrep.endDeleteOffset == BracketPage.KEY_AREA_END_OFFSET) {
				// entire page needs to be deleted -> do not physically delete
				// the page, but let it unchain by the bracket tree
				return false;
			} else {
				// delete
				currentOffset = page.delete(currentDeweyID, tempDeweyID,
						delPrep, null);
				return true;
			}

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

}
