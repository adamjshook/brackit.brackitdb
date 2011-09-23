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
package org.brackit.server.node.index.element.impl;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.element.ElementIndex;
import org.brackit.server.node.index.element.impl.NameDirectoyEncoderImpl.QVocID;
import org.brackit.server.node.index.external.IndexStatistics;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.node.txnode.IndexEncoderHelper;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.visitor.SizeCounterVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.services.KVLLockService;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElementIndexImpl<E extends TXNode<E>> implements ElementIndex<E> {
	private final Index index;

	private final NameDirectoryEncoder nameDirectoryEncoder;

	public ElementIndexImpl(BufferMgr bufferMgr) {
		this.index = new BPlusIndex(bufferMgr, new KVLLockService(
				ElementIndex.class.getSimpleName()));
		this.nameDirectoryEncoder = new NameDirectoyEncoderImpl();
	}

	@Override
	public void calculateStatistics(Tx tx, IndexDef idxDef)
			throws DocumentException {
		long idxSize = 0;
		long pageNo = 0;
		long indexLeaveCount = 0;
		long indexTuples = 0;
		long indexPointers = 0;
		int indexHeight = 0;

		IndexIterator iterator = null;

		try {
			iterator = index.open(tx, new PageID(idxDef.getID()),
					SearchMode.FIRST, null, null, OpenMode.READ);

			if (iterator.getKey() != null) {
				do {
					byte[] value = iterator.getValue();
					PageID nodeReferenceIdxNo = nameDirectoryEncoder
							.decodePageID(value);
					SizeCounterVisitor scv = new SizeCounterVisitor();
					index.traverse(tx, nodeReferenceIdxNo, scv);
					idxSize = scv.getIndexSize();
					pageNo += scv.getIndexPageCount();
					indexHeight = Math.max(indexHeight, scv.getIndexHeight());
					indexLeaveCount += scv.getIndexLeaveCount();
					indexTuples += scv.getIndexTuples();
					indexPointers += scv.getIndexPointers();
				} while (iterator.next());
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		} finally {
			iterator.close();
		}

		IndexStatistics indexStatistics = null;
		indexStatistics = new IndexStatistics();
		indexStatistics.setIdxSize(idxSize);
		indexStatistics.setIndexHeight(indexHeight);
		indexStatistics.setIndexLeaveCount(indexLeaveCount);
		indexStatistics.setIndexPointers(indexPointers);
		indexStatistics.setIndexTuples(indexTuples);
		indexStatistics.setPageCount(pageNo);
		idxDef.setIndexStatistics(indexStatistics);
	}

	public SubtreeListener<? super E> createBuilder(Tx tx,
			IndexEncoderHelper<E> helper, int containerNo, IndexDef idxDef)
			throws DocumentException {
		return new ElementIndexBuilder<E>(tx, index, helper, containerNo,
				idxDef);
	}

	public Stream<? extends E> open(Tx tx, IndexEncoderHelper<E> helper,
			int inverseIdxNo, QVocID qVocID, SearchMode searchMode,
			XTCdeweyID deweyID) throws DocumentException {
		IndexIterator iterator = null;
		try {
			byte[] value = index.read(tx, new PageID(inverseIdxNo),
					nameDirectoryEncoder.encodeKey(qVocID));

			if (value != null) {
				IndexEncoder<E> nodeReferenceEncoder = helper
						.getElementIndexEncoder();
				PageID nodeReferenceIdxNo = nameDirectoryEncoder
						.decodePageID(value);
				byte[] openKey = (deweyID != null) ? deweyID.toBytes() : null;
				byte[] openValue = (deweyID != null) ? deweyID.toBytes() : null;
				iterator = index.open(tx, nodeReferenceIdxNo, searchMode,
						openKey, openValue, OpenMode.READ);
				return new ElementIndexIteratorImpl<E>(iterator,
						nodeReferenceEncoder);
			} else {
				iterator = new NullIndexIterator(Field.NULL, Field.NULL);
				return new ElementIndexIteratorImpl<E>(iterator, null);
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public SubtreeListener<? super E> createListener(Tx tx,
			IndexEncoderHelper<E> helper, ListenMode mode, IndexDef idxDef)
			throws DocumentException {
		return new ElementIndexListener<E>(tx, index, helper, idxDef, mode);
	}

	@Override
	public void drop(Tx tx, int indexNo) throws DocumentException {
		NameDirectoryEncoder encoder = new NameDirectoyEncoderImpl();
		PageID rootPageID = new PageID(indexNo);
		IndexIterator iterator = null;

		try {
			iterator = index.open(tx, rootPageID, SearchMode.FIRST, null, null,
					OpenMode.READ);

			try {
				do {
					byte[] value = iterator.getValue();

					if (value != null) {
						PageID nodeReferenceIdxNo = encoder.decodePageID(value);
						index.dropIndex(tx, nodeReferenceIdxNo);
					}
				} while (iterator.next());
			} finally {
				iterator.close();
			}

			index.dropIndex(tx, rootPageID);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}
}