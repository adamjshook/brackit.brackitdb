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
package org.brackit.server.store.index.bracket.bulkinsert;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.bracket.page.Leaf;

/**
 * Contains all needed context information for the bulk insert.
 * 
 * @author Martin Hiller
 * 
 */
public class BulkInsertContext {

	private SeparatorEntryList separators;

	private Leaf leftmostPage;
	private PageID nextPageID;

	private Leaf currentLeftPage;
	private Leaf currentRightPage;

	private boolean firstSplitOccured;
	private long beforeFirstSplitLSN;

	public BulkInsertContext(Leaf currentPage, int maxSeparators) {
		this.separators = new SeparatorEntryList(maxSeparators);
		this.initialize(currentPage);
		this.firstSplitOccured = false;
		this.beforeFirstSplitLSN = -1;
	}

	public boolean splitOccurred(SeparatorEntry separator, Leaf leftPage,
			Leaf rightPage) {
		firstSplitOccured = true;
		currentLeftPage = leftPage;
		currentRightPage = rightPage;
		return separators.addSeparator(separator);
	}

	public boolean overflowOccurred(byte[] newSeparator) {
		firstSplitOccured = true;
		currentLeftPage = currentRightPage;
		currentRightPage = null;
		return separators.setLastSeparatorKey(newSeparator);
	}

	public void setNextPageID(PageID nextPageID) {
		this.nextPageID = nextPageID;
	}

	public boolean firstSplitOccured() {
		return firstSplitOccured;
	}

	public Leaf getCurrentRightPage() {
		return currentRightPage;
	}

	public Leaf getCurrentLeftPage() {
		return currentLeftPage;
	}

	public boolean isLeftmostPage(Leaf leaf) {
		return leaf == leftmostPage;
	}

	public PageID getNextPageID() {
		return nextPageID;
	}

	public long getBeforeFirstSplitLSN() {
		return beforeFirstSplitLSN;
	}

	public void setBeforeFirstSplitLSN(long beforeFirstSplitLSN) {
		this.beforeFirstSplitLSN = beforeFirstSplitLSN;
	}

	public byte[] getFirstSeparatorKey() {
		return separators.getFirstSeparatorKey();
	}

	public PageID getLeftmostPageID() {
		return leftmostPage.getPageID();
	}

	public SeparatorEntry[] getSeparators() {
		return separators.getAsArray();
	}

	public int getNumberOfSeparators() {
		return separators.getNumberOfSeparators();
	}

	public void separatorsWritten() {

		separators.clear();
		nextPageID = null;

		if (currentRightPage != null) {
			currentRightPage.cleanup();
			currentRightPage = null;
		}

		if (!isLeftmostPage(currentLeftPage)) {
			leftmostPage.cleanup();
			leftmostPage = currentLeftPage;
		}

		firstSplitOccured = false;
		beforeFirstSplitLSN = -1;

	}

	public void cleanup() {

		try {
			leftmostPage.cleanup();
		} catch (Exception e) {
		}
		leftmostPage = null;

		if (!isLeftmostPage(currentLeftPage)) {
			try {
				currentLeftPage.cleanup();
			} catch (Exception e) {
			}
		}
		currentLeftPage = null;

		if (currentRightPage != null) {
			try {
				currentRightPage.cleanup();
			} catch (Exception e) {
			}
			currentRightPage = null;
		}
	}

	public void initialize(Leaf leftmostPage) {
		this.leftmostPage = leftmostPage;
		this.currentLeftPage = leftmostPage;
		this.currentRightPage = null;
	}
}
