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
package org.brackit.server.node.txnode;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.index.IndexController;
import org.brackit.server.node.index.cas.CASIndex;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.definition.IndexType;
import org.brackit.server.node.index.name.NameIndex;
import org.brackit.server.node.index.name.impl.NameDirectoryEncoderImpl.QVocID;
import org.brackit.server.node.index.path.PathIndex;
import org.brackit.server.store.SearchMode;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.StreamSubtreeProcessor;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.stream.FlatteningStream;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.Type;

/**
 * Common base implementation for {@link IndexController}.
 * 
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 * 
 */
public abstract class IndexControllerImpl<E extends TXNode<E>> implements
		IndexController<E>, IndexEncoderHelper<E> {
	protected final TXCollection<E> coll;

	protected final NameIndex<E> nameIndex;

	protected final PathIndex<E> pathIndex;

	protected final CASIndex<E> casIndex;

	public IndexControllerImpl(TXCollection<E> collection, NameIndex<E> 
		nameIndex, PathIndex<E> pathIndex, CASIndex<E> casIndex) {
		super();
		this.coll = collection;
		this.nameIndex = nameIndex;
		this.pathIndex = pathIndex;
		this.casIndex = casIndex;
	}

	@Override
	public DictionaryMgr getDictionary() {
		return coll.getDictionary();
	}

	public void calculateStatistics() throws DocumentException {
		calculateStatisticsInternal();
	}

	protected void calculateStatisticsInternal() throws DocumentException {
		for (IndexDef idxDefinition : coll.get(Indexes.class).getIndexDefs()) {
			switch (idxDefinition.getType()) {
			case NAME:
				if (nameIndex == null) {
					throw new DocumentException(
							"This document does not support name indexes.");
				}
				nameIndex.calculateStatistics(coll.getTX(), idxDefinition);
				break;
			case PATH:
				if (pathIndex == null) {
					throw new DocumentException(
							"This document does not support path indexes.");
				}
				pathIndex.calculateStatistics(coll.getTX(), idxDefinition);
				break;
			case CAS:
				if (casIndex == null) {
					throw new DocumentException(
							"This document does not support cas indexes.");
				}
				casIndex.calculateStatistics(coll.getTX(), idxDefinition);
				break;
			default:
				throw new DocumentException("Index type %s not supported yet.",
						idxDefinition.getType());
			}
		}
	}

	@Override
	public void createIndexes(IndexDef... indexDefinitions)
			throws DocumentException {
		createIndexesInternal(indexDefinitions);
	}

	public List<? extends SubtreeListener<? super E>> getIndexListener(
			ListenMode mode) throws DocumentException {
		List<IndexDef> indexDefinitions = coll.get(Indexes.class)
				.getIndexDefs();
		ArrayList<SubtreeListener<? super E>> listeners = new ArrayList(
				indexDefinitions.size());

		for (IndexDef idxDefinition : indexDefinitions) {
			listeners.add(createIndexListener(idxDefinition, mode));
		}

		return listeners;
	}

	private void createIndexesInternal(IndexDef... indexDefinitions)
			throws DocumentException {
		SubtreeListener<? super E>[] builders = new SubtreeListener[indexDefinitions.length];
		int builderNo = 0;
		for (IndexDef def : indexDefinitions) {
			int containerNo = (def.getContainerID() != -1) ? def
					.getContainerID() : 0;
			SubtreeListener<? super E> idxBuilder = createIndexBuilder(def,
					containerNo);
			builders[builderNo] = idxBuilder;
			builderNo++;
		}

		Stream<? extends E> stream = new FlatteningStream<E, E>(coll
				.getDocuments()) {
			@Override
			protected Stream<? extends E> getOutStream(E next)
					throws DocumentException {
				return next.getSubtree();
			}
		};

		StreamSubtreeProcessor<? extends Node<?>> parser = new StreamSubtreeProcessor(
				stream, builders);
		parser.process();

		Indexes indexes = coll.get(Indexes.class);
		for (IndexDef idxDefinition : indexDefinitions) {
			indexes.add(idxDefinition);
		}
	}

	protected abstract SubtreeListener<? super E> createIndexBuilder(
			IndexDef idxDef, int containerNo) throws DocumentException;

	protected abstract SubtreeListener<? super E> createIndexListener(
			IndexDef idxDef, ListenMode mode) throws DocumentException;

	@Override
	public void dropIndex(IndexDef idxDefinition) throws DocumentException {
		dropIndexInternal(idxDefinition);
	}

	protected void dropIndexInternal(IndexDef idxDefinition)
			throws DocumentException {
		switch (idxDefinition.getType()) {
		case NAME:
			if (nameIndex == null) {
				throw new DocumentException(
						"This document does not support name indexes.");
			}
			nameIndex.drop(coll.getTX(), idxDefinition.getID());
			break;
		case PATH:
			if (pathIndex == null) {
				throw new DocumentException(
						"This document does not support path indexes.");
			}
			pathIndex.drop(coll.getTX(), idxDefinition.getID());
			break;
		case CAS:
			if (casIndex == null) {
				throw new DocumentException(
						"This document does not support cas indexes.");
			}
			casIndex.drop(coll.getTX(), idxDefinition.getID());
			break;
		default:
			throw new DocumentException("Index type %s not supported yet.",
					idxDefinition.getType());
		}
		Indexes indexes = coll.get(Indexes.class);
		indexes.removeIndex(idxDefinition.getID());
	}

	@Override
	public Stream<? extends E> openNameIndex(int indexNo, QNm name,
			SearchMode searchMode) throws DocumentException {
		if (nameIndex == null) {
			throw new DocumentException(
					"This document does not support name indexes.");
		}
		QVocID qVocID = QVocID.fromQNm(coll.getTX(), coll.getDictionary(), 
				name);
		return nameIndex.open(coll.getTX(), this, indexNo, qVocID,
				searchMode, null);
	}

	@Override
	public Stream<? extends E> openCASIndex(int indexNo,
			Filter<? super E> filter, Atomic minSearchKey, Atomic maxSearchKey,
			boolean includeMin, boolean includeMax, SearchMode searchMode)
			throws DocumentException {
		if (casIndex == null) {
			throw new DocumentException(
					"This document does not support cas indexes.");
		}
		IndexDef indexDef = coll.get(Indexes.class).getIndexDef(indexNo);
		if ((indexDef == null) || (indexDef.getType() != IndexType.CAS)) {
			throw new DocumentException("CAS index %s not defined for %s",
					indexNo, coll);
		}
		Type type = indexDef.getContentType();
		return casIndex.open(coll.getTX(), this, indexNo, type, searchMode,
				minSearchKey, maxSearchKey, includeMin, includeMax, filter);
	}

	@Override
	public Stream<? extends E> openPathIndex(int indexNo,
			Filter<? super E> filter, SearchMode searchMode)
			throws DocumentException {
		if (pathIndex == null) {
			throw new DocumentException(
					"This document does not support path indexes.");
		}
		return pathIndex.open(coll.getTX(), this, indexNo, searchMode, filter);
	}
}