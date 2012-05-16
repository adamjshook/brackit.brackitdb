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
package org.brackit.server.xquery.function.bdb.workload;

import org.brackit.server.node.util.NavigationStatistics;
import org.brackit.server.node.util.Traverser;
import org.brackit.server.xquery.function.FunUtil;
import org.brackit.server.xquery.function.bdb.BDBFun;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.util.annotation.FunctionAnnotation;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * @author Sebastian Baechle
 * 
 */
@FunctionAnnotation(description = "Perform a tree traversal in the given the document.", parameters = {
		"$document", "$skip-attributes", "$preorder-runs", "$postorder-runs",
		"$step-limit", "$stats-only" })
public class Traverse extends AbstractFunction {

	public static final QNm DEFAULT_NAME = new QNm(BDBFun.BDB_NSURI,
			BDBFun.BDB_PREFIX, "traverse");

	public Traverse() {
		super(DEFAULT_NAME, new Signature(new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.BOOL,
				Cardinality.ZeroOrOne), new SequenceType(AtomicType.INR,
				Cardinality.ZeroOrOne), new SequenceType(AtomicType.INR,
				Cardinality.ZeroOrOne), new SequenceType(AtomicType.INR,
				Cardinality.ZeroOrOne), new SequenceType(AtomicType.BOOL,
				Cardinality.ZeroOrOne)), true);
	}

	@Override
	public Sequence execute(StaticContext sctx, QueryContext ctx,
			Sequence[] args) throws QueryException {
		String doc = FunUtil.getString(args, 0, "$document", null, null, true);
		boolean skipAttributes = FunUtil.getBoolean(args, 1,
				"$skip-attributes", false, false);
		int numberOfPreOrder = FunUtil.getInt(args, 2, "$preorder-runs", 1,
				null, false);
		int numberOfPostOrder = FunUtil.getInt(args, 3, "$postorder-runs", -1,
				null, false);
		int limit = FunUtil.getInt(args, 3, "$step-limit", -1, null, false);
		boolean statsOnly = FunUtil.getBoolean(args, 5, "$stats-only", false,
				false);

		Collection<?> coll = ctx.getStore().lookup(doc);
		Traverser traverser = new Traverser(limit, skipAttributes);
		final Node<?> rootNode = coll.getDocument();

		long start = System.nanoTime();
		NavigationStatistics stats = traverser.run(ctx, rootNode,
				numberOfPreOrder, numberOfPostOrder);

		long end = System.nanoTime();

		StringBuilder out = new StringBuilder();

		if (!statsOnly) {
			out.append(String
					.format("Required %s ms for %s traversals of %s (%d preOrder, %s postOrder)\n",
							((end - start) / 1000000), coll.getName(),
							(numberOfPreOrder + numberOfPostOrder),
							numberOfPreOrder, numberOfPostOrder));
			out.append(String.format(
					"Processed %s elements, %s attributes, %s text\n",
					stats.getElementCount(), stats.getAttributeCount(),
					stats.getTextCount()));
		}
		out.append(stats);

		return new Str(out.toString());
	}
}