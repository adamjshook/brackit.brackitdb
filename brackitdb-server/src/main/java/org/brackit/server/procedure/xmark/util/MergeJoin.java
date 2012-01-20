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
package org.brackit.server.procedure.xmark.util;

import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.Tuple;
import org.brackit.xquery.operator.Cursor;
import org.brackit.xquery.xdm.Expr;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class MergeJoin implements Cursor {
	private final Cursor leftIn;

	private final Cursor rightIn;

	private final Expr predicate;

	private final int[] leftSortSpec;

	private final int[] rightSortSpec;

	private final int[] projections;

	private Tuple left;

	private Tuple right;

	public MergeJoin(Cursor leftIn, Cursor rightIn, Expr predicate) {
		this(leftIn, rightIn, predicate, new int[] { -1 }, new int[] { -1 },
				null);
	}

	public MergeJoin(Cursor leftIn, Cursor rightIn, Expr predicate,
			int... projections) {
		this(leftIn, rightIn, predicate, new int[] { -1 }, new int[] { -1 },
				projections);
	}

	public MergeJoin(Cursor leftIn, Cursor rightIn, Expr predicate,
			int[] leftSortSpec, int[] rightSortSpec) {
		this(leftIn, rightIn, predicate, leftSortSpec, rightSortSpec, null);
	}

	public MergeJoin(Cursor leftIn, Cursor rightIn, Expr predicate,
			int[] leftSortSpec, int[] rightSortSpec, int... projections) {
		this.leftIn = leftIn;
		this.rightIn = rightIn;
		this.predicate = predicate;
		this.leftSortSpec = leftSortSpec;
		this.rightSortSpec = rightSortSpec;
		this.projections = projections;
	}

	@Override
	public void open(QueryContext ctx) throws QueryException {
		leftIn.open(ctx);
		rightIn.open(ctx);
		left = null;
	}

	@Override
	public void close(QueryContext ctx) {
		leftIn.close(ctx);
		rightIn.close(ctx);
		left = null;
	}

	@Override
	public Tuple next(QueryContext ctx) throws QueryException {
		if ((left == null) && ((left = leftIn.next(ctx)) == null)) {
			return null;
		}

		if ((right = rightIn.next(ctx)) == null) {
			return null;
		}

		do {
			// System.out.println(String.format("Checking %s join %s", left,
			// right));
			Tuple joined = left.concat(right.array());

			boolean match = predicate.evaluate(ctx, joined).booleanValue();

			if (match) {
				Tuple result = (projections != null) ? joined
						.project(projections) : joined;
				right = null;
				return result;
			}

			boolean advanceLeft = false;

			for (int i = 0; i < leftSortSpec.length; i++) {
				int leftPosition = leftSortSpec[i];
				int rightPosition = rightSortSpec[i];

				if (TupleUtil.compare(ctx, left, right, leftPosition,
						rightPosition) < 0) {
					advanceLeft = true;
					break;
				}
			}

			if (advanceLeft) {
				left = leftIn.next(ctx);
			} else {
				right = rightIn.next(ctx);
			}
		} while ((left != null) && (right != null));

		return null;
	}
}
