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
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.tx.locking.services;

import org.brackit.server.procedure.InfoContributor;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.server.procedure.statistics.ListLocks;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.protocol.LockMode;
import org.brackit.server.tx.locking.table.LockTable;
import org.brackit.server.tx.locking.table.LockTableClient;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public abstract class BaseLockServiceImpl<T extends LockMode<T>> implements
		LockService, InfoContributor {
	protected LockTable<T> table;

	protected String name;

	protected BaseLockServiceImpl(LockTable<T> table, String name) {
		this.name = name;
		this.table = table;
		ProcedureUtil.register(ListLocks.class, this);
	}

	public final String listLocks() {
		return table.listLocks();
	}

	public final String getName() {
		return name;
	}

	protected LockTableClient<T> getClient(Tx tx) {
		LockTableClient<T> client = (LockTableClient<T>) tx.getLockCB().get(
				this);

		if (client == null) {
			client = new LockTableClient<T>(this, tx, table);
			tx.getLockCB().add(this, client);
		}

		return client;
	}

	@Override
	public int getInfoID() {
		return 0;
	}

	@Override
	public String getInfo() {
		return listLocks();
	}
}
