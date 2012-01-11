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
package org.brackit.server.store.page.slot;

import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class FieldCachingSlottedPageTest extends SlottedPageTest {

	@Override
	public void setUp() throws Exception {
		super.setUp();
		Handle handle = new Handle(BLOCK_SIZE) {
		};
		handle.init(new PageID(3));
		page = new FieldCachingSlottedPage(null, handle);
	}

	@Test
	public void testUpdateOfFieldBug() {
		page.format(page.getHandle().getPageID());
		byte[][] elementsA = new byte[][] { new byte[] { 12 },
				new byte[] { 3 }, new byte[] { 1 }, new byte[] { 1 },
				new byte[] { 8, 0, 0, 12 }, new byte[] { 8, 0, 0, 5 } };
		byte[][] elementsB = new byte[][] { new byte[] { 12, -28 },
				new byte[] { 12, -26 } };
		Tuple toWriteA = new ArrayTuple(elementsA);
		Tuple toWriteB = new ArrayTuple(elementsB);
		verifiedWrite(0, toWriteA, true);
		verifiedWrite(1, toWriteB, true);
		byte[] updatedField = page.readField(0, 0);
		updatedField[0] = 8; // directly use returned field for update
		verifiedFieldUpdate(0, 0, updatedField);
	}
}
