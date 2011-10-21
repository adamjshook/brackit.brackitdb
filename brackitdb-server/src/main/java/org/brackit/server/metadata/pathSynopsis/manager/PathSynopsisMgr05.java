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
package org.brackit.server.metadata.pathSynopsis.manager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.metadata.pathSynopsis.converter.PSConverter;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.PreCommitHook;
import org.brackit.server.tx.Tx;

/**
 * Collects for each path synopsis the maximal PCR that was created and flushes
 * the path synopsis up to this PCR before commit.
 * 
 * @author Karsten Schmidt
 * @author Martin Meiringer
 * @author Matthias Burkhart
 * @author Sebastian Baechle
 * 
 */
public class PathSynopsisMgr05 extends AbstractPathSynopsisMgr implements
		PreCommitHook {
	private static final Logger log = Logger.getLogger(PathSynopsisMgr05.class);

	private final ConcurrentHashMap<Tx, HashMap<PathSynopsis, Integer>> psNodesByTA;

	public PathSynopsisMgr05(PSConverter psc, DictionaryMgr dictionaryMgr,
			PathSynopsis ps) {
		super(psc, dictionaryMgr, ps);
		psNodesByTA = new ConcurrentHashMap<Tx, HashMap<PathSynopsis, Integer>>();
	}

	@Override
	protected void addNodeToTaList(Tx transaction, PathSynopsis pathSynopsis,
			PathSynopsisNode node) {
		// gets the IdxNo's of the PathSynopsis
		// that are affected by the transaction
		HashMap<PathSynopsis, Integer> psByTA = psNodesByTA.get(transaction);

		if (psByTA == null) {
			// the first time the transaction
			// affects the PathSynopsis
			psByTA = new HashMap<PathSynopsis, Integer>();
			psNodesByTA.put(transaction, psByTA);
			transaction.addPreCommitHook(this);
		}

		// gets the List of PathSynopsisNodes that are affected
		Integer maxPcrByPS = psByTA.get(pathSynopsis);

		if ((maxPcrByPS == null) || (node.getPCR() > maxPcrByPS)) {
			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Registering maxPCR %s for path synopsis %s", node
								.getPCR(), pathSynopsis.getIndexNumber()));
			}

			psByTA.put(pathSynopsis, node.getPCR());
			maxPcrByPS = node.getPCR();
		}
	}

	@Override
	public void prepare(Tx transaction) throws ServerException {
		HashMap<PathSynopsis, Integer> changedPS = psNodesByTA.get(transaction);

		if (changedPS != null) {
			Set<Entry<PathSynopsis, Integer>> tmpSet = changedPS.entrySet();
			Iterator<Entry<PathSynopsis, Integer>> it = tmpSet.iterator();

			while (it.hasNext()) {
				Entry<PathSynopsis, Integer> psEntry = it.next();
				PathSynopsis ps = psEntry.getKey();
				Integer maxPCR = psEntry.getValue();

				synchronized (ps) {
					psc.appendNodes(transaction, ps, maxPCR);
				}

				psNodesByTA.remove(transaction);
			}
		}
	}

	@Override
	public void abort(Tx transaction) {
		psNodesByTA.remove(transaction);
	}
}
