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
package org.brackit.server.store.index.aries;

import org.brackit.server.store.index.Index;
import org.brackit.server.util.Calc;
import org.brackit.xquery.util.Cfg;
import org.junit.Test;

public class TestBPlusIndexBugs extends AbstractBPlusIndexTest {
	@Test
	public void testSlottedPageDeleteCompressedOverflowsPageBug()
			throws Exception {
		// use slotted pages
		Cfg.set(Index.PAGE_VERSION, 1);
		setUp();

		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1891700), Calc
				.fromUIntVar(1891700));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1892200), Calc
				.fromUIntVar(1892200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1892800), Calc
				.fromUIntVar(1892800));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1892900), Calc
				.fromUIntVar(1892900));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1893000), Calc
				.fromUIntVar(1893000));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1893100), Calc
				.fromUIntVar(1893100));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1893900), Calc
				.fromUIntVar(1893900));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1894200), Calc
				.fromUIntVar(1894200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1894500), Calc
				.fromUIntVar(1894500));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1894800), Calc
				.fromUIntVar(1894800));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1895200), Calc
				.fromUIntVar(1895200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1895300), Calc
				.fromUIntVar(1895300));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1895900), Calc
				.fromUIntVar(1895900));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1896100), Calc
				.fromUIntVar(1896100));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1897200), Calc
				.fromUIntVar(1897200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1897600), Calc
				.fromUIntVar(1897600));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1897900), Calc
				.fromUIntVar(1897900));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1898000), Calc
				.fromUIntVar(1898000));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1899100), Calc
				.fromUIntVar(1899100));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1899300), Calc
				.fromUIntVar(1899300));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1899500), Calc
				.fromUIntVar(1899500));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1893600), Calc
				.fromUIntVar(1893600));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1891200), Calc
				.fromUIntVar(1891200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1892100), Calc
				.fromUIntVar(1892100));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1890300), Calc
				.fromUIntVar(1890300));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1899000), Calc
				.fromUIntVar(1899000));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1893200), Calc
				.fromUIntVar(1893200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1890000), Calc
				.fromUIntVar(1890000));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1898800), Calc
				.fromUIntVar(1898800));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1896700), Calc
				.fromUIntVar(1896700));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1891900), Calc
				.fromUIntVar(1891900));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1896400), Calc
				.fromUIntVar(1896400));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1896000), Calc
				.fromUIntVar(1896000));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1889900), Calc
				.fromUIntVar(1889900));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1899200), Calc
				.fromUIntVar(1899200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1895400), Calc
				.fromUIntVar(1895400));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1889800), Calc
				.fromUIntVar(1889800));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1896200), Calc
				.fromUIntVar(1896200));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1897500), Calc
				.fromUIntVar(1897500));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1893400), Calc
				.fromUIntVar(1893400));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1897700), Calc
				.fromUIntVar(1897700));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1891100), Calc
				.fromUIntVar(1891100));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1897300), Calc
				.fromUIntVar(1897300));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1892000), Calc
				.fromUIntVar(1892008));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1892900), Calc
				.fromUIntVar(1892900));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1899100), Calc
				.fromUIntVar(1899100));
		// The following delete lead to an inconsistent (slotted) page because
		// the delete logic
		// for compressed entries consumed temporary space in a full page
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1889900), Calc
				.fromUIntVar(1889900));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1894800), Calc
				.fromUIntVar(1894800));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1897700), Calc
				.fromUIntVar(1897700));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1892500), Calc
				.fromUIntVar(1892534));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1893200), Calc
				.fromUIntVar(1893200));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1896000), Calc
				.fromUIntVar(1896000));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1899400), Calc
				.fromUIntVar(1899423));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1894600), Calc
				.fromUIntVar(1894605));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1896100), Calc
				.fromUIntVar(1896100));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1893400), Calc
				.fromUIntVar(1893400));
		index.delete(t1, uniqueRootPageID, Calc.fromUIntVar(1896400), Calc
				.fromUIntVar(1896400));
		index.insert(t1, uniqueRootPageID, Calc.fromUIntVar(1896900), Calc
				.fromUIntVar(1896921));
	}
}
