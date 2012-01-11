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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.node.index.external;

import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.xquery.atomic.Int64;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.node.parser.FragmentHelper;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;

/**
 * 
 * @author Andreas M. Weiner
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 * 
 */
public class IndexStatistics implements Materializable {
	public static final QNm STATISTICS_TAG = new QNm("statistics");

	private static final QNm SIZE_ATTR = new QNm("size");

	private static final QNm CARD_ATTR = new QNm("card");

	private static final QNm HEIGHT_ATTR = new QNm("height");

	private static final QNm LEAVES_ATTR = new QNm("leaves");

	private static final QNm PAGES_ATTR = new QNm("pages");

	private static final QNm POINTERS_ATTR = new QNm("pointers");

	private static final QNm MINKEY_ATTR = new QNm("minKey");

	private static final QNm MAXKEY_ATTR = new QNm("maxKey");

	private long idxSize;

	private long pageCount;

	private long indexHeight;

	private long indexLeaveCount;

	private long indexTuples;

	private long indexPointers;

	private String minKey;

	private String maxKey;

	public IndexStatistics() {
	}

	@Override
	public synchronized void init(Node<?> root) throws DocumentException {
		QNm name = root.getName();

		if (!name.equals(STATISTICS_TAG)) {
			throw new DocumentException("Expected tag '%s' but found '%s'",
					STATISTICS_TAG, name);
		}

		idxSize = Long.parseLong(root.getAttribute(SIZE_ATTR)
				.getValue().stringValue());
		indexTuples = Long.parseLong(root.getAttribute(CARD_ATTR)
				.getValue().stringValue());
		indexHeight = Long.parseLong(root.getAttribute(HEIGHT_ATTR)
				.getValue().stringValue());
		indexLeaveCount = Long.parseLong(root.getAttribute(LEAVES_ATTR)
				.getValue().stringValue());
		pageCount = Long.parseLong(root.getAttribute(PAGES_ATTR)
				.getValue().stringValue());
		indexPointers = Long.parseLong(root.getAttribute(POINTERS_ATTR)
				.getValue().stringValue());
		
		Node<?> attr = root.getAttribute(MINKEY_ATTR);
		minKey = (attr != null) ? attr.getValue().stringValue() : null;
		attr = root.getAttribute(MAXKEY_ATTR);
		maxKey = (attr != null) ? attr.getValue().stringValue() : null;
	}

	@Override
	public synchronized Node<?> materialize() throws DocumentException {
		FragmentHelper helper = new FragmentHelper();

		helper.openElement(STATISTICS_TAG);
		helper.attribute(SIZE_ATTR, new Int64(idxSize));
		helper.attribute(CARD_ATTR, new Int64(indexTuples));
		helper.attribute(HEIGHT_ATTR, new Int64(indexHeight));
		helper.attribute(LEAVES_ATTR, new Int64(indexLeaveCount));
		helper.attribute(PAGES_ATTR, new Int64(pageCount));
		helper.attribute(POINTERS_ATTR, new Int64(indexPointers));

		if (minKey != null) {
			helper.attribute(MINKEY_ATTR, new Str(minKey));
			helper.attribute(MAXKEY_ATTR, new Str(maxKey));
		}

		helper.closeElement();

		return helper.getRoot();
	}

	public long getIdxSize() {
		return idxSize;
	}

	public long getIndexHeight() {
		return indexHeight;
	}

	public long getIndexLeaveCount() {
		return indexLeaveCount;
	}

	public long getIndexTuples() {
		return indexTuples;
	}

	public void setIdxSize(long idxSize) {
		this.idxSize = idxSize;
	}

	public void setPageCount(long pageCount) {
		this.pageCount = pageCount;
	}

	public void setIndexHeight(int indexHeight) {
		this.indexHeight = indexHeight;
	}

	public void setIndexLeaveCount(long indexLeaveCount) {
		this.indexLeaveCount = indexLeaveCount;
	}

	public void setIndexTuples(long indexTuples) {
		this.indexTuples = indexTuples;
	}

	public String getMinKey() {
		return minKey;
	}

	public String getMaxKey() {
		return maxKey;
	}

	public void setMinKey(String minKey) {
		this.minKey = minKey;
	}

	public void setMaxKey(String maxKey) {
		this.maxKey = maxKey;
	}

	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();

		buf.append("Index size " + idxSize + "\n");
		buf.append("Index height " + indexHeight + "\n");
		buf.append("leave nodes " + indexLeaveCount + "\n");
		buf.append("Index tuples " + indexTuples + "\n");
		buf.append("Page count " + pageCount + "\n");
		buf.append("Index Pointers " + indexPointers + "\n");
		buf.append("Min Key " + minKey + "\n");
		buf.append("Max key " + maxKey);

		return buf.toString();
	}

	public void setIndexPointers(long indexPointers) {
		this.indexPointers = indexPointers;
	}

	public long getIndexPointers() {
		return indexPointers;
	}

	/**
	 * 
	 * @param statistics
	 * @param hierarchy
	 *            , if true, the height is added (e.g., element index)
	 */
	public void add(IndexStatistics statistics, boolean hierarchy) {
		this.idxSize += statistics.idxSize;
		if (!hierarchy)
			this.indexHeight = Math.max(this.indexHeight,
					statistics.indexHeight);
		else
			this.indexHeight += statistics.indexHeight;
		this.indexLeaveCount += statistics.indexLeaveCount;
		this.indexPointers += statistics.indexPointers;
		this.indexTuples += statistics.indexTuples;
		this.pageCount += statistics.pageCount;
	}

	public long getIndexPageCount() {
		return this.pageCount;
	}
}
