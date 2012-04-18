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

import java.util.Arrays;

import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class CachingKeyValuePageImplTest extends KeyValuePageImplTest {
	@Override
	protected KeyValuePageImpl createPage() throws BufferException {
		Handle handle = new Handle(BLOCK_SIZE) {
		};
		handle.init(new PageID(3), 42);
		return new CachingKeyValuePageImpl(null, handle);
	}

	@Test
	public void testValueUpdateWithEmptyKey() {
		boolean compressed = true;
		page.format(page.getHandle().getPageID());
		Entry original = new Entry(new byte[0], new byte[12]);
		Entry update = new Entry(new byte[0], new byte[] { 3, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0 });
		if (!verifiedWrite(0, original, compressed)) {
			Assert.fail();
		}

		if (!verifiedUpdate(0, original, update, compressed)) {
			Assert.fail();
		}
	}

	@Test
	public void testUpdateOfFieldBug() {
		Entry toWriteA = new Entry(new byte[] { 12 }, new byte[] { 3 });
		Entry toWriteB = new Entry(new byte[] { 12, -28 }, new byte[] { 3 });
		page.format(page.getHandle().getPageID());
		verifiedWrite(0, toWriteA, true);
		System.out.println(page.getRecordCount());
		verifiedWrite(1, toWriteB, true);
		byte[] updateKey = page.getKey(0);
		updateKey[0] = 8; // directly use returned field for update
		page.setKey(0, updateKey);
		System.out.println(Arrays.toString(page.getKey(1)));
	}
}
