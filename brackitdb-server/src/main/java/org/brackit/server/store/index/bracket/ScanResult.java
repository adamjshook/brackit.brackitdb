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
import org.brackit.server.store.index.bracket.page.Leaf;

/**
 * This class is used as the return type of the scan method in
 * {@link BracketTree}.
 * 
 * @author Martin Hiller
 * 
 */
public class ScanResult {
	public final boolean nodeFound;
	public final Leaf resultLeaf;
	public final XTCdeweyID targetDeweyID;

	/**
	 * The requested node was found and is located in the result leaf.
	 * 
	 * @param result
	 *            the leaf context where the node was found
	 */
	public ScanResult(Leaf result) {
		this.nodeFound = true;
		this.resultLeaf = result;
		this.targetDeweyID = null;
	}

	/**
	 * The requested node was not found, but at least the DeweyID of the
	 * requested node is known by now.
	 * 
	 * @param targetDeweyID
	 *            the DeweyID of the target node
	 */
	public ScanResult(XTCdeweyID targetDeweyID) {
		this.nodeFound = false;
		this.resultLeaf = null;
		this.targetDeweyID = targetDeweyID;
	}

	/**
	 * The node was not found (yet).
	 */
	public ScanResult() {
		this.nodeFound = false;
		this.resultLeaf = null;
		this.targetDeweyID = null;
	}
}
