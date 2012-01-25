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
package org.brackit.server.metadata.manager.impl;

import org.brackit.server.metadata.materialize.Materializable;
import org.brackit.server.node.DocID;
import org.brackit.server.node.txnode.Persistor;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class Blob extends Item<Directory> implements Persistor {
	
	private final int collID;

	public Blob(int collID, String name, Directory parent,
			TXNode<?> masterDocNode) {
		super(name, parent, masterDocNode);
		this.collID = collID;
	}

	public int getID() {
		return collID;
	}

	@Override
	public synchronized void persist(Tx tx, Materializable materializable)
			throws DocumentException {
		Node<?> toMaterialize = materializable.materialize();

		// System.out.println("Materializing");
		// SubtreePrinter.print(transaction, toMaterialize, System.out);
		if (masterDocNode != null) {
			// System.out.println("master.xml before");
			// SubtreePrinter.print(transaction,
			// masterDocNode.getLocator().getRootNode(transaction), System.out);
			TXNode<?> myMasterDocNode = masterDocNode.copyFor(tx);
			TXNode<?> newMasterDocNode = myMasterDocNode
					.replaceWith(toMaterialize);
			masterDocNode = newMasterDocNode;
			// System.out.println("master.xml after");
			// SubtreePrinter.print(transaction,
			// masterDocNode.getLocator().getRootNode(transaction), System.out);
		} else {
			if (parent == null) {
				throw new DocumentException(
						"Cannot persist collection that is not in a directory");
			}
			TXNode<?> myParent = parent.getMasterDocNode().copyFor(tx);
			TXNode<?> newMasterDocNode = myParent.append(toMaterialize);
			masterDocNode = newMasterDocNode;
		}
	}
}
