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
package org.brackit.server.metadata.masterDocument;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.FragmentHelper;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.util.path.PathException;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 * 
 */
public class Indexes implements Materializable {
	public static final QNm INDEXES_TAG = new QNm("indexes");

	private ArrayList<IndexDef> indexes = null;

	public Indexes() {
		indexes = new ArrayList<IndexDef>();
	}

	public synchronized List<IndexDef> getIndexDefs() {
		return new ArrayList<IndexDef>(indexes);
	}

	public synchronized IndexDef getIndexDef(int indexNo) {
		for (IndexDef sid : indexes) {
			if (sid.getID() == indexNo) {
				return sid;
			}
		}
		return null;
	}

	@Override
	public synchronized void init(Node<?> root) throws DocumentException {
		QNm name = root.getName();
		if (!name.equals(INDEXES_TAG)) {
			throw new DocumentException("Expected tag '%s' but found '%s'",
					INDEXES_TAG, name);
		}

		Stream<? extends Node<?>> children = root.getChildren();

		try {
			Node<?> child;
			while ((child = children.next()) != null) {
				QNm childName = child.getName();

				if (!childName.equals(IndexDef.INDEX_TAG)) {
					throw new DocumentException(
							"Expected tag '%s' but found '%s'",
							IndexDef.INDEX_TAG, childName);
				}

				IndexDef indexDefinition = new IndexDef();
				indexDefinition.init(child);
				indexes.add(indexDefinition);
			}
		} finally {
			children.close();
		}
	}

	@Override
	public synchronized Node<?> materialize() throws DocumentException {
		FragmentHelper helper = new FragmentHelper();
		helper.openElement(INDEXES_TAG);

		for (IndexDef idxDef : indexes) {
			helper.insert(idxDef.materialize());
		}

		helper.closeElement();
		return helper.getRoot();
	}

	public synchronized void add(IndexDef indexDefinition) {
		indexes.add(indexDefinition);
	}

	public synchronized void removeIndex(int indexID) {
		for (int i = 0; i < indexes.size(); i++) {
			if (indexes.get(i).getID() == indexID) {
				indexes.remove(i);
				return;
			}
		}
	}

	public IndexDef findPathIndex(Path<QNm> path) throws DocumentException {
		try {
			List<IndexDef> candidates = new ArrayList<IndexDef>(indexes.size());

			for (IndexDef index : indexes) {
				if (index.isPathIndex()) {
					for (Path<QNm> indexedPath : index.getPaths()) {
						if (indexedPath.matches(path)) {
							candidates.add(index);
						}
					}
				}
			}

			return (candidates.size() > 0) ? candidates.get(0) : null;
		} catch (PathException e) {
			throw new DocumentException(e);
		}
	}

	public IndexDef findCASIndex(Path<QNm> path) throws DocumentException {
		try {
			List<IndexDef> candidates = new ArrayList<IndexDef>(indexes.size());

			for (IndexDef index : indexes) {
				if (index.isCasIndex()) {
					for (Path<QNm> indexedPath : index.getPaths()) {
						if (indexedPath.matches(path)) {
							candidates.add(index);
						}
					}
				}
			}

			return (candidates.size() > 0) ? candidates.get(0) : null;
		} catch (PathException e) {
			throw new DocumentException(e);
		}
	}

	public IndexDef findNameIndex() throws DocumentException {
		for (IndexDef index : indexes) {
			if (index.isNameIndex()) {
				return index;
			}
		}

		throw new DocumentException("No name index found.");
	}

	public IndexDef findContentIndex() throws DocumentException {
		for (IndexDef index : indexes) {
			if (index.isContentIndex()) {
				return index;
			}
		}

		throw new DocumentException("No content index found.");
	}
}
