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
package org.brackit.server.xquery.compiler.translator;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.index.bracket.filter.BracketFilter;
import org.brackit.server.store.index.bracket.filter.ChildPathNodeTypeFilter;
import org.brackit.server.xquery.compiler.XQExt;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.compiler.AST;
import org.brackit.xquery.compiler.Bits;
import org.brackit.xquery.compiler.XQ;
import org.brackit.xquery.compiler.translator.Binding;
import org.brackit.xquery.compiler.translator.TopDownTranslator;
import org.brackit.xquery.expr.Accessor;
import org.brackit.xquery.expr.StepExpr;
import org.brackit.xquery.xdm.Axis;
import org.brackit.xquery.xdm.Expr;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.type.NodeType;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * @author Sebastian Baechle
 * 
 */
public class DBTranslator extends TopDownTranslator {

	public DBTranslator(Map<QNm, Str> options) {
		super(options);
	}

	protected Expr anyExpr(AST node) throws QueryException {
		if (node.getType() == XQExt.MultiStepExpr) {
			return multiStepExpr(node);
		}
		return super.anyExpr(node);
	}

	private Expr multiStepExpr(AST node) throws QueryException {
		List<NodeType> tests = new ArrayList<NodeType>();
		int pos = 0;
		while (pos < node.getChildCount()) {
			AST child = node.getChild(pos);
			if (child.getType() != XQ.AxisSpec) {
				break;
			}
			pos++;
			child = node.getChild(pos++);
			tests.add(nodeTest(child, Axis.CHILD));
		}
		Accessor accessor = new MultiChildAccessor(
				tests.toArray(new NodeType[tests.size()]));
		Expr in = table.resolve(Bits.FS_DOT);

		int noOfPredicates = Math.max(node.getChildCount() - pos, 0);
		Expr[] filter = new Expr[noOfPredicates];
		boolean[] bindItem = new boolean[noOfPredicates];
		boolean[] bindPos = new boolean[noOfPredicates];
		boolean[] bindSize = new boolean[noOfPredicates];

		for (int i = 0; i < noOfPredicates; i++) {
			Binding itemBinding = table.bind(Bits.FS_DOT, SequenceType.ITEM);
			Binding posBinding = table.bind(Bits.FS_POSITION,
					SequenceType.INTEGER);
			Binding sizeBinding = table
					.bind(Bits.FS_LAST, SequenceType.INTEGER);
			filter[i] = expr(node.getChild(pos + i).getChild(0), true);
			table.unbind();
			table.unbind();
			table.unbind();
			bindItem[i] = itemBinding.isReferenced();
			bindPos[i] = posBinding.isReferenced();
			bindSize[i] = sizeBinding.isReferenced();
		}

		return new StepExpr(accessor, null, in, filter, bindItem, bindPos,
				bindSize);
	}

	private static class MultiChildAccessor extends Accessor {

		private final NodeType[] tests;
		private final Map<Integer, BracketFilter[]> filtersMap;

		public MultiChildAccessor(NodeType[] names) {
			super(Axis.CHILD);
			this.tests = names;
			this.filtersMap = new HashMap<Integer, BracketFilter[]>();
		}

		@Override
		public Stream<? extends Node<?>> performStep(Node<?> node, NodeType test)
				throws QueryException {
			BracketNode bn = (BracketNode) node;
			PSNode psNode = bn.getPSNode();
			int pcr = (psNode != null) ? psNode.getPCR() : -1;			
			BracketFilter[] filters = filtersMap.get(pcr);

			if (filters == null) {
				filters = new BracketFilter[tests.length];
				PathSynopsisMgr ps = bn.getPathSynopsis();
				BitSet matches = ps.matchChildPath(tests, pcr);
				for (int i = 0; i < tests.length; i++) {
					filters[i] = new ChildPathNodeTypeFilter(ps, tests[i],
							matches);
					// filters[i] = new NodeTypeFilter(ps, tests[i]);
				}
				filtersMap.put(pcr, filters);
			}

			return bn.getChildPath(filters);
		}

		@Override
		public Stream<? extends Node<?>> performStep(Node<?> node)
				throws QueryException {
			return null;
		}
	}
}
