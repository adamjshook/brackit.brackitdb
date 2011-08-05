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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.brackit.xquery.xdm.DocumentException;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class IndexDefHelper extends IndexDefBuilder {

	public IndexDefHelper() {
	}

	public IndexDef parse(String indexDefinition) throws DocumentException {
		try {
			IndexDefParser parser = new IndexDefParser(new StringReader(
					indexDefinition));
			return parser.index();
		} catch (Exception e) {
			throw new DocumentException(e,
					"Error parsing index definition: %s", indexDefinition);
		}
	}

	public IndexDef createElementIndexDefinition(
			Map<String, Cluster> selection, List<String> filter, Cluster cluster) {
		if (selection != null && selection.size() > 0) {
			if (filter != null) {
				throw new RuntimeException("Ambiguous element index definition");
			}

			// if clustering is not specified, get default value from
			// IndexDefBuilder
			if (cluster == null) {
				cluster = IndexDefBuilder.DEFAULT_CLUSTER;
			}
			// replace non-specified clusterings in each selection entry
			// with the command-specified
			if (selection != null) {
				for (Map.Entry<String, Cluster> e : selection.entrySet()) {
					if (e.getValue() == null) {
						e.setValue(cluster);
					}
				}
			}
			return createSelectiveElementIndexDefinition(selection, cluster);
		}

		// if selection not provided, create filtered index
		if (filter == null) {
			filter = new ArrayList<String>();
		}

		return createFilteredElementIndexDefinition(cluster, filter);
	}
}
