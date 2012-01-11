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
package org.brackit.server.util.sort;

import java.util.Arrays;

import org.brackit.xquery.util.log.Logger;
import org.junit.Test;

public class MergeSortStrategyTest {
	private static final Logger log = Logger
			.getLogger(MergeSortStrategyTest.class);

	@Test
	public static void main(String[] args) {
		for (int i = 1; i <= 10000; i++) {
			System.out.println("Testing size " + i);
			int[][] runs = new int[i][];
			for (int j = 0; j < i; j++) {
				runs[j] = new int[] { j + 1 };
			}

			int[] res = testMerge(runs);

			for (int j = 0; j < i; j++) {
				if (res[j] != j + 1)
					throw new RuntimeException();
			}
		}
	}

	private static int[] testMerge(int[][] runs) {
		int[][] newRuns;
		int runCount = runs.length;
		int mergePhase = 0;
		boolean forward = true;

		while (runCount > 1) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Starting merge phase %s type %s",
						mergePhase, forward));
			}

			if (log.isTraceEnabled()) {
				log.trace("Merge table");
				for (int i = 0; i < runCount; i++) {
					log.trace(String.format("%3s: %s", i, Arrays
							.toString(runs[i])));
				}
			}

			int merges = runCount / 2;
			boolean singleRun = runCount % 2 == 1;
			int shift = singleRun ? 1 : 0;
			int newRunCount = merges + shift;
			newRuns = new int[newRunCount][];

			if (log.isDebugEnabled()) {
				log.debug(String.format("Merge %s -> %s (single run: %s)",
						runCount, newRunCount, singleRun));
			}

			if (forward) // all merge pairs sorted forward, hottest at the end
			{
				int pos = 0;

				if (singleRun) {
					for (int i = newRunCount - 1; i > 0; i--) {
						newRuns[pos++] = mergeArrays(runs[2 * i - 1],
								runs[2 * i]);
					}
					newRuns[newRunCount - 1] = runs[0];
				} else {
					for (int i = newRunCount - 1; i >= 0; i--) {
						newRuns[pos++] = mergeArrays(runs[2 * i],
								runs[2 * i + 1]);
					}
				}
			} else // all merge pairs sorted backward, hottest at the end
			{
				int pos = 0;

				if (singleRun) {
					for (int i = newRunCount - 1; i > 0; i--) {
						newRuns[pos++] = mergeArrays(runs[2 * i],
								runs[2 * i - 1]);
					}
					newRuns[newRunCount - 1] = runs[0];
				} else {
					for (int i = newRunCount - 1; i >= 0; i--) {
						newRuns[pos++] = mergeArrays(runs[2 * i + 1],
								runs[2 * i]);
					}
				}
			}

			forward = !forward;
			runCount = newRunCount;
			runs = newRuns;

			if (log.isDebugEnabled()) {
				log.debug(String.format("Finished merge phase %s", mergePhase));
			}
			if (log.isTraceEnabled()) {
				log.trace("Resulting Merge table");
				for (int i = 0; i < runCount; i++) {
					log.trace(String.format("%3s: %s", i, Arrays
							.toString(runs[i])));
				}
			}
			mergePhase++;
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format("Final:\n" + Arrays.toString(runs[0])));
		}
		return runs[0];
	}

	private static int[] mergeArrays(int[] a, int[] b) {
		int[] res = new int[a.length + b.length];
		System.arraycopy(a, 0, res, 0, a.length);
		System.arraycopy(b, 0, res, a.length, b.length);
		return res;
	}
}
