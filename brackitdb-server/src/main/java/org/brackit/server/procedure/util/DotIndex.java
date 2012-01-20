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
package org.brackit.server.procedure.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.procedure.Procedure;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.store.index.aries.display.DisplayVisitor;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.xdm.Sequence;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class DotIndex implements Procedure {
	private static final String INFO = "Creates a graphviz (.dot) print the given index";
	private static final String[] PARAMETER = new String[] {
			"IDXID - ID of the index", "[FILE - output file]" };

	public Sequence execute(TXQueryContext context, String... params)
			throws QueryException {
		ProcedureUtil.checkParameterCount(params, 1, 2, PARAMETER);
		PageID rootPageID = PageID.fromString(params[0]);
		File file = null;

		if (params.length > 1) {
			file = new File(params[1]);
		}

		BufferMgr bufferMgr = context.getTX().getBufferManager();

		try {
			Index index = new BPlusIndex(bufferMgr);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			PrintStream stream = (file != null) ? new PrintStream(file)
					: new PrintStream(out);
			index.traverse(context.getTX(), rootPageID, new DisplayVisitor(
					stream, true));
			index.dump(context.getTX(), rootPageID, stream);
			String result = (file != null) ? String.format(
					"Dumped index %s to file %s", rootPageID, file) : out
					.toString();
			stream.close();
			return new Str(result);
		} catch (IndexAccessException e) {
			return new Str(e.getMessage());
		} catch (FileNotFoundException e) {
			return new Str(e.getMessage());
		}
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
