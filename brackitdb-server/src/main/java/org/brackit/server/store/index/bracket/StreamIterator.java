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
package org.brackit.server.store.index.bracket;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketLocator;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Martin Hiller
 * 
 */
public abstract class StreamIterator implements Stream<BracketNode> {

	protected static final OpenMode OPEN_MODE = OpenMode.READ;

	protected Leaf page;
	protected XTCdeweyID currentKey;
	protected final BracketLocator locator;
	protected final BracketTree tree;
	protected final Tx tx;
	protected final DeweyIDBuffer deweyIDBuffer;
	protected final XTCdeweyID startDeweyID;
	protected final HintPageInformation hintPageInfo;
	protected final BracketFilter filter;

	private BracketNode preFirstNode;
	private boolean preFirst;
	private boolean firstUsage;

	public StreamIterator(BracketLocator locator, BracketTree tree,
			XTCdeweyID startDeweyID, HintPageInformation hintPageInfo,
			BracketFilter filter) {
		this.locator = locator;
		this.tree = tree;
		this.startDeweyID = startDeweyID;
		this.hintPageInfo = hintPageInfo;
		this.filter = filter;
		this.tx = locator.collection.getTX();
		this.deweyIDBuffer = new DeweyIDBuffer();
		this.firstUsage = true;
		this.preFirst = true;
	}

	public StreamIterator(StreamIterator other, BracketFilter filter)
			throws DocumentException {

		this.locator = other.locator;
		this.tree = other.tree;

		try {
			this.page = other.page.fork();
		} catch (IndexOperationException e) {
			throw new DocumentException(e);
		}

		try {
			this.startDeweyID = page.getKey();
		} catch (IndexOperationException e) {
			page.cleanup();
			throw new DocumentException(e);
		}

		this.hintPageInfo = null;
		this.filter = filter;
		this.tx = other.tx;
		this.deweyIDBuffer = page.getDeweyIDBuffer();
		this.firstUsage = true;
		this.preFirst = true;
	}

	/**
	 * @see org.brackit.xquery.xdm.Stream#close()
	 */
	@Override
	public void close() {
		if (page != null) {
			page.cleanup();
			page = null;
		}
	}

	/**
	 * @see org.brackit.xquery.xdm.Stream#next()
	 */
	@Override
	public BracketNode next() throws DocumentException {

		if (!moveNext()) {
			// no next node qualifies
			return null;
		}

		return loadCurrent();
	}

	/**
	 * Loads and returns the node this iterator points to. This method may only
	 * be called after moveNext() was invoked and returned true.
	 */
	public BracketNode loadCurrent() throws DocumentException {

		// special handling for document node
		if (preFirstNode != null) {
			BracketNode out = preFirstNode;
			preFirstNode = null;
			return out;
		}

		// assertion: page != null

		try {

			return page.load(locator.bracketNodeLoader);

		} catch (IndexOperationException e) {
			page.cleanup();
			page = null;
			throw new DocumentException("Error loading current node.", e);
		}
	}

	/**
	 * Moves this iterator to the next qualifying node. It returns false if
	 * there are no more nodes, otherwise true.
	 */
	public boolean moveNext() throws DocumentException {

		try {

			// while current node is not accepted by the filter: go to next node
			while (true) {

				if (firstUsage) {

					if (preFirst) {
						preFirst = false;
						preFirstNode = preFirst();
						if (preFirstNode != null
								&& (filter == null || filter
										.accept(preFirstNode))) {
							return true;
						}
					}

					firstUsage = false;
					// try to load the hint page
					if (hintPageInfo != null) {
						page = tree.loadHintPage(tx, startDeweyID,
								hintPageInfo, OPEN_MODE, deweyIDBuffer);
					}
					first();
				} else {
					// go to next node
					nextInternal();
				}

				if (page == null) {
					// context initialization or navigating to next node failed
					return false;
				}

				// at this point, we know that the navigation succeeded and the
				// context points to a valid node
				currentKey = page.getKey();
				// check filter condition
				if (filter == null || page.accept(filter)) {
					return true;
				}
			}

		} catch (IndexOperationException e) {
			page.cleanup();
			page = null;
			throw new DocumentException("Error navigating to next node.", e);
		} catch (IndexAccessException e) {
			page = null;
			throw new DocumentException("Error navigating to next node.", e);
		}
	}

	protected abstract BracketNode preFirst() throws IndexOperationException,
			IndexAccessException;

	protected abstract void first() throws IndexOperationException,
			IndexAccessException;

	protected abstract void nextInternal() throws IndexOperationException,
			IndexAccessException;

}
