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
package org.brackit.server.xquery;

import org.brackit.server.BrackitDB;
import org.brackit.server.ServerException;
import org.brackit.server.XQueryBaseTest;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.XQuery;
import org.brackit.xquery.compiler.CompileChain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Max Bechtold
 *
 */
public class RegressionTest extends XQueryBaseTest {

	private static BrackitDB db;
	private static CompileChain chain;
	private static TXQueryContext ctx;
	private static Tx tx;
	
	private static final boolean install = true;
	
	@Before
	public void setup() throws Exception {
		db = new BrackitDB(install);
		
		if (install) {
			System.out.println("Storing document...");
			tx = db.getTaMgr().begin();
			storeFile("xmark2.xml", "/xmark/xmark2.xml");
			tx.commit();
		}
		
		tx = db.getTaMgr().begin();
		chain = new DBCompileChain(db.getMetadataMgr(), tx);
		ctx = new TXQueryContext(tx, db.getMetadataMgr());
	}
	
	@Test
	public void testOutOfMemoryTicket55() throws Exception {
		XQuery xq = new XQuery(chain, "fn:doc('xmark2.xml')//item//@category");
//		print(xq.execute(ctx));
		System.out.println(xq.execute(ctx).size());
	}
	
	@After
	public void after() throws ServerException {
		tx.commit();
		db.shutdown();
	}
}
