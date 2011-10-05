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
package org.brackit.server.metadata.pathSynopsis;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Namespace mapping on VocID basis. The VocID -1 for the prefix defines the
 * default namespace. -1 as VocID for the URI represents the empty string.
 * 
 * @author Martin Hiller
 * 
 */
public class NsMapping {
	
	private final Map<Integer, Integer> nsMap;
	private boolean finalized = false;
	
	public NsMapping(int intialPrefixVocID, int intialUriVocID) {
		this.nsMap = new TreeMap<Integer, Integer>();
		this.nsMap.put(intialPrefixVocID, intialUriVocID);
	}
	
	public void finalize() {
		finalized = true;
	}
	
	public void addPrefix(int prefixVocID, int uriVocID) {
		if (finalized) {
			throw new RuntimeException("This namespace mapping is already final!");
		}
		nsMap.put(prefixVocID, uriVocID);
	}
	
	public Set<Integer> getPrefixSet() {
		return nsMap.keySet();
	}
	
	public Set<Map.Entry<Integer, Integer>> getEntrySet() {
		return nsMap.entrySet();
	}
	
	public Integer resolve(int prefixVocID) {
		return nsMap.get(prefixVocID);
	}

	@Override
	public int hashCode() {
		return nsMap.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NsMapping other = (NsMapping) obj;
		return nsMap.equals(other.nsMap);
	}
	
	
}
