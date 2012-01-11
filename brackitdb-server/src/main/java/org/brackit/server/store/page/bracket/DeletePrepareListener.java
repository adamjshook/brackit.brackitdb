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
package org.brackit.server.store.page.bracket;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.XTCdeweyID;

/**
 * This interface is used by the BracketPage during delete preparation in order
 * to inform the leaf context about the currently processed node. The level
 * argument is the current node's level relative to the subtree root.
 * 
 * @author Martin Hiller
 * 
 */
public interface DeletePrepareListener {
	
	/**
	 * Informs about the currently processed node during delete preparation.
	 * @param deweyID the DeweyID
	 * @param value the value
	 * @param level the level relative to the subtree root
	 * @throws BracketPageException
	 */
	public void node(XTCdeweyID deweyID, byte[] value, int level) throws BracketPageException;
	
	/**
	 * Informs about the currently processed (externalized) node during delete preparation. 
	 * @param deweyID the DeweyID
	 * @param externalPageID the external PageID
	 * @param level the level relative to the subtree root
	 * @throws BracketPageException
	 */
	public void externalNode(XTCdeweyID deweyID, PageID externalPageID, int level) throws BracketPageException;
	
	/**
	 * Informs about the end of the subtree.
	 * @throws BracketPageException
	 */
	public void subtreeEnd() throws BracketPageException;

}
