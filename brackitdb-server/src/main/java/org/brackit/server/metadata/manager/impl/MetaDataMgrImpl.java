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
package org.brackit.server.metadata.manager.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.BaseBlobHandle;
import org.brackit.server.metadata.BaseCollection;
import org.brackit.server.metadata.BlobHandle;
import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.DBItem;
import org.brackit.server.metadata.cache.HookedCache;
import org.brackit.server.metadata.manager.MetaDataMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr03;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElCollection;
import org.brackit.server.node.el.ElNode;
import org.brackit.server.node.el.ElStore;
import org.brackit.server.node.index.IndexController;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.definition.IndexDefBuilder;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.impl.IndexBlobStore;
import org.brackit.server.tx.PostCommitHook;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.services.MetaLockService;
import org.brackit.server.tx.locking.services.UnifiedMetaLockService;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.util.Cfg;
import org.brackit.xquery.util.log.Logger;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.util.path.PathException;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;

/**
 * @author Sebastian Baechle
 * 
 */
public class MetaDataMgrImpl implements MetaDataMgr {
	private static final Logger log = Logger.getLogger(MetaDataMgrImpl.class);

	public static final QNm DIR_TAG = new QNm("dir");

	public static final QNm NAME_ATTR = new QNm("name");

	public static final QNm ID_ATTR = new QNm("id");

	private static final int DEFAULT_DICTIONARY_ID = 1;

	private static final PageID MASTERDOC_PAGEID = new PageID(2);

	private static final PageID MASTERDOC_PSPAGEID = new PageID(3);

	private static final int MASTERDOC_NAME_CASINDEX_NO = 4;

	private static final int MASTERDOC_ID_CASINDEX_NO = 5;

	private static final String MASTERDOC_NAME = "_master.xml";

	private static final String MASTERDOC_DEFAULTDOCUMENT = "<?xml version=\"1.0\"?><xtc><users/><dir name=\"/\"/></xtc>";

	private final HookedCache<String, Item<Directory>> itemCache;

	private final HookedCache<DocID, DBCollection<?>> collectionCache;

	private final HookedCache<DocID, BlobHandle> blobCache;

	private final ElStore elStore;

	private final BlobStore blobStore;

	private final MetaLockService<?> mls;

	private final DictionaryMgr defaultDictionary;

	private ElCollection mdCollection;

	private Directory mdRootDir;

	private int mdNameCasIndexNo;

	private int mdIDCasIndexNo;

	public MetaDataMgrImpl(TxMgr taMgr) {
		BufferMgr bufferMgr = taMgr.getBufferManager();
		itemCache = new HookedCache<String, Item<Directory>>();
		collectionCache = new HookedCache<DocID, DBCollection<?>>();
		blobCache = new HookedCache<DocID, BlobHandle>();
		defaultDictionary = new DictionaryMgr03(bufferMgr);
		int maxTransactions = Cfg.asInt(TxMgr.MAX_TX, 100);
		int maxLocks = Cfg.asInt(TxMgr.MAX_LOCKS, 200000);
		mls = new UnifiedMetaLockService("DocumentLockService", maxLocks,
				maxTransactions);
		elStore = new ElStore(bufferMgr, defaultDictionary, mls);
		blobStore = new IndexBlobStore(bufferMgr);
	}

	@Override
	public DBCollection<?> lookup(Tx tx, DocID id)
			throws ItemNotFoundException, DocumentException {
		Item<Directory> item = getItemByID(tx, id.value(), false);

		if (!(item instanceof Document)) {
			throw new MetaDataException("%s is not a document.", id);
		}

		Document document = (Document) item;
		return getCollectionInternal(tx, document, false);
	}

