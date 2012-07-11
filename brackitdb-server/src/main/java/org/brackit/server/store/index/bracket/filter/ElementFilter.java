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
package org.brackit.server.store.index.bracket.filter;

import java.util.BitSet;

import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.RecordInterpreter;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElementFilter extends BracketFilter {

	private final PathSynopsisMgr ps;
	private final QNm name;
	private final BitSet matches;

	public ElementFilter(PathSynopsisMgr ps, QNm name, BitSet matches) {
		this.ps = ps;
		this.name = name;
		this.matches = null;
	}

	@Override
	public boolean accept(DeweyIDBuffer deweyID, boolean hasRecord,
			RecordInterpreter value) {
		if (Kind.ELEMENT.ID != kind(hasRecord, value)) {
			return false;
		}
		if (name != null) {
			PSNode psn = value.getPsNode();
			if (psn == null) {
				int pcr = value.getPCR();
				try {
					psn = ps.get(pcr);
				} catch (DocumentException e) {
					return false;
				}
				value.setPsNode(psn);
			}
			int dist = deweyID.getLevel() - psn.getLevel();
			if (dist > 1) {
				throw new RuntimeException();
			}
			while (dist++ < 0) {
				psn = psn.getParent();
			}
			if (matches != null) {
				return matches.get(psn.getPCR());
			}
			return (psn.getName().atomicCmp(name) == 0);
		}
		return true;
	}

	@Override
	public boolean accept(BracketNode node) {
		// TODO Auto-generated method stub
		return false;
	}
}
