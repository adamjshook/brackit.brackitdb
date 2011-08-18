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
package org.brackit.server.node.sax;

import java.util.Stack;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Stream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SaxParser {
	private static final Logger log = Logger.getLogger(SaxParser.class);

	private boolean displayNodeIDs;

	private final Stream<? extends Node<?>> scanner;

	public SaxParser(Stream<? extends Node<?>> scanner) {
		super();
		this.displayNodeIDs = false;
		this.scanner = scanner;
	}

	public void parse(QueryContext ctx, ContentHandler contentHandler)
			throws DocumentException {
		try {
			Stack<Node<?>> elementStack = new Stack<Node<?>>();
			AttributesImpl attributes = null;
			String attributeName = null;
			Node<?> node = null;

			contentHandler.startDocument();

			while ((node = scanner.next()) != null) {
				// handle closing tags and start new element if necessary
				if (node.getKind() == Kind.ELEMENT
						|| node.getKind() == Kind.TEXT) {
					int pops = 0;
					while (!elementStack.empty()
							&& (!elementStack.peek().isParentOf(node))) {
						Node<?> element = elementStack.pop();
						String elementName = decorate(element, element
								.getName());

						if (attributes != null) {
							try {
								contentHandler.startElement("", "",
										elementName, attributes);
								attributes = null;
							} catch (SAXException e) {
								scanner.close();
								throw new DocumentException(e);
							}
						}

						try {
							contentHandler.endElement("", "", elementName);
						} catch (SAXException e) {
							scanner.close();
							throw new DocumentException(e);
						}
						pops++;
					}

					if (attributes != null) {
						try {
							contentHandler.startElement("", "", elementStack
									.peek().getName(), attributes);
							attributes = null;
						} catch (SAXException e) {
							scanner.close();
							throw new DocumentException(e);
						}
					}
				}

				// call handler methods depending on node type
				if (node.getKind() == Kind.ELEMENT) {
					elementStack.push(node);
					attributes = new AttributesImpl();
				} // element
				else if (node.getKind() == Kind.ATTRIBUTE) {
					if (attributes == null) {
						attributes = new AttributesImpl();
					}

					attributes.addAttribute("", "", decorate(node, node
							.getName()), "", node.getValue());
				} // attribute
				else if (node.getKind() == Kind.TEXT) {
					try {
						char[] ch = decorate(node, node.getValue())
								.toCharArray();
						contentHandler.characters(ch, 0, ch.length);
					} catch (SAXException e) {
						scanner.close();
						throw new DocumentException(e);
					}
				} // text
			} // while
			scanner.close();

			while (!elementStack.empty()) {
				Node<?> element = elementStack.pop();
				String lastElementName = element.getName();

				if (attributes != null) {
					try {
						contentHandler.startElement("", "", lastElementName,
								attributes);
						attributes = null;
					} catch (SAXException e) {
						throw new DocumentException(e);
					}
				}

				try {
					contentHandler.endElement("", "", lastElementName);
				} catch (SAXException e) {
					throw new DocumentException(e);
				}
			}

			contentHandler.endDocument();
		} catch (Exception e) {
			log.error(e);
			throw new DocumentException(e);
		}
	}

	private String decorate(Node<?> node, String value) {
		if ((displayNodeIDs) && (node instanceof TXNode)) {
			value = ((TXNode) node).getDeweyID() + ":" + value;
		}

		return value;
	}

	public boolean isDisplayNodeIDs() {
		return displayNodeIDs;
	}

	public void setDisplayNodeIDs(boolean displayNodeIDs) {
		this.displayNodeIDs = displayNodeIDs;
	}
}