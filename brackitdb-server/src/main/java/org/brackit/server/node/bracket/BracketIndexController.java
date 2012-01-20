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
package org.brackit.server.node.bracket;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.encoder.BracketCASFilter;
import org.brackit.server.node.bracket.encoder.BracketPathFilter;
import org.brackit.server.node.bracket.encoder.PCRClusterEncoder;
import org.brackit.server.node.bracket.encoder.PCRClusterPathEncoder;
import org.brackit.server.node.bracket.encoder.SplidClusterEncoder;
import org.brackit.server.node.bracket.encoder.SplidClusterPathEncoder;
import org.brackit.server.node.index.definition.Cluster;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.txnode.IndexControllerImpl;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.store.Field;
import org.brackit.server.tx.TxException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.ListenMode;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.util.path.Path;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Type;

/**
 * @author Sebastian Baechle
 * @author Martin Hiller
 * 
 */
public class BracketIndexController extends IndexControllerImpl<BracketNode> {

	private final BracketCollection collection;

	public BracketIndexController(BracketCollection collection) {
		super(collection, collection.store.nameIndex,
				collection.store.pathIndex, collection.store.casIndex);
		this.collection = collection;
	}

	@Override
	public void createIndexes(IndexDef... indexDefinitions)
			throws DocumentException {
		try {
			long undoNextLSN = collection.getTX().checkPrevLSN();
			super.createIndexes(indexDefinitions);
			collection.persist();
			collection.getTX().logDummyCLR(undoNextLSN);
		} catch (TxException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void calculateStatistics() throws DocumentException {
		try {
			long undoNextLSN = collection.getTX().checkPrevLSN();
			super.calculateStatistics();
			collection.persist();
			collection.getTX().logDummyCLR(undoNextLSN);
		} catch (TxException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	public void dropIndex(IndexDef idxDefinition) throws DocumentException {
		try {
			// Ensure exclusive access to the collection
			
			// TODO exclusive lock on collection
//			collection.store.mls
//					.lockTreeExclusive(collection.getTX(), XTCdeweyID
//							.newRootID(collection.getID()), collection.getTX()
//							.getIsolationLevel().lockClass(true), false);

			long undoNextLSN = collection.getTX().checkPrevLSN();
			super.dropIndex(idxDefinition);
			collection.persist();
			collection.getTX().logDummyCLR(undoNextLSN);
		} catch (TxException e) {
			throw new DocumentException(e);
		}
	}

	@Override
	protected SubtreeListener<? super BracketNode> createIndexBuilder(
			IndexDef idxDef, int containerNo) throws DocumentException {
		IndexEncoder<BracketNode> encoder;

		switch (idxDef.getType()) {
		case NAME:
			return nameIndex.createBuilder(collection.getTX(), this,
					containerNo, idxDef);
		case CAS:
			encoder = (idxDef.getClustering() == Cluster.SPLID) ? new SplidClusterEncoder(
					collection, idxDef.getContentType())
					: new PCRClusterEncoder(collection, idxDef.getContentType());
			return casIndex.createBuilder(collection.getTX(), encoder,
					new BracketCASFilter(idxDef.getPaths(), collection),
					containerNo, idxDef);
		case PATH:
			encoder = (idxDef.getClustering() == Cluster.SPLID) ? new SplidClusterPathEncoder(
					collection)
					: new PCRClusterPathEncoder(collection);
			return pathIndex.createBuilder(collection.getTX(), encoder,
					new BracketPathFilter(idxDef.getPaths(), collection),
					containerNo, idxDef);
		default:
			throw new DocumentException("Index type %s not supported yet.",
					idxDef.getType());
		}
	}

	@Override
	protected SubtreeListener<? super BracketNode> createIndexListener(
			IndexDef idxDef, ListenMode mode) throws DocumentException {
		IndexEncoder<BracketNode> encoder;

		switch (idxDef.getType()) {
		case NAME:
			return nameIndex.createListener(collection.getTX(), this, mode,
					idxDef);
		case CAS:
			encoder = (idxDef.getClustering() == Cluster.SPLID) ? new SplidClusterEncoder(
					collection, idxDef.getContentType())
					: new PCRClusterEncoder(collection, idxDef.getContentType());
			BracketCASFilter casFilter = new BracketCASFilter(
					idxDef.getPaths(), collection);
			return casIndex.createListener(collection.getTX(), encoder,
					casFilter, mode, idxDef);
		case PATH:
			encoder = (idxDef.getClustering() == Cluster.SPLID) ? new SplidClusterPathEncoder(
					collection)
					: new PCRClusterPathEncoder(collection);
			BracketPathFilter pathFilter = new BracketPathFilter(idxDef
					.getPaths(), collection);
			return pathIndex.createListener(collection.getTX(), encoder,
					pathFilter, mode, idxDef);
		default:
			throw new DocumentException("Index type %s not supported yet.",
					idxDef.getType());
		}
	}

	@Override
	public IndexEncoder<BracketNode> getCasIndexEncoder(Type contentType,
			Field keyType, Field valueType) throws DocumentException {
		if ((valueType == Field.DEWEYIDPCR)
				|| (valueType == Field.FULLDEWEYIDPCR)) {
			return new SplidClusterEncoder(collection, contentType);
		}
		if ((valueType == Field.PCRDEWEYID)
				|| (valueType == Field.PCRFULLDEWEYID)) {
			return new PCRClusterEncoder(collection, contentType);
		}
		throw new DocumentException("Unsupported case index value type: %s",
				valueType);
	}
	
	@Override
	public IndexEncoder<BracketNode> getNameIndexEncoder()
			throws DocumentException {
		return new SplidClusterPathEncoder(collection);
	}

	@Override
	public IndexEncoder<BracketNode> getPathIndexEncoder(Field keyType,
			Field valueType) throws DocumentException {
		if ((keyType == Field.DEWEYID) || (keyType == Field.FULLDEWEYID)) {
			return new SplidClusterPathEncoder(collection);
		}
		if ((keyType == Field.PCRDEWEYID) || (keyType == Field.PCRFULLDEWEYID)) {
			return new PCRClusterPathEncoder(collection);
		}
		throw new DocumentException("Unsupported path index key type: %s",
				keyType);
	}

	@Override
	public Filter<BracketNode> createCASFilter(String... queryString)
			throws DocumentException {
		List<Path<QNm>> paths = new ArrayList<Path<QNm>>(queryString.length);
		for (String path : queryString)
			paths.add(Path.parse(path));
		return new BracketCASFilter(paths, collection);
	}

	@Override
	public Filter<BracketNode> createPathFilter(String... queryString)
			throws DocumentException {
		List<Path<QNm>> paths = new ArrayList<Path<QNm>>(
				queryString.length);
		for (String path : queryString)
			paths.add(Path.parse(path));
		return new BracketPathFilter(paths, collection);
	}

	@Override
	public Filter<BracketNode> createCASFilter(List<Path<QNm>> paths)
			throws DocumentException {
		return new BracketPathFilter(new ArrayList<Path<QNm>>(paths),
				collection);
	}

	@Override
	public Filter<BracketNode> createPathFilter(List<Path<QNm>> paths)
			throws DocumentException {
		return new BracketPathFilter(new ArrayList<Path<QNm>>(paths),
				collection);
	}
}
