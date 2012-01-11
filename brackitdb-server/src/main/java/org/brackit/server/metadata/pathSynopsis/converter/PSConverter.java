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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.metadata.pathSynopsis.converter;

import java.util.Collection;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsis;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisNode;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.Tx;

/**
 * Transforms either an existing path synopsis tree into a list of bytes or a
 * list of bytes into a path synopsis tree. The path synopsis is loaded and
 * stored sequentially into an index.
 * 
 * @author Martin Meiringer
 * @author Karsten Schmidt
 * @author Matthias Burkhart
 * @author Sebastian Baechle
 * 
 */
public interface PSConverter {
	public PathSynopsis create(Tx tx, int cntNo) throws ServerException;

	/**
	 * Loads and returns the path synopsis stored as list of bytes starting at
	 * the given index position.
	 */
	public PathSynopsis load(Tx tx, DictionaryMgr dictionary, PageID idxNo)
			throws ServerException;

	/**
	 * Appends a single node to the underlying index structure
	 */
	public void append(Tx tx, PathSynopsis ps, PathSynopsisNode node)
			throws ServerException;

	/**
	 * Stores the psNodes on disk that are not yet stored.
	 */
	public void append(Tx tx, PathSynopsis ps,
			Collection<PathSynopsisNode> psNodes) throws ServerException;

	/**
	 * All pcrs that are not yet stored on disk and are smaller than maxPCR get
	 * stored, no matter from which transaction they were added.
	 */
	public void appendNodes(Tx ta, PathSynopsis ps, int maxPCR)
			throws ServerException;
}
