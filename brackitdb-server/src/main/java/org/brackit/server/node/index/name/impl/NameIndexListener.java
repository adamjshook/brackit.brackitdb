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
package org.brackit.server.node.index.name.impl;

import java.util.Map;
import java.util.Set;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.cas.impl.CASIndexListener;
import org.brackit.server.node.index.definition.Cluster;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.name.impl.NameDirectoryEncoderImpl.QVocID;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.node.txnode.IndexEncoderHelper;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.DefaultListener;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;

/**
 * @author Sebastian Baechle
 * 
 */
public class NameIndexListener<E extends TXNode<E>> extends
		DefaultListener<E> implements SubtreeListener<E> {
	private static final Logger log = Logger.getLogger(CASIndexListener.class);

	protected final Tx tx;
	protected final Index index;
	protected final IndexDef indexDef;
	private final ListenMode mode;
	private final PageID indexNo;
	private final NameDirectoryEncoder nameDirectoryEncoder;
	protected final IndexEncoderHelper<E> helper;

	private final Map<QNm, Cluster> includes;

	private final Set<QNm> excludes;

	public NameIndexListener(Tx tx, Index index,
			IndexEncoderHelper<E> helper, IndexDef indexDef, ListenMode mode) {
		this.tx = tx;
		this.index = index;
		this.indexDef = indexDef;
		this.mode = mode;
		this.helper = helper;

		this.indexNo = new PageID(indexDef.getID());
		this.nameDirectoryEncoder = new NameDirectoryEncoderImpl();
		this.includes = indexDef.getIncluded();
		this.excludes = indexDef.getExcluded();
	}

	@Override
	public <T extends E> void startElement(T node) throws DocumentException {
		switch (mode) {
		case INSERT:
			insertElement(node);
			break;
		case DELETE:
			deleteElement(node);
			break;
		default:
			// ignore text
		}
	}

	public <T extends E> void attribute(T node) throws DocumentException {
		// TODO Listen for attributes and handle them appropriately
	}
	
	protected <T extends E> void insertElement(T node) throws DocumentException {
		QNm name = node.getName();
		boolean included = (includes.isEmpty() || includes.containsKey(name));
		boolean excluded = (!excludes.isEmpty() && excludes.contains(name));
		if (!included || excluded) {
			return;
		}
		
		QVocID qVocID = QVocID.fromQNm(tx, helper.getDictionary(), name);
		try {
			PageID nodePageID;
			IndexEncoder<E> encoder = helper.getNameIndexEncoder();
			byte[] nameDirectoryKey = nameDirectoryEncoder.encodeKey(qVocID);
			byte[] nameDirectoryValue = index.read(tx, indexNo,
					nameDirectoryKey);

			if (nameDirectoryValue == null) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Creating new node reference " +
						"index for qVocID %s in index %s.", qVocID, indexNo));
				}

				nodePageID = index.createIndex(tx, indexNo.getContainerNo(),
						encoder.getKeyType(), encoder.getValueType(), true,
						true);

				index.insert(tx, indexNo, nameDirectoryKey,
						nameDirectoryEncoder.encodeValue(nodePageID));
			} else {
				nodePageID = nameDirectoryEncoder
						.decodePageID(nameDirectoryValue);
			}

			if (log.isDebugEnabled()) {
				log.debug(String.format("Inserting (%s, %s) in name " +
						"index %s.", qVocID, node.getDeweyID(), indexNo));
			}

			byte[] key = encoder.encodeKey(node);
			byte[] value = encoder.encodeValue(node);
			index.insert(tx, nodePageID, key, value);
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	protected <T extends E> void deleteElement(T node) throws DocumentException {
		QNm name = node.getName();
		boolean included = (includes.isEmpty() || includes.containsKey(name));
		boolean excluded = (!excludes.isEmpty() && excludes.contains(name));
		if (!included || excluded) {
			return;
		}
		
		QVocID qVocID = QVocID.fromQNm(tx, helper.getDictionary(), name);
		try {
			PageID nodePageID;
			IndexEncoder<E> encoder = helper.getNameIndexEncoder();
			byte[] nameDirectoryKey = nameDirectoryEncoder.encodeKey(qVocID);
			byte[] nameDirectoryValue = index.read(tx, indexNo,
					nameDirectoryKey);

			if (nameDirectoryValue == null) {
				if (log.isInfoEnabled()) {
					log.warn(String.format("No valid node reference index " +
						"for qVocID %s in index %s found.", qVocID, indexNo));
				}
			} else {
				nodePageID = nameDirectoryEncoder
						.decodePageID(nameDirectoryValue);
				if (log.isDebugEnabled()) {
					log.debug(String.format("Deleting (%s, %s) from name " +
							"index %s.", qVocID, node.getDeweyID(), indexNo));
				}
				byte[] key = encoder.encodeKey(node);
				byte[] value = encoder.encodeValue(node);
				index.delete(tx, nodePageID, key, value);
			}
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}
}