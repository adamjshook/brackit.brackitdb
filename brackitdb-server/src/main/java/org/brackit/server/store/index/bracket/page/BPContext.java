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
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.tx.thread.Latch;

/**
 * @author Martin Hiller
 * 
 */
public interface BPContext extends Latch {

	public PageID getPageID();

	public long getLSN();

	public int getSize();

	public int getFreeSpace();

	public int getUsedSpace();

	public PageID getRootPageID();

	public int getEntryCount();

	public PageID getPrevPageID() throws IndexOperationException;

	public void setPrevPageID(PageID lowPageID, boolean logged, long undoNextLSN)
			throws IndexOperationException;

	public boolean moveFirst() throws IndexOperationException;

	public boolean hasNext() throws IndexOperationException;

	public boolean moveNext() throws IndexOperationException;

	public boolean hasPrevious() throws IndexOperationException;

	public boolean moveLast() throws IndexOperationException;

	public String dump(String pageTitle) throws IndexOperationException;

	public BPContext format(boolean leaf, int unitID, PageID rootPageID,
			int height, boolean compressed, boolean logged, long undoNextLSN)
			throws IndexOperationException;

	public void deletePage() throws IndexOperationException;

	public void cleanup();

	public BPContext createClone() throws IndexOperationException;

	public int getUnitID();

	public int getHeight();
	
	public boolean isCompressed();

	boolean isLastInLevel();

	public boolean isLast();

	public byte[] getValue() throws IndexOperationException;

	public boolean isLeaf();

	public BasePage getPage();
	
	public boolean externalizeValue(byte[] value);
	
	public byte[] externalize(byte[] value) throws IndexOperationException;

}
