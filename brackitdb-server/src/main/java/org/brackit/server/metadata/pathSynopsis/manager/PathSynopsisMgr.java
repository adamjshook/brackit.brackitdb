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
import java.util.Set;

import org.brackit.server.metadata.pathSynopsis.PSSnapshotBuilder;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.tx.Tx;
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
public interface PathSynopsisMgr {
	/**
	 * Spawns a private path synopsis manager for the specified document,
	 * optimized for transaction-local bulk use (e.g. store,restore). Access to
	 * a different document or by a different transaction will cause an error.
	 */
	public PathSynopsisMgr spawnBulkPsManager(Tx tx) throws DocumentException;

	public Set<Integer> getPCRsForPaths(Tx tx, Collection<Path<String>> paths)
			throws DocumentException;

	public Set<Integer> match(Tx tx, Path<String> path)
			throws DocumentException;

	public PSNode get(Tx tx, int pcr) throws DocumentException;

	public PSNode getAncestor(Tx tx, int pcr, int level)
			throws DocumentException;

	public PSNode getAncestorOrParent(Tx tx, int pcr, int level)
			throws DocumentException;

	/**
	 * Returns the pcr of a child node with label <code>vocID</code> and type
	 * <code>nodeType</code> of pcr <code>parentPcr</code>. If such a child does
	 * not exist it is created and the new PCR is returned.
	 */
	public PSNode getChild(Tx tx, int parentPcr, int vocID, byte kind)
			throws DocumentException;

	/**
	 * Allows to make a snapshot of the path synopsis containing relevant meta
	 * data
	 */
	public void snapshot(Tx tx, PSSnapshotBuilder builder)
			throws DocumentException;

	public int getPathSynopsisNo() throws DocumentException;

	/**
	 * Attribute children are allowed but no element child nodes/PCRs
	 */
	public boolean isLeaf(Tx transaction, int pcr);

	public int getMaxPCR();
}