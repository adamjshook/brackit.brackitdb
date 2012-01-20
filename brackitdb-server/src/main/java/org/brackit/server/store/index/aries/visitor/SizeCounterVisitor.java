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
package org.brackit.server.store.index.aries.visitor;

import java.util.HashMap;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.IndexVisitor;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.page.PageContext;

/**
 * @author Karsten Schmidt
 * 
 */
public class SizeCounterVisitor implements IndexVisitor {
	int pageSize;
	long indexSize;
	long spare;
	int indexPageCount;
	int indexLeaveCount;
	private boolean debug = false;
	private HashMap<PageID, Integer> pageLevels;
	int currentLevel;
	long indexTuples;
	long indexPointers;
	long externalized;
	int minCPL = Integer.MAX_VALUE;
	int maxCPL;
	long sumCPLInPage;
	long sumKeyLength;
	private int numCPLNonZero;

	public SizeCounterVisitor() {
		pageLevels = new HashMap<PageID, Integer>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.brackit.server.io.file.index.IndexVisitor#end()
	 */
	@Override
	public void end() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.brackit.server.io.file.index.IndexVisitor#start()
	 */
	@Override
	public void start() {
		indexSize = 0;
		indexPageCount = 0;
		indexLeaveCount = 0;
		pageLevels.clear();
		currentLevel = 0;
		indexTuples = 0;
		indexPointers = 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.brackit.server.io.file.index.IndexVisitor#visitLeafPage(org.brackit
	 * .server.io.file.index.aries.page.PageContext, boolean)
	 */
	@Override
	public void visitLeafPage(PageContext page, boolean overflow)
			throws IndexOperationException {
		// TODO: following linked records
		pageSize = page.getSize();
		PageID pageID = page.getPageID();
		if (debug)
			System.out.println("leaf page : " + pageID.toString());
		this.indexPageCount++;
		this.indexLeaveCount++;
		this.indexSize += page.getUsedSpace();
		this.indexTuples += page.getEntryCount();
		this.spare += page.getFreeSpace();

		int CPLInPage = 0;

		int pos = 1;
		if (page.getKey() != null) {
			CPLInPage = page.getKey().length;
			byte[] prevKey = page.getKey();
			sumKeyLength += prevKey.length;
			externalized += (page.isExternalized(pos)) ? 1 : 0;

			while (page.hasNext()) {
				pos++;
				byte[] curKey = page.getKey();
				externalized += (page.isExternalized(pos)) ? 1 : 0;
				sumKeyLength += curKey.length;

				int CPL = 0;
				for (CPL = 0; CPL < Math.min(prevKey.length, curKey.length); CPL++) {
					if (prevKey[CPL] != curKey[CPL])
						break;
				}

				CPLInPage = Math.min(CPLInPage, CPL);
				prevKey = page.getPreviousKey();
			}
		}

		sumCPLInPage += CPLInPage;
		maxCPL = Math.max(CPLInPage, maxCPL);
		minCPL = Math.min(CPLInPage, minCPL);

		if (CPLInPage > 0)
			numCPLNonZero++;
		// if (debug)
		// System.out.println("free: "+((AbstractPageContext)page).getFreeSpace());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.brackit.server.io.file.index.IndexVisitor#visitTreePage(org.brackit
	 * .server.io.file.index.aries.page.PageContext)
	 */
	@Override
	public void visitTreePage(PageContext page) throws IndexOperationException {
		Field keyType = page.getKeyType();
		PageID pageID = page.getPageID();
		if (debug)
			System.out.println("tree page : " + pageID.toString());
		this.indexPageCount++;
		this.indexSize += page.getUsedSpace();
		if (debug)
			System.out.println("free: " + page.getFreeSpace());
		this.indexPointers += page.getEntryCount();
		if (this.pageLevels.containsKey(pageID))
			currentLevel = this.pageLevels.get(pageID);
		else {
			currentLevel = 1;
			this.pageLevels.put(pageID, currentLevel);
			if (debug)
				System.out.println("put . " + pageID.getBlockNo());
		}
		byte[] value = null;

		if (page.moveFirst()) {

			PageID beforePageID = page.getBeforePageID();
			this.pageLevels.put(beforePageID, currentLevel + 1);
			if (debug)
				System.out.println("put b " + beforePageID.getBlockNo());
			do {
				String keyString = (page.getKey() != null) ? keyType
						.toString(page.getKey()) : "?";
				value = (page.getKey() != null) ? page.getValue() : null;
				PageID afterPageID = (value != null) ? page.getAfterPageID()
						: null;

				if (afterPageID != null) {
					this.pageLevels.put(afterPageID, currentLevel + 1);
					if (debug)
						System.out.println("put a " + afterPageID.getBlockNo());
				}
			} while (page.hasNext());
		}
	}

	public long getIndexSize() {
		return this.indexSize;
	}

	public int getIndexPageCount() {
		return this.indexPageCount;
	}

	public int getIndexLeaveCount() {
		return this.indexLeaveCount;
	}

	public long getSpareSize() {
		return this.spare;
	}

	public int getIndexHeight() {
		int height = 0;
		for (PageID pageID : this.pageLevels.keySet()) {
			if (debug) {
				System.out.println("pageNo " + pageID.getBlockNo() + " level="
						+ this.pageLevels.get(pageID));
			}
			if (height < this.pageLevels.get(pageID))
				height = this.pageLevels.get(pageID);
		}

		// this only happens if we have an index without any internal tree page!
		if ((height == 0) && (this.indexPageCount > 0))
			height++;
		return height;
	}

	public long getIndexTuples() {
		return this.indexTuples;
	}

	public long getIndexPointers() {
		return this.indexPointers;
	}

	public int getMinCPL() {
		return minCPL;
	}

	public int getMaxCPL() {
		return maxCPL;
	}

	public double getAvgCPL() {
		return (double) sumCPLInPage / (double) indexLeaveCount;
	}

	public int getCountNonZeroCPL() {
		return numCPLNonZero;
	}

	@Override
	public String toString() {
		return String
				.format(
						"%s bytes in %s pages of size %s [leaves=%s, height=%s, tuples=%s, externalized=%s, separators=%s, avgCPL=%2.4f, minCPL=%s, maxCPL=%s, #CPL>0=%s]\n",
						indexSize, indexPageCount, pageSize, indexLeaveCount,
						getIndexHeight(), indexTuples, externalized,
						indexPointers, (double) sumCPLInPage
								/ (double) indexLeaveCount, minCPL, maxCPL,
						numCPLNonZero);
	}
}
