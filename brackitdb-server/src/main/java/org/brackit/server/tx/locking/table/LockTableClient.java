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
package org.brackit.server.tx.locking.table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxState;
import org.brackit.server.tx.TxStats;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.locking.Blocking;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.LockServiceClientCB;
import org.brackit.server.tx.locking.LockState;
import org.brackit.server.tx.locking.protocol.LockMode;
import org.brackit.server.tx.locking.services.LockService;
import org.brackit.server.tx.locking.services.LockServiceClient;

/**
 * 
 * @author Sebastian Baechle
 * 
 * @param <T>
 *            lock mode used by this client
 */
public class LockTableClient<T extends LockMode<T>> implements
		LockServiceClient {
	private static final Logger log = Logger.getLogger(LockTableClient.class);

	protected static final boolean DEBUG = false;

	protected final LockTable<T> table;

	protected final LockServiceClientCB lscb;

	// Redundant copy from lscb to avoid frequent getter calls
	protected final Tx tx;

	// Protected by lscb from concurrent access
	protected final ArrayList<Request<T>> blockedAt;

	// Protected by lscb from concurrent access
	protected Request<T> chain;

	public LockTableClient(LockService ls, Tx tx, LockTable<T> table) {
		this.tx = tx;
		this.table = table;
		this.lscb = new LockServiceClientCB(ls, tx);
		this.blockedAt = new ArrayList<Request<T>>(1);
	}

	public LockServiceClientCB getLockServiceCB() {
		return lscb;
	}

	protected Request<T> getChain() {
		return chain;
	}

	@Override
	public Collection<Blocking> blockedAt() {
		lscb.latchX();

		try {
			ArrayList<Blocking> list = new ArrayList<Blocking>(blockedAt.size());

			for (Request<T> req : blockedAt) {
				Tx blockedBy = req.blockedBy();
				if (blockedBy != null) {
					LockName name = req.getHeader().getName();
					LockClass lockClass = req.getLockClass();
					list.add(new Blocking(name, lockClass, tx, blockedBy));
				}
			}
			return list;
		} finally {
			lscb.unlatch();
		}
	}

	@Override
	public void unblock() {
		lscb.latchX();

		try {
			for (Request<T> request : blockedAt) {
				request.wakeup();
			}
		} finally {
			lscb.unlatch();
		}
	}

	public T request(LockName lockName, LockClass lockClass, T mode,
			boolean conditional) {
		long start = System.currentTimeMillis();
		lscb.latchX();

		try {
			return requestInternal(lockName, lockClass, mode, conditional);
		} finally {
			lscb.unlatch();
			long end = System.currentTimeMillis();
			tx.getStatistics().addTime(TxStats.LOCK_REQUEST_TIME, start, end);
		}
	}

	public void release(LockName lockName) {
		lscb.latchX();

		try {
			Header<T> header = table.find(lockName);

			if (header != null) {
				Request<T> request = header.findTaRequest(tx);

				if (request != null) {
					releaseLock(header, request, false);
				} else {
					if (log.isTraceEnabled()) {
						log.trace(String.format("%s has no lock for %s.", tx
								.toShortString(), header));
					}

					header.unlatch();
				}
			}
		} finally {
			lscb.unlatch();
		}
	}

	public void freeResources() {
		lscb.latchX();

		try {
			for (Request<T> req = getChain(); req != null; req = getChain()) {
				Header<T> header = req.getHeader();
				header.latchX();
				releaseLock(header, req, true);
			}
		} finally {
			lscb.unlatch();
		}
	}

	public List<XTClock> getLocks() {
		lscb.latchX();

		try {
			ArrayList<XTClock> list = new ArrayList<XTClock>();

			for (Request<T> req = getChain(); req != null; req = req
					.getTaPrevious()) {
				Header<T> header = req.getHeader();
				list
						.add(new XTClock(header.getName(), req.requestedBy()
								.getID(), req.getMode(), req.getState(), req
								.getCount()));
			}

			return list;
		} finally {
			lscb.unlatch();
		}
	}

	public XTClock getLock(LockName lockName) {
		lscb.latchX();

		try {
			XTClock lock = null;
			Header<T> header = table.find(lockName);

			if (header != null) {
				Request<T> request = header.findTaRequest(tx);

				if (request != null) {
					lock = new XTClock(lockName, tx.getID(), request.getMode(),
							request.getState(), request.getCount());
				}

				header.unlatch();
			}

			return lock;
		} finally {
			lscb.unlatch();
		}
	}

	public String listLocks() {
		lscb.latchX();

		try {
			StringBuffer list = new StringBuffer(String.format(
					"Locks of %s at %s:", tx.toShortString(), lscb
							.getLockService().getName()));
			for (Request<T> request = getChain(); request != null; request = request
					.getTaPrevious()) {
				list.append("\n");
				list.append(request.getHeader().printLockChain());
			}
			list.append("\n");

			return list.toString();
		} finally {
			lscb.unlatch();
		}
	}

	/**
	 * Releases the lock of the given resource for given object. The header must
	 * be valid and already latched!
	 */
	protected boolean releaseLock(Header<T> header, Request<T> request,
			boolean force) {
		boolean removedRequest = false;

		if (log.isTraceEnabled()) {
			log.trace(String.format("%s releases %s.", tx.toShortString(),
					request.getHeader()));
		}

		removedRequest = removeRequest(tx, header, request, force);

		if (header.getQueue() != null) // There are further locks for this
		// object
		{
			if (header.isWaiting()) {
				header.grantRequestChain(); // grant and wake up sleeping
				// requests
			}

			if ((DEBUG) && (log.isTraceEnabled())) {
				log.trace(String.format("%s released %s leaving it in mode %s",
						tx.toShortString(), header, header.getGrantedMode()));
				// log.trace(printLockChain(header));
			}

			header.unlatch();
		} else {
			// try to remove lock from queue
			header.unlatch();
			table.remove(header.getName());
		}

		return removedRequest;
	}

	protected boolean removeRequest(Tx transaction, Header<T> header,
			Request<T> request, boolean force) {
		int lockCount = request.getCount();
		LockClass lockClass = request.getLockClass();
		boolean removedRequest = false;

		if ((lockCount == 1) && (!force)
				&& (request.getState() != LockState.GRANTED)) // single request
		// lock not
		// granted yet,
		// someone is
		// waiting for it
		{
			log.error(String.format("Transaction %s attempted an unforced "
					+ "release of %s although it is requested "
					+ "only once and not granted yet.", transaction, request));
			log.error(header.printLockChain());
			throw new RuntimeException(
					"Cannot remove lock because it is not granted yet.");
		}

		if (((lockCount == 1) && (lockClass != LockClass.COMMIT_DURATION))
				|| (force)) {
			dequeue(request);
			header.dequeue(request);

			// remove the request itself
			removedRequest = true;

			if ((DEBUG) && (log.isTraceEnabled())) {
				log.trace(String.format("%s removed request %s.", transaction
						.toShortString(), request));
			}
		} else if (lockCount > 1) // resource requested several times
		{
			request.decCount();

			if ((DEBUG) && (log.isTraceEnabled())) {
				log.trace(String.format("%s decreased counter of %s.",
						transaction.toShortString(), request));
			}
		}

		return removedRequest;
	}

	protected T requestInternal(LockName lockName, LockClass lockClass, T mode,
			boolean conditional) {
		if ((log.isTraceEnabled())) {
			log.trace(String.format("%s Requests mode %s for %s.", tx
					.toShortString(), mode, lockName));
		}

		Header<T> header = table.allocate(lockName);

		// scan lock chain for a previous lock request of this transaction
		Request<T> request = header.findTaRequest(tx);

		if (request != null) {
			lscb.useRequest();

			if ((DEBUG) && (log.isTraceEnabled())) {
				log.trace(String.format("%s Found existing request %s.", tx
						.toShortString(), request));
			}
		} else {
			// enqueue new lock request
			request = new Request<T>(header, tx, lockClass);
			enqueue(request);
			header.enqueue(request);

			if ((DEBUG) && (log.isTraceEnabled())) {
				log.trace(String.format("%s Enqueued new request %s.", tx
						.toShortString(), request));
			}
		}

		// increase request counter
		request.incCount();

		T result = doRequest(header, request, lockClass, mode, conditional);

		if (result == null) {
			return null;
		}

		// remove instant lock immediately if the request was definitely
		// successful
		if (lockClass == LockClass.INSTANT_DURATION) {
			if (request == null) {
				request = header.findTaRequest(tx);
			}

			releaseLock(header, request, false);
		} else {
			header.unlatch();
		}

		return result;
	}

	protected T doRequest(Header<T> header, Request<T> request,
			LockClass lockClass, T mode, boolean conditional) {
		// remember the state of the current request
		T currentRequestMode = request.getMode();
		T currentConvertMode = request.getConvertMode();
		int currentRequestCount = request.getCount();
		LockState currentRequestState = request.getState();

		// try to grant lock
		if (!request.grant(mode)) {
			if (conditional) {
				if ((DEBUG) && (log.isTraceEnabled())) {
					log.trace(String.format("%s was not granted conditional"
							+ " request of mode %s for %s."));
				}

				cleanup(header, request, currentRequestMode,
						currentConvertMode, currentRequestCount,
						currentRequestState);
				return null;
			} else {
				if ((DEBUG) && (log.isTraceEnabled())) {
					log.trace(String.format(
							"%s waits for granting mode %s for %s\n", tx
									.toShortString(), mode, header));
				}

				boolean success = lockWait(header, request);

				if (!success) {
					// wait was not successful (abort or timeout)
					cleanup(header, request, currentRequestMode,
							currentConvertMode, currentRequestCount,
							currentRequestState);
					return null;
				}
			}
		}

		// save granted mode before giving up the latch
		T result = request.getMode();

		if ((DEBUG) && (log.isTraceEnabled())) {
			log.trace(String.format("%s granted mode %s for request of %s.", tx
					.toShortString(), request.getMode(), mode));
		}

		return result;
	}

	private boolean lockWait(Header<T> header, Request<T> request) {
		long blockTime = 0;

		try {
			synchronized (request) {
				blockedAt.add(request);
				header.unlatch();
				lscb.unlatch();

				if (log.isTraceEnabled()) {
					log.trace(String.format("%s is waiting for %s at %s.", tx
							.toShortString(), blockedAt, this));
				}

				long waitBegin = System.currentTimeMillis();
				try {
					request.wait(tx.getLockCB().getTimeout());
				} catch (InterruptedException e) { /* ignore */
				}
				long waitEnd = System.currentTimeMillis();
				blockTime = (waitEnd - waitBegin);
				lscb.addBlockTime(blockTime);
			}
		} finally {
			lscb.latchX();
			header.latchX();
			blockedAt.remove(request);
		}

		if (tx.getState() == TxState.ABORTED) {
			if (log.isTraceEnabled()) {
				log.trace(String.format(
						"%s was aborted while waiting for %s at %s.", tx
								.toShortString(), request, this));
			}

			return false;
		}

		if (request.getState() != LockState.GRANTED) {
			if (log.isTraceEnabled()) {
				log.trace(String.format(
						"%s timed out after waiting %s ms for %s at %s.", tx
								.toShortString(), blockTime, request, this));
			}

			return false;
		}

		return true;
	}

	protected void cleanup(Header<T> header, Request<T> request,
			T currentRequestMode, T currentConvertMode,
			int currentRequestCount, LockState currentRequestState) {
		// cleanup after failed lock request (timed, kill, or ungranted
		// conditional
		if ((DEBUG) && (log.isTraceEnabled())) {
			log.trace(String.format(
					"%s Request chain of %s after failed request.", tx
							.toShortString(), request));
			log.trace(header.printLockChain());
		}

		if (currentRequestState == LockState.IGNORED) {
			// just remove the new request
			removeRequest(tx, header, request, true);
		} else if (currentRequestState == LockState.GRANTED) {
			// reset lock to latest allowed status
			request.setMode(currentRequestMode);
			request.setConvertMode(currentConvertMode);
			request.setCount(currentRequestCount);
			request.setState(currentRequestState);
		}

		if (header.getQueue() != null) // There are further locks for this
		// object
		{
			header.grantRequestChain(); // grant and wake up sleeping requests
			header.unlatch();
		} else {
			header.unlatch();
			table.remove(header.getName()); // try to remove lock from queue
		}

		if ((DEBUG) && (log.isTraceEnabled())) {
			log.trace(String.format("%s Request chain of %s after cleanup.", tx
					.toShortString(), request));
			log.trace(header.printLockChain());
		}
	}

	protected void enqueue(Request<T> request) {
		// double chain transaction locks
		if (chain != null) {
			chain.taNext = request;
			request.taPrevious = chain;
		}
		chain = request;
		lscb.addRequest();
	}

	protected void dequeue(Request<T> request) {
		// remove request from transaction chain
		Request<T> taPrevious = request.taPrevious;
		Request<T> taNext = request.taNext;
		if (taPrevious != null)
			taPrevious.taNext = taNext;
		if (taNext != null)
			taNext.taPrevious = taPrevious;

		// remove request from transaction entry
		if (chain == request)
			chain = taPrevious;
		lscb.removeRequest();
	}
}