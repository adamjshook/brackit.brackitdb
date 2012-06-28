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

import java.util.Arrays;

import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.RecordInterpreter;

/**
 * 
 * @author Martin Hiller
 *
 */
public class MultiFilter extends BracketFilter {

	public enum Type {
		CONJUNCTION {
			@Override
			public boolean accept(boolean[] result) {
				for (boolean b : result) {
					if (!b) {
						return false;
					}
				}
				return true;
			}
		},
		DISJUNCTION {
			@Override
			public boolean accept(boolean[] result) {
				for (boolean b : result) {
					if (b) {
						return true;
					}
				}
				return false;
			}
		};
		
		public abstract boolean accept(boolean[] result);
	}
	
	private final Type type;
	private final BracketFilter[] filters;
	private boolean[] lastResults;
	
	public MultiFilter(Type type, BracketFilter... filters) {
		this.type = type;
		this.filters = filters;
	}
	
	@Override
	public boolean accept(DeweyIDBuffer deweyID, boolean hasRecord,
			RecordInterpreter value) {
		
		boolean[] accepts = acceptInternal(deweyID, hasRecord, value);
		lastResults = accepts;
		return type.accept(accepts);
	}

	@Override
	public boolean accept(BracketNode node) {
		
		boolean[] accepts = acceptInternal(node);
		lastResults = accepts;
		return type.accept(accepts);
	}
	
	public boolean[] getLastResults() {
		return Arrays.copyOf(lastResults, lastResults.length);
	}
	
	private boolean[] acceptInternal(DeweyIDBuffer deweyID, boolean hasRecord,
			RecordInterpreter value) {
		
		boolean[] result = new boolean[filters.length];		
		for (int i = 0; i < filters.length; i++) {
			result[i] = filters[i].accept(deweyID, hasRecord, value);
		}	
		return result;
	}
	
	private boolean[] acceptInternal(BracketNode node) {
		
		boolean[] result = new boolean[filters.length];		
		for (int i = 0; i < filters.length; i++) {
			result[i] = filters[i].accept(node);
		}	
		return result;
	}
}
