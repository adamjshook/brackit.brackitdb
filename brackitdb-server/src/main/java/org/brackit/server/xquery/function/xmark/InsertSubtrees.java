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
package org.brackit.server.xquery.function.xmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.brackit.server.metadata.DBCollection;
import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.store.SearchMode;
import org.brackit.server.xquery.function.FunUtil;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.node.parser.DocumentParser;
import org.brackit.xquery.util.annotation.FunctionAnnotation;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
@FunctionAnnotation(description = "Inserts random subtrees from a file into the asia region of an XMark document.", parameters = {
		"$document", "$file" })
public class InsertSubtrees extends AbstractFunction {

	public static final QNm DEFAULT_NAME = new QNm(XMarkFun.XMARK_NSURI,
			XMarkFun.XMARK_PREFIX, "query1");

	public InsertSubtrees() {
		super(DEFAULT_NAME, new Signature(new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.STR,
				Cardinality.One)), true);
	}

	@Override
	public Sequence execute(StaticContext sctx, QueryContext ctx,
			Sequence[] args) throws QueryException {
		String name = FunUtil.getString(args, 0, "$document", null, null, true);
		DBCollection<?> coll = (DBCollection<?>) ctx.getStore().lookup(name);
		String fragmentFile = FunUtil.getString(args, 1, "$file", null, null,
				true);

		QNm asia = new QNm("asia");
		int nameIdxNo = getNameIndex(ctx, coll, asia);
		Stream<? extends Node<?>> elements = coll.getIndexController()
				.openNameIndex(nameIdxNo, asia, SearchMode.FIRST);

		Node<?> refNode = null;
		if ((refNode = elements.next()) == null) {
			elements.close();
			throw new DocumentException("No reference node found");
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
			throw new DocumentException(e);
		}
	}

	private int getNameIndex(QueryContext ctx, DBCollection<?> coll, QNm... nms)
			throws DocumentException {
		Indexes indexes = coll.get(Indexes.class);
		IndexDef nameIndex = indexes.findNameIndex(nms);
		if (nameIndex != null) {
			return nameIndex.getID();
		}
		throw new DocumentException("No name index found for document %s.",
				coll.getID());
	}
}
