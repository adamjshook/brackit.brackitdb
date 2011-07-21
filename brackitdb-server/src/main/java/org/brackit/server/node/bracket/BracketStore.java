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
package org.brackit.server.node.bracket;

import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgrFactory;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.index.cas.CASIndex;
import org.brackit.server.node.index.cas.impl.CASIndexImpl;
import org.brackit.server.node.index.element.ElementIndex;
import org.brackit.server.node.index.element.impl.ElementIndexImpl;
import org.brackit.server.node.index.path.PathIndex;
import org.brackit.server.node.index.path.impl.PathIndexImpl;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.bracket.BracketIndex;
import org.brackit.server.store.index.bracket.BracketIndexImpl;
import org.brackit.server.tx.locking.services.MetaLockService;


/**
 * @author Martin Hiller
 *
 */
public class BracketStore {
	
	protected final MetaLockService<?> mls;

	protected final DictionaryMgr dictionary;

	protected final BracketIndex index;
	
	protected final Index stdIndex;

	protected final BufferMgr bufferMgr;

	protected final PathSynopsisMgrFactory pathSynopsisMgrFactory;

	protected final ElementIndex<BracketNode> elementIndex;

	protected final PathIndex<BracketNode> pathIndex;

	protected final CASIndex<BracketNode> casIndex;

	public BracketStore(BufferMgr bufferMgr, DictionaryMgr dictionary,
			MetaLockService<?> mls) {
		this.dictionary = dictionary;
		this.index = new BracketIndexImpl(bufferMgr);
		this.stdIndex = new BPlusIndex(bufferMgr);
		this.bufferMgr = bufferMgr;
		this.pathSynopsisMgrFactory = new PathSynopsisMgrFactory(bufferMgr);
		this.elementIndex = new ElementIndexImpl<BracketNode>(bufferMgr);
		this.pathIndex = new PathIndexImpl<BracketNode>(bufferMgr);
		this.casIndex = new CASIndexImpl<BracketNode>(bufferMgr);
		this.mls = mls;
	}
}
