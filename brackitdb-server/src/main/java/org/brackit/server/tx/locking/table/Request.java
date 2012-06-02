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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.locking.LockClass;
import org.brackit.server.tx.locking.LockState;
import org.brackit.server.tx.locking.protocol.LockMode;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public final class Request<T extends LockMode<T>> {
	private static final Logger log = Logger.getLogger(Request.class);

	final Header<T> header;

	final Tx tx;

	Request<T> taPrevious;

	Request<T> taNext;

	Request<T> next;

	T mode;

	T convertMode;

	int count = 0;

	LockClass lockClass;

	volatile LockState state = LockState.IGNORED; // may be read without

	// latching the header

	public Request(Header<T> header, Tx tx, LockClass lockClass) {
		this.header = header;
		this.tx = tx;
		this.lockClass = lockClass;
	}

	public Request<T> getTaPrevious() {
		return taPrevious;
	}

	public void setTaPrevious(Request<T> taPrevious) {
		this.taPrevious = taPrevious;
	}

	public Request<T> getTaNext() {
		return taNext;
	}

	public void setTaNext(Request<T> taNext) {
		this.taNext = taNext;
	}

	public Request<T> getNext() {
		return next;
	}

	public void setNext(Request<T> next) {
		this.next = next;
	}

	public T getMode() {
		return mode;
	}

	public void setMode(T mode) {
		this.mode = mode;
	}

	public T getConvertMode() {
		return convertMode;
	}

	public void setConvertMode(T convertMode) {
		this.convertMode = convertMode;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public void incCount() {
		count++;
	}

	public void decCount() {
		count--;
	}

	public LockClass getLockClass() {
		return lockClass;
	}

	public void setLockClass(LockClass lockClass) {
		this.lockClass = lockClass;
	}

	public LockState getState() {
		return state;
	}

	public void setState(LockState state) {
		this.state = state;
	}

	public Header<T> getHeader() {
		return header;
	}

	public Tx requestedBy() {
		return tx;
	}

	public Tx blockedBy() {
		Tx waitForTa = null;

		header.latchX();
		try {
			if (state != LockState.GRANTED) {
				waitForTa = findWaitForTransaction();
			}
		} finally {
			header.unlatch();
		}

		return waitForTa;
	}

	public boolean grant(T newMode) {
		T grantedMode = header.getGrantedMode();

		if (state == LockState.IGNORED) // new lock request
		{
			mode = newMode;

			if (grantedMode == null) {
				header.setGrantedMode(newMode);
				state = LockState.GRANTED;
				return true;
			} else if (grantedMode.isCompatible(newMode)) // check if we can
			// grant directly
			{
				grantedMode = grantedMode.convert(newMode);
				header.setGrantedMode(grantedMode);
				state = LockState.GRANTED;
				return true;
			} else // we have to wait for grant
			{
				state = LockState.WAITING;
			}
		} else if (state == LockState.GRANTED) // previous lock request already
		// granted
		{
			newMode = mode.convert(newMode);

			// calc the conversion mode we have to request
			if (log.isTraceEnabled()) {
				log.trace(String.format(
						"%s current mode is %s -> convert request of %s to %s",
						tx.toShortString(), mode, newMode, mode
								.convert(newMode)));
			}

			if (newMode == mode) // we do not need lock conversion
			{
				return true;
			} else if (grantedMode.isCompatible(newMode)) // check if we can
			// convert directly
			{
				grantedMode = grantedMode.convert(newMode);
				header.setGrantedMode(grantedMode);
				mode = newMode;
				return true;
			} else // ok then we have to wait for conversion
			{
				state = LockState.CONVERTING;
				convertMode = newMode;
			}
		} else if (state == LockState.WAITING) // previous request not granted
		// yet, just update request mode
		{
			newMode = mode.convert(newMode);
			mode = newMode;
		} else if (state == LockState.CONVERTING) // already converting previous
		// request, just update
		// conversion mode
		{
			newMode = convertMode.convert(newMode);
			convertMode = newMode;
		}

		// could not granted lock -> mark header waiting and recompute lock
		// grants
		header.setWaiting(true);
		header.grantRequestChain();

		return (state == LockState.GRANTED);
	}

	protected Tx findWaitForTransaction() {
		boolean passedMySelf = false;

		// which mode we are waiting for?
		T waitForMode = (state == LockState.WAITING) ? mode : convertMode;

		if (waitForMode == null) {
			log.error(String.format("Blocked transaction %s i"
					+ "s waiting for lock mode NONE. "
					+ "This should not happen.", tx));
			log.error(header.printLockChain());
			throw new RuntimeException(String.format(
					"Transaction %s is waiting for mode NONE.", tx
							.toShortString()));
		}

		// scan for first request of another tx that not compatible with myself
		for (Request<T> waitRequest = header.getQueue(); waitRequest != null; waitRequest = waitRequest
				.getNext()) {
			if ((passedMySelf) && (state != LockState.GRANTED)
					&& (state != LockState.CONVERTING)) {
				log.error(String.format("No incompatible granted or converting"
						+ " request found to block ta %s. "
						+ "This should not happen.", tx));
				log.error(header.printLockChain());
				throw new RuntimeException(
						"Blocked transaction is compatible with all predecessors.");
			}

			try {
				if (tx.equals(waitRequest.requestedBy())) {
					passedMySelf = true;
				} else if ((!waitRequest.getMode().isCompatible(waitForMode))
						|| ((waitRequest.getState() == LockState.CONVERTING) && (!waitRequest
								.getConvertMode().isCompatible(waitForMode)))
						|| ((!passedMySelf) && state == LockState.CONVERTING)) {
					return waitRequest.requestedBy();
				}
			} catch (NullPointerException e) {
				System.out.println(tx);
				System.out.println(waitRequest);
				throw e;
			}
		}

		return null;
		// log.error(String.format("No incompatible granted or converting request found to block ta %s. This should not happen.",
		// transaction));
		// log.error(header.printLockChain());
		// throw new
		// RuntimeException("Blocked transaction is compatible with all predecessors.");
	}

	@Override
	public String toString() {
		return String.format("{ %s | %s | %s | %s | %s | %s }", tx
				.toShortString(), header.toString(), mode, convertMode, state,
				count);
	}

	public synchronized void wakeup() {
		notifyAll();
	}
}