	@Override
	public DBCollection<?> lookup(Tx tx, String storedNamePath)
			throws ItemNotFoundException, DocumentException {
		// check for temporary document
		if (storedNamePath.contains("://")) {
			Document document = (Document) itemCache.get(tx, storedNamePath
					.toLowerCase());

			if (document == null) {
				throw new ItemNotFoundException(
						"Temporary document not found: %s", storedNamePath);
			}

			DBCollection<?> collection = collectionCache.get(tx, document
					.getID());

			if (collection == null) {
				throw new ItemNotFoundException(
						"Temporary document collection not found: %s",
						storedNamePath);
			}

			return collection;
		}

		Path<QNm> path = asPath(storedNamePath);
		Item<Directory> item = getItemByPath(tx, path, false);

		if (!(item instanceof Document)) {
			throw new MetaDataException("%s is not a document.", path);
		}

		Document document = (Document) item;
		return getCollectionInternal(tx, document, false);
	}

	public DBItem<?> getItem(Tx tx, String storedNamePath)
			throws ItemNotFoundException, DocumentException {
		Path<QNm> path = asPath(storedNamePath);
		Item<Directory> item = getItemByPath(tx, path, true); // pick item on DB

		if (item instanceof Document) {
			return lookup(tx, ((Document) item).getID());
		} else if (item instanceof Blob) {
			return getBlob(tx, ((Blob) item).getID());
		} else {
			throw new MetaDataException("Unknown item type: %s", item
					.getClass());
		}
	}

