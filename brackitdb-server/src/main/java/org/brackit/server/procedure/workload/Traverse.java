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
package org.brackit.server.procedure.workload;

import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.node.util.NavigationStatistics;
import org.brackit.server.node.util.Traverser;
import org.brackit.server.procedure.Procedure;
import org.brackit.server.procedure.ProcedureException;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;

/**
 * @author Sebastian Baechle
 * 
 */
public class Traverse implements Procedure {
	private static final String INFO = "DOM-Traverses a document";
	private static final String[] PARAMETER = new String[] {
			"DOCUMENT - name of the document",
			"[SKIP ATTRIBUTES] - skip attributes in traversal",
			"[PRE_ORDER_RUNS] - number of preOrder traversals",
			"[POST_ORDER_RUNS] - number of postOrder traversals",
			"[STEP_LIMIT] - max number of elements",
			"[STATS_ONLY] - return operation statistics only." };

	public Sequence execute(TXQueryContext ctx, String... params)
			throws QueryException {
		int numberOfPostOrder = 0;
		int numberOfPreOrder = 1;
		int limit = -1;
		boolean skipAttributes = false;
		boolean statsOnly = false;
		;

		ProcedureUtil.checkParameterCount(params, 1, 5, PARAMETER);

		try {
			if (params.length > 1)
				skipAttributes = Boolean.parseBoolean(params[1]);

			if (params.length > 2)
				numberOfPreOrder = Integer.parseInt(params[2]);

			if (params.length > 3)
				numberOfPostOrder = Integer.parseInt(params[3]);

			if (params.length > 4)
				limit = Integer.parseInt(params[4]);

			if (params.length > 5)
				statsOnly = Boolean.parseBoolean(params[5]);
		} catch (NumberFormatException e) {
			throw new ProcedureException("Invalid parameters: %s",
					(Object[]) params);
		}

		Collection<?> coll = ctx.getStore().lookup(params[0]);
		Traverser traverser = new Traverser(limit, skipAttributes);
		final Node<?> rootNode = coll.getDocument();

		long start = System.nanoTime();
		NavigationStatistics stats = traverser.run(ctx, rootNode,
				numberOfPreOrder, numberOfPostOrder);

		long end = System.nanoTime();

		StringBuilder out = new StringBuilder();

		if (!statsOnly) {
			out
					.append(String
							.format(
									"Required %s ms for %s traversals of %s (%d preOrder, %s postOrder)\n",
									((end - start) / 1000000), coll.getName(),
									(numberOfPreOrder + numberOfPostOrder),
									numberOfPreOrder, numberOfPostOrder));
			out.append(String.format(
					"Processed %s elements, %s attributes, %s text\n", stats
							.getElementCount(), stats.getAttributeCount(),
					stats.getTextCount()));
		}
		out.append(stats);

		return new Str(out.toString());
	}

	public String getInfo() {
		return INFO;
	}

	public String getName() {
		return getClass().getSimpleName();
	}

	public String[] getParameter() {
		return PARAMETER;
	}
}