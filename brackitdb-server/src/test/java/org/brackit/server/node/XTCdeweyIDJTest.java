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
package org.brackit.server.node;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.brackit.xquery.xdm.DocumentException;
import org.junit.Before;
import org.junit.Test;

public class XTCdeweyIDJTest {
	Random rand = null;

	@Before
	public void setUp() {
		this.rand = new Random(1234567879);
	} // setUp

	@Test
	public void testToBytes() throws DocumentException {
		XTCdeweyID nameNodeDeweyID = new XTCdeweyID("4711[0]:1.3.3.0");
		assertEquals("Equal reconstructed deweyID with virtual name node",
				nameNodeDeweyID, new XTCdeweyID(new DocID(4711, 0),
						nameNodeDeweyID.toBytes()));

		int runs = 100000;
		for (int run = 0; run < runs; run++) {
			// random docID
			DocID docID = new DocID(this.rand.nextInt(999999999), 0);
			// random number of divisions
			int numberOfDivisions = this.rand.nextInt(25) + 1;
			// create and fill array with divisions
			int[] divisions = new int[numberOfDivisions];
			for (int i = 0; i < numberOfDivisions; i++)
				divisions[i] = this.rand.nextInt(999999) + 1;

			// create DeweyID and compare
			String deweyIDstring = docID + XTCdeweyID.documentSeparator
					+ XTCdeweyID.rootNodeDivisionValueStr;

			for (int i = 0; i < divisions.length; i++)
				deweyIDstring += XTCdeweyID.divisionSeparator + divisions[i];

			XTCdeweyID original = new XTCdeweyID(deweyIDstring);
			XTCdeweyID restored = new XTCdeweyID(docID, original.toBytes());
			assertEquals("Equal divisions in reconstructed deweyID", original,
					restored);
			assertEquals("Equal level in reconstructed deweyID", original
					.getLevel(), restored.getLevel());
		} // for each run
	} // testToBytes

	@Test
	public void testLevel() throws DocumentException {
		assertEquals("Incorrect level.", new XTCdeweyID("123456").getLevel(), 0);
		assertEquals("Incorrect level.", new XTCdeweyID(new DocID(123456, 0))
				.getLevel(), 0);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1").getLevel(),
				1);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1.3")
				.getLevel(), 2);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1.3.1")
				.getLevel(), 3);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1.3.1.3")
				.getLevel(), 4);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1.3.1.3.1")
				.getLevel(), 5);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1.3.2.3")
				.getLevel(), 3);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1.3.2.3.1")
				.getLevel(), 4);
		assertEquals("Incorrect level.", new XTCdeweyID("123456:1.3.2.3.3")
				.getLevel(), 4);
	} // testLevel

}
