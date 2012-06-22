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
import java.util.Map;
import java.util.Set;

import org.brackit.server.metadata.TXObject;
import org.brackit.server.metadata.pathSynopsis.NsMapping;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.PSSnapshotBuilder;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.DocumentException;

/**
 * Handles access, loading and storing of the path synopsis.
 * 
 * 
 * @author Martin Meiringer
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 */
public interface PathSynopsisMgr extends TXObject<PathSynopsisMgr> {

	public static class SubtreeCopyResult {
		public final PSNode newRoot;
		public final Map<Integer, Integer> pcrMap;

		public SubtreeCopyResult(PSNode newRoot, Map<Integer, Integer> pcrMap) {
			super();
			this.newRoot = newRoot;
			this.pcrMap = pcrMap;
		}
	}

	/**
	 * Spawns a private path synopsis manager for the specified document,
	 * optimized for transaction-local bulk use (e.g. store,restore). Access to
	 * a different document or by a different transaction will cause an error.
	 */
	public PathSynopsisMgr spawnBulkPsManager() throws DocumentException;

	public Set<Integer> getPCRsForPaths(Collection<Path<QNm>> paths)
			throws DocumentException;

	public Set<Integer> match(Path<QNm> path) throws DocumentException;

	public PSNode get(int pcr) throws DocumentException;

	public PSNode getAncestor(int pcr, int level) throws DocumentException;

	public PSNode getAncestorOrParent(int pcr, int level)
			throws DocumentException;

	/**
	 * Returns the requested child PSNode. If such a child does not exist it is
	 * created and returned.
	 */
	public PSNode getChild(int parentPcr, QNm name, byte kind,
			NsMapping nsMapping) throws DocumentException;

	/**
	 * Allows to make a snapshot of the path synopsis containing relevant meta
	 * data
	 */
	public void snapshot(PSSnapshotBuilder builder) throws DocumentException;

	public int getPathSynopsisNo() throws DocumentException;

	/**
	 * Attribute children are allowed but no element child nodes/PCRs
	 */
	public boolean isLeaf(int pcr);

	public int getMaxPCR();

	/**
	 * Changes the NsMapping of the PSNode with PCR 'originalPCR'. This
	 * operation does not only create one new node in the path synopsis, but a
	 * whole new subtree. The mapping between the PCRs of the old and the new
	 * subtree is described by the returned Map.
	 */
	public SubtreeCopyResult copySubtree(int originalPCR,
			NsMapping newNsMapping, QNm newName) throws DocumentException;

	/**
	 * Returns the requested child PSNode. If such a child does not exist, null
	 * is returned.
	 */
	public PSNode getChildIfExists(int parentPcr, QNm name, byte kind,
			NsMapping nsMapping) throws DocumentException;

	public int[] matchChildPath(Path<QNm> path) throws DocumentException;
}