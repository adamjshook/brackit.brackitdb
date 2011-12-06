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
package org.brackit.server.node.el.index.page;

import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.el.index.log.ElBPlusIndexLogOperationHelper;
import org.brackit.server.node.el.index.log.ElUpdateLogOperation;
import org.brackit.server.store.index.aries.IndexOperationException;
import org.brackit.server.store.index.aries.page.DirectKeyValuePageContext;
import org.brackit.server.store.page.keyvalue.KeyValuePage;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogOperation;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ElKeyValuePageContext extends DirectKeyValuePageContext implements
		ElPageContext {
	public ElKeyValuePageContext(BufferMgr bufferMgr, Tx transaction,
			KeyValuePage page) {
		super(bufferMgr, transaction, page);
	}

	@Override
	public void insertSpecial(byte[] insertKey, byte[] insertValue, int level,
			boolean logged, long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = ElBPlusIndexLogOperationHelper.createUpdateLogOp(
					ElUpdateLogOperation.USER_INSERT_SPECIAL, page.getPageID(),
					page.getBasePageID(), insertKey, insertValue, null, level);
		}

		if (!page.insert(currentPos, insertKey, insertValue, isCompressed())) {
			throw new RuntimeException();
		}

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(transaction);
		}
		entryCount++;
	}

	@Override
	public void deleteSpecial(int level, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = ElBPlusIndexLogOperationHelper.createUpdateLogOp(
					ElUpdateLogOperation.USER_DELETE_SPECIAL, page.getHandle()
							.getPageID(), page.getBasePageID(), getKey(),
					getValue(), null, level);
		}

		page.delete(currentPos);

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(transaction);
		}
		entryCount--;
	}
}
