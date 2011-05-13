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

import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsis;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisNode;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;
import org.brackit.xquery.xdm.DocumentException;

/**
 * 
 * @author Sebastian Baechle
 * @author Karsten Schmidt
 * 
 */
public class PSNodeBuilder extends PSNodeRecordAccess {
	public PathSynopsisNode decode(Tx tx, DictionaryMgr dictionary,
			PathSynopsis ps, byte[] key, byte[] value) throws DocumentException {
		PathSynopsisNode parent = null;
		int[] info = decode(value[0]);
		int pcr = Calc.toInt(key);
		int type = info[0];
		int vocID = Calc.toInt(value, 1, info[1]);

		if (info[2] != 0) {
			int parentPCR = Calc.toInt(value, 1 + info[1], info[2]);
			parent = ps.getNodeByPcr(parentPCR);
		}

		String name = dictionary.resolve(tx, vocID);
		PathSynopsisNode node = ps.getNewNode(pcr, name, vocID, (byte) type,
				parent);

		return node;
	}
}
