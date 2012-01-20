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
package org.brackit.server.metadata.pathSynopsis.manager;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.brackit.server.metadata.pathSynopsis.NsMapping;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.PSSnapshotBuilder;
import org.brackit.server.metadata.pathSynopsis.converter.PSConverter;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.services.SimpleLockService;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.log.Logger;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * Abstract base for path synopsis managers.
 * 
 * @author Karsten Schmidt
 * @author Martin Meiringer
 * @author Matthias Burkhart
 * @author Sebastian Baechle
 * 
 */
public abstract class AbstractPathSynopsisMgr implements PathSynopsisMgr {

	private static final Logger log = Logger
			.getLogger(AbstractPathSynopsisMgr.class);

	protected final Tx tx;

	protected final DictionaryMgr dictionaryMgr;

	protected final PSConverter psc;

	protected final PathSynopsis ps;

	protected final SimpleLockService<Path<QNm>> ls;

	public AbstractPathSynopsisMgr(Tx tx, PSConverter psc,
			DictionaryMgr dictionaryMgr, PathSynopsis ps) {
		this.tx = tx;
		this.dictionaryMgr = dictionaryMgr;
		this.psc = psc;
		this.ps = ps;
		this.ls = new SimpleLockService<Path<QNm>>(PathSynopsis.class
				.getSimpleName());
	}

	@Override
	public PathSynopsisMgr spawnBulkPsManager() throws DocumentException {
		return new BulkPathSynopsisMgr(tx, psc, dictionaryMgr, ps);
	}

	@Override
	public int getPathSynopsisNo() throws DocumentException {
		return ps.getIndexNumber();
	}

	@Override
	public SubtreeCopyResult copySubtree(int rootPCR,
			NsMapping newNsMapping, QNm newName) throws DocumentException {

		// for changing the NsMapping, the whole subtree in the path synopsis
		// has to be copied
		// this map contains the PCR mapping from the PCRs in the old subtree to
		// the PCRs in the new subtree
		Map<Integer, Integer> pcrMap = new HashMap<Integer, Integer>();
		// to traverse the path synopsis, this queue is used to indicate which
		// nodes are still left to be copied
		Queue<PathSynopsisNode> nodesToCopy = new LinkedList<PathSynopsisNode>();
		
		PSNode newRoot = null;

		synchronized (ps) {
			PathSynopsisNode newNode;
			PathSynopsisNode oldNode;

			// start with the root node
			nodesToCopy.add(getNode(ps, rootPCR));

			boolean firstRun = true;

			while ((oldNode = nodesToCopy.poll()) != null) {

				int newUriVocID = oldNode.getURIVocID();
				int prefixVocID = oldNode.getPrefixVocID();
				int localNameVocID = oldNode.getLocalNameVocID();
				
				if (firstRun) {
					// QNm of root node might be changed
					newUriVocID = dictionaryMgr.translate(tx, newName.nsURI);
					prefixVocID = dictionaryMgr.translate(tx, newName.prefix);
					localNameVocID = dictionaryMgr.translate(tx, newName.localName);
				}

				// due to the new mapping, the uriVocID may have been changed
				Integer changedUriVocID = newNsMapping.resolve(prefixVocID);
				if (changedUriVocID != null) {
					newUriVocID = changedUriVocID;
				}

				// determine parent of new node
				PathSynopsisNode oldParent = oldNode.getParent();
				int newParentPCR = -1;
				if (oldParent != null) {
					// map old parent to new parent
					Integer mappedParentPCR = pcrMap.get(oldParent.getPCR());
					if (mappedParentPCR == null) {
						// first loop -> new node has the same parent
						newParentPCR = oldParent.getPCR();
					} else {
						newParentPCR = mappedParentPCR;
					}
				}
				// load parent
				PathSynopsisNode newParent = getNode(ps, newParentPCR);

				// determine new NsMapping
				NsMapping nsMapping = firstRun ? newNsMapping : oldNode
						.getNsMapping().copy();

				// determine new QName
				String URI = (newUriVocID != -1 ? dictionaryMgr.resolve(tx,
						newUriVocID) : "");
				String prefix = oldNode.getName().getPrefix();
				String localName = oldNode.getName().getLocalName();
				newNode = ps.getNewNode(new QNm(URI, prefix, localName),
						newUriVocID, prefixVocID, localNameVocID, oldNode
								.getKind(), nsMapping, newParent, 0);
				addNodeToTaList(ps, newNode);
				
				if (firstRun) {
					newRoot = newNode;
				}

				// put new PCR mapping
				pcrMap.put(oldNode.getPCR(), newNode.getPCR());

				// add children into the queue
				for (PathSynopsisNode child : oldNode.getChildren()) {
					nodesToCopy.add(child);
				}

				firstRun = false;
			}
		}

		return new SubtreeCopyResult(newRoot, pcrMap);

		// try {
		// Path<QNm> path = null;
		//
		// for (Path<QNm> pattern : ls.getLockedResources()) {
		// if (path == null) {
		// path = node.getPath();
		// }
		//
		// if (pattern.matches(path)) {
		// ls.lock(tx, pattern, LockClass.INSTANT_DURATION, false);
		// }
		// }
		//
		// return node;
		// } catch (Exception e) {
		// throw new DocumentException(e);
		// }
	}
	
