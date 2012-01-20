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
package org.brackit.server.store.page.keyvalue;

import java.util.ArrayList;

import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.util.Calc;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class KeyValuePageImplTest extends KeyValuePageTest {
	@Override
	protected KeyValuePageImpl createPage() throws BufferException {
		Handle handle = new Handle(BLOCK_SIZE) {
		};
		handle.init(new PageID(3));
		return new KeyValuePageImpl(null, handle);
	}

	@Test
	public void testPrepenedUntilFullLargeCompressed() {
		testWriteUntilFullLarge(true, true, false);
	}

	protected void testWriteUntilFullLarge(boolean compressed, boolean prepend,
			boolean random) {
		ArrayList<Entry> entries = new ArrayList<Entry>();
		boolean pageFull = false;
		page.format(page.getHandle().getPageID());
		int count = 500000;

		int no = 0;

		do {
			boolean success = false;
			Entry toWrite = new Entry(Calc.fromUIntVar(count), Calc
					.fromUIntVar(count--));
			int writeToPos = (random) ? (rand.nextInt(no + 1)) : ((prepend) ? 0
					: no);
			System.out.println(String.format("%3s: Writing %s to pos %s", no,
					toWrite, writeToPos));
			success = verifiedWrite(writeToPos, toWrite, compressed);

			if (success) {
				entries.add(writeToPos, toWrite);
			} else {
				pageFull = true;
			}

			for (int i = 0; i < entries.size(); i++) {
				checkReadEntry(entries.get(i), new Entry(page.getKey(i), page
						.getValue(i)));
			}

			no++;
		} while (!pageFull);
	}
}