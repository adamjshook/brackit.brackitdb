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
package org.brackit.server.tx.thread;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class SyncLatch implements Latch {

	private int mode;
	private int count;

	@Override
	public final synchronized void downS() {
		if ((mode != MODE_U) && (mode != MODE_X)) {
			throw new IllegalStateException(info());
		}

		mode = MODE_S;
		notifyAll();
	}

	@Override
	public final synchronized String info() {
		return String.format("mode=%s count=%s", mode, count);
	}

	public final synchronized boolean isLatchedS() {
		return (mode > MODE_NONE);
	}

	public final synchronized boolean isLatchedU() {
		return (mode == MODE_U);
	}

	public final synchronized boolean isLatchedX() {
		return (mode == MODE_X);
	}

	@Override
	public final synchronized void latchX() {
		while (!latchXC()) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public final synchronized boolean latchXC() {

		if (mode == MODE_NONE) {
			count++;
			mode = MODE_X;

			return true;
		} else {
			return false;
		}
	}

	@Override
	public final synchronized void latchS() {
		while (!latchSC()) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public final synchronized boolean latchSC() {

		if (mode <= MODE_S) {
			count++;
			mode = MODE_S;

			return true;
		} else {
			return false;
		}
	}

	@Override
	public final synchronized void latchSI() {
		latchS();
		unlatch();
	}

	@Override
	public final synchronized void latchU() {
		while (!latchUC()) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public final synchronized boolean latchUC() {

		if (mode <= MODE_S) {
			count++;
			mode = MODE_U;

			return true;
		} else {
			return false;
		}

	}

	@Override
	public final synchronized void unlatch() {
		if ((count == 0) || (mode == MODE_NONE)) {
			throw new IllegalStateException(info());
		}

		count--;

		if (count == 0) {
			mode = MODE_NONE;
		}

		notifyAll();
	}

	@Override
	public final synchronized void upX() {
		while (!upgradeLatchExclusiveConditional()) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}

	private boolean upgradeLatchExclusiveConditional() {
		if (mode != MODE_U) {
			throw new IllegalStateException(info());
		}

		if (count == 1) {
			mode = MODE_X;
			return true;
		} else {
			// some others still have shared access
			return false;
		}
	}

	public final synchronized int getMode() {
		return mode;
	}
}
