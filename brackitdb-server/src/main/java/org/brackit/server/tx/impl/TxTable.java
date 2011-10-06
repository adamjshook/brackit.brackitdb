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
package org.brackit.server.tx.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxID;

/**
 * Thread-safe transaction table for the ARIES restart procedure.
 * 
 * @author Sebastian Baechle
 * 
 */
class TxTable {
	private HashMap<TxID, TX> entries;

	private TaMgrImpl taMgr;

	TxTable(TaMgrImpl taMgr) {
		this.entries = new HashMap<TxID, TX>();
		this.taMgr = taMgr;
	}

	synchronized void remove(TxID txID) {
		entries.remove(txID);
	}

	synchronized void put(TxID txID, TX transaction) {
		TX current = entries.put(txID, transaction);

		if (current != null) {
			entries.put(txID, current);
			throw new RuntimeException(String.format(
					"A transaction with ID %s already exists.", txID));
		}
	}

	synchronized TX get(TxID txID) {
		return entries.get(txID);
	}

	synchronized TX getNextTransactionForUndo() {
		TX transaction = null;
		long nextUndoLSN = -1;

		for (TX entry : entries.values()) {
			if (entry.checkPrevLSN() > nextUndoLSN) {
				nextUndoLSN = entry.checkPrevLSN();
				transaction = entry;
			}
		}

		return transaction;
	}

	synchronized Collection<TX> getRolledbackLosers() {
		ArrayList<TX> rolledBackLosers = new ArrayList<TX>();

		for (TX entry : entries.values()) {
			if (entry.checkPrevLSN() == -1) {
				rolledBackLosers.add(entry);
			}
		}

		return rolledBackLosers;
	}

	synchronized Collection<TX> getTransactions() {
		return new ArrayList<TX>(entries.values());
	}

	public synchronized TxID getMaxTxID() {
		TxID maxTxID = null;

		for (TX entry : entries.values()) {
			if ((maxTxID == null) || entry.getID().compareTo(maxTxID) > 0) {
				maxTxID = entry.getID();
			}
		}

		return maxTxID;
	}

	public synchronized void clear() {
		entries.clear();
	}

	@Override
	public String toString() {
		StringBuffer out = new StringBuffer();
		for (Tx tx : getTransactions()) {
			out.append(tx);
			out.append("\n");
		}
		return out.toString();
	}
}