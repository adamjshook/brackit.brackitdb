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
package org.brackit.server.xquery.function.bdb.index;

import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.node.index.IndexController;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.definition.IndexType;
import org.brackit.server.store.SearchMode;
import org.brackit.server.xquery.function.FunUtil;
import org.brackit.server.xquery.function.bdb.BDBFun;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.expr.Cast;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.sequence.BaseIter;
import org.brackit.xquery.sequence.LazySequence;
import org.brackit.xquery.util.annotation.FunctionAnnotation;
import org.brackit.xquery.xdm.Item;
import org.brackit.xquery.xdm.Iter;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.Type;
import org.brackit.xquery.xdm.type.AnyNodeType;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
@FunctionAnnotation(description = "Scans the given path index for matching nodes.", parameters = {
		"$document", "$idx-no", "$paths" })
public class ScanPathIndex extends AbstractFunction {

	public final static QNm DEFAULT_NAME = new QNm(BDBFun.BDB_NSURI,
			BDBFun.BDB_PREFIX, "scan-path-index");

	public ScanPathIndex() {
		super(DEFAULT_NAME, new Signature(new SequenceType(
				AnyNodeType.ANY_NODE, Cardinality.ZeroOrMany),
				new SequenceType(AtomicType.STR, Cardinality.One),
				new SequenceType(AtomicType.INR, Cardinality.One),
				new SequenceType(AtomicType.STR, Cardinality.ZeroOrOne)), true);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Sequence execute(StaticContext sctx, QueryContext ctx,
			Sequence[] args) throws QueryException {
		TXQueryContext txCtx = (TXQueryContext) ctx;
		String name = FunUtil.getString(args, 0, "$document", null, null, true);
		final int idx = FunUtil.getInt(args, 1, "$idx-no", -1, null, true);

		DBCollection<?> col = (DBCollection<?>) txCtx.getStore().lookup(name);

		final IndexController<?> ic = col.getIndexController();
		IndexDef indexDef = col.get(Indexes.class).getIndexDef(idx);

		if (indexDef == null) {
			throw new QueryException(BDBFun.ERR_INDEX_NOT_FOUND,
					"Index no %s for document %s not found.", idx, name);
		}
		if (indexDef.getType() != IndexType.PATH) {
			throw new QueryException(BDBFun.ERR_INVALID_INDEX_TYPE,
					"Index no %s for document %s is not a path index.", idx,
					name);
		}
		String paths = FunUtil.getString(args, 2, "$paths", null, null, false);
		final Filter filter = (paths != null) ? ic.createPathFilter(paths
				.split(";")) : null;

		return new LazySequence() {
			@Override
			public Iter iterate() {
				return new BaseIter() {
					Stream s;

					@Override
					public Item next() throws QueryException {
						if (s == null) {
							s = ic.openPathIndex(idx, filter,
									SearchMode.LESS_OR_EQUAL);
						}
						return (Item) s.next();
					}

					@Override
					public void close() {
						if (s != null) {
							s.close();
						}
					}
				};
			}
		};
	}
}
