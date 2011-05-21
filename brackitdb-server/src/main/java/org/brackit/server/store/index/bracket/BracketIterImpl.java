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

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.bulkinsert.BulkInsertContext;
import org.brackit.server.store.index.bracket.page.Leaf;
import org.brackit.server.store.index.bracket.page.LeafBuffers;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.server.tx.Tx;

/**
 * @author Martin Hiller
 *
 */
public class BracketIterImpl implements BracketIter {

	private static Logger log = Logger.getLogger(BracketIterImpl.class);
	private static int BULK_INSERT_MAX_SEPARATORS = 512;
	protected final Tx tx;	
	protected final BracketTree tree;	
	protected final PageID rootPageID;	
	protected final OpenMode openMode;		
	protected Leaf page;	
	protected LeafBuffers leafBuffers;
	protected XTCdeweyID key;	
	protected XTCdeweyID insertKey;	
	protected byte[] value;	
	private final int pageSize;
	private long rememberedLSN;	
	private PageID rememberedPageID;
	private int rememberedOffset;
	private boolean on;
	
	private BulkInsertContext bulkContext;
	
	public BracketIterImpl(Tx tx, BracketTree tree, PageID rootPageID, Leaf page, OpenMode openMode, XTCdeweyID insertKey) throws IndexAccessException
	{
		try
		{
			this.tx = tx;
			this.tree = tree;
			this.rootPageID = rootPageID;
			this.page = page;
			this.leafBuffers = page.getDeweyIDBuffers();
			this.openMode = openMode;
			this.key = page.getKey();
			this.value = page.getValue();
			this.pageSize = page.getSize();
			this.insertKey = insertKey;
			off();
		}
		catch (IndexOperationException e)
		{
			throw new IndexAccessException(e, "Error initializing iterator");
		}
	}
	
	protected void off() throws IndexAccessException
	{
		if (openMode == OpenMode.LOAD)
		{
			on = false;
			return;
		}
		
		rememberedLSN = page.getLSN();
		rememberedPageID = page.getPageID();
		rememberedOffset = page.getOffset();
		page.unlatch();
		on = false;
	}
	
	protected void on(boolean checkThisPageOnly) throws IndexAccessException
	{
		if (openMode == OpenMode.LOAD)
		{
			on = true;
			return;
		}

		try
		{
			boolean refreshBufferedValues = false;
			
			if (page != null) {
				if (!openMode.forUpdate())
				{
					page.latchS();			
				}
				else
				{
					page.latchX();	
				}
				
				if (page.getLSN() != rememberedLSN)
				{
					refreshBufferedValues = true;
					
					if (log.isTraceEnabled())
					{
						log.trace(String.format("Repositioning cursor for index %s at (%s, %s)", rootPageID, key, value));
					}
					
					// Reposition the iterator with a new traversal
	
					if (insertKey == null)
					{
						NavigationStatus oldKey = null;
						if ((page.isLeaf()) && page.getRootPageID().equals(rootPageID) && key.compareDivisions(page.getHighKey()) < 0) {
							// navigate to the last seen record
							oldKey = page.navigateContextFree(key, NavigationMode.TO_KEY);		
						}
	
						if (oldKey == null || oldKey != NavigationStatus.FOUND) {
							page.cleanup();
							page = null;
							
							if (!checkThisPageOnly) {
								// descend down the index tree to the last seen record again
								page = tree.navigate(tx, rootPageID, NavigationMode.TO_KEY, key, openMode, null, leafBuffers);
							}
						}
					}
					else
					{
						// descend down the index tree to the insertion position
						page.cleanup();	
						page = tree.navigate(tx, rootPageID, NavigationMode.TO_INSERT_POS, insertKey, openMode, null, leafBuffers);
					}
				}
			} else if (!checkThisPageOnly) {
				// page is null
				refreshBufferedValues = true;
				page = tree.openInternal(tx, rootPageID, NavigationMode.TO_KEY, key, openMode, new HintPageInformation(rememberedPageID, rememberedLSN, rememberedOffset), leafBuffers);
			}

			if (page != null) {
				if (refreshBufferedValues) {
					rememberedLSN = page.getLSN();
					rememberedPageID = page.getPageID();
					rememberedOffset = page.getOffset();
					key = page.getKey();
					value = page.getValue();
				}
				on = true;
			} else {
				on = false;
			}
		}
		catch (IndexOperationException e)
		{
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			throw new IndexAccessException(e, "Error switching iterator on.");
		}
		catch (IndexAccessException e)
		{
			page = null;
			throw new IndexAccessException(e, "Error switching iterator on.");
		}
	}

	@Override
	public void close() throws IndexAccessException
	{
		// if bulk insert is not finished yet
		if (bulkContext != null) {
			endBulkInsert();
		}
		
		if (page != null)
		{
			if ((!on) && (openMode != OpenMode.LOAD))
			{
				page.latchS();
			}
			
			page.cleanup();
			page = null;
		}
		leafBuffers = null;
	}

	@Override
	public XTCdeweyID getKey() throws IndexAccessException
	{
		return key;
	}

	@Override
	public byte[] getValue() throws IndexAccessException
	{
		return value;
	}

	@Override
	public void insert(XTCdeweyID deweyID, byte[] value, int ancestorsToInsert)
			throws IndexAccessException
	{
		if (!openMode.forUpdate())
		{
			close();
			throw new IndexAccessException("Index %s not opened for update.", rootPageID);
		}
		
		if (bulkContext != null) {
			bulkInsert(deweyID, value, ancestorsToInsert);
			return;
		}
		
		on(false);
		try
		{			
			page = tree.insertIntoLeaf(tx, rootPageID, page, deweyID, value, ancestorsToInsert, openMode.compact(), openMode.doLog(), -1);
			key = deweyID;
			this.value = value;
			insertKey = null;
			off();
		}
		catch (IndexAccessException e)
		{
			page = null;
			throw e;
		}
	}
	
