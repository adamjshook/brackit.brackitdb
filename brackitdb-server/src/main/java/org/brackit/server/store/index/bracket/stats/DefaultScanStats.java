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
package org.brackit.server.store.index.bracket.stats;


/**
 * @author Martin Hiller
 *
 */
public class DefaultScanStats implements ScanStats {

	public long hintPageScanCount;
	public long hintPageHits;
	public long[] neighborHits;
	public long neighborFails;
	
	public long indexAccessCount;
	
	public DefaultScanStats(int neighborLeafsToScan) {
		neighborHits = new long[neighborLeafsToScan];
	}
	
	@Override
	public String printStats() {
		long total = hintPageScanCount + indexAccessCount;
		StringBuilder out = new StringBuilder();
		
		out.append(String.format("Total Scans: %d\n", total));
		out.append(String.format("\tHintPage Scans: %d (%.2f %%)\n", hintPageScanCount, (hintPageScanCount * 100) / (float) total));
		out.append(String.format("\tIndexAccess Scans: %d (%.2f %%)\n", indexAccessCount, (indexAccessCount * 100) / (float) total));
		out.append("\n");
		out.append("HintPage Scan Details:\n");
		out.append(String.format("\tHintPage Hits: %d (%.2f %%)\n", hintPageHits, (hintPageHits * 100) / (float) hintPageScanCount));
		for (int i = 0; i < neighborHits.length; i++) {
			out.append(String.format("\tNeighbor %d Hits: %d (%.2f %%)\n", i + 1, neighborHits[i], (neighborHits[i] * 100) / (float) hintPageScanCount));
		}
		out.append(String.format("\tNeighbor Fails: %d (%.2f %%)", neighborFails, (neighborFails * 100) / (float) hintPageScanCount));
		
		return out.toString();
	}
	
	@Override
	public void clear() {
		hintPageScanCount = 0;
		hintPageHits = 0;
		for (int i = 0; i < neighborHits.length; i++) {
			neighborHits[i] = 0;
		}
		neighborFails = 0;
		indexAccessCount = 0;
	}

	@Override
	public void hintPageHit()
	{
		hintPageHits++;
	}

	@Override
	public void hintPageScan()
	{
		hintPageScanCount++;
	}

	@Override
	public void indexAccess()
	{
		indexAccessCount++;
	}
}
