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
package org.brackit.server.node.txnode;

import org.brackit.xquery.util.log.Logger;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class DebugListener implements SubtreeListener<Node<?>> {
	private static final Logger log = Logger.getLogger(DebugListener.class);

	public void begin() throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug("Begin subtree traversal");
		}
	}

	public void end() throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug("End subtree traversal");
		}
	}

	public void startDocument() throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug("Start document");
		}
	}

	public void endDocument() throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug("End document");
		}
	}

	public void beginFragment() throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug("Begin fragment");
		}
	}

	public void endFragment() throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug("End fragment");
		}
	}

	@Override
	public void fail() throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug("Subtree traversal failed");
		}
	}

	@Override
	public <T extends Node<?>> void attribute(T node) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Attribute: %s", node));
		}
	}

	@Override
	public <T extends Node<?>> void startElement(T node)
			throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Start Element: %s", node));
		}
	}

	@Override
	public <T extends Node<?>> void endElement(T node) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("End Element: %s", node));
		}
	}

	@Override
	public <T extends Node<?>> void text(T node) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Text: %s", node));
		}
	}

	@Override
	public <T extends Node<?>> void comment(T node) throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Comment: %s", node));
		}
	}

	@Override
	public <T extends Node<?>> void processingInstruction(T node)
			throws DocumentException {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Processing Instruction: %s", node));
		}
	}
}
