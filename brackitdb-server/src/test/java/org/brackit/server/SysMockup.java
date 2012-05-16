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
package org.brackit.server;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.manager.impl.BufferMgrMockup;
import org.brackit.server.metadata.vocabulary.DictionaryMgr;
import org.brackit.server.metadata.vocabulary.DictionaryMgr03;
import org.brackit.server.store.index.aries.BPlusIndex;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrMockup;
import org.brackit.server.tx.locking.services.MetaLockService;
import org.brackit.server.tx.locking.services.UnifiedMetaLockService;
import org.brackit.xquery.xdm.DocumentException;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SysMockup {

	public static final String CONTAINER_NAME = "junit.cnt";

	public static final int CONTAINER_NO = 0;
	public static final int BUFFER_SIZE = 200;
	public static final int EXTEND_SIZE = 300;
	public static final int BLOCK_SIZE = 512;
	public static final int LARGE_BLOCK_SIZE = 4096 * 2;
	public static final int INITIAL_SIZE = 20;

	public int containerNo = CONTAINER_NO;
	public int bufferSize = BUFFER_SIZE;
	public int extendSize = EXTEND_SIZE;
	public int blockSize = LARGE_BLOCK_SIZE;
	public int initialSize = INITIAL_SIZE;
	public String containerName = CONTAINER_NAME;

	public TxMgr taMgr;

	public BPlusIndex index;

	public Buffer buffer;

	public DictionaryMgr dictionary;

	public BufferMgrMockup bufferManager;

	public MetaLockService<?> mls;

	public SysMockup(int blockSize) throws Exception {
		this.blockSize = blockSize;
		create(true);
	}
	
	public SysMockup() throws Exception {
		create(true);
	}

	public SysMockup(boolean createDictionary) throws Exception {
		create(createDictionary);
	}

	public void create(boolean createDictionary) throws BufferException,
			TxException, DocumentException {
		taMgr = new TaMgrMockup();
		bufferManager = (BufferMgrMockup) taMgr.getBufferManager();
		bufferManager.createBuffer(bufferSize, blockSize, containerNo,
				containerName, initialSize, extendSize);
		buffer = bufferManager.getBuffer(containerNo);
		dictionary = new DictionaryMgr03(bufferManager);
		mls = new UnifiedMetaLockService();
		if (createDictionary) {
			Tx tx = taMgr.begin();
			dictionary.create(tx);
			tx.commit();
		}
	}

	public Buffer recreateBuffer() throws BufferException {
		bufferManager.dropBuffer(containerNo);
		bufferManager.createBuffer(bufferSize, blockSize, containerNo,
				containerName, initialSize, extendSize);
		buffer = bufferManager.getBuffer(containerNo);
		return buffer;
	}
}
