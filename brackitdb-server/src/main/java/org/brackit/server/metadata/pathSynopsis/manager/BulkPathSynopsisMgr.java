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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.metadata.pathSynopsis.converter.PSConverter;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.PreCommitHook;
import org.brackit.server.tx.Tx;

/**
 * Transaction-private path synopsis manager for a specific document; optimized
 * for fast and transaction safe bulk methods like, e.g., document storage or
 * reconstruction.
 * 
 * @author Sebastian Baechle
 */
public class BulkPathSynopsisMgr extends AbstractPathSynopsisMgr implements
		PreCommitHook {
	private static final Logger log = Logger
			.getLogger(BulkPathSynopsisMgr.class);

	private final PathSynopsis ps;

	private PathSynopsisNode lruParentNode;

	private int maxPCR;

	public BulkPathSynopsisMgr(Tx tx, PSConverter psc,
			DictionaryMgr dictionaryMgr, PathSynopsis ps) {
		super(tx, psc, dictionaryMgr, ps);
		this.ps = ps;
		this.maxPCR = -1;
	}

	@Override
	protected PathSynopsisNode getNode(PathSynopsis ps, int parentPcr) {
		if ((lruParentNode != null) && (lruParentNode.getPCR() == parentPcr)) {
			return lruParentNode;
		} else {
			PathSynopsisNode parentNode = ps.getNodeByPcr(parentPcr);

			if (parentNode != null) {
				lruParentNode = parentNode;
			}

			return parentNode;
		}
	}

	@Override
	protected void addNodeToTaList(PathSynopsis pathSynopsis, PathSynopsisNode node) {
		if (maxPCR == -1) {
			tx.addPreCommitHook(this);
		}
		int b = node.getPCR();

		maxPCR = (maxPCR >= b) ? maxPCR : b;
	}

	@Override
	public void prepare(Tx transaction) throws ServerException {
		synchronized (ps) {
			psc.appendNodes(transaction, ps, maxPCR);
		}
	}

	@Override
	public void abort(Tx transaction) {
	}

	@Override
	public PathSynopsisMgr copyFor(Tx tx) {
		if (this.tx.equals(tx)) {
			return this;
		}
		return new BulkPathSynopsisMgr(tx, psc, dictionaryMgr, ps);
	}
}