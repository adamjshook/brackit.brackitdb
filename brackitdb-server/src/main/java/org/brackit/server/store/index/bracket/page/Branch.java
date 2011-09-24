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
package org.brackit.server.store.index.bracket.page;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.bracket.IndexOperationException;

/**
 * @author Martin Hiller
 * 
 */
public interface Branch extends BPContext {

	public int getPosition();

	public boolean moveTo(int position) throws IndexOperationException;

	public boolean insert(byte[] key, byte[] value, boolean logged,
			long undoNextLSN) throws IndexOperationException;

	public void delete(boolean logged, long undoNextLSN)
			throws IndexOperationException;

	public PageID getLowPageID() throws IndexOperationException;

	public void setLowPageID(PageID lowPageID, boolean logged, long undoNextLSN)
			throws IndexOperationException;

	public byte[] getKey() throws IndexOperationException;

	public PageID getValueAsPageID() throws IndexOperationException;

	public void setPageIDAsValue(PageID pageID, boolean logged, long undoNextLSN)
			throws IndexOperationException;

	public void moveAfterLast() throws IndexOperationException;

	public boolean isAfterLast() throws IndexOperationException;

	public int calcMaxInlineValueSize(int maxKeySize);

	public int calcMaxKeySize();

	public PageID searchNextPageID(SearchMode searchMode, byte[] searchKey)
			throws IndexOperationException;

	public int search(SearchMode searchMode, byte[] searchKey,
			byte[] searchValue) throws IndexOperationException;

	void setLastInLevel(boolean last);

	public boolean isExternalized(int position);

	public int getUsedSpace(int position);

	public void format(int unitID, PageID rootPageID, int height,
			boolean compressed, boolean logged, long undoNextLSN)
			throws IndexOperationException;
	
	public byte[] getValue() throws IndexOperationException;

	public boolean setValue(byte[] value, boolean logged, long undoNextLSN)
			throws IndexOperationException;
}
