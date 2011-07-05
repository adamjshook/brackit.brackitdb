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
package org.brackit.server.store.page.bracket;

import org.brackit.server.node.XTCdeweyID;

/**
 * Result object for the delete preparation in {@link BracketPage}. Contains
 * necessary information about an upcoming delete operation. If the
 * endDeleteOffset does not refer to the end of the page, then the
 * endDeleteDeweyID contains the DeweyID of the node starting at the
 * endDeleteOffset.
 * 
 * @author Martin Hiller
 * 
 */
public class DeletePreparation {

	public final XTCdeweyID previousDeweyID;
	public final int previousOffset;
	public final XTCdeweyID startDeleteDeweyID;
	public final int startDeleteOffset;
	public final XTCdeweyID endDeleteDeweyID;
	public final int endDeleteOffset;
	public final int numberOfNodes;
	public final int numberOfDataRecords;
	public final int dataRecordSize;
	public final int finalOverflowKeys;

	public DeletePreparation(XTCdeweyID previousDeweyID, int previousOffset,
			XTCdeweyID startDeleteDeweyID, int startDeleteOffset,
			XTCdeweyID endDeleteDeweyID, int endDeleteOffset,
			int numberOfNodes, int numberOfDataRecords, int dataRecordSize,
			int finalOverflowKeys) {
		this.previousDeweyID = previousDeweyID;
		this.previousOffset = previousOffset;
		this.startDeleteDeweyID = startDeleteDeweyID;
		this.startDeleteOffset = startDeleteOffset;
		this.endDeleteDeweyID = endDeleteDeweyID;
		this.endDeleteOffset = endDeleteOffset;
		this.numberOfNodes = numberOfNodes;
		this.numberOfDataRecords = numberOfDataRecords;
		this.dataRecordSize = dataRecordSize;
		this.finalOverflowKeys = finalOverflowKeys;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		out.append(String
				.format("Previous Node: %s, Offset %s\n",
						previousDeweyID,
						(previousOffset == BracketPage.LOW_KEY_OFFSET ? "LOW_ID_KEYOFFSET"
								: previousOffset)));
		out.append(String
				.format("Start Delete Node: %s, Offset %s\n",
						startDeleteDeweyID,
						(startDeleteOffset == BracketPage.LOW_KEY_OFFSET ? "LOW_ID_KEYOFFSET"
								: startDeleteOffset)));
		out.append(String
				.format("End Delete Node: %s, Offset %s\n\n",
						endDeleteDeweyID,
						(endDeleteOffset == BracketPage.KEY_AREA_END_OFFSET ? "PAGE_END_KEYOFFSET"
								: endDeleteOffset)));
		out.append(String.format("Number of Nodes (Data records): %s (%s)\n",
				numberOfNodes, numberOfDataRecords));
		out.append(String.format("Data record size: %s\n", dataRecordSize));
		out.append(String.format("Number of final Overflow Keys: %s\n",
				finalOverflowKeys));

		return out.toString();
	}
}