	@Override
	public PSNode getChildIfExists(int parentPcr, QNm name, byte kind,
			NsMapping nsMapping) throws DocumentException {

		int uriVocID = 0;
		if (name.nsURI.isEmpty()) {
			uriVocID = -1;
		} else {
			uriVocID = dictionaryMgr.translateIfExists(name.nsURI);
			if (uriVocID < 0) {
				// PS node can not exist
				return null;
			}
		}
		
		int prefixVocID = 0;
		if (name.prefix == null) {
			prefixVocID = -1;
		} else {
			prefixVocID = dictionaryMgr.translateIfExists(name.prefix);
			if (prefixVocID < 0) {
				// PS node can not exist
				return null;
			}
		}
		
		int localNameVocID = dictionaryMgr.translateIfExists(name.localName);
		if (localNameVocID < 0) {
			// PS node can not exist
			return null;
		}

		synchronized (ps) {
			PathSynopsisNode parent = null;

			if (parentPcr != -1) {
				parent = getNode(ps, parentPcr);

				for (PathSynopsisNode child : parent.children) {
					if ((child.uriVocID == uriVocID)
							&& (child.prefixVocID == prefixVocID)
							&& (child.localNameVocID == localNameVocID)
							&& (child.kind == kind)
							&& (child.hasNsMapping(nsMapping))) {
						if (!child.isStored()) {
							addNodeToTaList(ps, child);
						}
						return child;
					}
				}
			} else {
				for (PathSynopsisNode root : ps.getRoots()) {
					if ((root.uriVocID == uriVocID)
							&& (root.prefixVocID == prefixVocID)
							&& (root.localNameVocID == localNameVocID)
							&& (root.kind == kind)
							&& (root.hasNsMapping(nsMapping))) {
						return root;
					}
				}
			}

			return null;
		}
	}

	@Override
	public PSNode getChild(int parentPcr, QNm name, byte kind,
			NsMapping nsMapping) throws DocumentException {
		PathSynopsisNode node;

		int uriVocID = (name.nsURI.isEmpty() ? -1 : dictionaryMgr.translate(
				getTX(), name.nsURI));
		int prefixVocID = (name.prefix == null ? -1 : dictionaryMgr.translate(
				getTX(), name.prefix));
		int localNameVocID = dictionaryMgr.translate(getTX(), name.localName);

		synchronized (ps) {
			PathSynopsisNode parent = null;

			if (parentPcr != -1) {
				parent = getNode(ps, parentPcr);

				for (PathSynopsisNode child : parent.children) {
					if ((child.uriVocID == uriVocID)
							&& (child.prefixVocID == prefixVocID)
							&& (child.localNameVocID == localNameVocID)
							&& (child.kind == kind)
							&& (child.hasNsMapping(nsMapping))) {
						if (!child.isStored()) {
							addNodeToTaList(ps, child);
						}
						return child;
					}
				}
			} else {
				for (PathSynopsisNode root : ps.getRoots()) {
					if ((root.uriVocID == uriVocID)
							&& (root.prefixVocID == prefixVocID)
							&& (root.localNameVocID == localNameVocID)
							&& (root.kind == kind)
							&& (root.hasNsMapping(nsMapping))) {
						return root;
					}
				}
			}

			node = ps.getNewNode(name, uriVocID,
					prefixVocID, localNameVocID, kind, nsMapping, parent, 0);
			addNodeToTaList(ps, node);
		}

		try {
			Path<QNm> path = null;

			for (Path<QNm> pattern : ls.getLockedResources()) {
				if (path == null) {
					path = node.getPath();
				}

				if (pattern.matches(path)) {
					ls.lock(tx, pattern, LockClass.INSTANT_DURATION, false);
				}
			}

			return node;
		} catch (Exception e) {
			throw new DocumentException(e);
		}
	}

