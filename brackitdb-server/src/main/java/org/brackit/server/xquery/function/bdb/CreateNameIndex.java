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
package org.brackit.server.xquery.function.bdb;

import java.util.HashMap;
import java.util.Map;

import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.node.index.definition.Cluster;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.definition.IndexDefBuilder;
import org.brackit.server.xquery.DBCompileChain;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.xdm.Iter;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;

/**
 * Function for creating name indexes on stored documents, optionally restricted
 * to a set of QNames. If successful, this function returns statistics about the
 * newly created index as an XML fragment. Supported signatures are:</br>
 * <ul>
 * <li>
 * <code>bdb:create-name-index($coll as xs:string, $include as xs:QName*) as 
 * node()</code></li>
 * <li><code>bdb:create-name-index($coll as xs:string) as node()</code></li>
 * </ul>
 * 
 * @author Max Bechtold
 * 
 */
public class CreateNameIndex extends AbstractFunction {

	public final static QNm CREATE_NAME_INDEX = new QNm(
			DBCompileChain.BDB_NSURI, DBCompileChain.BDB_PREFIX,
			"create-name-index");

	public CreateNameIndex(QNm name, Signature signature) {
		super(name, signature, true);
	}

	@Override
	public Sequence execute(QueryContext ctx, Sequence[] args)
			throws QueryException {
		TXQueryContext txCtx = (TXQueryContext) ctx;
		DBCollection<?> col = (DBCollection<?>) txCtx.getStore().lookup(
				((Str) args[0]).str);
		Map<QNm, Cluster> include = new HashMap<QNm, Cluster>();
		if (args.length > 1 && args[1] != null) {
			Iter it = args[1].iterate();
			QNm next = (QNm) it.next();
			while (next != null) {
				include.put(next, Cluster.SPLID);
				next = (QNm) it.next();
			}
		}

		IndexDef idxDef = IndexDefBuilder.createSelectiveNameIdxDef(
				Cluster.SPLID, include);
		col.getIndexController().createIndexes(idxDef);
		return idxDef.materialize();
	}

}
