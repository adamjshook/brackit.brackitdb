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
package org.brackit.server.metaData.manager.impl;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.metadata.manager.impl.MetaDataMgrImpl;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxMgr;

/**
 * Test Mockup for JUnit tests.
 * 
 * @author Sebastian Baechle
 * 
 */
public class MetaDataMgrMockup extends MetaDataMgrImpl {
	private static final int CONTAINER_NO = 0;

	private static final int BUFFER_SIZE = 500;

	private static final int EXTEND_SIZE = 300;

	private static final int BLOCK_SIZE = 2048;

	private static final int INITIAL_SIZE = 20;

	public MetaDataMgrMockup(TxMgr taMgr) throws ServerException {
		super(taMgr);
		start(taMgr);
	}

	public MetaDataMgrMockup(TxMgr taMgr, String containerName)
			throws ServerException {
		super(taMgr);
		createContainer(taMgr, containerName);
		start(taMgr);
	}

	private void createContainer(TxMgr taMgr, String containerName)
			throws BufferException {
		taMgr.getBufferManager().createBuffer(BUFFER_SIZE, BLOCK_SIZE,
				CONTAINER_NO, containerName, INITIAL_SIZE, EXTEND_SIZE);
	}

	private void start(TxMgr taMgr) throws ServerException {
		Tx tx = taMgr.begin();
		start(tx, true);
		tx.commit();
	}
}