	protected PathSynopsisNode getNode(PathSynopsis ps, int pcr) {
		return ps.getNodeByPcr(pcr);
	}

	public boolean isLeaf(int pcr) {
		synchronized (ps) {
			PathSynopsisNode psN = getNode(ps, pcr);

			for (PathSynopsisNode child : psN.getChildren()) {
				if (child.getKind() == Kind.ELEMENT.ID)
					return false;
			}
		}
		return true;
	}

	protected abstract void addNodeToTaList(PathSynopsis pathSynopsis,
			PathSynopsisNode node) throws DocumentException;

	@Override
	public String toString() {
		synchronized (ps) {
			return ps.toString();
		}
	}

	@Override
	public Set<Integer> getPCRsForPaths(Collection<Path<QNm>> expressions)
			throws DocumentException {
		HashSet<Integer> pcrs = new HashSet<Integer>();
		for (Path<QNm> path : expressions) {
			Set<Integer> pcrsForPath = match(path);
			pcrs.addAll(pcrsForPath);
		}
		return pcrs;
	}

	@Override
	public Set<Integer> match(Path<QNm> path) throws DocumentException {
		try {
			try {
				ls.lock(tx, path, LockClass.COMMIT_DURATION, true);
			} catch (TxException e) {
				throw new DocumentException(e);
			}
		} catch (Exception e) {
			log.error(e); // TODO
			// https://lgis-devel.informatik.uni-kl.de:3276/XTC/trac/ticket/115
			throw new DocumentException(e);
		}

		synchronized (ps) {
			Set<Integer> pcrsForPath = ps.getPCRsForPath(tx, path);
			return pcrsForPath;
		}
	}

	@Override
	public PSNode get(int pcr) throws DocumentException {
		synchronized (ps) {
			PathSynopsisNode psN = ps.getNodeByPcr(pcr);

			if (psN == null) {
				throw new DocumentException(
						"Pathsynopsis node %s does not exist.", pcr);
			}

			return psN;
		}
	}

	@Override
	public PSNode getAncestor(int pcr, int level) throws DocumentException {
		synchronized (ps) {
			PathSynopsisNode psN = getNode(ps, pcr);

			if (level < 0) {
				throw new DocumentException("Invalid level: %s", level);
			}

			if (psN.getLevel() < level) {
				throw new DocumentException(
						"Requested level %s is higher than the level of PCR %s: %s",
						level, pcr, psN.getLevel());
			}

			while (psN.getLevel() > level) {
				psN = psN.getParent();
			}

			return psN;

		}
	}

	@Override
	public PSNode getAncestorOrParent(int pcr, int level)
			throws DocumentException {
		synchronized (ps) {
			PathSynopsisNode psN = getNode(ps, pcr);

			if (level < 0) {
				throw new DocumentException("Invalid level: %s", level);
			}

			if (psN == null) {
				throw new DocumentException("Invalid pcr: %s", pcr);
			}

			int nodeLevel = psN.getLevel();

			if (nodeLevel + 1 == level) {
				// anticipate a text node
				// requesting its
				// parent pcr
				return psN;
			}

			if (nodeLevel < level) {
				throw new DocumentException(
						"Requested level %s is higher than the level of PCR %s: %s",
						level, pcr, psN.getLevel());
			}

			for (int i = nodeLevel; i > level; i--) {
				psN = psN.getParent();
			}

			return psN;

		}
	}

	@Override
	public void snapshot(PSSnapshotBuilder builder) throws DocumentException {
		synchronized (ps) {
			for (PathSynopsisNode root : ps.getRoots()) {
				buildPathSynopsisSnapshot(root, builder);
			}
		}
	}

	private void buildPathSynopsisSnapshot(PathSynopsisNode root,
			PSSnapshotBuilder builder) throws DocumentException {
		builder
				.startNode(root.getPCR(), root.getURIVocID(), root
						.getPrefixVocID(), root.getLocalNameVocID(), new QNm(
						dictionaryMgr.resolve(tx, root.getURIVocID()),
						dictionaryMgr.resolve(tx, root.getPrefixVocID()),
						dictionaryMgr.resolve(tx, root.getLocalNameVocID())),
						root.getKind());

		for (PathSynopsisNode child : root.getChildren()) {
			buildPathSynopsisSnapshot(child, builder);
		}

		builder.endNode();
	}

	@Override
	public int getMaxPCR() {
		synchronized (ps) {
			return ps.getMaxPCR();
		}
	}

	@Override
	public Tx getTX() {
		return tx;
	}
}
