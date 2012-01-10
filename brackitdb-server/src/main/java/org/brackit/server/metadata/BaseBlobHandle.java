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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.server.metadata.materialize.MaterializableFactory;
import org.brackit.server.node.DocID;
import org.brackit.server.node.txnode.Persistor;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Una;
import org.brackit.xquery.node.parser.FragmentHelper;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * @author Henrique Valer
 * 
 */
public class BaseBlobHandle implements BlobHandle, Materializable {
	public static final QNm BLOB_TAG = new QNm("blob");

	public static final QNm ID_ATTRIBUTE = new QNm("id");

	public static final QNm NAME_ATTRIBUTE = new QNm("name");

	protected final BlobStore store;

	protected final Tx tx;

	protected String name;

	protected int collID;

	private Persistor persistor;

	private Map<Class<? extends Materializable>, Materializable> materializables;

	public BaseBlobHandle(Tx tx, BlobStore store) {
		this.store = store;
		this.tx = tx;
	}

	protected BaseBlobHandle(BaseBlobHandle b, Tx tx) {
		this.name = b.name;
		this.store = b.store;
		this.collID = b.collID;
		this.persistor = b.persistor;
		this.tx = tx;
	}

	@Override
	public BlobHandle copyFor(Tx tx) {
		return (this.tx.equals(tx)) ? this : new BaseBlobHandle(this, tx);
	}

	@Override
	public Tx getTX() {
		return tx;
	}

	@Override
	public int getID() {
		return collID;
	}

	@Override
	public String getName() {
		return name;
	}

	public void create(String name, int containerNo, InputStream in)
			throws DocumentException {
		try {
			PageID pageID = store.create(tx, containerNo);
			store.writeStream(tx, pageID, in, false);
			this.name = name;
			this.collID = pageID.value();
		} catch (BlobStoreAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void calculateStatistics() throws DocumentException {
	}

	@Override
	public void delete() throws DocumentException {
		try {
			store.drop(tx, new PageID(collID));
		} catch (BlobStoreAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public InputStream read() throws DocumentException {
		try {
			return store.readStream(tx, new PageID(collID));
		} catch (BlobStoreAccessException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void write(InputStream in) throws DocumentException {
		try {
			store.writeStream(tx, new PageID(collID), in, true);
		} catch (BlobStoreAccessException e) {
			throw new DocumentException(e);
		}
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
		collID = Integer.parseInt(root.getAttribute(ID_ATTRIBUTE)
				.getValue().stringValue());

		Stream<? extends Node<?>> children = root.getChildren();

		try {
			Node<?> child;
			while ((child = children.next()) != null) {
				Materializable materializable = MaterializableFactory
						.getInstance().create(child.getName().stringValue());
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
		helper.openElement(BLOB_TAG).attribute(ID_ATTRIBUTE,
				new Una(Integer.toString(collID))).attribute(NAME_ATTRIBUTE, new Una(name));
		if (materializables != null) {
			for (Materializable materializable : materializables.values()) {
				helper.insert(materializable.materialize());
			}
		}
		helper.closeElement();
		return helper.getRoot();
	}

	public Persistor getPersistor() {
		return persistor;
	}

	public void setPersistor(Persistor persistor) {
		this.persistor = persistor;
	}

	public void persist() throws OperationNotSupportedException,
			DocumentException {
		if (persistor == null) {
			throw new DocumentException(
					"Collection %s is not assigned to a persistor", toString());
		}

		persistor.persist(tx, this);
	}

	@Override
	public void serialize(OutputStream out) throws DocumentException {
		try {
			InputStream in = store.readStream(tx, new PageID(collID));

			try {
				byte[] buf = new byte[256];
				int len;
				while ((len = in.read(buf)) != -1) {
					out.write(buf, 0, len);
				}
			} finally {
				in.close();
			}
		} catch (BlobStoreAccessException e) {
			throw new DocumentException(e);
		} catch (IOException e) {
			throw new DocumentException(e);
		}
	}
}
