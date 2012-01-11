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
package org.brackit.server.store;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.util.DeweyIDGenerator;
import org.junit.Test;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SearchModeTest {
	private List<XTCdeweyID> deweyIDs;
	private List<byte[]> values;

	public SearchModeTest() throws Exception {
		deweyIDs = DeweyIDGenerator.generateDeweyIDs(new DocID(4711),
				getClass().getResource("/xmark/auction.xml").getFile());
		deweyIDs = deweyIDs.subList(0, 50);
		values = new ArrayList<byte[]>(deweyIDs.size());
		for (XTCdeweyID deweyID : deweyIDs) {
			values.add(deweyID.toBytes());
		}
	}

	@Test
	public void testInOut_FIRST() {
		testWithDeweyIDs(SearchMode.FIRST);
	}

	@Test
	public void testInOut_LAST() {
		testWithDeweyIDs(SearchMode.LAST);
	}

	@Test
	public void testInOut_LESS_OR_EQUAL() {
		testWithDeweyIDs(SearchMode.LESS_OR_EQUAL);
	}

	@Test
	public void testInOut_LESS() {
		testWithDeweyIDs(SearchMode.LESS);
	}

	@Test
	public void testInOut_GREATER_OR_EQUAL() {
		testWithDeweyIDs(SearchMode.GREATER_OR_EQUAL);
	}

	@Test
	public void testInOut_GREATER() {
		testWithDeweyIDs(SearchMode.GREATER);
	}

	@Test
	public void testInOut_GREATEST_HAVING_PREFIX() {
		testWithDeweyIDs(SearchMode.GREATEST_HAVING_PREFIX);
	}

	@Test
	public void testInOut_GREATEST_HAVING_PREFIX_RIGHT() {
		testWithDeweyIDs(SearchMode.GREATEST_HAVING_PREFIX_RIGHT);
	}

	@Test
	public void testInOut_LEAST_HAVING_PREFIX() {
		testWithDeweyIDs(SearchMode.LEAST_HAVING_PREFIX);
	}

	@Test
	public void testInOut_LEAST_HAVING_PREFIX_LEFT() {
		testWithDeweyIDs(SearchMode.LEAST_HAVING_PREFIX_LEFT);
	}

	private void testWithDeweyIDs(SearchMode mode) {
		testInsideOutside(Field.DEWEYID, values, mode);
	}

	private void testInsideOutside(Field type, List<byte[]> values,
			SearchMode mode) {
		if (mode.findGreatestInside()) {
			for (byte[] value1 : values) {
				byte[] outsideSince = null;

				for (byte[] value2 : values) {
					boolean isInside = mode.isInside(type, value2, value1);

					if ((isInside) && (outsideSince != null)) {
						fail(String
								.format(
										"%s (%s) is inside %s %s (%s) although it is not inside since %s (%s).",
										type.toString(value2), Arrays
												.toString(value2), mode, type
												.toString(value1), Arrays
												.toString(value1), type
												.toString(outsideSince), Arrays
												.toString(outsideSince)));
					}

					if ((outsideSince == null) && (!isInside)) {
						outsideSince = value2;
					}
				}
			}
		} else {
			for (byte[] value1 : values) {
				byte[] insideSince = null;

				for (byte[] value2 : values) {
					boolean isInside = mode.isInside(type, value2, value1);

					if ((!isInside) && (insideSince != null)) {
						fail(String
								.format(
										"%s (%s) is not inside %s %s (%s) although it is inside since %s (%s).",
										type.toString(value2), Arrays
												.toString(value2), mode, type
												.toString(value1), Arrays
												.toString(value1), type
												.toString(insideSince), Arrays
												.toString(insideSince)));
					}

					if ((insideSince == null) && (isInside)) {
						insideSince = value2;
					}
				}
			}
		}
	}
}
