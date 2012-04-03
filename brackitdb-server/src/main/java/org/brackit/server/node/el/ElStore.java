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
package org.brackit.server.node.el;

import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgrFactory;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.el.index.ElBPlusIndex;
import org.brackit.server.node.index.cas.CASIndex;
import org.brackit.server.node.index.cas.impl.CASIndexImpl;
import org.brackit.server.node.index.name.NameIndex;
import org.brackit.server.node.index.name.impl.NameIndexImpl;
import org.brackit.server.node.index.path.PathIndex;
import org.brackit.server.node.index.path.impl.PathIndexImpl;
import org.brackit.server.tx.locking.services.MetaLockService;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElStore {
	protected final MetaLockService<?> mls;

	protected final DictionaryMgr dictionary;

	protected final Elndex index;

	protected final BufferMgr bufferMgr;

	protected final PathSynopsisMgrFactory pathSynopsisMgrFactory;

	protected final NameIndex<ElNode> nameIndex;

	protected final PathIndex<ElNode> pathIndex;

	protected final CASIndex<ElNode> casIndex;

	public ElStore(BufferMgr bufferMgr, DictionaryMgr dictionary,
			MetaLockService<?> mls) {
		this.dictionary = dictionary;
		this.index = new ElBPlusIndex(bufferMgr, new ElRecordAccess());
		this.bufferMgr = bufferMgr;
		this.pathSynopsisMgrFactory = new PathSynopsisMgrFactory(bufferMgr);
		this.nameIndex = new NameIndexImpl<ElNode>(bufferMgr);
		this.pathIndex = new PathIndexImpl<ElNode>(bufferMgr);
		this.casIndex = new CASIndexImpl<ElNode>(bufferMgr);
		this.mls = mls;
	}
}