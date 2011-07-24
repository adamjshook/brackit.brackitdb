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

import org.brackit.xquery.ErrorCode;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.Tuple;
import org.brackit.xquery.expr.Axis;
import org.brackit.xquery.operator.Cursor;
import org.brackit.xquery.operator.TupleImpl;
import org.brackit.xquery.sequence.type.ItemTest;
import org.brackit.xquery.xdm.Expr;
import org.brackit.xquery.xdm.Item;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * 
 * @param <A>
 * @param <O>
 */
public class StepOp implements Cursor {
	private final Cursor in;

	private final int position;

	private final Axis axis;

	private final ItemTest test;

	private final Expr[] predicates;
	//	
	// final boolean bindItem;
	//	
	// final boolean bindPos;
	//
	// final boolean bindSize;
	//
	// final int bindCount;

	private final int[] projections;

	/*
	 * Return single output tuple when axis step does not deliver a single
	 * result
	 */
	private final boolean pipeSingle;

	private Stream<? extends Node<?>> stream;

	private Tuple a;

	private boolean foundRelative;

	public StepOp(Cursor in, int position, Axis axis, ItemTest test,
			Expr[] predicates, boolean pipeSingle) {
		this(in, position, axis, test, predicates, pipeSingle, null);
	}

	public StepOp(Cursor in, int position, Axis axis, ItemTest test,
			Expr[] predicates, boolean pipeSingle, int... projections) {
		super();
		this.in = in;
		this.position = position;
		this.axis = axis;
		this.test = test;
		this.predicates = predicates;
		this.pipeSingle = pipeSingle;
		this.projections = projections;
	}

	@Override
	public void open(QueryContext ctx) throws QueryException {
		in.open(ctx);
	}

	@Override
	public Tuple next(final QueryContext ctx) throws QueryException {
		try {
			while (true) {
				if (stream != null) {
					Node<?> next;
					while ((next = stream.next()) != null) {
						foundRelative = true;
						if (predicate(ctx, next)) {
							return (projections != null) ? a.concat(
									next.array()).project(projections) : a
									.concat((Sequence) next);
						}
					}

					stream.close();
					stream = null;

					if ((!foundRelative) && (pipeSingle)) {
						Tuple joined = a.concat((Sequence) null);
						return (projections != null) ? joined
								.project(projections) : joined;
					}
				}

				a = in.next(ctx);

				if (a == null) {
					return null;
				}

				stream = axis.performStep((Node<?>) a.get(position));
				foundRelative = false;
			}
		} catch (ClassCastException e) {
			throw new QueryException(e, ErrorCode.ERR_TYPE_NOT_A_NODE);
		}
	}

	private boolean predicate(QueryContext ctx, Item item)
			throws QueryException {
		if (!test.matches(item)) {
			return false;
		}

		// Tuple current = tuple;
		//
		// if (bindCount > 0)
		// {
		// current = new TupleImpl(current, bindItem ? item : null, bindPos ?
		// (pos = pos.inc()) : null, bindSize ? inSeqSize : null);
		// }
		//		
		// for (int i = 0; i < predicates.length; i++)
		// {
		// pos = (bindPos) ? pos : pos.inc();
		// Sequence res = predicates[i].evaluate(ctx, current);
		// if (res == null)
		// {
		// return false;
		// }
		// if (res instanceof Numeric)
		// {
		// return (((Numeric) res).cmp(pos) == 0);
		// }
		// Iter it = res.iterate();
		// try
		// {
		// Item first = it.next();
		// if ((first != null) && (it.next() == null) && (first instanceof
		// Numeric) && (((Numeric) first).cmp(pos) == 0))
		// {
		// return false;
		// }
		// }
		// finally
		// {
		// it.close();
		// }
		// if (!res.booleanValue(ctx))
		// {
		// return false;
		// }
		// }
		return true;
	}

	@Override
	public void close(QueryContext ctx) {
		in.close(ctx);

		if (stream != null) {
			stream.close();
		}
	}
}
