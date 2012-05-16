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
package org.brackit.server.xquery.function.bdb.statistics;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.services.LockServiceClient;
import org.brackit.server.xquery.function.FunUtil;
import org.brackit.server.xquery.function.bdb.BDBFun;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.util.annotation.FunctionAnnotation;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * Lists the lock tables
 * 
 * @author Sebastian Baechle
 * @author Karsten Schmidt
 * 
 */

@FunctionAnnotation(description = "Dumps the currently lock tables.", parameters = { "$onlyOwnLocks" })
public class ListLocks extends AbstractFunction {

	public static final QNm DEFAULT_NAME = new QNm(BDBFun.BDB_NSURI,
			BDBFun.BDB_PREFIX, "list-locks");

	public ListLocks() {
		super(DEFAULT_NAME, new Signature(new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.BOOL,
				Cardinality.ZeroOrOne)), true);
	}

	protected static final List<InfoContributor> ic = new ArrayList<InfoContributor>();

	public static void add(InfoContributor info) {
		ic.add(info);
	}

	public static void remove(InfoContributor info) {
		ic.remove(info);
	}

	@Override
	public Sequence execute(StaticContext sctx, QueryContext ctx,
			Sequence[] args) throws QueryException {
		boolean self = FunUtil.getBoolean(args, 0, "onlyOwnLocks", false,
				false);
		StringBuilder out = new StringBuilder();
		if (self) {
			Tx tx = ((TXQueryContext) ctx).getTX();
			for (LockServiceClient lsc : tx.getLockCB().getLockServiceClients()) {
				out.append(lsc.listLocks());
			}
		} else {
			for (InfoContributor i : ic) {
				if (i != null) {
					out.append(i.getInfo());
				}
			}
		}
		return new Str(out.toString());
	}
}
