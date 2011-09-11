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
package org.brackit.server.metadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.server.metadata.materialize.MaterializableFactory;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.AbstractCollection;
import org.brackit.xquery.node.parser.FragmentHelper;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * 
 * @param <E>
 */
public abstract class BaseCollection<E extends TXNode<E>> extends
		AbstractCollection<E> implements DBCollection<E> {
	public static final String DOCUMENT_TAG = "doc";

	public static final QNm ID_ATTRIBUTE = new QNm("id");

	public static final QNm NAME_ATTRIBUTE = new QNm("name");

	protected DictionaryMgr dictionary;

	protected DocID docID;

	private Map<Class<? extends Materializable>, Materializable> materializables;

	public BaseCollection() {
		super((String) null);
	}

	protected BaseCollection(BaseCollection<E> collection) {
		super(collection);
		this.dictionary = collection.dictionary;
		this.docID = collection.docID;
		this.materializables = collection.materializables;
	}

	public BaseCollection(DocID docID, String name, DictionaryMgr dictionary) {
		super(name);
		this.dictionary = dictionary;
		this.docID = docID;
	}

	public DocID getID() {
		return docID;
	}

	@Override
	public DictionaryMgr getDictionary() {
		return dictionary;
	}

	@Override
	public <T extends Materializable> T check(Class<T> type)
			throws DocumentException {
		if (materializables == null) {
			return null;
		}

		Materializable materializable = materializables.get(type);

		if (materializable == null) {
			return null;
		}

		return type.cast(materializable);
	}

	@Override
	public <T extends Materializable> T get(Class<T> type)
			throws DocumentException {
		if (materializables == null) {
			materializables = new ConcurrentHashMap<Class<? extends Materializable>, Materializable>();
		}

		Materializable materializable = materializables.get(type);

		if (materializable == null) {
			// create empty materializable
			try {
				materializable = type.newInstance();
			} catch (Exception e) {
				throw new DocumentException(e,
						"Could not instantiate materializable %s", type);
			}
			materializables.put(type, materializable);
		}

		return type.cast(materializable);
	}

	@Override
	public <T extends Materializable> void set(T type) throws DocumentException {
		if (materializables == null) {
			materializables = new ConcurrentHashMap<Class<? extends Materializable>, Materializable>();
		}

		materializables.put(type.getClass(), type);
	}

	@Override
	public <T extends Materializable> void remove(Class<T> type)
			throws DocumentException {
		if (materializables != null) {
			materializables.remove(type);
		}
	}

	@Override
	public void init(Node<?> root) throws DocumentException {
		name = root.getAttribute(NAME_ATTRIBUTE).getValue().stringValue();
		docID = new DocID(Integer.parseInt(root.getAttribute(ID_ATTRIBUTE)
				.getValue().stringValue()));

		Stream<? extends Node<?>> children = root.getChildren();

		try {
			Node<?> child;
			while ((child = children.next()) != null) {
				Materializable materializable = MaterializableFactory
						.getInstance().create(child.getName());
				materializable.init(child);
				set(materializable);
			}
		} finally {
			children.close();
		}
	}

	@Override
	public Node<?> materialize() throws DocumentException {
		FragmentHelper helper = new FragmentHelper();
		helper.openElement(DOCUMENT_TAG).attribute(ID_ATTRIBUTE,
				new Una(docID.toString())).attribute(NAME_ATTRIBUTE, new Una(name));
		if (materializables != null) {
			for (Materializable materializable : materializables.values()) {
				helper.insert(materializable.materialize());
			}
		}
		helper.closeElement();
		return helper.getRoot();
	}

	@Override
	public void calculateStatistics() throws DocumentException {
	}

	@Override
	public void delete() throws DocumentException {
		for (IndexDef idxDef : get(Indexes.class).getIndexDefs()) {
			getIndexController().dropIndex(idxDef);
		}
	}
}
