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
package org.brackit.server;

import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.io.manager.impl.SlimBufferMgr;
import org.brackit.server.metadata.manager.MetaDataMgr;
import org.brackit.server.metadata.manager.impl.MetaDataMgrImpl;
import org.brackit.server.session.SessionMgr;
import org.brackit.server.session.SessionMgrImpl;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.impl.TaMgrImpl;
import org.brackit.server.tx.log.Log;
import org.brackit.server.tx.log.impl.DefaultLog;
import org.brackit.xquery.util.Cfg;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BrackitDB {

	public static final String SYS_CNT_FILE_NAME = "org.brackit.server.syscnt.filename";
	public static final String SYS_CNT_BLK_SIZE = "org.brackit.server.syscnt.blocksize";
	public static final String SYS_CNT_BUF_SIZE = "org.brackit.server.syscnt.buffersize";
	public static final String SYS_CNT_INI_SIZE = "org.brackit.server.syscnt.inisize";
	public static final String SYS_CNT_EXT_SIZE = "org.brackit.server.syscnt.extsize";

	private final MetaDataMgr metadataMgr;

	private final TxMgr taMgr;

	private final SessionMgr sessionMgr;

	private final Log transactionLog;

	private final BufferMgr bufferMgr;

	private boolean running = false;

	private final int SYS_CNT_NO = 0;

	private final String SYS_CNT_NAME = Cfg.asString(SYS_CNT_FILE_NAME, "sys");

	private final int SYS_CNT_BLKSIZE = Cfg.asInt(SYS_CNT_BLK_SIZE, 8192);

	private final int SYS_CNT_BUFSIZE = Cfg.asInt(SYS_CNT_BLK_SIZE, 512);

	private final int SYS_CNT_INISIZE = Cfg.asInt(SYS_CNT_BLK_SIZE, 250);

	private final int SYS_CNT_EXTSIZE = Cfg.asInt(SYS_CNT_BLK_SIZE, 250);

	public BrackitDB(boolean install) throws ServerException {
		transactionLog = new DefaultLog();
		bufferMgr = new SlimBufferMgr(transactionLog);
		taMgr = new TaMgrImpl(transactionLog, bufferMgr);
		metadataMgr = new MetaDataMgrImpl(taMgr);
		sessionMgr = new SessionMgrImpl(taMgr);
		// boot the system
		boot(install);
		running = true;
	}

	private void boot(boolean install) throws ServerException {
		if (!install) {
			bufferMgr.start();
			transactionLog.open();
			taMgr.recover();
		} else {
			bufferMgr.createBuffer(SYS_CNT_BUFSIZE, SYS_CNT_BLKSIZE,
					SYS_CNT_NO, SYS_CNT_NAME, SYS_CNT_INISIZE, SYS_CNT_EXTSIZE);
			transactionLog.clear();
			transactionLog.open();
		}

		Tx tx = taMgr.begin();

		if (install) {
			// create default user containers and store sample document
			metadataMgr.start(tx, true);
			createDefaultDocuments(tx);
		} else {
			metadataMgr.start(tx, false);
		}

		tx.commit();
	}

	public MetaDataMgr getMetadataMgr() {
		return metadataMgr;
	}

	public TxMgr getTaMgr() {
		return taMgr;
	}

	public SessionMgr getSessionMgr() {
		return sessionMgr;
	}

	protected void createDefaultDocuments(Tx tx) throws ServerException {
		// try
		// {
		// // store sample document
		// metadataMgr.create(ctx, "/sample.xml", new
		// DocumentParser(XTCsvrCfg.defaultSampleDocument));
		// // store sample document
		// metadataMgr.create(ctx, "/index.html", new
		// DocumentParser(XTCsvrCfg.defaultIndexDocument));
		// }
		// catch (DocumentException e)
		// {
		// throw new ServerException(e);
		// }
	}

	public synchronized void shutdown() throws ServerException {
		if (!running) {
			return;
		}
		sessionMgr.shutdown();
		metadataMgr.shutdown();
		taMgr.shutdown();
		bufferMgr.shutdown();
		transactionLog.close();
		running = false;
	}
}
