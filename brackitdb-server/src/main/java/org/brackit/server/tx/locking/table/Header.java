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
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.LockState;
import org.brackit.server.tx.locking.protocol.LockMode;
import org.brackit.server.tx.thread.Latch;
import org.brackit.server.tx.thread.SyncLatch;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class Header<T extends LockMode<T>> extends SyncLatch implements Latch {
	private static final Logger log = Logger.getLogger(Header.class);

	final LockName name;

	T grantedMode;

	Request<T> queue;

	boolean isWaiting;

	public Header(LockName name) {
		this.name = name;
	}

	public LockName getName() {
		return name;
	}

	public T getGrantedMode() {
		return grantedMode;
	}

	public void setGrantedMode(T grantedMode) {
		this.grantedMode = grantedMode;
	}

	public boolean isWaiting() {
		return isWaiting;
	}

	public void setWaiting(boolean isWaiting) {
		this.isWaiting = isWaiting;
	}

	public Request<T> getQueue() {
		return queue;
	}

	public void enqueue(Request<T> request) {
		// scan for previous request
		Request<T> previous = findPreviousRequest(request);

		// append request to end of lock chain
		if (previous == null)
			queue = request;
		else
			previous.next = request;
	}

	public void dequeue(Request<T> request) {
		// scan for previous request
		Request<T> previous = findPreviousRequest(request);

		// remove request from lock queue
		if (previous == null) // request was first in lock chain
		{
			queue = request.next;
		} else // other requests in lock chain are in front
		{
			previous.next = request.next;
		}
	}

	public Request<T> findTaRequest(Tx transaction) {
		for (Request<T> request = queue; request != null; request = request.next) {
			if (request.tx.equals(transaction)) {
				return request;
			}
		}
		return null;
	}

	private Request<T> findPreviousRequest(Request<T> request) {
		Request<T> previousRequest = null;

		for (Request<T> current = queue; current != null; current = current.next) {
			if (current == request) {
				break;
			} else {
				previousRequest = current;
			}
		}

		return previousRequest;
	}

	public void grantRequestChain() {
		// save reset granted mode
		T grantedMode = null;
		boolean wait = false;

		if (log.isTraceEnabled()) {
			log.trace(String.format("Recompute lock grants for %s.", this));
			log.trace(printLockChain());
		}

		if (!isWaiting) {
			if (log.isTraceEnabled()) {
				log.trace("No waiting requests to grant.");
			}

			return;
		}

		for (Request<T> request = getQueue(); request != null; request = request
				.getNext()) {
			LockState state = request.getState();

			if (log.isTraceEnabled()) {
				log.trace("Current granted mode is " + grantedMode);
			}

			if (state == LockState.GRANTED) {
				if (grantedMode == null) {
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Request %s is already granted. "
										+ "Setting granted mode to %s",
								request, grantedMode, request.getMode()));
					}

					grantedMode = request.getMode();
				} else {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Request %s"
								+ " is already granted. "
								+ "Updating granted mode from %s to %s",
								request, grantedMode, grantedMode
										.convert(request.getMode())));
					}

					grantedMode = grantedMode.convert(request.getMode());
				}
			} else if (state == LockState.CONVERTING) {
				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Request %s is leading of converters. "
									+ "Remember it for later", request));
				}

				for (Request<T> trailing = request.getNext(); trailing != null; trailing = trailing
						.getNext()) {
					LockState trailingState = trailing.getState();

					if ((trailingState == LockState.GRANTED)
							|| (trailingState == LockState.CONVERTING)) {
						T newMode = (grantedMode != null) ? grantedMode
								.convert(request.getMode()) : request.getMode();

						if (log.isTraceEnabled()) {
							log.trace(String.format("Trailing request %s "
									+ "is already granted. "
									+ "Updating granted mode"
									+ " from %s to %s", request, grantedMode,
									newMode));
						}

						grantedMode = newMode;
					} else {
						break;
					}
				}

				if (log.isTraceEnabled()) {
					log.trace(String.format(
							"Continue with conversion request %s", request));
				}

				T convertMode = request.getConvertMode();

				if (grantedMode == null) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Conversion request %s"
								+ " is first in lock chain. "
								+ "Setting granted mode "
								+ "to %s and continue lock granting", request,
								convertMode));
					}

					grantedMode = convertMode;
					request.setState(LockState.GRANTED);
					request.setMode(convertMode);
					request.setConvertMode(null); // reset conversion
					request.wakeup(); // wakeup blocked transaction
				} else if (grantedMode.isCompatible(convertMode)) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Conversion request %s"
								+ " is compatible with grantedMode."
								+ " Updating granted mode from"
								+ " %s to %s and continue lock granting",
								request, grantedMode, grantedMode
										.convert(convertMode)));
					}

					grantedMode = grantedMode.convert(convertMode);
					request.setState(LockState.GRANTED);
					request.setMode(convertMode);
					request.setConvertMode(null); // reset conversion
					request.wakeup(); // wakeup blocked transaction
				} else // conversion lock cannot be granted
				{
					if (log.isTraceEnabled()) {
						log.trace(String.format("Conversion request %s"
								+ " is NOT compatible with current "
								+ "grantedMode. Update granted mode "
								+ "from %s to %s and stop lock granting",
								request, grantedMode, grantedMode
										.convert(request.getMode())));
					}

					grantedMode = grantedMode.convert(request.getMode());
					wait = true;
					break;
				}
			} else if (state == LockState.WAITING) {
				T requestedMode = request.getMode();

				if (log.isTraceEnabled()) {
					log.trace(String.format("Request %s is waiting.", request));
				}

				if (grantedMode == null) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Waiting Request %s"
								+ " is first in lock chain. "
								+ "Updating granted mode to %s", request,
								requestedMode));
					}

					grantedMode = requestedMode;
					request.setState(LockState.GRANTED);
					request.wakeup(); // wakeup blocked transaction
				} else if (grantedMode.isCompatible(requestedMode)) {
					if (log.isTraceEnabled()) {
						log.trace(String.format("Waiting Request %s"
								+ " is compatible with grantedMode. "
								+ "updating granted mode from %s to %s",
								request, grantedMode, grantedMode
										.convert(requestedMode)));
					}

					grantedMode = grantedMode.convert(requestedMode);
					request.setState(LockState.GRANTED);
					request.wakeup(); // wakeup blocked transaction
				} else // lock cannot be granted
				{
					if (log.isTraceEnabled()) {
						log.trace(String.format("Waiting request %s"
								+ " is NOT compatible with "
								+ "current grantedMode.", request));
					}

					wait = true;
					break;
				}
			}
		}

		// set new granted mode
		setGrantedMode(grantedMode);
		setWaiting(wait);

		if (log.isTraceEnabled()) {
			log.trace("Finished granting in lock chain");
			// log.trace(printLockChain());
		}
	}

	@Override
	public int hashCode() {
		if (name == null) {
			return 0;
		}

		return name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj instanceof Header) {
			Header<T> header = (Header<T>) obj;
			return name.equals(header.name);
		}

		return false;
	}

	public String printLockChain() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("[%s | %s | %s]", name, grantedMode,
				isWaiting));
		for (Request<T> request = queue; request != null; request = request
				.getNext())
			buffer.append(" -> " + request);
		return buffer.toString();
	}

	@Override
	public String toString() {
		return name.toString();
	}
}