	private void bulkInsert(XTCdeweyID deweyID, byte[] value, int ancestorsToInsert) throws IndexAccessException {
		// assert(page is exclusively latched)
		
		try {
			
			page = tree.insertIntoLeafBulk(tx, rootPageID, page, deweyID, value, ancestorsToInsert, bulkContext, openMode.doLog(), -1);
			key = deweyID;
			this.value = value;
		}
		catch (IndexAccessException e)
		{
			page = null;
			bulkContext.cleanup();
			bulkContext = null;
			throw e;
		}
	}

	@Override
	public boolean navigate(NavigationMode navMode) throws IndexAccessException
	{
		if (insertKey != null) {
			close();
			throw new IndexAccessException("Navigation not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}
		
		try {
			
			on(true);
					
			page = tree.navigate(tx, rootPageID, navMode, key, openMode, page, leafBuffers);
			
			if (page == null) {
				return false;
			}
			
			key = page.getKey();
			value = page.getValue();
			
			off();	
			
			return true;
		}
		catch (IndexAccessException e)
		{
			page = null;
			throw e;
		}
		catch (IndexOperationException e)
		{
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			throw new IndexAccessException(e, "Error navigating to specified record.");
		}
	}

	@Override
	public boolean next() throws IndexAccessException
	{
		if (insertKey != null) {
			close();
			throw new IndexAccessException("Navigation not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}
		
		try
		{
			on(false);
			
			page = tree.moveNext(tx, rootPageID, page, openMode);
			
			if (page == null) {
				return false;
			}
			
			key = page.getKey();
			value = page.getValue();
			
			off();	
			
			return true;
		}
		catch (IndexAccessException e)
		{
			page = null;
			throw e;
		}
		catch (IndexOperationException e)
		{
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			throw new IndexAccessException(e, "Error moving to next record.");
		}
	}

	@Override
	public void update(byte[] newValue) throws IndexAccessException
	{
		if (insertKey != null) {
			close();
			throw new IndexAccessException("Update not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}
		
		on(false);
		if (!openMode.forUpdate())
		{
			close();
			throw new IndexAccessException("Index %s not opened for update.", rootPageID);
		}

		try
		{
			page = tree.updateInLeaf(tx, rootPageID, page, newValue, -1);
			value = newValue;
			off();
		}
		catch (IndexAccessException e)
		{
			page = null;
			throw e;
		}
	}

	@Override
	public HintPageInformation getPageInformation() throws IndexAccessException
	{
		return new HintPageInformation(rememberedPageID, rememberedLSN, rememberedOffset);
	}

	@Override
	public void startBulkInsert() throws IndexAccessException
	{
		// latch current page exclusively for the duration of the bulk insert
		on(false);
		
		// create new bulk insert context
		bulkContext = new BulkInsertContext(page, BULK_INSERT_MAX_SEPARATORS);
	}
	
	@Override
	public void endBulkInsert() throws IndexAccessException
	{
		tree.completeBulkInsert(tx, rootPageID, bulkContext, openMode.doLog());
		bulkContext = null;
		off();
	}

	@Override
	public void insertPrefixAware(XTCdeweyID deweyID, byte[] value,
			int ancestorsToInsert) throws IndexAccessException
	{
		if (bulkContext != null) {
			throw new RuntimeException("BulkInsert not supported for the prefix-aware insertion!");
		}
		
		if (!openMode.forUpdate())
		{
			close();
			throw new IndexAccessException("Index %s not opened for update.", rootPageID);
		}
		
		on(false);
		
		try
		{
			// check whether insertion is supposed to take place in this or the next page
			if (page.isLast()) {
				XTCdeweyID highKey = page.getHighKey();
				if (highKey != null && deweyID.compareDivisions(highKey) >= 0) {
					// insertion in NEXT page
					Leaf nextPage =	(Leaf) tree.getPage(tx, page.getNextPageID(), true, false);
					page.cleanup();
					page = nextPage;
				}
			}
			
			page = tree.insertIntoLeaf(tx, rootPageID, page, deweyID, value, ancestorsToInsert, openMode.compact(), openMode.doLog(), -1);
			key = deweyID;
			this.value = value;
			insertKey = null;
			off();
		}
		catch (IndexAccessException e)
		{
			page = null;
			throw e;
		}
		catch (IndexOperationException e)
		{
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			throw new IndexAccessException(e, "Error fetching next page.");
		}
	}

	@Override
	public void deleteSubtree(SubtreeDeleteListener deleteListener)
			throws IndexAccessException
	{
		if (insertKey != null) {
			close();
			throw new IndexAccessException("Deletion not allowed if index is opened for insertion!");
		} else if (bulkContext != null) {
			endBulkInsert();
		}
		
		on(false);
		if (!openMode.forUpdate())
		{
			close();
			throw new IndexAccessException("Index %s not opened for update.", rootPageID);
		}

		try
		{
			page = tree.deleteFromLeaf(tx, rootPageID, page, deleteListener, -1, openMode.doLog());
			key = page.getKey();
			value = page.getValue();
			off();
		}
		catch (IndexAccessException e)
		{
			page = null;
			throw e;
		}
		catch (IndexOperationException e)
		{
			try {
				page.cleanup();
				page = null;
			} catch (Exception ex) {
			}
			throw new IndexAccessException(e, "Error deleting subtree.");
		}
	}
}
