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
package org.brackit.server.procedure.statistics;

import java.util.ArrayList;

import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.procedure.DynamicProcedure;
import org.brackit.server.procedure.InfoContributor;
import org.brackit.server.procedure.Procedure;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.xdm.Sequence;

/**
 * Lists the currently established connections.
 * 
 * @author Sebastian Baechle
 * @author Karsten Schmidt
 * 
 */
public class ListConnections implements Procedure, DynamicProcedure {
	private static final String INFO = "Dumps the currently established connections";
	private static final String[] PARAMETER = new String[] {};
	private static final ArrayList<InfoContributor> ic = new ArrayList<InfoContributor>(
			1);

	public Sequence execute(TXQueryContext context, String... params)
			throws QueryException {
		ProcedureUtil.checkParameterCount(params, 0, 0, PARAMETER);
		StringBuilder out = new StringBuilder();
		out.append("Active connections:");
		for (InfoContributor i : ic) {
			out.append("\n");
			out.append(i.getInfo());
		}
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

	@Override
	public void addInfoContributor(InfoContributor info) {
		ic.add(info);
	}

	@Override
	public void removeInfoContributor(InfoContributor info) {
		ic.remove(info);
	}
}
