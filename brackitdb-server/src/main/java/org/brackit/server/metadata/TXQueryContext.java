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
package org.brackit.server.metadata;

import org.brackit.server.metadata.manager.MetaDataMgr;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Store;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class TXQueryContext extends QueryContext {
	final Tx tx;
	final MetaDataMgr mdm;

	public static class MDMStore implements Store {

		final Tx tx;
		final MetaDataMgr mdm;

		public MDMStore(Tx tx, MetaDataMgr mdm) {
			this.tx = tx;
			this.mdm = mdm;
		}
		
		@Override
		public Collection<?> create(String name, SubtreeParser parser)
				throws DocumentException {
			return mdm.create(tx, name, parser);
		}

		@Override
		public void drop(String name) throws DocumentException {
			mdm.drop(tx, name);
		}

		@Override
		public Collection<?> lookup(String name) throws DocumentException {
			return mdm.lookup(tx, name);
		}

		@Override
		public void makeDir(String path) throws DocumentException {
			mdm.mkdir(tx, path);
		}
	}

	public TXQueryContext(Tx tx, MetaDataMgr mdm) {
		super(new MDMStore(tx, mdm));
		this.tx = tx;
		this.mdm = mdm;
	}

	public Tx getTX() {
		return tx;
	}

	public MetaDataMgr getMDM() {
		return mdm;
	}
}
