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
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;

/**
 * @author Martin Hiller
 * 
 */
public final class ChildStream extends StreamIterator {

	public ChildStream(BracketLocator locator, BracketTree tree,
			XTCdeweyID parentDeweyID, HintPageInformation hintPageInfo,
			BracketFilter filter) {
		super(locator, tree, parentDeweyID, hintPageInfo, filter);
	}

	/**
	 * @see org.brackit.server.store.index.bracket.StreamIterator#first()
	 */
	@Override
	protected void first() throws IndexOperationException, IndexAccessException {

		if (startDeweyID.isDocument()) {
			page = tree.openInternal(tx, locator.rootPageID,
					NavigationMode.TO_KEY,
					XTCdeweyID.newRootID(startDeweyID.getDocID()), OPEN_MODE,
					null, deweyIDBuffer);
		} else {

			if (page != null) {
				// hint page was already loaded
				NavigationStatus navStatus = page.navigateFirstChild();
				if (navStatus == NavigationStatus.FOUND) {
					return;
				} else if ((navStatus == NavigationStatus.NOT_EXISTENT)) {
					page.cleanup();
					page = null;
					return;
				}
				page = tree.navigateAfterHintPageFail(tx, locator.rootPageID,
						NavigationMode.FIRST_CHILD, startDeweyID, OPEN_MODE,
						page, deweyIDBuffer, navStatus);
				return;
			}

			page = tree.navigateViaIndexAccess(tx, locator.rootPageID,
					NavigationMode.FIRST_CHILD, startDeweyID, OPEN_MODE,
					deweyIDBuffer);
		}
	}

	/**
	 * @see org.brackit.server.store.index.bracket.StreamIterator#nextInternal()
	 */
	@Override
	protected void nextInternal() throws IndexOperationException,
			IndexAccessException {
		// try to find node without BracketTree
		NavigationStatus navStatus = page.navigateNextSibling();
		if (navStatus == NavigationStatus.FOUND) {
			return;
		} else if ((navStatus == NavigationStatus.NOT_EXISTENT)) {
			page.cleanup();
			page = null;
			return;
		}

		// use BracketTree to continue the scan
		page = tree.navigateAfterHintPageFail(tx, locator.rootPageID,
				NavigationMode.NEXT_SIBLING, currentKey, OPEN_MODE, page,
				deweyIDBuffer, navStatus);
	}

	@Override
	protected BracketNode preFirst() throws IndexOperationException,
			IndexAccessException {
		return null;
	}
}
