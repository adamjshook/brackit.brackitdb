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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Sebastian Baechle
 * 
 */
public class NameIndexIteratorImpl<E extends Node<E>> implements Stream<E> {
	private static final Logger log = Logger
			.getLogger(NameIndexIteratorImpl.class);

	private IndexIterator iterator;

	private final IndexEncoder<E> encoder;

	private boolean first = true;

	public NameIndexIteratorImpl(IndexIterator iterator,
			IndexEncoder<E> encoder) {
		this.iterator = iterator;
		this.encoder = encoder;
	}

	@Override
	public E next() throws DocumentException {
		if (iterator == null) {
			return null;
		}

		try {
			if (!first) {
				iterator.next();
			} else {
				first = false;
			}

			byte[] key = iterator.getKey();
			if (key == null) {
				return null;
			}

			byte[] value = iterator.getValue();

			return encoder.decode(key, value);
		} catch (DocumentException e) {
			close();
			throw e;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void close() {
		if (iterator != null) {
			iterator.close();
			iterator = null;
		}
	}
}