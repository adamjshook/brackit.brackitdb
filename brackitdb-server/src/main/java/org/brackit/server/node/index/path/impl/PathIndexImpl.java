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
package org.brackit.server.node.index.path.impl;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.path.PathIndex;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.node.txnode.IndexEncoderHelper;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.visitor.IndexStatisticsVisitor;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.services.KVLLockService;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Karsten Schmidt
 * 
 */
public class PathIndexImpl<E extends Node<E>> implements PathIndex<E> {
	private final Index index;

	public PathIndexImpl(BufferMgr bufferMgr) {
		this.index = new BPlusIndex(bufferMgr, new KVLLockService(
				PathIndex.class.getSimpleName()));
	}

	@Override
	public void calculateStatistics(Tx tx, IndexDef idxDef)
			throws DocumentException {
		try {
			IndexStatisticsVisitor visitor = new IndexStatisticsVisitor();
			index.traverse(tx, new PageID(idxDef.getID()), visitor);
			idxDef.setIndexStatistics(visitor.getIndexStatistics());
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public SubtreeListener<? super E> createBuilder(Tx tx,
			IndexEncoder<E> encoder, Filter<? super E> filter, int containerNo,
			IndexDef idxDef) throws DocumentException {
		return new PathIndexBuilder<E>(tx, index, containerNo, idxDef, encoder,
				filter);
	}

	@Override
	public SubtreeListener<? super E> createListener(Tx tx,
			IndexEncoder<E> encoder, Filter<? super E> filter, ListenMode mode,
			IndexDef idxDef) throws DocumentException {
		return new PathIndexListener<E>(tx, index, idxDef, mode, encoder,
				filter);
	}

	@Override
	public Stream<? extends E> open(Tx tx, IndexEncoderHelper<E> helper,
			int idxNo, SearchMode searchMode, Filter<? super E> filter)
			throws DocumentException {
		try {
			IndexIterator iterator = index.open(tx, new PageID(idxNo),
					searchMode, null, null, OpenMode.READ);
			IndexEncoder<E> encoder = helper.getPathIndexEncoder(iterator
					.getKeyType(), iterator.getValueType());
			return new PathIndexIteratorImpl<E>(iterator, encoder, filter);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void drop(Tx tx, int idxNo) throws DocumentException {
		try {
			index.dropIndex(tx, new PageID(idxNo));
		} catch (IndexAccessException e) {
			throw new DocumentException();
		}
	}
}
