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
package org.brackit.server.metadata.pathSynopsis.converter;

import java.util.Collection;

import org.apache.log4j.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsis;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisNode;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.xdm.DocumentException;

/**
 * 
 * @author Martin Meiringer
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 */
public class PSConverterImpl implements PSConverter {
	private static final Logger log = Logger.getLogger(PSConverterImpl.class);

	private final Index index;

	private final PSNodeBuilder builder;

	public PSConverterImpl(BufferMgr bufferMgr) {
		this.index = new BPlusIndex(bufferMgr);
		this.builder = new PSNodeBuilder();
	}

	@Override
	public PathSynopsis create(Tx tx, int cntNo) throws ServerException {
		try {
			PageID psIdxNo = index.createIndex(tx, cntNo, Field.UINTEGER,
					Field.PS_REC, true, true, -1);
			PathSynopsis ps = new PathSynopsis(psIdxNo.value());

			if (log.isDebugEnabled()) {
				log.debug(String
						.format("Created path synopsis no %s.", psIdxNo));
			}

			return ps;
		} catch (IndexAccessException e) {
			throw new ServerException(e);
		}
	}

	@Override
	public PathSynopsis load(Tx tx, DictionaryMgr dictionary, PageID psIdxNo)
			throws ServerException {
		try {
			if (log.isDebugEnabled()) {
				log.debug(String
						.format("Loading path synopsis no %s.", psIdxNo));
			}

			PathSynopsis ps = new PathSynopsis(psIdxNo.value());
			int maxPcr = 0;

			IndexIterator iterator = index.open(tx, psIdxNo, SearchMode.FIRST,
					null, null, OpenMode.READ);
			if (iterator.getKey() != null) {
				do {
					byte[] key = iterator.getKey();
					byte[] value = iterator.getValue();

					PathSynopsisNode node = builder.decode(tx, dictionary, ps,
							key, value);

					if (log.isDebugEnabled()) {
						log.debug(String.format(
								"Read PCR %s VocID %s ParentPCR %s", node
										.getPCR(), node.getVocID(), (node
										.getParent() != null) ? node
										.getParent().getPCR() : null));
					}

					// check for maximum pcr
					if (maxPcr < node.getPCR()) {
						maxPcr = node.getPCR();
					}

					node.setStored(true);

				} while (iterator.next());
			}
			iterator.close();

			ps.setPcr(maxPcr);
			ps.setMaxStoredPCR(maxPcr);
			return ps;
		} catch (DocumentException e) {
			throw new ServerException(e);
		}
	}

	@Override
	public void append(Tx tx, PathSynopsis ps, PathSynopsisNode node)
			throws ServerException {
		if (node.isStored()) {
			return;
		}

		int psIdxNo = ps.getIndexNumber();

		byte[] value = builder.encodeValue(node);
		byte[] key = builder.encodeKey(node);

		if (log.isDebugEnabled()) {
			log
					.debug(String
							.format(
									"Appending PCR %s VocID %s ParentPCR %s to path synopsis %s.",
									node.getPCR(), node.getVocID(), (node
											.getParent() != null) ? node
											.getParent().getPCR() : null,
									psIdxNo));
		}

		index.insertPersistent(tx, new PageID(psIdxNo), key, value);
		node.setStored(true);

		if (node.getPCR() >= ps.getMaxStoredPCR()) {
			ps.setMaxStoredPCR(node.getPCR());
		}
	}

	@Override
	public void appendNodes(Tx transaction, PathSynopsis ps, int maxPCR)
			throws ServerException {
		IndexIterator iterator = null;
		byte[] key = null;
		byte[] value = null;
		int psIdxNo = ps.getIndexNumber();

		if (log.isDebugEnabled()) {
			log.debug(String.format(
					"Appending nodes up to PCR %s to path synopsis no %s.",
					maxPCR, psIdxNo));
		}

		for (int currentPCR = ps.getMaxStoredPCR() + 1; currentPCR <= maxPCR; currentPCR++) {
			PathSynopsisNode currentNode = ps.getNodeByPcr(currentPCR);

			if (currentNode == null) {
				throw new ServerException(String.format(
						"No psNode for PCR %s found.", currentPCR));
			} else if (!currentNode.isStored()) {
				key = builder.encodeKey(currentNode);
				value = builder.encodeValue(currentNode);

				if (iterator == null) {
					iterator = index.open(transaction, new PageID(psIdxNo),
							SearchMode.GREATER_OR_EQUAL, key, null,
							OpenMode.BULK);
				} else {
					iterator.next();
				}

				if (log.isDebugEnabled()) {
					log
							.debug(String
									.format(
											"Appending PCR %s VocID %s ParentPCR %s to path synopsis %s.",
											currentNode.getPCR(),
											currentNode.getVocID(),
											(currentNode.getParent() != null) ? currentNode
													.getParent().getPCR()
													: null, psIdxNo));
				}

				iterator.insertPersistent(key, value);
				currentNode.setStored(true);
				ps.setMaxStoredPCR(currentPCR);
			}
		}

		if (iterator != null) {
			iterator.close();
		}
	}

	@Override
	public void append(Tx tx, PathSynopsis ps,
			Collection<PathSynopsisNode> psNodes) throws ServerException {
		IndexIterator indexIterator = null;
		int psIdxNo = ps.getIndexNumber();

		if (log.isDebugEnabled()) {
			log.debug(String.format("Inserting nodes in path synopsis no %s.",
					psIdxNo));
		}

		for (PathSynopsisNode node : psNodes) {
			if (!node.isStored()) {
				int maxStoredPCR = ps.getMaxStoredPCR();
				int currentPCR = node.getPCR();
				byte[] key = builder.encodeKey(node);
				byte[] value = builder.encodeValue(node);

				if (currentPCR < maxStoredPCR) {
					if (log.isDebugEnabled()) {
						log
								.debug(String
										.format(
												"Inserting PCR %s VocID %s ParentPCR %s in path synopsis %s.",
												node.getPCR(),
												node.getVocID(),
												(node.getParent() != null) ? node
														.getParent().getPCR()
														: null, psIdxNo));
					}

					index.insertPersistent(tx, new PageID(psIdxNo), key, value);
				} else {
					if (indexIterator == null) {
						indexIterator = index.open(tx, new PageID(psIdxNo),
								SearchMode.GREATER_OR_EQUAL, key, null,
								OpenMode.BULK);
					} else {
						indexIterator.next();
					}

					if (log.isDebugEnabled()) {
						log
								.debug(String
										.format(
												"Appending PCR %s VocID %s ParentPCR %s to path synopsis %s.",
												node.getPCR(),
												node.getVocID(),
												(node.getParent() != null) ? node
														.getParent().getPCR()
														: null, psIdxNo));
					}

					indexIterator.insertPersistent(key, value);
					ps.setMaxStoredPCR(currentPCR);
				}

				node.setStored(true);
			}
		}

		if (indexIterator != null) {
			indexIterator.close();
		}
	}
}
