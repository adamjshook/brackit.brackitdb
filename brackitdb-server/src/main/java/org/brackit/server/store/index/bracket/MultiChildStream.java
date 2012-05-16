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
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.xquery.xdm.DocumentException;

/**
 * A Stream for efficient evaluation of multiple child steps in sequence, e.g.
 * ./a/b/c
 * 
 * @author Martin Hiller
 * 
 */
public class MultiChildStream extends StreamIterator {

	private final int firstChildLevel;
	private final BracketFilter[] filters;

	private NavigationStatus navStatus;
	private ScanResult scanRes;

	private int currentDepth;

	public MultiChildStream(BracketLocator locator, BracketTree tree,
			XTCdeweyID rootDeweyID, HintPageInformation hintPageInfo,
			BracketFilter... filters) {
		super(locator, tree, rootDeweyID, hintPageInfo, null);
		this.filters = filters;
		this.firstChildLevel = rootDeweyID.getLevel() + 1;
		
		for (int i = 0; i < filters.length; i++) {
			if (filters[i] == null) {
				filters[i] = BracketFilter.TRUE;
			}
		}
	}

	public MultiChildStream(StreamIterator other, BracketFilter... filters)
			throws DocumentException {
		super(other, null);
		this.filters = filters;
		this.firstChildLevel = startDeweyID.getLevel() + 1;
		
		for (int i = 0; i < filters.length; i++) {
			if (filters[i] == null) {
				filters[i] = BracketFilter.TRUE;
			}
		}
	}

	@Override
	protected void first() throws IndexOperationException, IndexAccessException {

		// go to first child
		
		if (page != null) {

			// hint page was already loaded
			navStatus = page.navigateFirstChild(false);

			if (navStatus == NavigationStatus.FOUND) {
				currentDepth = 0;
			} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
				page.cleanup();
				page = null;
				return;
			} else {
				scanRes = tree
						.navigateAfterHintPageFail(tx, locator.rootPageID,
								NavigationMode.FIRST_CHILD, startDeweyID,
								OPEN_MODE, page, deweyIDBuffer, navStatus);
				page = scanRes.resultLeaf;
				if (page == null) {
					currentDepth = -1;
					return;
				}
				currentDepth = page.getLevel() - firstChildLevel;
			}

		} else {

			scanRes = tree.navigateViaIndexAccess(tx, locator.rootPageID,
					NavigationMode.FIRST_CHILD, startDeweyID, OPEN_MODE,
					deweyIDBuffer);
			page = scanRes.resultLeaf;
			if (page == null) {
				currentDepth = -1;
				return;
			}
			currentDepth = page.getLevel() - firstChildLevel;

		}
		
		// start main loop
		mainLoop();
	}

	@Override
	protected void nextInternal() throws IndexOperationException,
			IndexAccessException {

		// go to next sibling

		navStatus = page.navigateNextSibling(true);
		if (navStatus == NavigationStatus.FOUND) {
			// currentDepth does not change
		} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
			// adjust depth
			currentDepth = page.getLevel() - firstChildLevel;
		} else {
			// use index
			scanRes = tree.navigateAfterHintPageFail(tx, locator.rootPageID,
					NavigationMode.NEXT_SIBLING, currentKey, OPEN_MODE, page,
					deweyIDBuffer, navStatus);
			page = scanRes.resultLeaf;
			if (page == null) {
				currentDepth = -1;
				return;
			}
			currentDepth = page.getLevel() - firstChildLevel;
		}

		// start main loop
		mainLoop();
	}

	private void mainLoop() throws IndexOperationException,
			IndexAccessException {

		while (true) {

			if (currentDepth < 0) {
				// we left the subtree
				page.cleanup();
				page = null;
				return;
			}

			// check filter for current depth
			if (page.accept(filters[currentDepth])) {

				if (currentDepth == filters.length - 1) {
					// finished
					return;
				}

				// go one level deeper
				currentKey = page.getKey();
				navStatus = page.navigateFirstChild(true);
				if (navStatus == NavigationStatus.FOUND) {
					currentDepth++;
				} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
					// adjust depth
					currentDepth = page.getLevel() - firstChildLevel;
				} else {
					// use index
					scanRes = tree.navigateAfterHintPageFail(tx,
							locator.rootPageID, NavigationMode.FIRST_CHILD,
							currentKey, OPEN_MODE, page, deweyIDBuffer,
							navStatus);
					page = scanRes.resultLeaf;
					if (page == null) {
						currentDepth = -1;
						return;
					}
					currentDepth = page.getLevel() - firstChildLevel;
				}

			} else {

				// go to next sibling
				currentKey = page.getKey();
				navStatus = page.navigateNextSibling(true);
				if (navStatus == NavigationStatus.FOUND) {
					// currentDepth does not change
				} else if (navStatus == NavigationStatus.NOT_EXISTENT) {
					// adjust depth
					currentDepth = page.getLevel() - firstChildLevel;
				} else {
					// use index
					scanRes = tree.navigateAfterHintPageFail(tx,
							locator.rootPageID, NavigationMode.NEXT_SIBLING,
							currentKey, OPEN_MODE, page, deweyIDBuffer,
							navStatus);
					page = scanRes.resultLeaf;
					if (page == null) {
						currentDepth = -1;
						return;
					}
					currentDepth = page.getLevel() - firstChildLevel;
				}
			}
		}
	}
}
