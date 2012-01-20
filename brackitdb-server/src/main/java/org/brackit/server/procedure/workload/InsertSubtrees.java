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
package org.brackit.server.procedure.workload;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.procedure.Procedure;
import org.brackit.server.procedure.ProcedureException;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.server.store.SearchMode;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Stream;

public class InsertSubtrees implements Procedure {
	public final static String INFO = "Inserts random subtrees in a document";

	private static final String[] PARAMETER = new String[] {
			"DOCUMENT - the target document",
			"FRAGMENT_FILE - file with the fragments to insert" };

	public Sequence execute(TXQueryContext ctx, String... params)
			throws QueryException {
		ProcedureUtil.checkParameterCount(params, 2, 2, PARAMETER);
		DBCollection<?> coll = (DBCollection<?>) ctx.getStore().lookup(
				params[0]);
		String fragmentFile = params[1];

		QNm asia = new QNm("asia");
		int nameIdxNo = getNameIndex(ctx, coll, asia);
		Stream<? extends Node<?>> elements = coll.getIndexController()
				.openNameIndex(nameIdxNo, asia, SearchMode.FIRST);

		Node<?> refNode = null;
		if ((refNode = elements.next()) == null) {
			elements.close();
			throw new ProcedureException("Now reference node found");
		}
		elements.close();

		try {
			BufferedReader reader = new BufferedReader(new FileReader(new File(
					fragmentFile)));
			StringBuffer fragment = new StringBuffer();
			int i = 1;
			for (String line = reader.readLine(); line != null; line = reader
					.readLine()) {
				System.out.println("Inserting subtree " + i);
				Node<?> subtreeRoot = refNode.append(new DocumentParser(line));
				i++;
			}

			return new Str(String.format("Inserted %s subtrees", i));
		} catch (IOException e) {
			throw new ProcedureException(e);
		}
	}

	private int getNameIndex(QueryContext ctx, DBCollection<?> coll, 
			QNm... nms)
			throws DocumentException {
		Indexes indexes = coll.get(Indexes.class);
		IndexDef nameIndex = indexes.findNameIndex(nms);
		if (nameIndex != null) {
			return nameIndex.getID();
		}
		throw new DocumentException("No name index found for document %s.",
				coll.getID());
	}

	public String getInfo() {
		return INFO;
	}

	public String getName() {
		return getClass().getSimpleName();
	}

	public String[] getParameter() {
		return new String[0];
	}
}
