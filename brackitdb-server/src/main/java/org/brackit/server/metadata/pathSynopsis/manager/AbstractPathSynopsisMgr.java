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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.log.Logger;
import org.brackit.server.metadata.pathSynopsis.PSSnapshotBuilder;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.converter.PSConverter;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.services.SimpleLockService;
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

	protected final DictionaryMgr dictionaryMgr;

	protected final PSConverter psc;

	protected final PathSynopsis ps;

	protected final SimpleLockService<Path<QNm>> ls;

	public AbstractPathSynopsisMgr(PSConverter psc,
			DictionaryMgr dictionaryMgr, PathSynopsis ps) {
		this.dictionaryMgr = dictionaryMgr;
		this.psc = psc;
		this.ps = ps;
		this.ls = new SimpleLockService<Path<QNm>>(
				PathSynopsis.class.getSimpleName());
	}

	@Override
	public PathSynopsisMgr spawnBulkPsManager(Tx tx) throws DocumentException {
		return new BulkPathSynopsisMgr(psc, dictionaryMgr, ps);
	}

	@Override
	public int getPathSynopsisNo() throws DocumentException {
		return ps.getIndexNumber();
	}

	@Override
	public PSNode getChild(Tx tx, int parentPcr, int vocID, byte kind)
			throws DocumentException {
		PathSynopsisNode node;

		synchronized (ps) {
			PathSynopsisNode parent = null;

			if (parentPcr != -1) {
				parent = getNode(ps, parentPcr);

				for (PathSynopsisNode child : parent.children) {
					if ((child.vocId == vocID) && (child.kind == kind)) {
						if (!child.isStored()) {
							addNodeToTaList(tx, ps, child);
						}
						return child;
					}
				}
			} else {
				for (PathSynopsisNode root : ps.getRoots()) {
					if ((root.vocId == vocID) && (root.kind == kind)) {
						return root;
					}
				}
			}

			String name = dictionaryMgr.resolve(tx, vocID);
			node = ps.getNewNode(name, vocID, kind, parent, 0);
			addNodeToTaList(tx, ps, node);
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

	public boolean isLeaf(Tx tx, int pcr) {
		synchronized (ps) {
			PathSynopsisNode psN = getNode(ps, pcr);

			for (PathSynopsisNode child : psN.getChildren()) {
				if (child.getKind() == Kind.ELEMENT.ID)
					return false;
			}
		}
		return true;
	}

	protected abstract void addNodeToTaList(Tx tx, PathSynopsis pathSynopsis,
			PathSynopsisNode node) throws DocumentException;

	@Override
	public String toString() {
		synchronized (ps) {
			return ps.toString();
		}
	}

	@Override
	public Set<Integer> getPCRsForPaths(Tx tx, Collection<Path<QNm>> expressions)
			throws DocumentException {
		HashSet<Integer> pcrs = new HashSet<Integer>();
		for (Path<QNm> path : expressions) {
			Set<Integer> pcrsForPath = match(tx, path);
			pcrs.addAll(pcrsForPath);
		}
		return pcrs;
	}

	@Override
	public Set<Integer> match(Tx tx, Path<QNm> path) throws DocumentException {
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
			Set<Integer> pcrsForPath = ps.getPCRsForPath(tx, path.toString());
			return pcrsForPath;
		}
	}

	@Override
	public PSNode get(Tx tx, int pcr) throws DocumentException {
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
	public PSNode getAncestor(Tx tx, int pcr, int level)
			throws DocumentException {
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
	public PSNode getAncestorOrParent(Tx tx, int pcr, int level)
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
	public void snapshot(Tx tx, PSSnapshotBuilder builder)
			throws DocumentException {
		synchronized (ps) {
			for (PathSynopsisNode root : ps.getRoots()) {
				buildPathSynopsisSnapshot(root, tx, builder);
			}
		}
	}

	private void buildPathSynopsisSnapshot(PathSynopsisNode root, Tx tx,
			PSSnapshotBuilder builder) throws DocumentException {
		builder.startNode(root.getPCR(), root.getURIVocID(), root
				.getPrefixVocID(), root.getLocalNameVocID(),
				new QNm(dictionaryMgr.resolve(tx, root.getURIVocID()),
						dictionaryMgr.resolve(tx, root.getPrefixVocID()),
						dictionaryMgr.resolve(tx, root.getLocalNameVocID())),
				root.getKind());

		for (PathSynopsisNode child : root.getChildren()) {
			buildPathSynopsisSnapshot(child, tx, builder);
		}

		builder.endNode();
	}

	@Override
	public int getMaxPCR() {
		synchronized (ps) {
			return ps.getMaxPCR();
		}
	}
}
