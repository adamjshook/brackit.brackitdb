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
package org.brackit.server.metadata.pathSynopsis.converter;

import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisNode;
import org.brackit.server.util.Calc;
import org.brackit.xquery.xdm.Kind;

/**
 * 
 * @author Sebastian Baechle
 * @author Karsten Schmidt
 * 
 */
public class PSNodeRecordAccess {
	public byte[] encodeKey(PathSynopsisNode node) {
		return Calc.fromUIntVar(node.getPCR());
	}

	// TODO: encode root's parent pcr with 0 instead of "null" and skip length
	// encoding zero-length!
	public byte[] encodeValue(PathSynopsisNode node) {
		byte[] vocID = Calc.fromUIntVar(node.getVocID());
		PathSynopsisNode parent = node.getParent();
		byte[] parentPCR = (parent != null) ? Calc.fromUIntVar(parent.getPCR())
				: null;

		int vLength = vocID.length;
		int pLength = (parentPCR != null) ? parentPCR.length : 0;
		byte[] nodeBytes = new byte[1 + vLength + pLength];

		int pos = 1;
		System.arraycopy(vocID, 0, nodeBytes, pos, vLength);
		pos += vLength;

		if (parentPCR != null) {
			System.arraycopy(parentPCR, 0, nodeBytes, pos, pLength);
			pos += pLength;
		}

		nodeBytes[0] = encode(node.getKind(), vLength, pLength);

		return nodeBytes;
	}

	protected byte encode(byte nodeType, int vocIdLength, int parentPcrLength) {
		// vocIDLength and countLength must be greater than 0 !! therefore shift
		// the bit in the infoByte!
		vocIdLength -= 1;

		int infoByte = 0;
		infoByte |= (nodeType << 4);
		infoByte |= (vocIdLength << 2);
		infoByte |= (parentPcrLength);
		return (byte) infoByte;
	}

	protected static int[] decode(byte infoByte) {
		int[] info = new int[4];
		info[3] = ((infoByte >> 6) & 3);
		info[0] = ((infoByte >> 4) & 3);
		info[1] = ((infoByte >> 2) & 3);
		info[2] = ((infoByte) & 3);

		// vocIDLength and countLength must be greater than 0 !! therefore shift
		// the bit in the infoByte!
		info[3] += 1;
		info[1] += 1;

		return info;
	}

	public static String valueToString(byte[] value) {
		int[] info = decode(value[0]);
		int type = info[0];
		int vocID = Calc.toInt(value, 1, info[1]);
		int parentPCR = -1;

		if (info[2] != 0) {
			parentPCR = Calc.toInt(value, 1 + info[1], info[2]);
		}

		if (type == Kind.ELEMENT.ID) {
			return String.format("%s(parent:%s)", vocID, parentPCR);
		} else {
			return String.format("@%s(parent:%s)", vocID, parentPCR);
		}
	}
}
