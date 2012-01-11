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
package org.brackit.server.io.buffer.impl;

import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.file.BlockSpace;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.tx.log.Log;

/**
 * @author Sebastian Baechle
 */
public final class TQBuffer extends AbstractBuffer {
	private static final boolean DEBUG = false;

	enum State {
		FREE, AM, A1, PREFETCHED
	}

	private class TQP extends Frame {
		State state = State.FREE;
		TQP toLRU;
		TQP toMRU;

		volatile int fix = 0;

		TQP(int pageSize) {
			super(pageSize);
		}

		@Override
		int fixCount() {
			return fix;
		}

		@Override
		void drop() {
			if (state == State.PREFETCHED) {
				removeFromP();
			} else if (state == State.A1) {
				removeFromA1();
			} else if (state == State.AM) {
				removeFromAM();
			}
		}

		@Override
		void prefetched() {
			addAsMRUToP();
		}

		@Override
		void fix() {
			if (state == State.FREE) {
				// add page to FIFO queue A1
				addAsMRUToA1();
			} else if (state == State.PREFETCHED) {
				// promote from prefetch list
				// to FIFO queue A1
				removeFromP();
				addAsMRUToA1();
			} else if (state == State.A1) {
				// promote page from FIFO
				// queue A1 to LRU chain AM
				removeFromA1();
				addAsMRUToAM();
			} else if (state == State.AM) {
				// simply move page to MRU
				// of LRU chain AM
				moveToAMMRU();
			} else {
				throw new IllegalStateException("State: " + state);
			}
			fix++;
		}

		@Override
		boolean isFixed() {
			return fix > 0;
		}

		@Override
		void unfix() {
			if (fix-- == 0) {
				throw new RuntimeException("handle already unfixed");
			}
		}

		void removeFromA1() {
			if (DEBUG) {
				if (state != State.A1)
					throw new IllegalStateException("State: " + state);
				if ((toMRU != null) && (toMRU.state != State.A1))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.A1))
					throw new IllegalStateException("State: " + toLRU.state);
			}

			if (toLRU == null)
				a1LRU = toMRU;
			else
				toLRU.toMRU = toMRU;

			if (toMRU == null)
				a1MRU = toLRU;
			else
				toMRU.toLRU = toLRU;

