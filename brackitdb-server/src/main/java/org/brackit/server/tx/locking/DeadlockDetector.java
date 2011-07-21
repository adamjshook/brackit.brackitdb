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
package org.brackit.server.tx.locking;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.TxMgr;
import org.brackit.server.tx.locking.services.LockServiceClient;
import org.brackit.xquery.util.Cfg;

/**
 * @author Sebastian Baechle
 * 
 */
public class DeadlockDetector {
	private static final Logger log = Logger.getLogger(DeadlockDetector.class);

	private volatile boolean logDeadlock = true;

	private final LinkedHashSet<Tx> trackedWaitPath;

	private final HashSet<Tx> skipSet;

	private final TxMgr taMgr;

	public DeadlockDetector(TxMgr taMgr, boolean logDeadlock) {
		this.taMgr = taMgr;
		this.logDeadlock = logDeadlock;
		this.trackedWaitPath = new LinkedHashSet<Tx>();
		this.skipSet = new HashSet<Tx>();
	}

	public void detectDeadlocks() {
		boolean restart = false;

		do {
			for (Tx transaction : taMgr.getTransactions()) {
				if (!skipSet.contains(transaction)) {
					restart = (restart || exploreWaitPath(trackedWaitPath,
							transaction, skipSet));
				}
			}
		} while (restart);

		skipSet.clear();
	}

	private boolean exploreWaitPath(LinkedHashSet<Tx> waitPath, Tx transaction,
			Set<Tx> skipSet) {
		// add current transaction to tracking
		waitPath.add(transaction);

		for (Blocking request : waitFor(transaction)) {
			Tx blockingTransaction = request.getBlockedBy();

			if (blockingTransaction == null) {
				System.out.println(listWaitForGraph());
			}

			if (log.isTraceEnabled()) {
				log.trace(String.format("%s is waiting for %s.", transaction
						.toShortString(), blockingTransaction.toShortString()));
			}

			if (waitPath.contains(blockingTransaction)) {
				// loop detected
				List<Tx> deadlock = extractDeadlock(waitPath,
						blockingTransaction);

				// verify that is really a manifested deadlock and does not only
				// come from a "shifting view"
				// if (verifyDeadlock(deadlock))
				{
					if (log.isDebugEnabled()) {
						log
								.debug(String.format("Found deadlock %s.",
										deadlock));
					}

					resolveDeadlock(deadlock);
				}

				// prepare for restart
				skipSet.clear();
				waitPath.clear();
				return true;
			}

			// depth-first search
			boolean restart = exploreWaitPath(waitPath, blockingTransaction,
					skipSet);

			if (restart) {
				return true;
			}
		}

		// current transaction is not part of a deadlock loop
		// take no action -> back-tracking
		skipSet.add(transaction);
		waitPath.remove(transaction);

		return false;
	}

	private List<Tx> extractDeadlock(LinkedHashSet<Tx> trackedWaitPath,
			Tx blockingTx) {
		List<Tx> deadlock = new ArrayList<Tx>();

		for (Tx tx : trackedWaitPath) {
			if (tx.equals(blockingTx)) {
				if (deadlock.size() == 0) {
					deadlock.add(tx);
				} else {
					break;
				}
			} else if (deadlock.size() > 0) {
				deadlock.add(tx);
			}
		}
		return deadlock;
	}

	private void resolveDeadlock(List<Tx> deadlock) {
		int minNumberOfLocks = Integer.MAX_VALUE;
		Tx victim = null;

		for (Tx tx : deadlock) {
			TxState state = tx.getState();
			int numberOfLocks = tx.getLockCB().getTotalCount();

			if ((numberOfLocks < minNumberOfLocks)
					&& ((state == TxState.RUNNING) || (state == TxState.ROLLBACK))) {
				boolean inRollback = (state == TxState.ROLLBACK);
				boolean isReadOnly = tx.isReadOnly();

				// try to avoid kill of a transaction during rollback
				if ((victim == null) || (!inRollback) || (isReadOnly)) {
					minNumberOfLocks = numberOfLocks;
					victim = tx;
				}
			}
		}

		if (logDeadlock) {
			logDeadlock(minNumberOfLocks, victim, deadlock);
		}

		if (log.isTraceEnabled()) {
			log.trace("Deadlock detected.");
			log.trace(listWaitForGraph());
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format("Killing %s to resolve deadlock.", victim
					.toShortString()));
		}

