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
package org.brackit.server.node.el;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Sebastian Baechle
 * 
 */
public class ElementlessRecordAccessTest {
	@Test
	public void testEncodeDecode() {
		String storedValue = "1,2,3,4";

		for (byte type = 0; type < 6; type++) {
			for (int pcr = 1; pcr < 514; pcr++) {
				encodeDecode(storedValue, type, pcr);
			}
			for (int pcr = Integer.MAX_VALUE - 514; pcr < Integer.MAX_VALUE; pcr++) {
				encodeDecode(storedValue, type, pcr);
			}
		}
	}

	private void encodeDecode(String val, byte type, int pcr) {
		byte[] record = ElRecordAccess.createRecord(pcr, type, val);

		int rType = ElRecordAccess.getType(record);
		int rPCR = ElRecordAccess.getPCR(record);
		String rVal = ElRecordAccess.getValue(record);

		assertEquals("type is the same", type, rType);
		assertEquals("pcr is the same", pcr, rPCR);
		assertTrue("value is the same", val.equals(rVal));
	}
}
