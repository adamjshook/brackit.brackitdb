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
package org.brackit.server.node.util;

import org.brackit.xquery.QueryContext;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class Traverser {
	private final int limit;

	private final boolean skipAttributes;

	public Traverser(int limit, boolean skipAttributes) {
		this.limit = limit;
		this.skipAttributes = skipAttributes;
	}

	public NavigationStatistics run(QueryContext ctx, Node<?> rootNode,
			int numberPreOrderTraversals, int numberPostOrderTraversals)
			throws DocumentException {
		NavigationStatistics stats = new NavigationStatistics();
		int level = -1;

		long start = System.nanoTime();

		for (int i = 0; i < numberPreOrderTraversals; i++) {
			checkSubtreePreOrder(stats, ctx, rootNode, -1, 0);

			if (numberPreOrderTraversals > 1) {
				try {
					Thread.sleep(2000);
				} catch (Exception e) {
				}
			}
		}

		for (int i = 0; i < numberPostOrderTraversals; i++) {
			checkSubtreePostOrder(stats, ctx, rootNode, -1, 0);

			if (numberPostOrderTraversals > 1) {
				try {
					Thread.sleep(2000);
				} catch (Exception e) {
				}
			}
		}

		long end = System.nanoTime();

		return stats;
	}

	private int checkSubtreePreOrder(NavigationStatistics stats,
			QueryContext ctx, Node<?> node, int level, int processed)
			throws DocumentException {
		Node<?> child = null;
		level++;

		if ((limit > 0) && (processed++ >= limit)) {
			return processed;
		}

		if (node.getKind() == Kind.ELEMENT) {
			stats.countElement();

			if (!skipAttributes) {
				processed = traverseAttributes(stats, ctx, node, level,
						processed);
			}

			for (Node<?> c = firstChild(stats, ctx, node, level); c != null; c = nextSibling(
					stats, ctx, c, level)) {
				processed = checkSubtreePreOrder(stats, ctx, c, level,
						processed);
			}
		} else if (node.getKind() == Kind.TEXT) {
			stats.countText();
			Atomic text = node.getValue();
		} else {
			throw new DocumentException("Unexpected node type: %s", node);
		}

		level--;
		return processed;
	}

	private int checkSubtreePostOrder(NavigationStatistics stats,
			QueryContext ctx, Node<?> node, int level, int processed)
			throws DocumentException {
		Node<?> child = null;
		level++;

		if ((limit > 0) && (processed++ >= limit)) {
			return processed;
		}

		if (node.getKind() == Kind.ELEMENT) {
			stats.countElement();

			if (!skipAttributes) {
				processed = traverseAttributes(stats, ctx, node, level,
						processed);
			}

			for (Node<?> c = lastChild(stats, ctx, node, level); c != null; c = prevSibling(
					stats, ctx, c, level)) {
				processed = checkSubtreePostOrder(stats, ctx, c, level,
						processed);
			}
		} else if (node.getKind() == Kind.TEXT) {
			stats.countText();
			Atomic text = node.getValue();
		} else {
			throw new DocumentException("Unexpected node type: %s", node);
		}

		level--;
		return processed;
	}

	private Node<?> nextSibling(NavigationStatistics stats, QueryContext ctx,
			Node<?> node, int level) throws DocumentException {
		long start = System.nanoTime();
		Node<?> ns = node.getNextSibling();
		long end = System.nanoTime();

		stats.get(2).addTiming(level, (end - start));
		return ns;
	}

	private Node<?> firstChild(NavigationStatistics stats, QueryContext ctx,
			Node<?> node, int level) throws DocumentException {
		long start = System.nanoTime();
		Node<?> fc = node.getFirstChild();
		long end = System.nanoTime();

		stats.get(0).addTiming(level, (end - start));
		return fc;
	}

	private Node<?> prevSibling(NavigationStatistics stats, QueryContext ctx,
			Node<?> node, int level) throws DocumentException {
		long start = System.nanoTime();
		Node<?> ps = node.getPreviousSibling();
		long end = System.nanoTime();

		stats.get(3).addTiming(level, (end - start));
		// System.out.println(ps + "<--" + node);
		return ps;
	}

	private Node<?> lastChild(NavigationStatistics stats, QueryContext ctx,
			Node<?> node, int level) throws DocumentException {
		long start = System.nanoTime();
		Node<?> lc = node.getLastChild();
		long end = System.nanoTime();

		stats.get(1).addTiming(level, (end - start));
		// System.out.println(node + "\\__" + lc);
		return lc;
	}

	private Stream<? extends Node<?>> attributes(NavigationStatistics stats,
			QueryContext ctx, Node<?> node, int level) throws DocumentException {
		long start = System.nanoTime();
		Stream<? extends Node<?>> attributes = node.getAttributes();
		long end = System.nanoTime();

		stats.get(4).addTiming(level, (end - start));
		return attributes;
	}

	private int traverseAttributes(NavigationStatistics stats,
			QueryContext ctx, Node<?> node, int level, int processed)
			throws DocumentException {
		Stream<? extends Node<?>> attributes = attributes(stats, ctx, node,
				level);
		Node<?> attribute;

		while ((attribute = attributes.next()) != null) {
			processed++;
			stats.countAttribute();
			QNm attributeName = attribute.getName();
			Atomic attributeValue = attribute.getValue();
		}
		attributes.close();

		return processed;
	}
}