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

import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.node.sax.SaxParser;
import org.brackit.server.procedure.Procedure;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.Sequence;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Sebastian Baechle
 * 
 */
public class SaxScan extends DefaultHandler implements Procedure {
	private static final String INFO = "Performas a SAX scan of the document with the default (null) handler";
	private static final String[] PARAMETER = new String[] { "DOCUMENT - name of the document" };

	int elementCount = 0;
	int attributeCount = 0;
	int textCount = 0;

	public Sequence execute(TXQueryContext ctx, String... params)
			throws QueryException {
		ProcedureUtil.checkParameterCount(params, 1, 1, PARAMETER);
		String storedNamePath = ProcedureUtil.getString(params, 0, "Document",
				null, null, true);
		Collection<?> coll = ctx.getStore().lookup(storedNamePath);

		long start = System.nanoTime();

		SaxParser parser = new SaxParser(coll.getDocument().getSubtree());
		parser.setDisplayNodeIDs(false);
		parser.parse(ctx, this);

		long end = System.nanoTime();

		return new Str(String.format(
				"Required %s ms for (%s elements, %s attributes, %s text)",
				((end - start) / 1000000), elementCount, attributeCount,
				textCount));
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		textCount++;
	}

	@Override
	public void startElement(String uri, String localName, String name,
			Attributes attributes) throws SAXException {
		elementCount++;
		attributeCount += attributes.getLength();
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
