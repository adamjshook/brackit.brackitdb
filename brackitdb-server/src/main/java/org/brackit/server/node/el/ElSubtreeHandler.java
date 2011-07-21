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
package org.brackit.server.node.el;

import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.txnode.SubtreeBuilder;
import org.brackit.server.tx.Tx;
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

	public ElSubtreeHandler(ElLocator locator, ElNode parent,
			XTCdeweyID rootDeweyID, SubtreeListener<ElNode>[] listener)
			throws DocumentException {
		super(parent, rootDeweyID, listener);
		this.locator = locator;
		this.tx = locator.collection.getTX();
		this.dictionary = locator.collection.getDictionary();
		this.psMgr = locator.pathSynopsis.spawnBulkPsManager(tx);
	}

	@Override
	protected ElNode buildElement(ElNode parent, String name, XTCdeweyID deweyID)
			throws DocumentException {
		int vocID = dictionary.translate(tx, name);
		PSNode psNode = psMgr.getChild(tx, parent.getPCR(), vocID,
				Kind.ELEMENT.ID);
		return new ElNode(locator, deweyID, Kind.ELEMENT.ID, null, psNode);
	}

	@Override
	protected ElNode buildText(ElNode parent, String text, XTCdeweyID deweyID)
			throws DocumentException {
		return new ElNode(locator, deweyID, Kind.TEXT.ID, text, parent.psNode);
	}

	@Override
	protected ElNode buildAttribute(ElNode parent, String name, String value,
			XTCdeweyID deweyID) throws DocumentException {
		int vocID = dictionary.translate(tx, name);
		PSNode psNode = psMgr.getChild(tx, parent.getPCR(), vocID,
				Kind.ATTRIBUTE.ID);
		return new ElNode(locator, deweyID, Kind.ATTRIBUTE.ID, value, psNode);
	}

	@Override
	protected ElNode buildComment(ElNode parent, String text, XTCdeweyID deweyID)
			throws DocumentException {
		return new ElNode(locator, deweyID, Kind.COMMENT.ID, text,
				parent.psNode);
	}

	@Override
	protected ElNode buildProcessingInstruction(ElNode parent, String text,
			XTCdeweyID deweyID) throws DocumentException {
		return new ElNode(locator, deweyID, Kind.PROCESSING_INSTRUCTION.ID,
				text, parent.psNode);
	}
}
