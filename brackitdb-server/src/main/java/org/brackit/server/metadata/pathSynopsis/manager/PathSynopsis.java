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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.brackit.server.metadata.pathSynopsis.NsMapping;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.util.path.PathException;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * Realizes the path synopsis as a tree.
 * 
 * A path synopsis is a collection of path classes of an xml collection. It is
 * organized as multiple trees counting the number of the same classes in each
 * node. A path class is a collection of the same types of parent-child related
 * xml nodes. The names of the xml nodes (attributes respectively) are stored as
 * ids referencing the actual names. These can be resolved in the vocabulary
 * manager.
 * 
 * Every node has a pcr.
 * 
 * @author Martin Meiringer
 * @author Karsten Schmidt
 * @author Matthias Burkhart
 * @author Sebastian Baechle
 */
public class PathSynopsis {

	// insert new nodes as children of this node
	private PathSynopsisNode[] roots;

	// maps pcrs to the corresponding nodes
	private PathSynopsisNode[] pcrTable;

	// pcr count
	private int pcr = 0;

	// max pcr stored on disk. Needed for optimized storage of the path synopsis
	// nodes at transaction commit
	private int maxStoredPCR = 0;

	private int psIdxNo = -1;

	// needs to be cleared when a path is added or removed !!
	private HashMap<Path<QNm>, Set<Integer>> pathCache = new HashMap<Path<QNm>, Set<Integer>>();

	public PathSynopsis(int idxNo) {
		this.psIdxNo = idxNo;
		this.roots = new PathSynopsisNode[1];
		this.pcrTable = new PathSynopsisNode[20];
	}

	public PathSynopsisNode getNewNode(int pcr, QNm name, int uriVocID,
			int prefixVocID, int localNameVocID, byte kind,
			NsMapping nsMapping, PathSynopsisNode parent) {
		pathCache.clear();
		PathSynopsisNode psN = new PathSynopsisNode(uriVocID, prefixVocID,
				localNameVocID, pcr, name, kind, nsMapping, parent, this);

		if (pcr > this.pcr) {
			this.pcr++;
		}

		while (pcr >= pcrTable.length) {
			PathSynopsisNode[] newPcrs = new PathSynopsisNode[(pcrTable.length * 3) / 2 + 1];
			System.arraycopy(pcrTable, 0, newPcrs, 0, pcrTable.length);
			pcrTable = newPcrs;
		}
		pcrTable[pcr] = psN;

		if (parent == null) {
			int i = 0;
			while ((i < roots.length) && (roots[i] != null)) {
				i++;
			}

			if (i == roots.length) {
				PathSynopsisNode[] newRoots = new PathSynopsisNode[(roots.length * 3) / 2 + 1];
				System.arraycopy(roots, 0, newRoots, 0, roots.length);
				roots = newRoots;
			}

			roots[i] = psN;
		}

		return psN;
	}

	public PathSynopsisNode getNewNode(QNm name, int uriVocID, int prefixVocID,
			int localNameVocID, byte kind, NsMapping nsMapping,
			PathSynopsisNode parent, int count) {
		return getNewNode(++pcr, name, uriVocID, prefixVocID, localNameVocID,
				kind, nsMapping, parent);
	}

	public PathSynopsisNode getNodeByPcr(int pcr) {
		return ((pcr <= this.pcr) && (pcr > 0)) ? pcrTable[pcr] : null;
	}

	public Set<Integer> getPCRsForPath(Tx transaction, Path<QNm> path)
			throws DocumentException {
		try {
			Set<Integer> pcrSet = pathCache.get(path);

			if (pcrSet != null) {
				return pcrSet;
			}

			pcrSet = new HashSet<Integer>();

			boolean isAttributePattern = path.isAttribute();
			int pathLength = path.getLength();

			for (int i = 1; i <= pcr; i++) {
				PathSynopsisNode node = pcrTable[i];

				if (node == null) {
					continue;
				}

				if (node.getLevel() < pathLength) {
					continue;
				}

				if (isAttributePattern ^ (node.getKind() == Kind.ATTRIBUTE.ID)) {
					continue;
				}

				if (path.matches(node.getPath())) {
					pcrSet.add(node.getPCR());
				}
			}
			pathCache.put(path, pcrSet);
			return pcrSet;
		} catch (PathException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder(
				"path synopsis (Format [PCR:VocID:Count] ):");
		for (PathSynopsisNode root : roots) {
			if (root != null) {
				ArrayList<StringBuffer> levelBuffers = new ArrayList<StringBuffer>();
				printNode(root, levelBuffers);
				for (StringBuffer buf : levelBuffers) {
					buffer.append("\n");
					buffer.append(buf);
				}
			}
		}
		buffer.append("\n Max PCR=" + this.pcr);
		return buffer.toString();
	}

	private void printNode(PathSynopsisNode node,
			ArrayList<StringBuffer> levelBuffers) {
		if (levelBuffers.size() - 1 <= node.getLevel()) {
			levelBuffers.add(new StringBuffer());
		}

		for (PathSynopsisNode child : node.getChildren()) {
			printNode(child, levelBuffers);
		}

		StringBuffer buf = levelBuffers.get(node.getLevel() - 1);
		String str = String.format("[%s:%s]  ", node.getPCR(),
				((node.getKind() == Kind.ATTRIBUTE.ID) ? "@" : "")
						+ String.format("(%s,%s,%s)", node.getURIVocID(), node
								.getPrefixVocID(), node.getLocalNameVocID()));
		buf.append(str);

		if (levelBuffers.size() - 1 > node.getLevel()) // child level exists
		{
			int indent = levelBuffers.get(node.getLevel() + 1).length()
					- buf.length();
			for (int i = indent + 2; i > 0; i--)
				buf.append(" ");
		}
	}

	public PathSynopsisNode[] getRoots() {
		int i = 0;
		while ((i < roots.length) && (roots[i] != null)) {
			i++;
		}
		PathSynopsisNode[] clone = new PathSynopsisNode[i];
		System.arraycopy(roots, 0, clone, 0, i);
		return clone;
	}

	public void setPcr(int pcr) {
		this.pcr = pcr;
	}

	public void setIndexNumber(int idxNo) {
		this.psIdxNo = idxNo;
	}

	public int getIndexNumber() {
		return psIdxNo;
	}

	public int getMaxStoredPCR() {
		return maxStoredPCR;
	}

	public void setMaxStoredPCR(int maxStoredPCR) {
		this.maxStoredPCR = maxStoredPCR;
	}

	public int getMaxPCR() {
		return pcr;
	}

	public void clearCache() {
		this.pathCache.clear();
	}
}
