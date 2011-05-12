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
package org.brackit.server.metadata.vocabulary;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.OpenMode;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.index.Index;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.IndexIterator;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.tx.PreCommitHook;
import org.brackit.server.tx.Tx;
import org.brackit.server.util.Calc;
import org.brackit.xquery.xdm.DocumentException;

/**
 * Uses the voc id manager to perform actions (one global vocabulary for all
 * documents)
 * 
 * @author Martin Meiringer
 * @author Sebastian Baechle
 */
public class DictionaryMgr03 implements DictionaryMgr, PreCommitHook {
	private final BufferMgr bufferMgr;

	private final Index index;

	private final VocIDMapping vocabulary;

	private int vocIdxNo;

	private int minVolatileVocID;

	public DictionaryMgr03(BufferMgr bufferMgr) {
		this.bufferMgr = bufferMgr;
		this.index = new BPlusIndex(bufferMgr);
		this.vocabulary = new ConcurrentVocIDMapping(2000);
		this.minVolatileVocID = 0;
	}

	public String resolve(Tx transaction, int vocID) throws DocumentException {
		return resolve(vocID);
	}

	public String list(Tx transaction) throws DocumentException {
		return listVocabulary();
	}

	public int translate(Tx transaction, String string)
			throws DocumentException {
		int vocID = vocabulary.translate(string);

		if (vocID < 0) {
			vocID = vocabulary.add(string);
			transaction.addPreCommitHook(this);
		}

		return vocID;
	}

	public int getSize(Tx transaction) {
		return size();
	}

	public synchronized int create(Tx transaction) throws DocumentException {
		try {
			vocIdxNo = index.createIndex(transaction, -1, Field.UINTEGER,
					Field.STRING, true, true, -1).value();
			transaction.addPreCommitHook(this);
			return vocIdxNo;
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	public synchronized void load(Tx tx, int vocIdxNo) throws DocumentException {
		try {
			this.vocIdxNo = vocIdxNo;
			this.minVolatileVocID = 0;
			PageID pageID = new PageID(vocIdxNo);
			IndexIterator iterator = index.open(tx, pageID, SearchMode.FIRST,
					null, null, OpenMode.READ);

			if (iterator.getKey() != null) {
				do {
					int vocID = Calc.toUIntVar(iterator.getKey());
					String name = Calc.toString(iterator.getValue());
					vocabulary.add(name);
					minVolatileVocID = vocID + 1;
				} while (iterator.next());
			}

			iterator.close();
		} catch (IndexAccessException e) {
			throw new DocumentException(e);
		}
	}

	private synchronized void storeVocabulary(Tx transaction)
			throws ServerException {
		PageID pageID = new PageID(vocIdxNo);
		int vocIDSize = vocabulary.size();

		for (int vocID = minVolatileVocID; vocID < vocIDSize; vocID++) {
			byte[] vocIDBytes = Calc.fromUIntVar(vocID);
			String string = vocabulary.resolve(vocID);

			if (string != null) {
				byte[] vocabularyBytes = Calc.fromString(string);
				index.insertPersistent(transaction, pageID, vocIDBytes,
						vocabularyBytes);
				minVolatileVocID = vocID + 1;
			} else {
				break;
			}
		}
	}

	public String resolve(int vocID) {
		String name = vocabulary.resolve(vocID);

		if (name == null) {
			throw new RuntimeException("Unkown vocID " + vocID);
		}

		return name;
	}

	public boolean exists(String string) {
		return vocabulary.exists(string);
	}

	public String listVocabulary() {
		StringBuffer str = new StringBuffer();
		int maxVocID = vocabulary.size();

		for (int vocID = 0; vocID < maxVocID; vocID++) {
			if (vocID < 1000)
				str.append(" ");
			if (vocID < 100)
				str.append(" ");
			if (vocID < 10)
				str.append(" ");

			str.append(vocID);
			str.append(": ");
			str.append(vocabulary.resolve(vocID));
			str.append("\n");
		}
		return str.toString();
	}

	public synchronized int size() {
		return this.vocabulary.size();
	}

	@Override
	public void abort(Tx transaction) throws ServerException {
	}

	@Override
	public void prepare(Tx transaction) throws ServerException {
		storeVocabulary(transaction);
	}
}