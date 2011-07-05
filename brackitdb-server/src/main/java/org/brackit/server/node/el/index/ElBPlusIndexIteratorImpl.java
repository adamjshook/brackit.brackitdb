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
package org.brackit.server.node.el.index;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.el.ElIndexIterator;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndexIterator;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.page.PageContext;
import org.brackit.server.tx.Tx;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElBPlusIndexIteratorImpl extends BPlusIndexIterator implements
		ElIndexIterator {
	private static final Logger log = Logger
			.getLogger(ElBPlusIndexIteratorImpl.class);

	public ElBPlusIndexIteratorImpl(Tx transaction, ElBPlusTree tree,
			PageID rootPageID, PageContext page, OpenMode openMode)
			throws IndexAccessException {
		super(transaction, tree, rootPageID, page, openMode);
	}

	@Override
	public void deletePrefixAware(int level) throws IndexAccessException {
		on();
		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		try {
			page = ((ElBPlusTree) tree).deletePrefixAwareFromLeaf(transaction,
					rootPageID, page, key, value, level, openMode.doLog(), -1);
			key = page.getKey();
			value = page.getValue();
		} catch (IndexAccessException e) {
			page = null;
			throw e;
		} catch (IndexOperationException e) {
			page = null;
			throw new IndexAccessException(e);
		}

		off();
	}

	@Override
	public void insertPrefixAware(byte[] insertKey, byte[] insertValue,
			int level) throws IndexAccessException {
		on();
		if (!openMode.forUpdate()) {
			close();
			throw new IndexAccessException("Index %s not opened for update.",
					rootPageID);
		}

		try {
			if (openMode != OpenMode.LOAD) {
				checkPrefixAwareInsertPosition(insertKey, insertValue);
			}

			page = ((ElBPlusTree) tree).insertPrefixAwareIntoLeaf(transaction,
					rootPageID, page, insertKey, insertValue, level, openMode
							.compact(), openMode.doLog(), -1);
			key = insertKey;
			value = insertValue;
		} catch (IndexAccessException e) {
			page = null;
			throw e;
		}

		off();
	}

	private void checkPrefixAwareInsertPosition(byte[] insertKey,
			byte[] insertValue) throws IndexAccessException {
		try {
			byte[] currentKey = page.getKey();
			byte[] currentValue = page.getValue();
			byte[] previousKey = page.getPreviousKey();
			byte[] previousValue = page.getPreviousValue();

			if ((currentKey != null)
					&& ((keyType.compare(insertKey, currentKey) > 0) || ((keyType
							.compare(insertKey, currentKey) == 0) && ((!page
							.isUnique()) || (valueType.compare(insertValue,
							currentValue) >= 0))))) {
				close();
				throw new IndexAccessException(
						"Insert of (%s, %s) at current position "
								+ "violates index integrity because it is greater "
								+ "than the current record (%s, %s) or a duplicate.",
						keyType.toString(insertKey), valueType
								.toString(insertValue), keyType
								.toString(currentKey), valueType
								.toString(currentValue));
			}

			if ((previousKey != null)
					&& ((keyType.compare(previousKey, insertKey) > 0) || ((keyType
							.compare(previousKey, insertKey) == 0) && ((!page
							.isUnique()) || (valueType.compare(previousValue,
							insertValue) >= 0))))) {
				close();
				throw new IndexAccessException(
						"Insert of (%s, %s) at current position "
								+ "violates index integrity because it is smaller "
								+ "than the previous record (%s, %s) or a duplicate.",
						keyType.toString(insertKey), valueType
								.toString(insertValue), keyType
								.toString(previousKey), valueType
								.toString(previousValue));
			}

			if ((previousKey == null)
					|| ((currentKey == null) && (page.getNextPageID() != null))) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Restart with a tree traversal to insert (%s, %s) "
									+ "because insert position is ambigious.",
							page, keyType.toString(insertKey), valueType
									.toString(insertValue)));
				}

				page.cleanup();
				page = tree.descendToPosition(transaction, rootPageID,
						SearchMode.GREATER_OR_EQUAL, insertKey, insertValue,
						openMode.forUpdate(), true);
			}
		} catch (IndexOperationException e) {
			page = null;
			throw new IndexAccessException(e,
					"Error validating insert position");
		}
	}
}
