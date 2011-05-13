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

import org.brackit.server.tx.Tx;
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
	private HashMap<String, Set<Integer>> pathCache = new HashMap<String, Set<Integer>>();

	public PathSynopsis(int idxNo) {
		this.psIdxNo = idxNo;
		this.roots = new PathSynopsisNode[1];
		this.pcrTable = new PathSynopsisNode[20];
	}

	public PathSynopsisNode getNewNode(int pcr, String name, int vocId,
			byte kind, PathSynopsisNode parent) {
		pathCache.clear();
		PathSynopsisNode psN = new PathSynopsisNode(vocId, pcr, name, kind,
				parent, this);

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

	public PathSynopsisNode getNewNode(String name, int vocId, byte kind,
			PathSynopsisNode parent, int count) {
		return getNewNode(++pcr, name, vocId, kind, parent);
	}

	public PathSynopsisNode getNodeByPcr(int pcr) {
		return ((pcr <= this.pcr) && (pcr > 0)) ? pcrTable[pcr] : null;
	}

	@Deprecated
	public Set<Integer> getPCRsForPathMax(Tx transaction, String queryString) {
		Set<Integer> returnSet = this.pathCache.get(queryString);
		if (returnSet == null)
			returnSet = new HashSet<Integer>();
		else
			return returnSet;

		PathSynopsisNode checkedNode = null;
		PathSynopsisNode currentNode = null;
		PathSynopsisNode pendingNode = null;

		String[] queryArray = queryString.split("/");
		String lastPart = queryArray[queryArray.length - 1];

		// Determine number of actual elements for level value
		int e = 0;
		for (String part : queryArray) {
			if (!part.isEmpty())
				e++;
		}

		// 1st pass: Filter out non-qualifying nodes
		// by looking at their names
		// (i.e. end of resp. path)
		for (int i = 1; i <= pcr; i++) {
			checkedNode = pcrTable[i];

			if (checkedNode == null) {
				continue;
			}
			currentNode = checkedNode;
			pendingNode = null;

			int depth = queryArray.length - 1;
			boolean unmatch = false;
			boolean wildcard = false;
			int pending = -1;
			int cl = e; // current level
			int pl = 0; // pending level

			// Handle case when query ends on attribute
			if (lastPart.startsWith("@")) {
				if (currentNode.getKind() != Kind.ATTRIBUTE.ID
						|| !String.valueOf(currentNode.getVocID()).equals(
								lastPart.substring(1))) {
					unmatch = true;
				} else {
					depth--;
					cl--;
					currentNode = currentNode.getParent();
				}
			}

			// Bottom-up run through query resp. path arrays to decide whether
			// they match or not
			// Note: First element of queryArray will always be empty (due to
			// split op on "/...")
			while (!unmatch && depth > 0 && currentNode != null
					&& cl <= currentNode.getLevel() + 1) {
				if (queryArray[depth].isEmpty()) { // wildcard found
					if (depth == 1) {
						break;
					} else {
						pending = --depth;
						wildcard = true;
						pendingNode = null;
					}
				}

				while (depth > 0 && currentNode != null
						&& cl <= currentNode.getLevel() + 1) {
					if (!queryArray[depth].equals("*")
							&& !queryArray[depth].equals(String
									.valueOf(currentNode.getVocID()))) {
						unmatch = true;
					} else {
						unmatch = false;
						depth--;
						cl--;
						if (wildcard && currentNode.getLevel() > 0) {
							pendingNode = currentNode;
							pl = cl;
						}
					}

					currentNode = currentNode.getParent();

					if (!wildcard || !unmatch) {
						break;
					}
				}

				wildcard = false;

				if (pendingNode != null && (unmatch || depth == 0)) {
					// Reset to pending node´s parent
					// and continue searching
					currentNode = pendingNode.getParent();
					pendingNode = null;
					depth = pending;
					cl = pl;
					unmatch = false;
					wildcard = true;
				}

			}

			// Treat different cases of termination and set unmatch accordingly
			if (depth == 1 && queryArray[1].isEmpty() && !unmatch) {
				// query starts with wildcard and path
				// matches until there unmatch = false;
				// obsolete
			} else if ((depth == 0 && currentNode != null)
					|| (depth > 0 && currentNode == null)) {
				// path matched until end, but either
				// path or query unfinished
				unmatch = true;
			} else if (currentNode != null && cl > currentNode.getLevel() + 1) {
				// too few elements/ left in path
				// to match query
				unmatch = true;
			}

			if (!unmatch) {
				// if the paths match add this
				// node to the return set
				returnSet.add(checkedNode.getPCR());
			}

		}

		this.pathCache.put(queryString, returnSet);
		return returnSet;
	}

	public Set<Integer> getPCRsForPath(Tx transaction, String queryString)
			throws DocumentException {
		try {
			Set<Integer> pcrSet = pathCache.get(queryString);

			if (pcrSet != null) {
				return pcrSet;
			}

			pcrSet = new HashSet<Integer>();

			Path<String> path = Path.parse(queryString);
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
			pathCache.put(queryString, pcrSet);
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
				(node.getKind() == Kind.ATTRIBUTE.ID) ? "@" + node.getVocID()
						: node.getVocID());
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
