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
package org.brackit.server.util;

import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IntValueMapTest {

	private static char[] symbols;

	static {
		symbols = new char[126 - 33 + 1];
		int pos = 0;
		for (int i = 33; i <= 126; i++) {
			symbols[pos++] = (char) i;
		}
		// System.out.println(Arrays.toString(symbols));
	}

	private Random rand;

	@Test
	public void testPutAndGetUnique() {
		IntValueMap<String> map = new IntValueMap<String>(64);
		String[] s = generate(10000, 2, 10, true);
		// System.out.println("GO");
		long start = System.currentTimeMillis();
		for (int i = 0; i < s.length; i++) {
			map.put(s[i], i);
		}
		for (int i = 0; i < s.length; i++) {
			Assert.assertEquals(i, map.get(s[i]));
		}
		long end = System.currentTimeMillis();
		// map.statistics(System.out);
		// System.out.println(end - start);
	}

	private String[] generate(int count, int minLength, int maxLength,
			boolean unique) {
		String[] s = new String[count];
		for (int i = 0; i < count; i++) {
			s[i] = generate(minLength, maxLength);
		}
		if (unique) {
			for (int i = 1; i < count; i++) {
				int j = 0;
				while (j < i) {
					if (s[j].equals(s[i])) {
						// generate new entry and restart scan
						// System.out.println("Regenerate " + i);
						s[i] = generate(minLength, maxLength);
						j = 0;
					} else {
						j++;
					}
				}
			}
		}
		// System.out.println(Arrays.toString(s));
		return s;
	}

	private String generate(int minLength, int maxLength) {
		int len = minLength + rand.nextInt(maxLength - minLength + 1);
		char[] c = new char[len];
		for (int j = 0; j < len; j++) {
			c[j] = symbols[rand.nextInt(symbols.length)];
		}
		String tmp = new String(c);
		return tmp;
	}

	private boolean duplicate(String[] s, int i) {
		String check = s[i];
		for (int j = 0; j < i; i++) {
			if (s[i].equals(check)) {
				System.out.println("duplicate");
				return true;
			}
		}
		return false;

	}

	@Before
	public void setUp() {
		rand = new Random(123456789);
	}
}
