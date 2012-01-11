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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.node.index.definition;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.server.node.index.external.IndexStatistics;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.module.Namespaces;
import org.brackit.xquery.node.SubtreePrinter;
import org.brackit.xquery.node.parser.FragmentHelper;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.Type;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class IndexDef implements Materializable, Serializable {

	private static final QNm EXCLUDING_TAG = new QNm("excluding");

	private static final QNm INCLUDING_TAG = new QNm("including");

	private static final QNm PATH_TAG = new QNm("path");

	private static final QNm UNIQUE_ATTRIBUTE = new QNm("unique");

	private static final QNm CLUSTER_ATTRIBUTE = new QNm("cluster");

	private static final QNm CONTENT_TYPE_ATTRIBUTE = new QNm("keyType");

	private static final QNm TYPE_ATTRIBUTE = new QNm("type");

	private static final QNm ID_ATTRIBUTE = new QNm("id");

	private static final long serialVersionUID = 1L;

	public static final QNm INDEX_TAG = new QNm("index");

	private IndexType type;

	private Cluster cluster = Cluster.SPLID;

	// paths for path and CAS indexes
	private final List<Path<QNm>> paths = new ArrayList<Path<QNm>>();

	// only for name indexes
	private final Set<QNm> excluded = new HashSet<QNm>();
	private final Map<QNm, Cluster> included = new HashMap<QNm, Cluster>();

	// unique flag (for CAS indexes)
	private boolean unique = false;

	// for CAS indexes
	private Type contentType;

	// populated when index is built
	private int id;
	private int containerID = -1; // -1 same as collection

	private IndexStatistics indexStatistics = null;

	public IndexDef() {
	}

	/*
	 * Name index
	 */
	public IndexDef(Cluster cluster, Map<QNm, Cluster> included,
			Set<QNm> excluded) {
		this.type = IndexType.NAME;
		this.included.putAll(included);
		this.excluded.addAll(excluded);
	}

	/*
	 * Path index
	 */
	public IndexDef(Cluster cluster, List<Path<QNm>> paths) {
		this.type = IndexType.PATH;
		this.paths.addAll(paths);
		this.cluster = cluster;
	}

	/*
	 * CAS index
	 */
	public IndexDef(Type contentType, Cluster cluster,
			List<Path<QNm>> paths, boolean unique) {
		this.type = IndexType.CAS;
		this.contentType = contentType;
		this.paths.addAll(paths);
		this.cluster = cluster;
		this.unique = unique;
	}

	@Override
	public void init(Node<?> root) throws DocumentException {
		QNm name = root.getName();

		if (!name.equals(INDEX_TAG)) {
			throw new DocumentException("Expected tag '%s' but found '%s'",
					INDEX_TAG, name);
		}

		Node<?> attribute;

		attribute = root.getAttribute(ID_ATTRIBUTE);
		if (attribute != null) {
			id = Integer.valueOf(attribute.getValue().stringValue());
		}

		attribute = root.getAttribute(TYPE_ATTRIBUTE);
		if (attribute != null) {
			type = (IndexType.valueOf(attribute.getValue().stringValue()));
		}

		attribute = root.getAttribute(CONTENT_TYPE_ATTRIBUTE);
		if (attribute != null) {
			contentType = (resolveType(attribute.getValue().stringValue()));
		}

		attribute = root.getAttribute(CLUSTER_ATTRIBUTE);
		if (attribute != null) {
			cluster = (Cluster.valueOf(attribute.getValue().stringValue()));
		}

		attribute = root.getAttribute(UNIQUE_ATTRIBUTE);
		if (attribute != null) {
			unique = (Boolean.valueOf(attribute.getValue().stringValue()));
		}

		Stream<? extends Node<?>> children = root.getChildren();

		try {
			Node<?> child;
			while ((child = children.next()) != null) {
				if (child.getName().equals(IndexStatistics.STATISTICS_TAG)) {
					indexStatistics = new IndexStatistics();
					indexStatistics.init(child);
				} else {
					QNm childName = child.getName();
					String value = child.getValue().stringValue();

					if (childName.equals(PATH_TAG)) {
						String path = value;
						paths.add(Path.parse(path));
					} else if (childName.equals(INCLUDING_TAG)) {
						for (String s : value.split(",")) {
							if (s.length() > 0) {
								String includeString = s;
								String[] tmp = includeString.split("@");
								included.put(new QNm(tmp[0]), 
										Cluster.valueOf(tmp[1]));
							}
						}
					} else if (childName.equals(EXCLUDING_TAG)) {
						for (String s : value.split(",")) {
							if (s.length() > 0)
								excluded.add(new QNm(s));
						}
					}
				}
			}
		} finally {
			children.close();
		}
	}

	@Override
	public Node<?> materialize() throws DocumentException {
		FragmentHelper tmp = new FragmentHelper();

		tmp.openElement(INDEX_TAG);
		tmp.attribute(TYPE_ATTRIBUTE, new Una(type.toString()));
		tmp.attribute(ID_ATTRIBUTE, new Una(Integer.toString(id)));
		tmp.attribute(CLUSTER_ATTRIBUTE, new Una(cluster.toString()));

		if (contentType != null) {
			tmp.attribute(CONTENT_TYPE_ATTRIBUTE, new Una(contentType.toString()));
		}

		if (isUnique()) {
			tmp.attribute(UNIQUE_ATTRIBUTE, new Una(Boolean.toString(isUnique())));
		}

		if (paths != null && !paths.isEmpty()) {
			for (Path<QNm> path : paths) {
				tmp.openElement(PATH_TAG);
				tmp.content(path.toString()); // TODO
				tmp.closeElement();
			}
		}
		if (!excluded.isEmpty()) {
			tmp.openElement(EXCLUDING_TAG);

			StringBuilder buf = new StringBuilder();
			for (QNm s : excluded) {
				buf.append(s + ",");
			}
			// remove trailing ","
			buf.deleteCharAt(buf.length() - 1);
			tmp.content(buf.toString());
			tmp.closeElement();
		}

		if (!included.isEmpty()) {
			tmp.openElement(INCLUDING_TAG);

			StringBuilder buf = new StringBuilder();
			for (Entry<QNm, Cluster> e : included.entrySet()) {
				buf.append(e.getKey() + "@" + e.getValue() + ",");
			}
			// remove trailing ","
			buf.deleteCharAt(buf.length() - 1);
			tmp.content(buf.toString());
			tmp.closeElement();
		}

		if (indexStatistics != null) {
			tmp.insert(indexStatistics.materialize());
		}

		tmp.closeElement();
		return tmp.getRoot();
	}

	private Type resolveType(String s) throws DocumentException {
		QNm name = new QNm(Namespaces.XS_NSURI, Namespaces.XS_PREFIX, s
				.substring(Namespaces.XS_PREFIX.length() + 1));
		for (Type type : Type.builtInTypes) {
			if (type.getName().getLocalName().equals(name.getLocalName())) {
				return type;
			}
		}
		throw new DocumentException("Unknown content type type: '%s'", name);
	}

	public boolean isNameIndex() {
		return type == IndexType.NAME;
	}

	public boolean isCasIndex() {
		return type == IndexType.CAS;
	}

	public boolean isPathIndex() {
		return type == IndexType.PATH;
	}

	public boolean isUnique() {
		return this.unique;
	}

	public int getID() {
		return id;
	}

	public IndexType getType() {
		return type;
	}

	public List<Path<QNm>> getPaths() {
		return Collections.unmodifiableList(paths);
	}

	public Cluster getClustering() {
		return this.cluster;
	}

	public Map<QNm, Cluster> getIncluded() {
		return Collections.unmodifiableMap(included);
	}

	public Set<QNm> getExcluded() {
		return Collections.unmodifiableSet(excluded);
	}

	@Override
	public String toString() {
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			SubtreePrinter.print(materialize(), new PrintStream(buf));
			return buf.toString();
		} catch (DocumentException e) {
			return e.getMessage();
		}
	}

	public Type getContentType() {
		return contentType;
	}

	public void createdAs(int containerID, int id) {
		this.containerID = containerID;
		this.id = id;
	}

	public IndexStatistics getIndexStatistics() {
		return indexStatistics;
	}

	public void setIndexStatistics(IndexStatistics indexStatistics) {
		this.indexStatistics = indexStatistics;
	}

	public void setContainerID(int containerID) {
		this.containerID = containerID;
	}

	public int getContainerID() {
		return containerID;
	}

	public void setType(IndexType type) {
		this.type = type;
	}

	public void addPath(Path<QNm> path) {
		this.paths.add(path);
	}
}
