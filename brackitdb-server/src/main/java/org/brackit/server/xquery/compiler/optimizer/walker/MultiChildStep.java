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
package org.brackit.server.xquery.compiler.optimizer.walker;

import org.brackit.server.xquery.DBCompileChain;
import org.brackit.server.xquery.compiler.XQExt;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.XQuery;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.compiler.AST;
import org.brackit.xquery.compiler.XQ;
import org.brackit.xquery.compiler.optimizer.walker.Walker;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.xdm.Function;

/**
 * @author Sebastian Baechle
 * 
 */
public class MultiChildStep extends Walker {

	public MultiChildStep(StaticContext sctx) {
		super(sctx);
	}

	@Override
	protected AST visit(AST node) {
		if (node.getType() != XQ.PathExpr) {
			return node;
		}

		snapshot();
		boolean checkInput = false;
		boolean skipDDO = false;
		int len = 0;
		int stepCount = node.getChildCount();		
		
		for (int i = 1; i < node.getChildCount(); i++) {
			AST step = node.getChild(i);
			boolean childStep = ((step.getType() == XQ.StepExpr) && (getAxis(step) == XQ.CHILD));
			boolean hasPredicate = (step.getChildCount() > 2);
			if (childStep) {
				skipDDO |= step.checkProperty("skipDDO");
				checkInput |= step.checkProperty("checkInput");
				if (!hasPredicate) {
					len++;
				} else {
					if (len > 1) {					
						merge(node, i - len, len);
						i -= len;
					}
					len = 0;
				}
			} else {
				if (len > 1) {
					merge(node, i - len, len - 1);
					i -= len - 1;
				}
				len = 0;
			}
		}
		if (len > 0) {
			merge(node, node.getChildCount() - len, len - 1);
		}
		
		return node;
	}

	private void merge(AST node, int start, int len) {
		AST multistep = new AST(XQExt.MultiStepExpr, XQExt.toName(XQExt.MultiStepExpr));
		for (int j = 0; j <= len; j++) {
			AST pstep = node.getChild(start);
			for (int k = 0; k < pstep.getChildCount(); k++) {
				multistep.addChild(pstep.getChild(k).copyTree());
			}
			node.deleteChild(start);
		}
		node.insertChild(start, multistep);
		snapshot();
	}
	
	public static void main(String[] args) throws Exception {
		//new XQuery(new DBCompileChain(null, null), "let $a := <x/> return $a/b/c/d//e/x/y/z//u/v/w");
		new XQuery(new DBCompileChain(null, null), "let $a := <x/> return $a/b/@aha");
	}

	/**
	 * Try to infer if this path step returns only a single item or the empty
	 * sequence
	 */
	private boolean isAtomicOrEmpty(AST step) {
		// TODO
		// Life would be great if we already had static typing...
		if (step.getType() == XQ.ContextItemExpr) {
			return true;
		}
		if (step.getType() == XQ.FunctionCall) {
			int childCount = step.getChildCount();
			QNm name = (QNm) step.getValue();
			Function fun = sctx.getFunctions().resolve(name, childCount);
			return fun.getSignature().getResultType().getCardinality()
					.atMostOne();
		}
		if (step.getType() == XQ.VariableRef) {
			// TODO check if if we can derive information
			// about this variable (e.g., if for-bound)
		}
		return false;
	}

	protected boolean sortAfterStep(AST node, int position, int lastPosition)
			throws QueryException {
		AST child = node.getChild(position);

		if (child.getType() != XQ.StepExpr) {
			return true;
		}

		int axis = getAxis(child);
		if ((axis == XQ.CHILD) || (axis == XQ.ATTRIBUTE) || (axis == XQ.SELF)) {
			return true;
		}
		return false;
	}

	private boolean isForwardStep(AST step) {
		return ((step.getType() == XQ.StepExpr) && isForwardAxis(getAxis(step)));
	}

	private boolean isBackwardStep(AST step) {
		return ((step.getType() == XQ.StepExpr) && !isForwardAxis(getAxis(step)));
	}

	private boolean isDescOrDescOSStep(AST step) {
		if (step.getType() != XQ.StepExpr) {
			return false;
		}
		int axis = getAxis(step);
		return ((axis == XQ.DESCENDANT) || (axis == XQ.DESCENDANT_OR_SELF));
	}

	private boolean isForwardAxis(int axis) {
		return ((axis == XQ.CHILD) || (axis == XQ.DESCENDANT)
				|| (axis == XQ.ATTRIBUTE) || (axis == XQ.SELF)
				|| (axis == XQ.DESCENDANT_OR_SELF)
				|| (axis == XQ.FOLLOWING_SIBLING) || (axis == XQ.FOLLOWING));
	}

	private boolean isReverseAxis(int axis) {
		return !isForwardAxis(axis);
	}

	private int getAxis(AST stepExpr) {
		return stepExpr.getChild(0).getChild(0).getType();
	}
}
