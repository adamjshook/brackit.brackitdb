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
package org.brackit.server.node.index.path.impl;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.parser.DefaultListener;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;

/**
 * @author Sebastian Baechle
 * 
 */
public class PathIndexListener<E extends Node<E>> extends DefaultListener<E>
		implements SubtreeListener<E> {
	private static final Logger log = Logger.getLogger(PathIndexListener.class);

	private final Index index;
	private final ListenMode mode;
	private final IndexEncoder<E> encoder;
	private final PageID indexNo;
	private final Filter<? super E> filter;
	private final Tx tx;

	public PathIndexListener(Tx tx, Index index, IndexDef indexDef,
			ListenMode mode, IndexEncoder<E> encoder, Filter<? super E> filter) {
		this.tx = tx;
		this.index = index;
		this.mode = mode;

		this.indexNo = new PageID(indexDef.getID());
		this.encoder = encoder;
		this.filter = filter;
	}

	@Override
	public <T extends E> void startElement(T node) throws DocumentException {
		switch (mode) {
		case INSERT:
			insert(node);
			break;
		case DELETE:
			delete(node);
		default:
			// ignore text
		}
	}

	@Override
	public <T extends E> void attribute(T node) throws DocumentException {
		switch (mode) {
		case INSERT:
			insert(node);
			break;
		case DELETE:
			delete(node);
		default:
			// ignore text
		}
	}

	private void insert(E node) throws DocumentException {
		if ((filter != null) && (filter.filter(node))) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Inserting (%s) in path index %s.",
						node, indexNo));
			}

			byte[] key = encoder.encodeKey(node);
			byte[] value = encoder.encodeValue(node);

			try {
				index.insert(tx, indexNo, key, value);
			} catch (IndexAccessException e) {
				throw new DocumentException(e);
			}
		}
	}

	private void delete(E node) throws DocumentException {
		if ((filter != null) && (filter.filter(node))) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Deleting (%s) from path index %s.",
						node, indexNo));
			}

			byte[] key = encoder.encodeKey(node);
			byte[] value = encoder.encodeValue(node);

			try {
				index.delete(tx, indexNo, key, value);
			} catch (IndexAccessException e) {
				throw new DocumentException(e);
			}
		}
	}
}