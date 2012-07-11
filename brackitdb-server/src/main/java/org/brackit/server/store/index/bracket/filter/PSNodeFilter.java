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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.RecordInterpreter;
import org.brackit.xquery.xdm.DocumentException;

/**
 * Only accepts nodes with a given PSNode.
 * 
 * @author Martin Hiller
 * 
 */
public class PSNodeFilter extends BracketFilter {

	private final PathSynopsisMgr psMgr;
	private final Set<Integer> descendents;
	private final List<Integer> temp;
	
	private final int pcr;
	private final PSNode psNode;
	private final int level;
	private final boolean checkLeafsOnly;
	
	public PSNodeFilter(PathSynopsisMgr psMgr, int pcr, boolean checkLeafsOnly) throws DocumentException {
		this(psMgr, psMgr.get(pcr), checkLeafsOnly);
	}
	
	public PSNodeFilter(PathSynopsisMgr psMgr, PSNode psNode, boolean checkLeafsOnly) {
		this.psMgr = psMgr;
		this.psNode = psNode;
		this.pcr = psNode.getPCR();
		this.level = psNode.getLevel();
		this.descendents = new HashSet<Integer>();
		this.descendents.add(psNode.getPCR());
		this.temp = new ArrayList<Integer>();
		this.checkLeafsOnly = checkLeafsOnly;
	}

	@Override
	public boolean accept(DeweyIDBuffer deweyID, boolean hasRecord,
			RecordInterpreter value) {
		
		if (hasRecord) {
			
			if (value.getPCR() == pcr) {
				value.setPsNode(psNode);
				return true;
			} else {
				return false;
			}
			
		} else {
			
			if (checkLeafsOnly) {
				return false;
			}
			
			// delivered record might belong to a descendent of this node
			try {
				if (deweyID.getLevel() != level) {
					return false;
				}
				
				int currentPCR = value.getPCR();
				if (descendents.contains(currentPCR)) {
					// PCR is definitely a descendent
					value.setPsNode(psNode);
					return true;
				}
				
				// lookup node
				PSNode current = psMgr.get(currentPCR);
				temp.add(currentPCR);
				while (current.getLevel() > level) {
					
					current = current.getParent();
					currentPCR = current.getPCR();
					if (descendents.contains(currentPCR)) {
						descendents.addAll(temp);
						temp.clear();
						value.setPsNode(psNode);
						return true;
					}
					temp.add(currentPCR);
				}
				
				temp.clear();
				return false;
				
			} catch (DocumentException e) {
				throw new RuntimeException("TODO: Proper error handling.");
			}
		}
		
	}

	@Override
	public boolean accept(BracketNode node) {
		return node.getPCR() == pcr;
	}

}
