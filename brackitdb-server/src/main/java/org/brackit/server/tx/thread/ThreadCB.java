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
package org.brackit.server.tx.thread;

import java.util.HashMap;
import java.util.Random;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;

public final class ThreadCB {
	private final static String LOG_PATH = "/media/ramdisk/";

	public final Logger log;

	public final Thread thread;

	public final Random random;

	public Thread supervisor;

	private HashMap<PageID, Integer> myFixes = new HashMap<PageID, Integer>(64);

	private HashMap<Latch, Integer> myLatches = new HashMap<Latch, Integer>(64);

	public final int id;

	private int fixed;

	private int unfixed;

	private int currentyFixed;

	private int pageHintMisses;

	private int pageHintHits;

	public ThreadCB waiting = null;

	private static ThreadLocal<ThreadCB> controlBlock = new ThreadLocal<ThreadCB>() {
		@Override
		protected ThreadCB initialValue() {
			return new ThreadCB();
		}
	};

	private ThreadCB() {
		id = IDAssigner.counter.getAndIncrement();
		thread = Thread.currentThread();
		random = new Random();
		log = Logger.getLogger(thread.getName());
		// try
		// {
		// //FileAppender fileAppender = new FileAppender(new
		// PatternLayout("%d [%l] %-5p %c{1} - %m%n"), LOG_PATH +
		// thread.getName() + ".log", false);
		// FileAppender fileAppender = new FileAppender(new
		// PatternLayout("%d [%t] %-5p %c{1} - %m%n"), LOG_PATH +
		// thread.getName() + ".log", false);
		// log.setLevel(Level.TRACE);
		// log.addAppender(fileAppender);
		// }
		// catch (IOException e)
		// {
		// log.error(e);
		// }
	}

	public final static ThreadCB get() {
		return controlBlock.get();
	}

	public void registerUnfix(PageID pageID) {
		int fixCount = myFixes.get(pageID);

		if (fixCount == -1) {
			throw new RuntimeException("Did not fix " + pageID + " my fix set "
					+ myFixes);
		} else if (fixCount == 1) {
			myFixes.remove(pageID);
		} else {
			myFixes.put(pageID, fixCount - 1);
		}

		unfixed++;
		currentyFixed--;

		if (currentyFixed != fixed - unfixed) {
			throw new RuntimeException(String.format(
					"CurrentlyFixed %s Fixed %s  Unfixed %s", currentyFixed,
					fixed, unfixed));
		}

		if (unfixed > fixed) {
			throw new RuntimeException(String.format(
					"CurrentlyFixed %s Fixed %s  Unfixed %s", currentyFixed,
					fixed, unfixed));
		}
	}

	public void registerFix(PageID pageID) {
		int fixCount = myFixes.get(pageID);

		if (fixCount == -1) {
			myFixes.put(pageID, 1);
		} else {
			myFixes.put(pageID, fixCount + 1);
		}

		fixed++;
		currentyFixed++;
	}

	public void registerUnlatch(Latch latch) {
		Integer lc = myLatches.get(latch);
		int latchCount = (lc == null) ? -1 : lc;

		if (latchCount == -1) {
			throw new RuntimeException("Did not latch " + latch
					+ " my latch set " + myLatches);
		} else if (latchCount == 1) {
			myLatches.remove(latch);
		} else {
			myLatches.put(latch, latchCount - 1);
		}
	}

	public void registerUpdateLatch(Latch latch) {
		Integer lc = myLatches.get(latch);
		int latchCount = (lc == null) ? -1 : lc;

		if (latchCount == -1) {
			myLatches.put(latch, 1);
		} else {
			myLatches.put(latch, latchCount + 1);
		}
	}

	public void registerExclusiveLatch(Latch latch) {
		Integer lc = myLatches.get(latch);
		int latchCount = (lc == null) ? -1 : lc;

		if (latchCount == -1) {
			myLatches.put(latch, 1);
		} else {
			myLatches.put(latch, latchCount + 1);
		}
	}

	public void registerSharedLatch(Latch latch) {
		Integer lc = myLatches.get(latch);
		int latchCount = (lc == null) ? -1 : lc;

		if (latchCount == -1) {
			myLatches.put(latch, 1);
		} else {
			myLatches.put(latch, latchCount + 1);
		}
	}

	public int getLatchedCount() {
		return myLatches.size();
	}
	
	public String getLatches() {
		return myLatches.toString();
	}

	public void countPageHintMiss() {
		pageHintMisses++;
	}

	public void countPageHintHit() {
		pageHintHits++;
	}

	public int getPageHintMisses() {
		return pageHintMisses;
	}

	public int getPageHintHits() {
		return pageHintHits;
	}

	public void clearPageHintCounter() {
		pageHintMisses = 0;
		pageHintHits = 0;
	}

	public int getFixCount() {
		return myFixes.size();
	}
}
