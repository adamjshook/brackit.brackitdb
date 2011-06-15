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
package org.brackit.server.node.bracket;

import org.apache.log4j.Logger;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.BracketIndex;
import org.brackit.server.store.index.bracket.BracketIter;
import org.brackit.server.store.index.bracket.HintPageInformation;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Martin Hiller
 *
 */
public class BracketChildStream implements Stream<BracketNode> {
	
	private static final Logger log = Logger.getLogger(BracketChildStream.class);
	
	private final XTCdeweyID parentDeweyID;
	private final BracketLocator locator;
	private final HintPageInformation hintPageInfo;
	private final BracketIndex index;
	
	private BracketIter iterator;
	private BracketNode next;
	
	public BracketChildStream(XTCdeweyID parentDeweyID, BracketLocator locator, HintPageInformation hintPageInfo) throws DocumentException {
		this.parentDeweyID = parentDeweyID;
		this.locator = locator;
		this.hintPageInfo = hintPageInfo;
		this.index = locator.collection.store.index;
		
		open();
	}
	
	private void open() throws DocumentException
	{		
		try
		{
			iterator = parentDeweyID.isDocument() ?
					index.open(locator.collection.getTX(), locator.rootPageID, NavigationMode.TO_KEY, XTCdeweyID.newRootID(parentDeweyID.getDocID()), OpenMode.READ):
					index.open(locator.collection.getTX(), locator.rootPageID, NavigationMode.FIRST_CHILD, parentDeweyID, OpenMode.READ, hintPageInfo);

			if (iterator == null) {
				next = null;
			} else {
				readNext();
			}
		}
		catch (IndexAccessException e)
		{
			throw new DocumentException(e);
		}
	}
	
	private void readNext() throws IndexAccessException, DocumentException {
		next = locator.fromBytes(iterator.getKey(), iterator.getValue());
		next.hintPageInfo = iterator.getPageInformation();	
	}

	@Override
	public void close()
	{
		if (iterator != null)
		{
			try
			{
				iterator.close();
				iterator = null;
			}
			catch (IndexAccessException e)
			{
				log.error(e);
			}
		}
	}

	public boolean hasNext() throws DocumentException
	{
		return next != null;
	}

	@Override
	public BracketNode next() throws DocumentException
	{
		BracketNode out = next;
		
		try
		{
			if (iterator != null) {	
				if (iterator.navigate(NavigationMode.NEXT_SIBLING)) {
					readNext();
				} else {
					next = null;
					iterator.close();
					iterator = null;
				}					
			} else {
				next = null;
			}
		}
		catch (IndexAccessException e)
		{
			throw new DocumentException(e, "Error accessing document index");
		}
		
		return out;
	}

}
