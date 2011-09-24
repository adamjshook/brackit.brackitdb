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

		try {
			BracketNode node = null;

			// while current node is filtered out: go to next node
			while (node == null) {

				if (firstUsage) {

					if (preFirst) {
						preFirst = false;
						node = preFirst();
						if (node != null && filter != null
								&& !filter.accept(node)) {
							node = null;
						}
						continue;
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
					return null;
				}

				// at this point, we know that the navigation succeeded and the
				// context points to a valid node
				currentKey = page.getKey();
				node = page.load(locator.bracketNodeLoader, filter);
				// if node is still 'null' it was not accepted by the filter
			}

			return node;

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
