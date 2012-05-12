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
package org.brackit.server.tx.locking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.external.LockServiceStats;
import org.brackit.server.tx.locking.services.LockService;
import org.brackit.server.tx.locking.services.LockServiceClient;

/**
 * Central organization structure that manages all initialized
 * {@link LockServiceClient LockServiceClients} of a specific transaction.
 * 
 * @author Sebastian Baechle
 * 
 */
public class LockControlBlock {
	private static final Logger log = Logger.getLogger(LockControlBlock.class);

	private LockService[] services;

	private LockServiceClient[] clients;

	private final Tx tx;

	private volatile long timeout;

	public LockControlBlock(Tx tx, long timeout) {
		this.tx = tx;
		this.timeout = timeout;
		this.clients = new LockServiceClient[0];
		this.services = new LockService[0];
	}

	public synchronized LockServiceClient get(LockService ls) {
		for (int i = 0; i < clients.length; i++) {
			if (services[i] == ls)
				return clients[i];
		}
		return null;
	}

	public synchronized void add(LockService ls, LockServiceClient client) {
		int length = clients.length;

		for (int i = 0; i < length; i++) {
			if (services[i] == ls)
				throw new RuntimeException(String.format(
						"%s has already lock service client for %s.", tx, ls));
		}

		services = Arrays.copyOf(services, length + 1);
		clients = Arrays.copyOf(clients, length + 1);

		services[length] = ls;
		clients[length] = client;
	}

	public synchronized LockServiceClient[] getLockServiceClients() {
		return Arrays.copyOf(clients, clients.length);
	}

	public synchronized int getTotalCount() {
		int value = 0;

		for (LockServiceClient client : clients) {
			value += client.getLockServiceCB().getCount();
		}

		return value;
	}

	public synchronized int getTotalBlockCount() {
		int value = 0;

		for (LockServiceClient client : clients) {
			value += client.getLockServiceCB().getBlockCount();
		}

		return value;
	}

	public synchronized long getTotalBlockTime() {
		int value = 0;

		for (LockServiceClient client : clients) {
			value += client.getLockServiceCB().getBlockTime();
		}

		return value;
	}

	public synchronized int getTotalRequestCount() {
		int value = 0;

		for (LockServiceClient client : clients) {
			value += client.getLockServiceCB().getRequestCount();
		}

		return value;
	}

	public synchronized Collection<LockServiceStats> getStatistics() {
		List<LockServiceStats> stats = new ArrayList<LockServiceStats>(
				clients.length);

		for (LockServiceClient client : clients) {
			stats.add(client.getLockServiceCB().getStatistics());
		}

		return stats;
	}

	public synchronized LockServiceStats getStatistics(String name) {
		for (int i = 0; i < services.length; i++) {
			if (services[i].getName().equals(name)) {
				return clients[i].getLockServiceCB().getStatistics();
			}
		}

		// ta requested no locks for this service
		return new LockServiceStats(name, 0, 0);
	}

	public synchronized void wakeup() {
		for (LockServiceClient lsc : clients) {
			lsc.unblock();
		}
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
}