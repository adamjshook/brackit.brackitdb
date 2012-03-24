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
import org.brackit.server.store.index.bracket.page.Leaf;

/**
 * This class is used as the return type of the scan method in
 * {@link BracketTree}.
 * 
 * @author Martin Hiller
 * 
 */
public class ScanResult {
	
	public enum Status {
		FOUND, NOT_FOUND, NOT_EXISTENT
	}
	
	public final Status status;
	public final Leaf resultLeaf;
	public final XTCdeweyID targetDeweyID;
	
	/**
	 * The requested node was found and is located in the result leaf.
	 */
	public static ScanResult found(Leaf result) {
		return new ScanResult(Status.FOUND, result, null);
	}
	
	/**
	 * The requested node was not found, but the DeweyID of the
	 * requested node might be known.
	 */
	public static ScanResult notFound(XTCdeweyID targetDeweyID) {
		return new ScanResult(Status.NOT_FOUND, null, targetDeweyID);
	}
	
	/**
	 * The requested node does not exist. The given leaf context points to the node where the non-existence was determined. 
	 */
	public static ScanResult notExistent(Leaf result) {
		return new ScanResult(Status.NOT_EXISTENT, result, null);
	}
	
	private ScanResult(Status status, Leaf resultLeaf, XTCdeweyID targetDeweyID) {
		this.status = status;
		this.resultLeaf = resultLeaf;
		this.targetDeweyID = targetDeweyID;
	}
	
	public boolean scanCompleted() {
		return status == Status.FOUND || status == Status.NOT_EXISTENT;
	}
}
