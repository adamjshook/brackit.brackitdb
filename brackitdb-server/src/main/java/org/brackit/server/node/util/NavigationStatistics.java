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
package org.brackit.server.node.util;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class NavigationStatistics {
	private int elementCount;
	private int attributeCount;
	private int textCount;
	private OperationStatistics[] stats;

	public NavigationStatistics() {
		stats = new OperationStatistics[] {
				new OperationStatistics("firstChild"),
				new OperationStatistics("lastChild"),
				new OperationStatistics("nextSibling"),
				new OperationStatistics("prevSibling"),
				new OperationStatistics("attributes"), };
	}

	public void countAttribute() {
		attributeCount++;
	}

	public void countElement() {
		elementCount++;
	}

	public void countText() {
		textCount++;
	}

	public int getElementCount() {
		return elementCount;
	}

	public int getAttributeCount() {
		return attributeCount;
	}

	public int getTextCount() {
		return textCount;
	}

	@Override
	public String toString() {
		return dumpStats(0, getMaxLevel());
	}

	public int getMaxLevel() {
		int maxAvialableLevel = 0;
		for (OperationStatistics stat : stats) {
			maxAvialableLevel = Math.max(maxAvialableLevel, stat.getMaxLevel());
		}
		return maxAvialableLevel;
	}

	public String dumpStats(int minLevel, int maxLevel) {
		StringBuilder out = new StringBuilder();

		int maxAvialableLevel = getMaxLevel();

		if (maxLevel < 0) {
			maxLevel = maxAvialableLevel;
		}

		out.append(String.format("%5s |", "Level"));
		for (OperationStatistics stat : stats) {
			out.append(String.format(" %16s |", "# " + stat.getName()));
		}
		out.append("\n");
		for (int i = 0; i <= maxAvialableLevel; i++) {
			out.append(String.format("%5s |", i));
			for (OperationStatistics stat : stats) {
				out.append(String.format(" %16d |", stat.getCounter(i)));
			}
			out.append("\n");
		}
		out.append(String.format("%5s |", minLevel + ".." + maxLevel));
		out.append("\n\n");

		out.append(String.format("%5s |", "Level"));
		for (OperationStatistics stat : stats) {
			out.append(String.format(" %16s |", "Avg. " + stat.getName()));
		}
		out.append("\n");
		for (int i = 0; i <= maxAvialableLevel; i++) {
			out.append(String.format("%5s |", i));
			for (OperationStatistics stat : stats) {
				out.append(String.format(" %16d |", stat.getAvgTimings(i)));
			}
			out.append("\n");
		}
		out.append(String.format("%5s |", minLevel + ".." + maxLevel));
		for (OperationStatistics stat : stats) {
			out.append(String.format(" %16d |", stat.getAggregatedAvgTimings(
					minLevel, maxLevel)));
		}
		out.append("\n");

		return out.toString();
	}

	public OperationStatistics get(int i) {
		return stats[i];
	}
}
