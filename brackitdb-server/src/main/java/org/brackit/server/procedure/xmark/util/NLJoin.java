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
package org.brackit.server.procedure.xmark.util;

import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.Tuple;
import org.brackit.xquery.operator.Cursor;
import org.brackit.xquery.xdm.Expr;
import org.brackit.xquery.xdm.Sequence;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class NLJoin implements Cursor {
	private final Cursor inner;

	private final Cursor outer;

	private final Expr predicate;

	private final int[] projections;

	private final boolean leftOuterJoin;

	private boolean delivered;

	private Tuple left;

	private Tuple right;

	public NLJoin(Cursor outer, Cursor inner, Expr predicate,
			boolean leftOuterJoin) {
		this(outer, inner, predicate, leftOuterJoin, null);
	}

	public NLJoin(Cursor outer, Cursor inner, Expr predicate,
			boolean leftOuterJoin, int... projections) {
		this.inner = inner;
		this.outer = outer;
		this.predicate = predicate;
		this.projections = projections;
		this.leftOuterJoin = leftOuterJoin;
	}

	@Override
	public void open(QueryContext ctx) throws QueryException {
		inner.open(ctx);
		outer.open(ctx);
	}

	@Override
	public Tuple next(QueryContext ctx) throws QueryException {
		while ((left != null) || ((left = outer.next(ctx)) != null)) {
			if ((right = inner.next(ctx)) == null) {
				Tuple lojTuple = null;

				if ((!delivered) && (leftOuterJoin)) {
					Tuple tmp = left.concat((Sequence) null);
					return (projections != null) ? tmp.project(projections) : tmp;
				}

				inner.close(ctx);
				inner.open(ctx);
				left = null;
				delivered = false;

				if (lojTuple != null) {
					return lojTuple;
				}

				continue;
			}

			Tuple joined = left.concat(right.array());

			if (predicate.evaluate(ctx, joined).booleanValue(ctx)) {
				delivered = true;
				return (projections != null) ? joined.project(projections) : joined;
			}
		}

		return null;
	}

	@Override
	public void close(QueryContext ctx) {
		inner.close(ctx);
		outer.close(ctx);
	}
}
