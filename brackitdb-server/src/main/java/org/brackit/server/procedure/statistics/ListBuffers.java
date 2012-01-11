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
 * Lists statistics about the buffers, TODO: consolidate with listbuffer
 * procedure (only one procedure is necessary for showing all buffers or a
 * distinct one (in detail))
 * 
 * @author Sebastian Baechle
 * @author Karsten Schmidt
 * 
 */
public class ListBuffers implements Procedure, DynamicProcedure {
	private final String INFO = "Dumps information about the buffers..";
	private static final ArrayList<InfoContributor> ic = new ArrayList<InfoContributor>();
	private static final String[] PARAMETER = new String[] {};

	@Override
	public Sequence execute(TXQueryContext ctx, String... params)
			throws QueryException {
		ProcedureUtil.checkParameterCount(params, 0, 0, PARAMETER);
		StringBuilder out = new StringBuilder();
		for (InfoContributor i : ic)
			out.append(i.getInfo());
		return new Str(out.toString());
	}

	@Override
	public String getInfo() {
		return INFO;
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
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
