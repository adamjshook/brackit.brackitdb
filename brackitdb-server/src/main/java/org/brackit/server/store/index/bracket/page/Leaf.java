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

import java.util.List;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.store.index.bracket.SubtreeDeleteListener;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;

/**
 * @author Martin Hiller
 * 
 */
public interface Leaf extends BPContext {

	public void format(int unitID, PageID rootPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException;

	/**
	 * Navigates along the navigation axis specified in the NavigationMode
	 * argument. The hintParentDeweyID is optional to make the 'previousSibling'
	 * navigation more efficient.
	 * 
	 * @param navMode
	 *            the axis to navigate on
	 * @return the Navigation Status
	 * @throws IndexAccessException
	 */
	public NavigationStatus navigate(NavigationMode navMode);

	/**
	 * Navigates along the navigation axis specified in the NavigationMode
	 * argument. The hintParentDeweyID is optional to make the 'previousSibling'
	 * navigation more efficient.
	 * 
	 * @param referenceDeweyID
	 *            the source DeweyID for the navigation operation
	 * @param navMode
	 *            the axis to navigate on
	 * @return the Navigation Status
	 * @throws IndexAccessException
	 */
	public NavigationStatus navigateContextFree(XTCdeweyID referenceDeweyID,
			NavigationMode navMode);

	public void moveBeforeFirst();

	public boolean insertAfter(XTCdeweyID deweyID, byte[] record,
			int ancestorsToInsert, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException;

	public XTCdeweyID getKey();

	public PageID getNextPageID() throws IndexOperationException;

	public void setNextPageID(PageID nextPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException;

	public boolean isBeforeFirst();

	public boolean isFirst();

	public void setContext(XTCdeweyID deweyID, int offset);

	public void setContext(BracketContext context);

	public BracketContext getContext();

	public LeafBuffers getDeweyIDBuffers();

	public void setDeweyIDBuffers(LeafBuffers deweyIDBuffers);

	public int getOffset();

	public void useBuffersFrom(Leaf other);

	public boolean split(Leaf emptyRightPage, XTCdeweyID key,
			boolean forUpdate, boolean compact, boolean splitAfterCurrent,
			boolean logged, long undoNextLSN) throws IndexOperationException;

	public void copyContentAndContextTo(Leaf other, boolean logged,
			long undoNextLSN);

	public XTCdeweyID getLowKey();

	public byte[] getLowKeyBytes();

	public void setHighKey(XTCdeweyID highKey);

	public XTCdeweyID getHighKey();

	public byte[] getHighKeyBytes();

	/**
	 * Deletes current node/subtree. Returns true if deletion of subtree is
	 * completed, and false if the next page has to be inspected. If the
	 * externalPageIDs list is specified (not null), external values are NOT
	 * deleted within this method. Instead they are inserted into this list for
	 * later deletion.
	 * 
	 * @param deleteListener
	 * @param externalPageIDs
	 *            a list for collecting external values
	 * @param isStructureModification
	 * @param logged
	 * @param undoNextLSN
	 * @return true if deletion of subtree is completed, and false if the next
	 *         page has to be inspected
	 * @throws IndexOperationException
	 * @throws EmptyLeafException
	 *             if the deletion would cause this leaf to become empty (->
	 *             deletion not yet executed)
	 */
	public boolean delete(SubtreeDeleteListener deleteListener,
			List<PageID> externalPageIDs, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException,
			EmptyLeafException;

	/**
	 * Deletes all nodes from the beginning of this page until the specified
	 * subtree ends. Returns true if subtree end was found in this page, and
	 * false if this page needs to be unchained. If the externalPageIDs list is
	 * specified (not null), external values are NOT deleted within this method.
	 * Instead they are inserted into this list for later deletion.
	 * 
	 * @param subtreeRoot
	 * @param deleteListener
	 * @param externalPageIDs
	 *            a list for collecting external values
	 * @param isStructureModification
	 * @param logged
	 * @param undoNextLSN
	 * @return
	 * @throws IndexOperationException
	 */
	public boolean deleteRemainingSubtree(XTCdeweyID subtreeRoot,
			SubtreeDeleteListener deleteListener, List<PageID> externalPageIDs,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexOperationException;

}