			a1Length--;
			toMRU = null;
			toLRU = null;
			state = State.FREE;
		}

		void addAsMRUToAM() {
			if (DEBUG) {
				if (state != State.FREE)
					throw new IllegalStateException("State: " + state);
				if (toLRU != null)
					throw new IllegalStateException("toLRU: " + toLRU.state);
				if (toMRU != null)
					throw new IllegalStateException("toLRU: " + toMRU.state);
			}

			if (amLRU == null) {
				amLRU = this;
				amMRU = this;
				toLRU = null;
				toMRU = null;
			} else {
				amMRU.toMRU = this;
				toLRU = amMRU;
				toMRU = null;
				amMRU = this;
			}
			state = State.AM;
			if (DEBUG) {
				if ((toMRU != null) && (toMRU.state != State.AM))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.AM))
					throw new IllegalStateException("State: " + toLRU.state);
				if ((amLRU != null) && (amLRU.state != State.AM))
					throw new IllegalStateException("State: " + amLRU.state);
				if (amMRU != this)
					throw new IllegalStateException();
			}
		}

		void addAsMRUToA1() {
			if (DEBUG) {
				if (state != State.FREE)
					throw new IllegalStateException("State: " + state);
				if (toLRU != null)
					throw new IllegalStateException("toLRU: " + toLRU.state);
				if (toMRU != null)
					throw new IllegalStateException("toLRU: " + toMRU.state);
			}

			if (a1LRU == null) {
				a1LRU = this;
				a1MRU = this;
				toLRU = null;
				toMRU = null;
			} else {
				a1MRU.toMRU = this;
				toLRU = a1MRU;
				a1MRU = this;
				toMRU = null;
			}
			a1Length++;
			state = State.A1;

			if (DEBUG) {
				if ((toMRU != null) && (toMRU.state != State.A1))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.A1))
					throw new IllegalStateException("State: " + toLRU.state);
				if ((a1LRU != null) && (a1LRU.state != State.A1))
					throw new IllegalStateException("State: " + a1LRU.state);
				if (a1MRU != this)
					throw new IllegalStateException();
			}
		}

		void addAsMRUToP() {
			if (DEBUG) {
				if (state != State.FREE)
					throw new IllegalStateException("State: " + state);
				if (toLRU != null)
					throw new IllegalStateException("toLRU: " + toLRU.state);
				if (toMRU != null)
					throw new IllegalStateException("toLRU: " + toMRU.state);
			}

			if (pMRU == null) {
				pMRU = this;
				toLRU = null;
				toMRU = null;
			} else {
				pMRU.toMRU = this;
				toLRU = pMRU;
				toMRU = null;
				pMRU = this;
			}
			state = State.PREFETCHED;
			if (DEBUG) {
				if ((toMRU != null) && (toMRU.state != State.PREFETCHED))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.PREFETCHED))
					throw new IllegalStateException("State: " + toLRU.state);
				if (pMRU != this)
					throw new IllegalStateException();
			}
		}

		void removeFromP() {
			if (DEBUG) {
				if (state != State.PREFETCHED)
					throw new IllegalStateException("State: " + state);
				if ((toMRU != null) && (toMRU.state != State.PREFETCHED))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.PREFETCHED))
					throw new IllegalStateException("State: " + toLRU.state);
			}

			if (toLRU != null)
				toLRU.toMRU = toMRU;

			if (toMRU == null)
				pMRU = toLRU;
			else
				toMRU.toLRU = toLRU;

			toMRU = null;
			toLRU = null;
			state = State.FREE;
		}

		void removeFromAM() {
			if (DEBUG) {
				if (state != State.AM)
					throw new IllegalStateException("State: " + state);
				if ((toMRU != null) && (toMRU.state != State.AM))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.AM))
					throw new IllegalStateException("State: " + toLRU.state);
			}

			if (toLRU == null)
				amLRU = toMRU;
			else
				toLRU.toMRU = toMRU;

			if (toMRU == null)
				amMRU = toLRU;
			else
				toMRU.toLRU = toLRU;

			toMRU = null;
			toLRU = null;
			state = State.FREE;
		}

		void moveToAMMRU() {
			if (DEBUG) {
				if (state != State.AM)
					throw new IllegalStateException("State: " + state);
				if ((toMRU != null) && (toMRU.state != State.AM))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.AM))
					throw new IllegalStateException("State: " + toLRU.state);
			}

			if (amMRU == this) {
				return;
			}
			TQP n = toMRU;

			if (toLRU != null) {
				toLRU.toMRU = n;
				if (n != null) {
					n.toLRU = toLRU;
				}
			} else {
				amLRU = toMRU;
				toMRU.toLRU = null;
			}

			toMRU = null;
			amMRU.toMRU = this;
			toLRU = amMRU;
			amMRU = this;
			if (DEBUG) {
				if ((toMRU != null) && (toMRU.state != State.AM))
					throw new IllegalStateException("State: " + toMRU.state);
				if ((toLRU != null) && (toLRU.state != State.AM))
					throw new IllegalStateException("State: " + toLRU.state);
			}
		}

		@Override
		public String toString() {
			return ((state != null) ? super.toString() + state : super
					.toString());
		}
	}

	final int threshold;

	// prefetched
	TQP pMRU;

	// AM
	TQP amLRU;
	TQP amMRU;

	// A1
	TQP a1LRU;
	TQP a1MRU;
	int a1Length;

	public TQBuffer(BlockSpace blockSpace, int bufferSize, Log transactionLog,
			BufferMgr bufferMgr) throws BufferException {
		super(blockSpace, bufferSize, transactionLog, bufferMgr);
		threshold = Math.max(1, bufferSize / 10);
	}

	@Override
	protected Frame grow(int pageSize) {
		TQP p = new TQP(pageSize);
		return p;
	}

	@Override
	protected Frame shrink() {
		if (pMRU != null) {
			TQP p = pMRU;
			p.removeFromP();
			return p;
		}
		if (a1Length > threshold) {
			for (TQP p = a1LRU; p != null; p = p.toMRU) {
				if (p.fix == 0) {
					p.removeFromA1();
					return p;
				}
			}
		}
		for (TQP p = amLRU; p != null; p = p.toMRU) {
			if (p.fix == 0) {
				p.removeFromAM();
				return p;
			}
		}
		// ultima ratio: drain FIFO
		for (TQP p = a1LRU; p != null; p = p.toMRU) {
			if (p.fix == 0) {
				p.removeFromA1();
				return p;
			}
		}

		return null;
	}
}