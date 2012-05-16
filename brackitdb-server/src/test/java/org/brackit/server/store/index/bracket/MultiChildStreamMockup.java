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
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Martin Hiller
 * 
 */
public class MultiChildStreamMockup implements Stream<BracketNode> {

	private final BracketLocator locator;
	private final BracketTree tree;
	private final XTCdeweyID rootDeweyID;
	private final HintPageInformation hintPageInfo;

	private final BracketFilter[] filters;
	private final ChildStream[] streams;
	
	private int currentDepth;

	public MultiChildStreamMockup(BracketLocator locator, BracketTree tree,
			XTCdeweyID rootDeweyID, HintPageInformation hintPageInfo,
			BracketFilter... filters) {

		this.locator = locator;
		this.tree = tree;
		this.rootDeweyID = rootDeweyID;
		this.hintPageInfo = hintPageInfo;
		this.filters = filters;

		for (int i = 0; i < filters.length; i++) {
			if (filters[i] == null) {
				filters[i] = BracketFilter.TRUE;
			}
		}

		this.streams = new ChildStream[filters.length];
		this.currentDepth = 0;
	}

	@Override
	public BracketNode next() throws DocumentException {
		
		while (true) {
			
			// open next stream
			if (currentDepth < streams.length) {
			
				if (currentDepth == 0) {
					streams[0] = new ChildStream(locator, tree,
							rootDeweyID, hintPageInfo, filters[0]);
				} else {
					streams[currentDepth] = new ChildStream(streams[currentDepth - 1], filters[currentDepth]);
				}
				currentDepth++;
			}
			
			// go to next node
			while (!streams[currentDepth - 1].moveNext()) {
				currentDepth--;
				streams[currentDepth].close();
				streams[currentDepth] = null;
				
				if (currentDepth == 0) {
					close();
					return null;
				}
			}
			
			if (currentDepth == streams.length) {
				return streams[streams.length - 1].loadCurrent();
			}
		}
	}

	@Override
	public void close() {
		
		for (int i = 0; i < streams.length; i++) {
			if (streams[i] != null) {
				streams[i].close();
				streams[i] = null;
			}
		}
	}
}
