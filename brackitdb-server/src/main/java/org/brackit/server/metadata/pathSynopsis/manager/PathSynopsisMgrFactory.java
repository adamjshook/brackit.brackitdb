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
package org.brackit.server.metadata.pathSynopsis.manager;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.pathSynopsis.converter.PSConverter;
import org.brackit.server.metadata.pathSynopsis.converter.PSConverterImpl;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.xdm.DocumentException;

/**
 * @author Sebastian Baechle
 * 
 */
public class PathSynopsisMgrFactory {
	private final PSConverter psc;

	public PathSynopsisMgrFactory(BufferMgr bufferMgr) {
		this.psc = new PSConverterImpl(bufferMgr);
	}

	public PathSynopsisMgr load(Tx tx, DictionaryMgr dictionary,
			PageID psID) throws DocumentException {
		try {
			return new PathSynopsisMgr05(tx, psc, dictionary, psc.load(tx,
					dictionary, psID));
		} catch (ServerException e) {
			throw new DocumentException(e);
		}
	}

	public PathSynopsisMgr create(Tx tx, DictionaryMgr dictionary,
			int containerID) throws DocumentException {
		try {
			return new PathSynopsisMgr05(tx, psc, dictionary, psc.create(
					tx, containerID));
		} catch (ServerException e) {
			throw new DocumentException(e);
		}
	}
}
