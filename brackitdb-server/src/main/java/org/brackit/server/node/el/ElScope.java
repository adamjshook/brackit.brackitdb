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

import java.util.Collection;
import java.util.Iterator;

import org.brackit.server.metadata.pathSynopsis.NsMapping;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.stream.EmptyStream;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Scope;
import org.brackit.xquery.xdm.Stream;

/**
 * Scope implementation for EL element scopes.
 * 
 * @author Martin Hiller
 * 
 */
public class ElScope implements Scope {

	private final ElNode element;
	private NsMapping nsMapping;
	private final DictionaryMgr dictionary;
	private final Tx tx;

	private class LookupStream implements Stream<String> {

		private Iterator<Integer> iter;

		public LookupStream(Collection<Integer> vocIDs) {
			this.iter = vocIDs.iterator();
		}

		@Override
		public void close() {
			iter = null;
		}

		@Override
		public String next() throws DocumentException {
			if (iter == null || !iter.hasNext()) {
				close();
				return null;
			}
			return dictionary.resolve(null, iter.next());
		}
	}

	public ElScope(ElNode element) {
		this.element = element;
		this.nsMapping = element.psNode.getNsMapping();
		this.dictionary = element.locator.collection.getDictionary();
		this.tx = element.getTX();
	}

	@Override
	public Stream<String> localPrefixes() throws DocumentException {
		if (nsMapping == null) {
			return new EmptyStream<String>();
		}
		return new LookupStream(nsMapping.getPrefixSet());
	}

	@Override
	public String defaultNS() throws DocumentException {
		return resolvePrefix("");
	}

	@Override
	public void addPrefix(String prefix, String uri) throws DocumentException {

		// translates strings to VocIDs
		int prefixVocID = dictionary.translate(tx, prefix);
		int uriVocID = dictionary.translate(tx, uri);

		NsMapping newMapping = null;
		if (nsMapping != null) {
			if (nsMapping.contains(prefixVocID, uriVocID)) {
				// exactly the same mapping already exists
				return;
			}

			// copy current NsMapping
			newMapping = nsMapping.copy();

			// add new prefix
			newMapping.addPrefix(prefixVocID, uriVocID);

		} else {
			newMapping = new NsMapping(prefixVocID, uriVocID);
		}

		// change mapping, leave name unchanged (unless current prefix is
		// overwritten in the new mapping)
		element.setNsMappingAndName(newMapping, element.getName());

		nsMapping = newMapping;
	}

	@Override
	public String resolvePrefix(String prefix) throws DocumentException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDefaultNS(String uri) throws DocumentException {
		addPrefix("", uri);
	}

}