		victim.abort();
		victim.getLockCB().wakeup();
	}

	private final void logDeadlock(int minLockRequests, Tx victim,
			List<Tx> deadlock) {
		try {
			Date now = new Date(System.currentTimeMillis());
			DateFormat df = new SimpleDateFormat("MM-dd-HHmm-ssSS");
			String timestamp = df.format(now);

			File logFile = new File(Cfg.asString(TxMgr.DEADLOCK_LOG_DIR, "log")
					+ "/deadlock." + timestamp + ".log");
			BufferedWriter out = new BufferedWriter(new PrintWriter(
					new FileOutputStream(logFile)));

			out.write(String.format("Deadlock detected at %s:\n\n", timestamp));
			out.write("Running transactions:\n");
			for (Tx transaction : taMgr.getTransactions())
				out.write(String.format(" * %s\n", transaction.toString()));

			out.newLine();
			out.write(listWaitForGraph());
			out.newLine();
			out.write("Deadlock loop:");
			out.newLine();
			for (Tx entry : deadlock) {
				out.write(entry.getID().toString());
				out.write(" -> ");
			}
			out.write(deadlock.get(0).getID().toString());
			out.newLine();
			out.write(String.format("Abort TA %s with %s lock requests", victim
					.getID(), minLockRequests));

			for (Tx entry : deadlock) {
				out.newLine();
				out.write("Locks of TA ");
				out.write(entry.getID().toString());
				out.write(":");
				for (LockServiceClient lsc : entry.getLockCB()
						.getLockServiceClients()) {
					out.newLine();
					out.write("at ");
					out
							.write(lsc.getLockServiceCB().getLockService()
									.getName());
					out.write(":");
					out.newLine();
					out.write(lsc.listLocks());
				}
				out.newLine();
			}

			// synchronized (lockServices)
			// {
			// for (LockService service : lockServices.values())
			// logWriter.write(String.format("Lock service %s:\n %s",
			// service.getName(), service.listLocks()));
			// }

			out.close();
		} catch (Throwable e) {
			log.error("Error while logging deadlock.", e);
		}
	}

	public final String listWaitForGraph() {
		StringBuilder builder = new StringBuilder("Wait-For-Graph:\n");
		Tx firstEntry = null;
		LinkedHashSet<Tx> visited = new LinkedHashSet<Tx>();

		for (Tx tx : taMgr.getTransactions()) {
			if (!visited.contains(tx)) {
				builder.append(tx.toShortString());
				printBlocks(tx, builder, visited, new ArrayList<Tx>());
				builder.append("\n");
			}
		}
		return builder.toString();
	}

	private void printBlocks(Tx tx, StringBuilder builder, Set<Tx> visited,
			List<Tx> currentPath) {
		currentPath.add(tx);

		boolean indent = false;
		int indentTo = builder.length() - builder.lastIndexOf("\n");

		for (LockServiceClient lsc : tx.getLockCB().getLockServiceClients()) {
			for (Blocking blocking : lsc.blockedAt()) {
				if (indent) {
					builder.append("\n");
					for (int i = 1; i < indentTo; i++) {
						builder.append(" ");
					}
				}

				indent = true;

				Tx blockedBy = blocking.getBlockedBy();

				builder
						.append(String.format(" --%s@%s--> %s", blocking
								.getBlockedAt(), lsc.getLockServiceCB()
								.getLockService().getName(), blockedBy
								.toShortString()));

				if (currentPath.contains(blockedBy)) {
					builder.append(" [Deadlock]");
				} else if (!visited.contains(blockedBy)) {
					printBlocks(blockedBy, builder, visited, currentPath);
				}
			}
		}

		currentPath.remove(tx);
		visited.add(tx);
	}

	private Collection<Blocking> waitFor(Tx transaction) {
		List<Blocking> waitForList = new ArrayList<Blocking>();

		for (LockServiceClient lsc : transaction.getLockCB()
				.getLockServiceClients()) {
			waitForList.addAll(lsc.blockedAt());
		}

		return waitForList;
	}
}