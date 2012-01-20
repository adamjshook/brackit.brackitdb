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
package org.brackit.server.node.el;

import org.brackit.server.metadata.pathSynopsis.NsMapping;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.SubtreeBuilder;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.atomic.Atomic;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElSubtreeHandler extends SubtreeBuilder<ElNode> {
	private final ElLocator locator;

	private final DictionaryMgr dictionary;

	private final PathSynopsisMgr psMgr;

	private final Tx tx;

	private NsMapping nsMapping;

	public ElSubtreeHandler(ElLocator locator, ElNode parent,
			XTCdeweyID rootDeweyID, SubtreeListener<ElNode>[] listener)
			throws DocumentException {
		super(parent, rootDeweyID, listener);
		this.locator = locator;
		this.tx = locator.collection.getTX();
		this.dictionary = locator.collection.getDictionary();
		this.psMgr = locator.pathSynopsis.spawnBulkPsManager();
	}

	@Override
	protected ElNode buildElement(ElNode parent, QNm name, XTCdeweyID deweyID)
			throws DocumentException {

		PSNode psNode = psMgr.getChild(parent.getPCR(), name, Kind.ELEMENT.ID,
				nsMapping);
		nsMapping = null;

		return new ElNode(locator, deweyID, Kind.ELEMENT.ID, null, psNode);
	}

	@Override
	protected ElNode buildText(ElNode parent, Atomic text, XTCdeweyID deweyID)
			throws DocumentException {
		return new ElNode(locator, deweyID, Kind.TEXT.ID, text, parent.psNode);
	}

	@Override
	protected ElNode buildAttribute(ElNode parent, QNm name, Atomic value,
			XTCdeweyID deweyID) throws DocumentException {

		if (nsMapping != null) {
			throw new RuntimeException();
		}
		PSNode psNode = psMgr.getChild(parent.getPCR(), name,
				Kind.ATTRIBUTE.ID, null);

		return new ElNode(locator, deweyID, Kind.ATTRIBUTE.ID, value, psNode);
	}

	@Override
	protected ElNode buildComment(ElNode parent, Atomic text, XTCdeweyID deweyID)
			throws DocumentException {
		return new ElNode(locator, deweyID, Kind.COMMENT.ID, text,
				parent.psNode);
	}

	@Override
	protected ElNode buildProcessingInstruction(ElNode parent, QNm name,
			Atomic text, XTCdeweyID deweyID) throws DocumentException {

		if (nsMapping != null) {
			throw new RuntimeException();
		}
		PSNode psNode = psMgr.getChild(parent.getPCR(), name,
				Kind.PROCESSING_INSTRUCTION.ID, null);

		return new ElNode(locator, deweyID, Kind.PROCESSING_INSTRUCTION.ID,
				text, psNode);
	}

	@Override
	public void startMapping(String prefix, String uri)
			throws DocumentException {

		int prefixVocID = (prefix == null || prefix.isEmpty() ? -1 : dictionary
				.translate(tx, prefix));
		int uriVocID = (uri.isEmpty() ? -1 : dictionary.translate(tx, uri));

		if (nsMapping == null) {
			nsMapping = new NsMapping(prefixVocID, uriVocID);
		} else {
			nsMapping.addPrefix(prefixVocID, uriVocID);
		}
	}

	@Override
	public void endMapping(String prefix) throws DocumentException {
	}
}
