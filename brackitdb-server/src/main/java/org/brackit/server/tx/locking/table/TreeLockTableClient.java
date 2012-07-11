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

import java.util.Arrays;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxStats;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.LockState;
import org.brackit.server.tx.locking.protocol.TreeLockMode;
import org.brackit.server.tx.locking.services.LockService;

/**
 * @author Sebastian Baechle
 * 
 */
public class TreeLockTableClient<T extends TreeLockMode<T>> extends
		LockTableClient<T> {
	private static final Logger log = Logger
			.getLogger(TreeLockTableClient.class);

	protected Request<T>[] lastPath;

	protected volatile double escalationGain;

	protected volatile int maxEscalationCount;

	public TreeLockTableClient(LockService ls, Tx tx, LockTable<T> table) {
		super(ls, tx, table);
		this.escalationGain = -1;
		this.maxEscalationCount = 30000;
	}

	public TreeLockTableClient(LockService ls, Tx tx, LockTable<T> table,
			int maxEscalationCount, double escalationGain) {
		super(ls, tx, table);
		this.escalationGain = escalationGain;
		this.maxEscalationCount = maxEscalationCount;
	}

	public double getEscalationGain() {
		return escalationGain;
	}

	public void setEscalationGain(double escalationGain) {
		this.escalationGain = escalationGain;
	}

	public int getMaxEscalationCount() {
		return maxEscalationCount;
	}

	public void setMaxEscalationCount(int maxEscalationCount) {
		this.maxEscalationCount = maxEscalationCount;
	}

	public int getEscalationTreshold(int level) {
		if (maxEscalationCount == -1)
			return -1;
		else if (escalationGain == -1)
			return maxEscalationCount;
		else
			return (int) (maxEscalationCount / ((1 << level) * escalationGain));
	}

	public T request(TreeLockNameFactory factory, LockClass lockClass, T mode,
			boolean conditional) {
		long start = System.currentTimeMillis();
		lscb.latchX();

		try {
			int level = factory.getTargetLevel();

			while ((level > tx.getLockDepth()) && (level > 0)) {
				if ((DEBUG) && (log.isTraceEnabled())) {
					log.trace(String.format(
							"%s Lock request exceeds maximum lock depth %s."
									+ " Escalating from %s for %s at "
									+ "level %s to %s for %s at level %s.", tx
									.toShortString(), tx.getLockDepth(), mode,
							factory.getLockName(level), level,
							mode.escalate(0), factory.getLockName(level - 1),
							level - 1));
				}

				mode = mode.escalate(0);
				level--;
			}

			return requestPath(factory, level, lockClass, mode, conditional);
		} finally {
			lscb.unlatch();
			long end = System.currentTimeMillis();
			tx.getStatistics().addTime(TxStats.LOCK_REQUEST_TIME, start, end);
		}
	}

	public void release(TreeLockNameFactory factory) {
		lscb.latchX();

		try {
			releasePath(factory, factory.getTargetLevel());
		} finally {
			lscb.unlatch();
		}
	}

	/**
	 * Releases the locks along the given path from leaf to root. We can stop as
	 * soon as we a) reach the root, or b) only can decrease the counter of a
	 * lock because it has been locked several times by this transaction.
	 * 
	 * Example for b)
	 * 
	 * Before the release of leafB:
	 * 
	 * leafA(1)->parentX(1)->grantParent(2)->root(1) leafB(1)->parentY(2)->
	 * leafC(1)->
	 * 
	 * After the release of leafB:
	 * 
	 * leafA(1)->parentX(1)->grantParent(2)->root(1) leafC(1)->parentY(1)->
	 */
	private void releasePath(TreeLockNameFactory factory, int level) {
		for (int i = level; i >= 0; i--) {
			Header<T> header = table.find(factory.getLockName(i));

			if (header != null) {
				Request<T> request = header.findTaRequest(tx);

				if (request != null) {
					if (!releaseLock(header, request, false)) {
						break;
					}
				} else {
					if ((DEBUG) && (log.isTraceEnabled())) {
						log.trace(String.format("%s has no lock for %s.", tx
								.toShortString(), header));
					}

					header.unlatch();
				}
			}
		}
	}

	protected T requestPath(TreeLockNameFactory factory, int targetLevel,
			LockClass lockClass, T targetMode, boolean conditional) {
		Header<T> header;
		Request<T> request;
		T grantedMode = null;
		Request<T> parentRequest = null;
		boolean newParentRequest = false;
		int level = 0;

		if (lastPath == null) {
			lastPath = new Request[targetLevel + 1];
		} else if (lastPath.length < targetLevel + 1) {
			lastPath = Arrays.copyOf(lastPath, targetLevel + 1);
		}

		while (level <= targetLevel) {
			boolean newRequest = false;
			int distanceToTargetLevel = targetLevel - level;
			LockName lockName = factory.getLockName(level);
			T mode = targetMode.requiredAncestorMode(distanceToTargetLevel);

			if (((request = lastPath[level]) != null)
					&& ((header = request.getHeader()).getName()
							.equals(lockName))) {

				// The following code is similar to the standard case below,
				// except that we try to perform instant lock granting
				// and lock escalation without latching the lock header.
				// For heavily contended resources (e.g. the document root)
				// this may drastically increase thread-level parallelism
				lscb.useRequest();

				T escalationMode = suggestEscalation(request, level, mode,
						targetMode, distanceToTargetLevel);

				if (escalationMode == mode) {
					T currentMode = request.getMode();
					if ((currentMode.implies(targetMode, distanceToTargetLevel))
							&& (currentMode.convert(mode) == currentMode)) {
						if (lockClass != LockClass.INSTANT_DURATION) {
							if (parentRequest != null) {
								// Increase parent counter to indicate locality
								// in its subtree. We do not hold the parent's
								// header latch, but simply updating the counter
								// is not harmful as we hold the transactions
								// lscb latch
								parentRequest.incCount();
							}

							request.incCount();
							lscb.useRequest();
						}
						return currentMode.implicitMode(distanceToTargetLevel);
					} else if ((currentMode == mode)
							|| (currentMode.convert(mode) == currentMode)) {
						level++;
						parentRequest = request;
						continue;
					}
				}
				// OK, we need to follow the standard case
				header.latchX();
			} else {
				header = table.allocate(lockName);
				request = header.findTaRequest(tx);
			}

			if (request != null) {
				lastPath[level] = request;
				lscb.useRequest();

				if ((DEBUG) && (log.isTraceEnabled())) {
					log.trace(String.format("%s Found existing request %s.", tx
							.toShortString(), request));
				}

				T currentMode = request.getMode();

				if ((currentMode.implies(targetMode, distanceToTargetLevel) && (currentMode
						.convert(mode) == currentMode))) {
					if (lockClass != LockClass.INSTANT_DURATION) {
						if (parentRequest != null) {
							// Increase parent counter to indicate locality in
							// its subtree.
							// We do not hold the parent's header latch, but
							// simply updating
							// the counter is not harmful as we hold the
							// transactions lscb latch
							parentRequest.incCount();
						}

						request.incCount();
						lscb.useRequest();
					}

					header.unlatch();
					return currentMode.implicitMode(distanceToTargetLevel);
				}

				T escalationMode = performEscalation(header, request, level,
						mode, targetMode, distanceToTargetLevel);

				if ((escalationMode != mode)
						&& (escalationMode.requiredParentMode() != mode
								.requiredParentMode()) && (level > 0)) {
					// Restart from root
					targetMode = escalationMode;
					targetLevel = level;
					level = 0;
					header.unlatch();
					continue;
				}

				mode = escalationMode;
			} else {
				// enqueue new lock request
				request = new Request<T>(header, tx, lockClass);
				enqueue(request);
				header.enqueue(request);
				lastPath[level] = request;
				newRequest = true;

				// increase request counter
				request.incCount();

				if ((parentRequest != null) && (!newParentRequest)) {
					// Increase parent counter to indicate branching (locality)
					// in its subtree.
					// We do not hold the parent's header latch, but simply
					// updating
					// the counter is not harmful as we hold the transactions
					// lscb latch
					parentRequest.incCount();
				}

				if ((DEBUG) && (log.isTraceEnabled())) {
					log.trace(String.format("%s Enqueued new request %s.", tx
							.toShortString(), request));
				}
			}

			grantedMode = doRequest(header, request, lockClass, mode,
					conditional);

			if (grantedMode == null) {
				// request failed clean up
				releasePath(factory, level - 1);
				return null;
			}

			boolean implies = grantedMode.implies(targetMode,
					distanceToTargetLevel);

			if ((!newRequest) && (implies)) {
				// explicitly increase counter of re-visited request only when
				// a) it is the parent where the fanout happens, or
				// b) it implies the requested target mode
				request.incCount();
			}

			header.unlatch();
			level++;

			if (implies) {
				grantedMode = grantedMode.implicitMode(distanceToTargetLevel);
				break;
			}

			parentRequest = request;
			newParentRequest = newRequest;
		}

		// remove instant locks immediately if the request was definitely
		// successful
		if ((lockClass == LockClass.INSTANT_DURATION) && (grantedMode != null)) {
			releasePath(factory, level - 1);
		}

		return grantedMode;
	}

	protected T performEscalation(Header<T> header, Request<T> request,
			int level, T mode, T targetMode, int distanceToTargetLevel) {
		int requestCount = request.getCount();
		int threshold = getEscalationTreshold(level);
		T escalationMode = targetMode.escalate(distanceToTargetLevel);

		if ((threshold != -1) && (requestCount >= threshold)
				&& (escalationMode != mode)
				&& (request.getMode() != escalationMode)
				&& (!existsIncompatibleRequest(header, escalationMode))) {
			if ((DEBUG) && (log.isTraceEnabled()))
				log.trace(String.format("%s count %s at level %s execeeds"
						+ " escalation treshold %s -> escalate request "
						+ "of %s to %s to cover target mode %s", tx
						.toShortString(), requestCount, level, threshold, mode,
						escalationMode, targetMode));

			// escalate the request
			mode = escalationMode;

			// calc the conversion mode we have to request
			if ((DEBUG) && (log.isTraceEnabled()))
				log.trace(String.format(
						"%s current mode is %s -> convert request of %s to %s",
						tx.toShortString(), request.getMode(), mode, request
								.getMode().convert(mode)));
			mode = request.getMode().convert(mode);
		}

		return mode;
	}

	protected T suggestEscalation(Request<T> request, int level, T mode,
			T targetMode, int distanceToTargetLevel) {
		int requestCount = request.getCount();
		int threshold = getEscalationTreshold(level);
		T escalationMode = targetMode.escalate(distanceToTargetLevel);

		if ((threshold != -1) && (requestCount >= threshold)
				&& (escalationMode != mode)
				&& (request.getMode() != escalationMode)) {
			// escalate the request
			mode = escalationMode;
			// calc the conversion mode we have to request
			mode = request.getMode().convert(mode);
		}

		return mode;
	}

	protected boolean existsIncompatibleRequest(Header<T> header,
			T escalationMode) {
		boolean predecessor = true;

		for (Request<T> request = header.getQueue(); request != null; request = request
				.getNext()) {
			if (request.requestedBy().equals(tx)) {
				LockState state = request.getState();

				if (state == LockState.WAITING)
					break;
				else if ((state == LockState.GRANTED)
						&& (!request.getMode().isCompatible(escalationMode))) {
					// log.trace("Incompatible granted request of ta " +
					// transactionId + "found");
					return true;
				} else if ((state == LockState.CONVERTING)
						&& (predecessor)
						&& (!request.getConvertMode().isCompatible(
								escalationMode))) {
					// log.trace("Incompatible convert request of ta " +
					// transactionId + "found");
					return true;
				}
			} else {
				predecessor = false;
			}
		}
		return false;
	}

	protected void updateIntentionLocks(LockName[] lockNames, int level,
			T childMode) {
		Header<T> header = null;
		T requiredParentMode = childMode.requiredParentMode();

		for (int i = level - 1; i >= 0; i--) {
			header = table.find(lockNames[level]);

			if (header == null) {
				log.trace(String.format(
						"Transaction %s has no intention lock on %s.", tx,
						lockNames[i]));
			}

			Request<T> request = header.findTaRequest(tx);

			if (request == null) {
				log.trace(String.format(
						"Transaction %s has no intention lock on %s.", tx,
						lockNames[i]));
			}

			int count = request.getCount();
			if (count == 1) {
				if ((DEBUG) && (log.isTraceEnabled()))
					log.trace(String.format(
							"%s downgrade mode of %s from %s to %s", tx
									.toShortString(), lockNames[i], request
									.getMode(), requiredParentMode));

				request.setMode(requiredParentMode);

				header.unlatch();
				header = null;

				requiredParentMode = requiredParentMode.requiredParentMode();
			} else {
				if ((DEBUG) && (log.isTraceEnabled()))
					log.trace(String.format("%s stop downgrading intention"
							+ " modes because remaining lock %s "
							+ "is locked %s times", tx.toShortString(),
							lockNames[i], count));

				header.unlatch();
				header = null;

				break;
			}
		}
	}
}