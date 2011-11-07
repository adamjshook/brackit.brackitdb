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
package org.brackit.server.node.index.name.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.definition.Cluster;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.external.IndexStatistics;
import org.brackit.server.node.index.name.impl.NameDirectoryEncoderImpl.QVocID;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.node.txnode.IndexEncoderHelper;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.sort.MergeSort;
import org.brackit.server.util.sort.Sort;
import org.brackit.server.util.sort.SortItem;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.DefaultListener;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.util.log.Logger;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
class NameIndexBuilder<E extends TXNode<E>> extends DefaultListener<E>
		implements SubtreeListener<E> {
	private static final Logger log = Logger
			.getLogger(NameIndexBuilder.class);

	private final Tx tx;

	private final Index index;

	private final int containerNo;

	private final IndexDef idxDefinition;

	private final IndexEncoderHelper<E> helper;

	private final NameDirectoryEncoderImpl nameDirEncoder;

	private final HashMap<QVocID, NodeReferenceIndex> nodeRefIndexes;

	private final Map<QNm, Cluster> includes;

	private final Set<QNm> excludes;

	private final class NodeReferenceIndex {
		QVocID qVocID;
		IndexEncoder<E> encoder;
		byte[] linkValue;
		Sort sorter;

		private NodeReferenceIndex(QVocID qVocID, IndexEncoder<E> encoder,
				Sort sorter) {
			super();
			this.qVocID = qVocID;
			this.encoder = encoder;
			this.sorter = sorter;
		}
	}

	public NameIndexBuilder(Tx tx, Index index,
			IndexEncoderHelper<E> helper, int containerNo, IndexDef indexDef) {
		this.tx = tx;
		this.index = index;
		this.containerNo = containerNo;
		this.idxDefinition = indexDef;
		this.nameDirEncoder = new NameDirectoryEncoderImpl();
		this.helper = helper;

		this.nodeRefIndexes = new HashMap<QVocID, NodeReferenceIndex>();
		this.includes = indexDef.getIncluded();
		this.excludes = indexDef.getExcluded();
	}

	@Override
	public void fail() throws DocumentException {
		for (NodeReferenceIndex index : nodeRefIndexes.values()) {
			index.sorter.errorCleanup();
		}
	}

	@Override
	public <T extends E> void startElement(T node) throws DocumentException {
		QNm name = node.getName();
		boolean included = (includes.isEmpty() || includes.containsKey(name));
		boolean excluded = (!excludes.isEmpty() && excludes.contains(name));
		if (!included || excluded) {
			return;
		}
		
		QVocID qVocID = QVocID.fromQNm(tx, helper.getDictionary(), name);
		NodeReferenceIndex index = nodeRefIndexes.get(qVocID);

		if (index == null) {
			IndexEncoder<E> encoder = helper.getNameIndexEncoder();
			Sort sorter;

			// TODO: each sort stream allocates the full index size (here a
			// path synopsis look up may help to correct estimations)
			sorter = new MergeSort(encoder.getKeyType(), 
					encoder.getValueType());
			index = new NodeReferenceIndex(qVocID, encoder, sorter);
			nodeRefIndexes.put(qVocID, index);
		}

		try {
			// build a record depending on clustering
			byte[] key = index.encoder.encodeKey(node);
			byte[] value = index.encoder.encodeValue(node);
			index.sorter.add(new SortItem(key, value));
		} catch (ServerException e) {
			throw new DocumentException(e);
		}
	}
	
	@Override
	public <T extends E> void attribute(T node) throws DocumentException {
		// TODO Listen for attributes and handle them appropriately
	}

	@Override
	public void end() throws DocumentException {
		IndexStatistics indexStatistics = null;

		for (NodeReferenceIndex nodeRefIndex : nodeRefIndexes.values()) {
			Stream<? extends SortItem> sorted = null;

			try {
				sorted = nodeRefIndex.sorter.sort();
				long start = System.nanoTime();
				PageID rootPageID = index.createIndex(tx, containerNo,
						nodeRefIndex.encoder.getKeyType(),
						nodeRefIndex.encoder.getValueType(), true, true,
						nodeRefIndex.encoder.getUnitID());

				IndexIterator iterator = index.open(tx, rootPageID,
						SearchMode.FIRST, null, null, OpenMode.LOAD);
				iterator.triggerStatistics();

				int i = 0;
				SortItem item;
				while ((item = sorted.next()) != null) {
					iterator.insert(item.getKey(), item.getValue());
					iterator.next();
					i++;
				}

				iterator.close();
				if (indexStatistics == null)
					indexStatistics = iterator.getStatistics();
				else
					indexStatistics.add(iterator.getStatistics(), false);

				long end = System.nanoTime();

				if (log.isDebugEnabled()) {
					log.debug("Building node reference index for vocID "
							+ nodeRefIndex.qVocID + " with " + i
							+ " items took " + ((end - start) / 1000000)
							+ " ms");
				}

				nodeRefIndex.linkValue = nameDirEncoder
						.encodeValue(rootPageID);
			} catch (ServerException e) {
				throw new DocumentException(e);
			} finally {
				if (sorted != null) {
					sorted.close();
				}
			}
		}

		try {
			List<QVocID> qVocIDs = 
				new ArrayList<QVocID>(nodeRefIndexes.keySet());
			Collections.sort(qVocIDs);

			long start = System.nanoTime();

			Field keyType = nameDirEncoder.getKeyType();
			PageID nameDirRootPageID = index.createIndex(tx, containerNo,
					keyType, nameDirEncoder.getValueType(), false, true, -1);

			IndexIterator iterator = index.open(tx, nameDirRootPageID,
					SearchMode.FIRST, null, null, OpenMode.LOAD);

			iterator.triggerStatistics();
			for (QVocID qVocID : qVocIDs) {
				byte[] key = nameDirEncoder.encodeKey(qVocID);
				byte[] value = nodeRefIndexes.get(qVocID).linkValue;
				iterator.insert(key, value);
				iterator.next();
			}
			iterator.close();

			if (indexStatistics == null) {
				indexStatistics = iterator.getStatistics();
			} else {
				indexStatistics.add(iterator.getStatistics(), true);
			}
			idxDefinition.setIndexStatistics(indexStatistics);

			long end = System.nanoTime();

			if (log.isDebugEnabled()) {
				log.debug("Building name directory index with " + qVocIDs.size()
						+ " items took " + ((end - start) / 1000000) + " ms");
			}

			int id = nameDirRootPageID.value();
			idxDefinition.createdAs(containerNo, id);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}
}
