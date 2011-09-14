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
package org.brackit.server.node.index.definition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.Type;

/**
 * @author Sebastian Baechle
 * 
 */
public class IndexDefBuilder {

	public final static Cluster DEFAULT_CLUSTER = Cluster.SPLID;

	public IndexDefBuilder() {
	}

	IndexDef createContentIndexDefinition(boolean onElementContent,
			boolean onAttributeContent, Type type) {
		return new IndexDef(type, onElementContent, onAttributeContent, false);
	}

	IndexDef createCASIndexDefinition(List<Path<QNm>> paths,
			Cluster cluster, boolean unique, Type type) {

		if (cluster == null)
			cluster = DEFAULT_CLUSTER;
		return new IndexDef(type, cluster, paths, unique);
	}

	IndexDef createPathIndexDefinition(List<Path<QNm>> paths, Cluster cluster) {

		if (cluster == null)
			cluster = DEFAULT_CLUSTER;
		return new IndexDef(cluster, paths);
	}

	IndexDef createFilteredElementIndexDefinition(Cluster cluster,
			List<QNm> filter) {

		HashSet<QNm> excluded = new HashSet<QNm>(filter);
		HashMap<QNm, Cluster> included = new HashMap<QNm, Cluster>();

		if (cluster == null)
			cluster = DEFAULT_CLUSTER;
		return new IndexDef(cluster, included, excluded);
	}

	IndexDef createSelectiveElementIndexDefinition(
			Map<QNm, Cluster> included, Cluster cluster) {

		HashSet<QNm> excluded = new HashSet<QNm>();

		if (cluster == null)
			cluster = DEFAULT_CLUSTER;
		return new IndexDef(cluster, included, excluded);
	}
}
