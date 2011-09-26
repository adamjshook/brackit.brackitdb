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
package org.brackit.server.node.index;

import java.util.List;

import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.store.SearchMode;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;

/**
 * Controller class for index management and index access.
 * 
 * @author Sebastian Baechle
 * @author Karsten Schmidt
 * 
 */
public interface IndexController<E extends Node<E>> {

	/**
	 * Creates new indexes for the given document.
	 */
	public void createIndexes(IndexDef... indexDefinitions)
			throws DocumentException;

	/**
	 * Drops an index
	 */
	public void dropIndex(IndexDef indexDefinition) throws DocumentException;

	/**
	 * Opens a stream for the given content index positioned for the given
	 * search key.
	 */
	public Stream<? extends E> openContentIndex(int indexNo,
			Atomic minSearchKey, Atomic maxSearchKey, boolean includeMin,
			boolean includeMax, SearchMode searchMode) throws DocumentException;

	/**
	 * Opens a stream for the given name index positioned for the given
	 * search key.
	 */
	public Stream<? extends E> openNameIndex(int indexNo, QNm name,
			SearchMode searchMode) throws DocumentException;

	/**
	 * Opens a stream for the given path index positioned for the given search
	 * key.
	 */
	public Stream<? extends E> openPathIndex(int indexNo,
			Filter<? super E> filter, SearchMode searchMode)
			throws DocumentException;

	/**
	 * Opens a stream for the given CAS index positioned for the given search
	 * key.
	 */
	public Stream<? extends E> openCASIndex(int indexNo,
			Filter<? super E> filter, Atomic minSearchKey, Atomic maxSearchKey,
			boolean includeMin, boolean includeMax, SearchMode searchMode)
			throws DocumentException;

	public Filter<E> createPathFilter(List<Path<QNm>> paths)
			throws DocumentException;

	public Filter<E> createPathFilter(String... queryString)
			throws DocumentException;

	public Filter<E> createCASFilter(List<Path<QNm>> paths)
			throws DocumentException;

	public Filter<E> createCASFilter(String... queryString)
			throws DocumentException;
}
