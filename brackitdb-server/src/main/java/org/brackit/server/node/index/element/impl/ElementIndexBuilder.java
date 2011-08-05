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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.definition.Cluster;
import org.brackit.server.node.index.definition.IndexDef;
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
import org.brackit.server.tx.Tx;
import org.brackit.server.util.sort.MergeSort;
import org.brackit.server.util.sort.Sort;
import org.brackit.server.util.sort.SortItem;
import org.brackit.xquery.node.parser.DefaultListener;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
class ElementIndexBuilder<E extends TXNode<E>> extends DefaultListener<E>
		implements SubtreeListener<E> {
	private static final Logger log = Logger
			.getLogger(ElementIndexBuilder.class);

	private final Tx tx;

	private final Index index;

	private final int containerNo;

	private final IndexDef idxDefinition;

	private final IndexEncoderHelper<E> helper;

	private final NameDirectoyEncoderImpl nameDirectoryEncoder;

	private final HashMap<Integer, NodeReferenceIndex> nodeRefIndexes;

	private final boolean hasIncludes;

	private final Map<String, Cluster> includes;

	private final boolean hasExcludes;

	private final Set<String> excludes;

	private final class NodeReferenceIndex {
		int vocID;
		IndexEncoder<E> encoder;
		byte[] linkValue;
		Sort sorter;

		private NodeReferenceIndex(int vocID, IndexEncoder<E> encoder,
				Sort sorter) {
			super();
			this.vocID = vocID;
			this.encoder = encoder;
			this.sorter = sorter;
		}
	}

	public ElementIndexBuilder(Tx tx, Index index,
			IndexEncoderHelper<E> helper, int containerNo, IndexDef indexDef) {
		this.tx = tx;
		this.index = index;
		this.containerNo = containerNo;
		this.idxDefinition = indexDef;
		this.nameDirectoryEncoder = new NameDirectoyEncoderImpl();
		this.helper = helper;

		this.nodeRefIndexes = new HashMap<Integer, NodeReferenceIndex>();
		this.includes = indexDef.getIncluded();
		this.excludes = indexDef.getExcluded();
		hasIncludes = includes.size() > 0;
		hasExcludes = excludes.size() > 0;
	}

	@Override
	public void fail() throws DocumentException {
		for (NodeReferenceIndex index : nodeRefIndexes.values()) {
			index.sorter.errorCleanup();
		}
	}

	@Override
	public <T extends E> void startElement(T node) throws DocumentException {
		String name = node.getName();
		boolean included = (!hasIncludes) || includes.containsKey(name);
		boolean excluded = (!hasExcludes) || !excludes.contains(name);
		if ((!included) || (excluded)) {
			return;
		}
		int vocID = helper.getDictionary().translate(tx, name);
		NodeReferenceIndex index = nodeRefIndexes.get(vocID);

		if (index == null) {
			IndexEncoder<E> encoder = helper.getElementIndexEncoder();
			Sort sorter;

			// TODO: each sort stream allocates the full index size (here a
			// path synopsis look up may help to correct estimations)
			sorter = new MergeSort(encoder.getKeyType(), encoder.getValueType());
			index = new NodeReferenceIndex(vocID, encoder, sorter);
			nodeRefIndexes.put(vocID, index);
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
	public void end() throws DocumentException {
		IndexStatistics indexStatistics = null;

		for (NodeReferenceIndex nodeReferenceIndex : nodeRefIndexes.values()) {
			Stream<? extends SortItem> sorted = null;

			try {
				sorted = nodeReferenceIndex.sorter.sort();
				long start = System.nanoTime();
				PageID rootPageID = index.createIndex(tx, containerNo,
						nodeReferenceIndex.encoder.getKeyType(),
						nodeReferenceIndex.encoder.getValueType(), true, true,
						nodeReferenceIndex.encoder.getUnitID());

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
							+ nodeReferenceIndex.vocID + " with " + i
							+ " items took " + ((end - start) / 1000000)
							+ " ms");
				}

				nodeReferenceIndex.linkValue = nameDirectoryEncoder
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
			ArrayList<Integer> vocIDs = new ArrayList<Integer>(nodeRefIndexes
					.keySet());
			Collections.sort(vocIDs);

			long start = System.nanoTime();

			Field keyType = nameDirectoryEncoder.getKeyType();
			PageID nameDirectoryRootPageID = index.createIndex(tx, containerNo,
					keyType, nameDirectoryEncoder.getValueType(), false, true,
					-1);

			IndexIterator iterator = index.open(tx, nameDirectoryRootPageID,
					SearchMode.FIRST, null, null, OpenMode.LOAD);

			iterator.triggerStatistics();
			for (Integer vocID : vocIDs) {
				byte[] key = nameDirectoryEncoder.encodeKey(vocID);
				byte[] value = nodeRefIndexes.get(vocID).linkValue;
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
				log.debug("Building name directory index with " + vocIDs.size()
						+ " items took " + ((end - start) / 1000000) + " ms");
			}

			int id = nameDirectoryRootPageID.value();
			idxDefinition.createdAs(containerNo, id);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}
}
