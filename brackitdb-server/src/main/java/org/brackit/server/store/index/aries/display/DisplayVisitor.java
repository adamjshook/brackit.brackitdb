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
package org.brackit.server.store.index.aries.display;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.store.index.IndexVisitor;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.page.PageContext;

/**
 * @author Sebastian Baechle
 * 
 */
public class DisplayVisitor implements IndexVisitor {
	private final static int LEAF_ROW_COUNT = 3;

	private final static String DEFAULT_COLOR = "white";

	private final static String HIGHLIGHT_COLOR = "turquoise3";

	private final static String INSERT_COLOR = "green";

	private final PrintStream out;

	private final boolean showValues;

	private ArrayList<Entry> inserts;

	private ArrayList<Entry> highlights;

	private PageID lastTreePageID = null;

	private int lastLeafPageChildPos = 0;

	private StringBuffer nodes = new StringBuffer();

	private StringBuffer edges = new StringBuffer();

	public DisplayVisitor(PrintStream out, boolean showValues) {
		this.out = out;
		this.showValues = showValues;
		inserts = new ArrayList<Entry>();
		highlights = new ArrayList<Entry>();
	}

	public void addInsert(byte[] key, byte[] value) {
		inserts.add(new Entry(key, value));
	}

	public void highlightEntry(byte[] key, byte[] value) {
		highlights.add(new Entry(key, value));
	}

	@Override
	public void start() {
		nodes.append("node [shape=plaintext]\n");
	}

	@Override
	public void end() {
		out.append("digraph g {\n bgcolor=\"white\"\n");
		out.println();
		out.append(nodes);
		out.println();
		out.append(edges);
		out.println();
		out.append("}");
		out.flush();
	}

	@Override
	public void visitLeafPage(PageContext page, boolean overflow)
			throws IndexOperationException {
		StringBuffer pageNode = new StringBuffer();
		Field keyType = page.getKeyType();
		Field valueType = page.getValueType();
		PageID previousPage = page.getPreviousPageID();
		PageID nextPage = page.getNextPageID();
		int fieldCount = 0;

		if (previousPage != null) {
			edges
					.append(String
							.format(
									"page%s:previousPage -> page%s:nextPage [constraint=false];\n",
									page.getPageID(), previousPage));
		}

		if (nextPage != null) {
			edges
					.append(String
							.format(
									"page%s:nextPage -> page%s:previousPage [constraint=false];\n",
									page.getPageID(), nextPage));
		}

		if (overflow) {
			edges.append(String.format(
					"page%s:child%s -> page%s:page [style=invis];\n",
					lastTreePageID, lastLeafPageChildPos, page.getPageID()));
		} else {
			this.lastLeafPageChildPos++;
		}

		if (page.moveFirst()) {
			pageNode.append("<TR>");

			do {
				if (fieldCount > 0) {
					if (fieldCount % LEAF_ROW_COUNT == 0) // start new row
						pageNode.append("</TR><TR>");
				}

				String keyString = (page.getKey() != null) ? keyType
						.toString(page.getKey()) : "?";
				keyString = maskHTML(keyString);
				byte[] value = (page.getKey() != null) ? page.getValue() : null;

				String valueString = (value != null) ? valueType
						.toString(value) : "?";
				valueString = maskHTML(valueString);

				String color = DEFAULT_COLOR;
				for (Entry highlight : highlights) {
					if (Arrays.equals(highlight.key, page.getKey()))
						color = HIGHLIGHT_COLOR;
				}

				if (showValues) {
					pageNode.append(addField(keyString, valueString, null,
							color));
				} else {
					pageNode.append(addField(keyString, null, null, color));
				}

				fieldCount++;
			} while (page.hasNext());

			if (fieldCount % LEAF_ROW_COUNT != 0)
				pageNode.append(String.format("<TD COLSPAN=\"%s\"></TD>",
						LEAF_ROW_COUNT - (fieldCount % LEAF_ROW_COUNT)));

			pageNode.append("</TR>");
		}

		int rowSpan = LEAF_ROW_COUNT - 2;
		nodes.append(String.format("page%s [ label=<<TABLE BORDER=\"0\" "
				+ "CELLBORDER=\"1\" CELLSPACING=\"0\">", page.getPageID()));
		nodes.append(String.format("<TR><TD PORT=\"previousPage\">*</TD> "
				+ "<TD PORT=\"page\" COLSPAN=\"%s\">%s</TD>"
				+ "<TD PORT=\"nextPage\">*</TD></TR>", rowSpan, page
				.getPageID()));
		nodes.append(pageNode);

		nodes.append("</TABLE>>];\n");

		// increase child pos tracker
		// lastLeafPageChildPos++;
	}

	private String maskHTML(String valueString) {
		return valueString.replace(">", "&gt;").replace("<", "&lt;").replace(
				"&", "&amp;").replace("'", "&apos;").replace("\"", "&quot;");
	}

	@Override
	public void visitTreePage(PageContext page) throws IndexOperationException {
		Field keyType = page.getKeyType();
		Field valueType = page.getValueType();
		PageID pageID = page.getPageID();

		StringBuffer pageNode = new StringBuffer();
		byte[] value = null;
		int fieldCount = 0;
		int childCount = 0;

		if (page.moveFirst()) {
			pageNode.append("<TR>");

			PageID beforePageID = page.getBeforePageID();
			pageNode.append(addField("*", null, "child" + childCount,
					DEFAULT_COLOR));
			fieldCount++;
			edges.append(String.format("page%s:child%s -> page%s:page;\n",
					pageID, childCount, beforePageID));
			childCount++;

			do {
				String keyString = (page.getKey() != null) ? keyType
						.toString(page.getKey()) : "?";
				value = (page.getKey() != null) ? page.getValue() : null;

				pageNode.append(addField(keyString, null, null, DEFAULT_COLOR));
				fieldCount++;
				pageNode.append(addField("*", null, "child" + childCount,
						DEFAULT_COLOR));
				fieldCount++;

				PageID afterPageID = (value != null) ? page.getAfterPageID()
						: null;

				if (afterPageID != null) {
					edges.append(String.format(
							"page%s:child%s -> page%s:page;\n", pageID,
							childCount, afterPageID));
				}

				childCount++;
			} while (page.hasNext());
			pageNode.append("</TR>");
		}

		int rowSpan = ((fieldCount > 2) ? fieldCount - 2 : 1);
		nodes.append(String.format("page%s [ label=<<TABLE BORDER=\"0\""
				+ " CELLBORDER=\"1\" CELLSPACING=\"0\">", page.getPageID()));
		nodes.append(String.format("<TR><TD PORT=\"previousPage\">*</TD> "
				+ "<TD PORT=\"page\" COLSPAN=\"%s\">%s</TD>"
				+ "<TD PORT=\"nextPage\">*</TD></TR>", rowSpan, page
				.getPageID()));
		nodes.append(pageNode);
		nodes.append("</TABLE>>];\n");

		// reset position tracker
		lastTreePageID = pageID;
		lastLeafPageChildPos = 0;
	}

	private String addField(String key, String value, String reference,
			String color) {
		String ref = (reference != null) ? String.format("PORT=\"%s\"",
				reference) : "";

		if (value == null)
			return String.format("<TD  %s BGCOLOR=\"%s\">%s</TD>", ref, color,
					key);
		else
			return String.format("<TD %s BGCOLOR=\"%s\">%s : %s </TD>", ref,
					color, key, value);
	}
}