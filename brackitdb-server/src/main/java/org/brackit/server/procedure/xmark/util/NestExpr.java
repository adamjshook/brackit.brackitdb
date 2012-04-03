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
import org.brackit.xquery.operator.TupleImpl;
import org.brackit.xquery.xdm.Expr;
import org.brackit.xquery.xdm.Item;
import org.brackit.xquery.xdm.Sequence;

/**
 * This is an experimental expr that allows to treat some tuple entries as
 * tuples itself. This can be very useful to avoid tons of coding stuff for
 * node.getName/Value/XXX. Just treat it like a tuple. ;-)
 * 
 * @author Sebastian Baechle
 * 
 */
public class NestExpr implements Expr {
	private final Expr expr;

	private final int[][] positions;

	public NestExpr(Expr expr, int[][] positions) {
		super();
		this.expr = expr;
		this.positions = positions;
	}

	@Override
	public Sequence evaluate(QueryContext ctx, Tuple tuple)
			throws QueryException {
		Sequence[] extracted = new Sequence[positions.length];
		for (int i = 0; i < positions.length; i++) {
			Tuple t = tuple;

			for (int j = 0; j < positions[i].length; j++) {
				int position = positions[i][j];
				t = (Tuple) t.get(position);
			}

			extracted[i] = (Sequence) t;
		}

		return expr.evaluate(ctx, new TupleImpl(extracted));
	}

	@Override
	public Item evaluateToItem(QueryContext ctx, Tuple tuple)
			throws QueryException {
		Sequence[] extracted = new Sequence[positions.length];
		for (int i = 0; i < positions.length; i++) {
			Tuple t = tuple;

			for (int j = 0; j < positions[i].length; j++) {
				int position = positions[i][j];
				t = (Tuple) t.get(position);
			}

			extracted[i] = (Sequence) t;
		}

		return expr.evaluateToItem(ctx, new TupleImpl(extracted));
	}

	@Override
	public boolean isUpdating() {
		return expr.isUpdating();
	}

	@Override
	public boolean isVacuous() {
		return false;
	}
}