	@Override
	public DBCollection<?> create(Tx tx, String name, SubtreeParser parser)
			throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Storing document %s.", name));
		}

		// store persistent document
		Path<QNm> path = asPath(name);
		name = path.toString();
		Item<Directory> item = getItemByPath(tx, path.leading(), false);

		if (!(item instanceof Directory)) {
			throw new MetaDataException("%s is not a directory", path.leading());
		}

		Directory directory = (Directory) item;
		assertInsertion(tx, path, directory);
		TXCollection<?> collection = null;

		StorageSpec spec = new StorageSpec(name, defaultDictionary);
		ElCollection elCollection = new ElCollection(tx, elStore);
		elCollection.create(spec, parser);

		collection = elCollection;
		Document document = new Document(collection.getID(), name, directory,
				null);
		collection.setPersistor(document);

		// Finally persist and put into cache to make it available for others
		collection.persist();
		itemCache.putIfAbsent(tx, name, document);
		collectionCache.putIfAbsent(tx, document.getID(), collection);

		return collection;
	}

	@Override
	public DBCollection<?> create(Tx tx, String name) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Creating collection %s.", name));
		}

		// store persistent document
		Path<QNm> path = asPath(name);
		name = path.toString();
		Item<Directory> item = getItemByPath(tx, path.leading(), false);

		if (!(item instanceof Directory)) {
			throw new MetaDataException("%s is not a directory", path.leading());
		}

		Directory directory = (Directory) item;
		assertInsertion(tx, path, directory);
		TXCollection<?> collection = null;

		StorageSpec spec = new StorageSpec(name, defaultDictionary);
		ElCollection elCollection = new ElCollection(tx, elStore);
		elCollection.create(spec);

		collection = elCollection;
		Document document = new Document(collection.getID(), name, directory,
				null);
		collection.setPersistor(document);

		// Finally put into cache and persist to make document available for
		// others
		// Finally persist and put into cache to make it available for others
		collection.persist();
		itemCache.putIfAbsent(tx, name, document);
		collectionCache.putIfAbsent(tx, document.getID(), collection);

		return collection;
	}

	private void assertInsertion(Tx tx, Path<QNm> path, Directory parent)
			throws MetaDataException, DocumentException {
		Node<?> parentNode = parent.getMasterDocNode();
		
		// Check if object is already in cache
		while (true) {
			Item<Directory> item = itemCache.get(tx, path.toString());

			if (item == null) {
				break;
			}

			// acquire read lock for the item to keep the error stable
			mls.lockTreeShared(tx, item.getMasterDocNode().getDeweyID(), tx
					.getIsolationLevel().lockClass(false), false);

			if (!item.isDeleted()) {
				throw new MetaDataException("%s already exists.", path);
			}

			// object from the cache has been concurrently deleted. 
			// Unlock and retry.
			mls.unlockNode(tx, item.getMasterDocNode().getDeweyID());
		}

		// Check if persisted object exists
		IndexController<ElNode> indexController = mdCollection.copyFor(tx)
				.getIndexController();
		Str name = new Str(path.toString());
		Stream<? extends Node<?>> stream = indexController.openCASIndex(
				mdNameCasIndexNo, null, name, null, true,
				true, SearchMode.GREATER_OR_EQUAL);

		try {
			Node<?> child;
			if ((child = stream.next()) != null) {
				if (name.equals(child.getValue())) {
					// TODO downgrade lock
					throw new MetaDataException("%s already exists.", path);
				}
			}
		} finally {
			stream.close();
		}
	}

	private DBCollection<?> getCollectionInternal(Tx tx, Document document,
			boolean forUpdate) throws MetaDataException, DocumentException {
		DBCollection<?> collection = collectionCache.get(tx, document.getID());

		if (collection == null) {
			collection = buildCollection(tx, document);
		}

		// Put locator in cache and re-assign item because it could have
		// been loaded by another thread concurrently.
		collection = collectionCache.putIfAbsent(tx, document.getID(),
				collection);

		// Ensure that collection is bound to requesting tx
		collection = collection.copyFor(tx);

		// Announce shared access to the collection
		mls.lockNodeShared(tx, XTCdeweyID.newRootID(collection.getID()), tx
				.getIsolationLevel().lockClass(true), false);

		return collection;
	}

	private DBCollection<?> buildCollection(Tx tx, Document document)
			throws DocumentException {

		TXCollection<?> collection = null;
		Node<?> node = document.getMasterDocNode();
		boolean elementless = (node
				.getAttribute(ElCollection.PATHSYNOPSIS_ID_ATTRIBUTE) != null);
		if (elementless) {
			collection = new ElCollection(tx, elStore);
		}
		collection.init(node);
		collection.setPersistor(document);

		return collection;
	}

	private Path<QNm> asPath(String path) throws MetaDataException {
		try {
			Path<QNm> p = Path.parse(path);

			if (p.isRelative()) {
				p = p.trailing();
			}

			return p;
		} catch (PathException e) {
			throw new MetaDataException(e);
		}
	}

	private Item<Directory> getItemByPath(Tx tx, Path<QNm> path,
			boolean forUpdate) throws ItemNotFoundException, MetaDataException,
			DocumentException {
		if (!path.isAbsolute() || path.isAttribute()) {
			throw new MetaDataException("Invalid path: %s", path);
		}

		while (true) {
			Item<Directory> parent = mdRootDir;
			Item<Directory> item = null;

			item = itemCache.get(tx, path.toString());

			if (item == null) {
				for (Path<QNm> currentPath : path.explode()) {
					if ((parent != null) && (!(parent instanceof Directory))) {
						throw new MetaDataException(
								"%s is not a directory.: %s", parent.getName(),
								parent.getClass());
					}

					item = itemCache.get(tx, currentPath.toString());

					if (item == null) {
						item = loadItem(tx, (Directory) parent, currentPath);
					}

					parent = item;
				}
			} else {
				// item comes from cache -> create own copy if not assigned yet
			}

			if (forUpdate) {
				mls.lockTreeUpdate(tx, item.getMasterDocNode().getDeweyID(), tx
						.getIsolationLevel().lockClass(false), false);
			} else {
				mls.lockTreeShared(tx, item.getMasterDocNode().getDeweyID(), tx
						.getIsolationLevel().lockClass(false), false);
			}

			if (!item.isDeleted()) {
				return item;
			}

			mls.unlockNode(tx, item.getMasterDocNode().getDeweyID());
		}
	}

	private Item<Directory> getItemByID(Tx tx, int id, boolean forUpdate)
			throws ItemNotFoundException, MetaDataException, DocumentException {
		IndexController<ElNode> indexController = mdCollection.copyFor(tx)
				.getIndexController();
		Stream<? extends Node<?>> stream = indexController.openCASIndex(
				mdIDCasIndexNo, null, new Str(Integer.toString(id)), null,
				true, true, SearchMode.GREATER_OR_EQUAL);
		String name = null;

		try {
			Node<?> attribute;
			if ((attribute = stream.next()) != null) {
				if (Integer.toString(id).equals(attribute.getValue()
						.stringValue())) {
					name = attribute.getParent().getAttribute(NAME_ATTR)
							.getValue().stringValue();
				}
			}
		} finally {
			stream.close();
		}

		if (name == null) {
			throw new ItemNotFoundException("ID %s does not exist.", id);
		}

		Path<QNm> path = asPath(name);
		return getItemByPath(tx, path, forUpdate);
	}

	private Item<Directory> loadItem(Tx tx, Directory parent, Path<QNm> path)
			throws ItemNotFoundException, MetaDataException, DocumentException {
		Item<Directory> item = null;
		TXNode<?> itemRoot = null;
		;
		String name = path.toString();

		IndexController<ElNode> indexController = mdCollection.copyFor(tx)
				.getIndexController();
		Stream<? extends TXNode<?>> stream = indexController.openCASIndex(
				mdNameCasIndexNo, null, new Str(name), null, true, true,
				SearchMode.GREATER_OR_EQUAL);

		try {
			TXNode<?> attribute;
			if ((attribute = stream.next()) != null) {
				if (name.equals(attribute.getValue().stringValue())) {
					itemRoot = attribute.getParent();
				}
			}
		} finally {
			stream.close();
		}

		if (itemRoot == null) {
			throw new ItemNotFoundException("%s does not exist.", path);
		}

		item = createItem(tx, parent, itemRoot, true);

		// Put item in cache and re-assign item because it could have
		// been loaded by another thread concurrently.
		item = itemCache.putIfAbsent(tx, path.toString(), item);

		return item;
	}

	private Item<Directory> createItem(Tx tx, Directory parent,
			TXNode<?> itemRoot, boolean loadChildren) throws DocumentException,
			MetaDataException {
		Item<Directory> item;
		QNm itemRootTag = itemRoot.getName();
		String name = itemRoot.getAttribute(NAME_ATTR).getValue().stringValue();

		if (BaseCollection.DOCUMENT_TAG.equals(itemRootTag)) {
			// SubtreePrinter.print(transaction, itemRoot, System.out);
			DocID docID = new DocID(Integer.parseInt(itemRoot
					.getAttribute(BaseCollection.ID_ATTRIBUTE)
					.getValue().stringValue()));
			item = new Document(docID, name, parent, itemRoot);
		} else if (DIR_TAG.equals(itemRootTag)) {
			Directory directory = new Directory(name, parent, itemRoot);

			if (loadChildren) {
				Stream<? extends TXNode<?>> children = itemRoot.getChildren();
				TXNode<?> childRoot;
				while ((childRoot = children.next()) != null) {
					Item<Directory> child = createItem(tx, directory,
							childRoot, false);
					directory.addChild(child);
				}
				children.close();
			}
			item = directory;
		} else if (BaseBlobHandle.BLOB_TAG.equals(itemRootTag)) {
			DocID docID = new DocID(Integer.parseInt(itemRoot
					.getAttribute(BaseBlobHandle.ID_ATTRIBUTE)
					.getValue().stringValue()));
			item = new Blob(docID, name, parent, itemRoot);
		} else {
			throw new MetaDataException("Unknown item tag: %s.", itemRootTag);
		}

		return item;
	}

	@Override
	public void mkdir(Tx tx, String name) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Creating directory %s.", name));
		}

		Path<QNm> path = asPath(name);
		Item<Directory> item = getItemByPath(tx, path.leading(), false);

		if (!(item instanceof Directory)) {
			throw new MetaDataException("%s is not a directory", path.leading());
		}

		Directory parentDirectory = (Directory) item;
		assertInsertion(tx, path, parentDirectory);
		Directory directory = new Directory(name, parentDirectory, null);
		directory.create();

		// Finally put into cache and persist to make document available for
		// others
		itemCache.putIfAbsent(tx, path.toString(), directory);
	}

	@Override
	public void drop(Tx tx, String storedNamePath) throws DocumentException {
		Path<QNm> path = asPath(storedNamePath);
		Item<Directory> item = getItemByPath(tx, path, true);
		deleteItem(tx, item);
	}

	private void deleteItem(Tx tx, Item<?> item) throws MetaDataException,
			DocumentException {
		if (item instanceof Document) {
			Document document = (Document) item;
			deleteCollection(tx, document);
		} else if (item instanceof Directory) {
			Directory directory = (Directory) item;
			deleteDirectory(tx, directory);
		} else if (item instanceof Blob) {
			Blob blob = (Blob) item;
			deleteBlob(tx, blob);
		} else {
			throw new MetaDataException("Unknown item type: %s", item
					.getClass());
		}

		itemCache.remove(item.getName());
	}

	private void deleteDirectory(Tx tx, Directory directory)
			throws MetaDataException, DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Deleting directory %s.", directory
					.getName()));
		}

		if (directory.getName().isEmpty()) {
			throw new MetaDataException("Cannot delete root directory.");
		}

		// we have to assert that all children are loaded
		Stream<? extends TXNode<?>> children = directory.getMasterDocNode()
				.getChildren();
		TXNode<?> childRoot;
		while ((childRoot = children.next()) != null) {
			// TODO synchronization (load / add interleaving)
			if (!directory.hasChild(childRoot.getAttribute(NAME_ATTR)
					.getValue().stringValue())) {
				Item<Directory> child = createItem(tx, directory, childRoot,
						false);
				directory.addChild(child);
			}
		}
		children.close();

		// iterate over copy to
		// prevent ConcurrentModificationException
		for (Item<?> child : new ArrayList<Item<?>>(directory.getChildren())) {
			deleteItem(tx, child);
		}

		directory.delete();
	}

	private void deleteCollection(final Tx tx, Document document)
			throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Deleting collection %s.", document
					.getName()));
		}

		DBCollection<?> cachedCollection = collectionCache.get(tx, document
				.getID());
		final DBCollection<?> collection;

		if (cachedCollection == null) {
			collection = buildCollection(tx, document);
		} else {
			collection = cachedCollection;
		}

		// object cast is a hack for a sun compiler bug
		if (cachedCollection.getID()
				.equals(new DocID(MASTERDOC_PAGEID.value())))
			throw new DocumentException(
					"Deletion of master document forbidden.");

		// Delete document entry from meta data document
		// This will acquire an exclusive lock on the master doc node
		// and finally ensures that no concurrent transaction will have
		// access to the document after the delete. This operation
		// will block until all transactions, which have already "seen"
		// this document, i.e. have already lock on the master doc
		// node have ended. Directly afterwards, the document is
		// marked as deleted, to allow transactions queued meanwhile
		// to do verify that the document is still OK.
		document.getParent().deleteChild(document);
		document.delete();

		collectionCache.remove(document.getID());

		// finally add an post commit hook to delete document physically
		tx.addPostCommitHook(new PostCommitHook() {
			public void execute(Tx tx) throws ServerException {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Deleting locator of document %s.",
							collection.getName()));
				}
				try {
					collection.delete();
				} catch (DocumentException e) {
					throw new ServerException(e);
				}
			}
		});
	}

	@Override
	public BlobHandle getBlob(Tx tx, String storedBlobPath)
			throws DocumentException {
		Path<QNm> path = asPath(storedBlobPath);
		Item<Directory> item = getItemByPath(tx, path, false);

		if (!(item instanceof Blob)) {
			throw new MetaDataException("%s is not a blob.", path);
		}

		Blob blob = (Blob) item;
		return getBlobInternal(tx, blob, false);
	}

	@Override
	public BlobHandle getBlob(Tx tx, DocID id) throws DocumentException {
		Item<Directory> item = getItemByID(tx, id.value(), false);

		if (!(item instanceof Blob)) {
			throw new MetaDataException("%s is not a blob.", id);
		}

		Blob blob = (Blob) item;
		return getBlobInternal(tx, blob, false);
	}

	private BlobHandle getBlobInternal(Tx tx, Blob blob, boolean forUpdate)
			throws DocumentException {
		BlobHandle handle = blobCache.get(tx, blob.getID());

		if (handle == null) {
			handle = buildBlob(tx, blob);
		}

		// Put locator in cache and re-assign item because it could have
		// been loaded by another thread concurrently.
		handle = blobCache.putIfAbsent(tx, blob.getID(), handle);

		// Ensure that blob is bound to requesting tx
		handle = handle.copyFor(tx);

		return handle;
	}

	@Override
	public boolean isDirectory(Tx tx, String storedNamePath)
			throws DocumentException {
		Path<QNm> path = asPath(storedNamePath);
		Item<Directory> item;
		try {
			item = getItemByPath(tx, path, false);
		} catch (ItemNotFoundException e) {
			return false;
		}

		if (!(item instanceof Directory)) {
			return false;
		}

		return true;
	}

	@Override
	public BlobHandle putBlob(Tx tx, InputStream in, String storedNamePath,
			int containerNo) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Storing document %s.", storedNamePath));
		}

		// store persistent blob
		Path<QNm> path = asPath(storedNamePath);
		String name = path.toString();
		Item<Directory> item = getItemByPath(tx, path.leading(), false);

		if (!(item instanceof Directory)) {
			throw new MetaDataException("%s is not a directory", path.leading());
		}

		Directory directory = (Directory) item;
		assertInsertion(tx, path, directory);

		BaseBlobHandle handle = new BaseBlobHandle(tx, blobStore);
		handle.create(storedNamePath, containerNo, in);

		Blob blob = new Blob(handle.getID(), storedNamePath, directory, null);
		handle.setPersistor(blob);

		// Finally put into cache and persist to make document available for
		// others
		itemCache.putIfAbsent(tx, name, blob);
		blobCache.putIfAbsent(tx, blob.getID(), handle);
		handle.persist();

		return handle;
	}

	private void deleteBlob(final Tx tx, Blob blob) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Deleting blob %s.", blob.getName()));
		}

		BlobHandle cachedBlob = blobCache.get(tx, blob.getID());
		final BlobHandle blobHandle;

		if (cachedBlob == null) {
			blobHandle = buildBlob(tx, blob);
		} else {
			blobHandle = cachedBlob;
		}

		// Delete document entry from meta data document
		// This will acquire an exclusive lock on the master doc node
		// and finally ensures that no concurrent transaction will have
		// access to the document after the delete. This operation
		// will block until all transactions, which have already "seen"
		// this document, i.e. have already lock on the master doc
		// node have ended. Directly afterwards, the document is
		// marked as deleted, to allow transactions queued meanwhile
		// to do verify that the document is still OK.
		blob.getParent().deleteChild(blob);
		blob.delete();

		blobCache.remove(blob.getID());

		// finally add an post commit hook to delete document physically
		tx.addPostCommitHook(new PostCommitHook() {
			public void execute(Tx tx) throws ServerException {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Deleting blob %s.", blobHandle
							.getName()));
				}
				try {
					blobHandle.delete();
				} catch (DocumentException e) {
					throw new ServerException(e);
				}
			}
		});
	}

	private BlobHandle buildBlob(Tx tx, Blob blob) throws DocumentException {
		BaseBlobHandle handle = new BaseBlobHandle(tx, blobStore);
		handle.init(blob.getMasterDocNode());
		handle.setPersistor(blob);
		return handle;
	}

	@Override
	public void mv(Tx tx, String oldNamePath, String newNamePath)
			throws DocumentException {
		throw new DocumentException("Not implemented yet");
	}

	@Override
	public void shutdown() throws ServerException {
	}

	@Override
	public void start(Tx tx, boolean install) throws ServerException {
		// restart caches
		itemCache.clear();
		collectionCache.clear();
		blobCache.clear();

		try {
			if (install) {
				storeMasterDocument(tx);
			} else {
				loadMasterDocument(tx);
			}
		} catch (DocumentException e) {
			throw new ServerException(e);
		}
	}

	private void loadMasterDocument(Tx tx) throws DocumentException {
		// load metadata document and perform delayed init to load indexes
		// of metadata document etc.
		defaultDictionary.load(tx, DEFAULT_DICTIONARY_ID);
		mdCollection = new ElCollection(tx, elStore);

		mdCollection.init(MASTERDOC_NAME, MASTERDOC_PAGEID, MASTERDOC_PSPAGEID);
		TXNode<?> mdRootNode = mdCollection.getDocument().getFirstChild();
		TXNode<?> mdDirNode = mdRootNode.getLastChild();
		TXNode<?> mdDocNode = mdDirNode.getFirstChild();
		mdCollection.init(mdDocNode);
		mdNameCasIndexNo = MASTERDOC_NAME_CASINDEX_NO;
		mdIDCasIndexNo = MASTERDOC_ID_CASINDEX_NO;

		// create root directory for cache
		mdRootDir = new Directory("", null, mdDirNode);
		Path<String> rootPath = new Path<String>();

		// create master document for cache
		Document document = new Document(mdCollection.getID(), MASTERDOC_NAME,
				mdRootDir, mdDocNode);
		mdCollection.setPersistor(document);

		itemCache.putIfAbsent(tx, rootPath.toString(), mdRootDir, true);
		itemCache.putIfAbsent(tx, rootPath.copy().child(MASTERDOC_NAME)
				.toString(), document, true);
		collectionCache.putIfAbsent(tx, mdCollection.getID(), mdCollection,
				true);
	}

	private void storeMasterDocument(Tx tx) throws DocumentException,
			OperationNotSupportedException {
		StorageSpec spec = new StorageSpec(MASTERDOC_NAME, defaultDictionary);

		// store metadata document
		int dictionaryID = defaultDictionary.create(tx);
		mdCollection = new ElCollection(tx, elStore);
		mdCollection
				.create(spec, new DocumentParser(MASTERDOC_DEFAULTDOCUMENT));

		// create root directory for cache
		ElNode rootNode = mdCollection.getDocument().getFirstChild();
		TXNode<?> dirRootNode = rootNode.getLastChild();
		mdRootDir = new Directory("", null, dirRootNode);
		Path<String> rootPath = new Path<String>();

		// create master document for cache
		Document document = new Document(mdCollection.getID(), MASTERDOC_NAME,
				mdRootDir, null);
		mdCollection.setPersistor(document);

		itemCache.putIfAbsent(tx, rootPath.toString(), mdRootDir, true);
		itemCache.putIfAbsent(tx, rootPath.copy().child(MASTERDOC_NAME)
				.toString(), document, true);
		collectionCache.putIfAbsent(tx, mdCollection.getID(), mdCollection,
				true);

		List<Path<QNm>> paths = new LinkedList<Path<QNm>>();
		paths.add(Path.parse("//@name"));
		
		IndexDef idxDef = 
			IndexDefBuilder.createCASIdxDef(null, false, null, paths);
		mdCollection.getIndexController().createIndexes(idxDef);
		mdNameCasIndexNo = idxDef.getID();
		
		paths.clear();
		paths.add(Path.parse("//@id"));
		idxDef = IndexDefBuilder.createCASIdxDef(null, false, null, paths);
		mdCollection.getIndexController().createIndexes(idxDef);
		mdIDCasIndexNo = idxDef.getID();
		
		mdCollection.calculateStatistics();
		mdCollection.persist();
	}
}