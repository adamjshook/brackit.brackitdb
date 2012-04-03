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
package org.brackit.server.node;

import java.util.HashMap;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.io.manager.impl.SlimBufferMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr03;
import org.brackit.server.node.bracket.BracketCollection;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.node.txnode.StorageSpec;
import org.brackit.server.node.txnode.TXCollection;
import org.brackit.server.node.txnode.TXNode;
import org.brackit.server.tx.IsolationLevel;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrImpl;
import org.brackit.server.tx.locking.services.MetaLockService;
import org.brackit.server.tx.locking.services.UnifiedMetaLockService;
import org.brackit.server.tx.log.Log;
import org.brackit.server.tx.log.impl.DefaultLog;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.DocumentException;

/**
 * @author Martin Hiller
 * 
 */
public abstract class StoreMockup<E extends TXNode<E>> {

	protected final String CONTAINER_NAME;

	protected static final int CONTAINER_NO = 0;

	protected static final int BUFFER_SIZE = 512;

	protected static final int EXTEND_SIZE = 250;

	protected static final int BLOCK_SIZE = 8192;

	protected static final int INITIAL_SIZE = 250;

	protected final String LOGFILE_DIRECTORY;

	protected static final String LOGFILE_BASENAME = "tx";

	protected static final long LOGFILE_SEGMENTSIZE = 10000;

	protected Buffer buffer;

	public DictionaryMgr dictionary;

	protected MetaLockService<?> mls;

	public TxMgr taMgr;

	protected Log transactionLog;

	protected BufferMgr bufferMgr;

	public StoreMockup() throws Exception {
		this("sys", "log");
	}

	public StoreMockup(String containerName, String logfileDir)
			throws Exception {
		this.CONTAINER_NAME = containerName;
		this.LOGFILE_DIRECTORY = logfileDir;
		create();
	}

	protected void create() throws Exception {

		transactionLog = new DefaultLog(LOGFILE_DIRECTORY, LOGFILE_BASENAME,
				LOGFILE_SEGMENTSIZE);
		bufferMgr = new SlimBufferMgr(transactionLog);
		taMgr = new TaMgrImpl(transactionLog, bufferMgr);

		bufferMgr.createBuffer(BUFFER_SIZE, BLOCK_SIZE, CONTAINER_NO,
				CONTAINER_NAME, INITIAL_SIZE, EXTEND_SIZE);
		transactionLog.clear();
		transactionLog.open();

		buffer = bufferMgr.getBuffer(CONTAINER_NO);
		dictionary = new DictionaryMgr03(bufferMgr);
		mls = new UnifiedMetaLockService();

		Tx tx = taMgr.begin();
		dictionary.create(tx);
		tx.commit();
	}

	public void shutdown() throws TxException, BufferException {
		taMgr.shutdown();
		bufferMgr.shutdown();
	}

	public abstract TXCollection<E> createDocument(String name,
			SubtreeParser parser) throws DocumentException, TxException;

	public abstract TXCollection<E> createDocument(Tx tx, String name,
			SubtreeParser parser) throws DocumentException, TxException;

	public abstract TXCollection<E> createCollection(String name, SubtreeParser parser)
			throws DocumentException, TxException;

	public abstract TXCollection<E> createCollection(Tx tx, String name, SubtreeParser parser)
			throws DocumentException, TxException;

	public TXCollection<E> newTXforDocument(TXCollection<E> coll,
			boolean readOnly) throws TxException {
		return (TXCollection<E>) coll.copyFor(taMgr.begin(IsolationLevel.NONE,
				null, readOnly));
	}

}
