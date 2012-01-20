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
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.page.BracketNodeLoader;
import org.brackit.server.store.page.bracket.RecordInterpreter;

/**
 * @author Martin Hiller
 * 
 */
public interface BracketIter {

	public boolean navigate(NavigationMode navMode) throws IndexAccessException;

	public XTCdeweyID getKey() throws IndexAccessException;
	
	public RecordInterpreter getRecord() throws IndexAccessException;

	public BracketNode load(BracketNodeLoader loader) throws IndexAccessException;

	public void deleteSubtree(SubtreeDeleteListener deleteListener)
			throws IndexAccessException;

	public void update(byte[] newValue) throws IndexAccessException;

	public void close() throws IndexAccessException;

	/**
	 * Moves the pointer to the next index entry
	 * 
	 * @return <code>TRUE</code>, iff the iterator has found another entry
	 * @throws IndexAccessException
	 *             if there was an error moving the pointer to the next record
	 */
	public boolean next() throws IndexAccessException;

	/**
	 * Returns information (PageID, LSN, Offset) of the current page.
	 * 
	 * @return information (PageID, LSN, Offset) of the current page
	 * @throws IndexAccessException
	 */
	public HintPageInformation getPageInformation() throws IndexAccessException;
}
