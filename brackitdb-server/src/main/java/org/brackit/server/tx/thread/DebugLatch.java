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

public class DebugLatch implements Latch {
	private final Latch latch;

	public DebugLatch(Latch latch) {
		this.latch = latch;
	}

	private final int[] states = new int[500];

	public void downS() {
		int myID = myID();
		if (states[myID] != -1)
			throw new RuntimeException();
		latch.downS();
		states[myID] = 1;
	}

	private int myID() {
		return ThreadCB.get().id;
	}

	public int getMode() {
		return latch.getMode();
	}

	public String info() {
		return latch.info();
	}

	public boolean isLatchedX() {
		return latch.isLatchedX();
	}

	public boolean isLatchedS() {
		return latch.isLatchedS();
	}

	public boolean isLatchedU() {
		return latch.isLatchedU();
	}

	public void latchX() {
		int myID = myID();
		if (states[myID] != 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		latch.latchX();
		states[myID] = -2;
	}

	public boolean latchXC() {
		int myID = myID();
		if (states[myID] != 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		boolean res = latch.latchXC();
		if (res)
			states[myID] = -2;
		return res;
	}

	public void latchS() {
		int myID = myID();
		if (states[myID] < 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		latch.latchS();
		states[myID] += 1;
	}

	public boolean latchSC() {
		int myID = myID();
		if (states[myID] < 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		boolean res = latch.latchSC();
		if (res)
			states[myID] += 1;
		return res;
	}

	public void latchSI() {
		int myID = myID();
		if (states[myID] < 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		latch.latchSI();
	}

	public void latchU() {
		int myID = myID();
		if (states[myID] != 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		latch.latchU();
		states[myID] = -1;
	}

	public boolean latchUC() {
		int myID = myID();
		if (states[myID] != 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		boolean res = latch.latchUC();
		if (res)
			states[myID] = -1;
		return res;
	}

	public void unlatch() {
		int myID = myID();
		if (states[myID] == 0)
			throw new RuntimeException(Integer.toString(states[myID]));
		if (states[myID] == -1)
			throw new RuntimeException(Integer.toString(states[myID]));
		latch.unlatch();
		if (states[myID] == -2)
			states[myID] = 0;
		else
			states[myID] -= 1;
	}

	public void upX() {
		int myID = myID();
		if (states[myID] != -1)
			throw new RuntimeException(Integer.toString(states[myID]));
		latch.upX();
		states[myID] = -2;
	}

	public void check(int expected) {
		int myID = myID();
		if (states[myID] != expected)
			throw new RuntimeException(String.format(
					"Expected %s but was %s: %s", expected, Integer
							.toString(states[myID]), latch.info()));
	}
}
