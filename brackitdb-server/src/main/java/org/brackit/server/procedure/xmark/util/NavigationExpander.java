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
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.node.stream.AtomStream;
import org.brackit.xquery.node.stream.EmptyStream;
import org.brackit.xquery.node.stream.filter.Filter;
import org.brackit.xquery.node.stream.filter.FilteredStream;
import org.brackit.xquery.operator.Cursor;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Expr;
import org.brackit.xquery.xdm.Kind;
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
public class NavigationExpander implements Cursor {
	public static abstract class Step {
		protected Expr predicate;
		protected Str name;
		protected Str value;
		protected Kind kind;

		public Step(Expr predicate, Str name, Str value, Kind kind) {
			super();
			this.predicate = predicate;
			this.name = name;
			this.value = value;
			this.kind = kind;
		}

		abstract Stream<? extends Node<?>> evaluate(QueryContext ctx,
				Node<?> node) throws QueryException;

		protected Stream<? extends Node<?>> filter(final QueryContext ctx,
				Stream<? extends Node<?>> in) throws QueryException {
			if (predicate != null) {
				in = new FilteredStream<Node<?>>(in, new Filter<Node<?>>() {
					@Override
					public boolean filter(Node<?> node)
							throws DocumentException {
						try {
							return !predicate.evaluate(ctx, node).booleanValue();
						} catch (QueryException e) {
							throw new DocumentException(e);
						}
					}
				});
			}

			if (kind != null) {
				in = new FilteredStream<Node<?>>(in, new Filter<Node<?>>() {
					@Override
					public boolean filter(Node<?> node)
							throws DocumentException {
						return node.getKind() != kind;
					}
				});
			}

			if (name != null) {
				in = new FilteredStream<Node<?>>(in, new Filter<Node<?>>() {
					@Override
					public boolean filter(Node<?> node)
							throws DocumentException {
						return !name.str.equals(node.getName());
					}
				});
			}

			if (value != null) {
				in = new FilteredStream<Node<?>>(in, new Filter<Node<?>>() {
					@Override
					public boolean filter(Node<?> node)
							throws DocumentException {
						return !name.equals(node.getValue());
					}
				});
			}

			return in;
		}
	}

	public static class Child extends Step {
		public Child(Expr predicate, Str name, Str value, Kind kind) {
			super(predicate, name, value, kind);
		}

		@Override
		Stream<? extends Node<?>> evaluate(QueryContext ctx, Node<?> node)
				throws QueryException {
			return filter(ctx, node.getChildren());
		}
	}

	public static class Attribute extends Step {
		private Str name;

		public Attribute(Expr predicate, Str name, Str value) {
			super(predicate, null, value, null);
			this.name = name;
		}

		@Override
		Stream<? extends Node<?>> evaluate(QueryContext ctx, Node<?> node)
				throws QueryException {
			Node<?> attribute = node.getAttribute(name.stringValue());
			return (attribute == null) ? new EmptyStream<Node<?>>() : filter(
					ctx, new AtomStream<Node<?>>(attribute));
		}
	}

	public static class Ancestor extends Step {
		private int count;

		public Ancestor(Expr predicate, Str name, Str value, Kind kind,
				int count) {
			super(predicate, name, value, kind);
			this.count = count;
		}

		@Override
		Stream<? extends Node<?>> evaluate(QueryContext ctx, Node<?> node)
				throws QueryException {
			Node<?> ancestor = node;

			for (int i = 0; i < count && ancestor != null; i++) {
				ancestor = ancestor.getParent();
			}

			return (ancestor == null) ? new EmptyStream<Node<?>>() : filter(
					ctx, new AtomStream<Node<?>>(ancestor));
		}
	}

	private final Cursor in;

	private final int position;

	private final Step step;

	private final int[] projections;

	private final boolean eliminate;

	private Stream<? extends Node<?>> stream;

	private Tuple a;

	private boolean foundRelative;

	public NavigationExpander(Cursor in, int position, Step step,
			boolean eliminate) {
		this(in, position, step, eliminate, null);
	}

	public NavigationExpander(Cursor in, int position, Step step,
			boolean eliminate, int... projections) {
		super();
		this.in = in;
		this.position = position;
		this.step = step;
		this.projections = projections;
		this.eliminate = eliminate;
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
					if ((next = stream.next()) != null) {
						foundRelative = true;
						Tuple tmp = a.concat(next);
						return (projections != null) ? tmp.project(projections)
								: tmp;
					}

					stream.close();
					stream = null;

					if ((!eliminate) && (!foundRelative)) {
						Tuple joined = a.concat((Sequence) null);
						return (projections != null) ? a.project(projections)
								: joined;
					}
				}

				a = in.next(ctx);

				if (a == null) {
					return null;
				}

				stream = step.evaluate(ctx, (Node<?>) a.get(position));
				foundRelative = false;
			}
		} catch (ClassCastException e) {
			throw new QueryException(e, ErrorCode.ERR_TYPE_NOT_A_NODE);
		}
	}

	@Override
	public void close(QueryContext ctx) {
		in.close(ctx);

		if (stream != null) {
			stream.close();
		}
	}
}
