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
package org.brackit.server.store.index.aries.visitor;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.index.IndexVisitor;
import org.brackit.server.store.index.aries.page.PageContext;

/**
 * @author Karsten Schmidt
 * 
 */
public class PageCollectorVisitor implements IndexVisitor {
	int indexPageCount;
	private ArrayList<PageID> pages;

	public PageCollectorVisitor() {
		pages = new ArrayList<PageID>();
	}

	@Override
	public void end() {
	}

	@Override
	public void start() {
		indexPageCount = 0;
	}

	@Override
	public void visitLeafPage(PageContext page, boolean overflow) {
		this.pages.add(page.getPageID());
		// TODO: following linked records
	}

	@Override
	public void visitTreePage(PageContext page) {
		this.pages.add(page.getPageID());
	}

	public int getIndexPageCount() {
		return this.indexPageCount;
	}

	public List<PageID> getPages() {
		return pages;
	}
}
