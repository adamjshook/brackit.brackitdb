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
package org.brackit.server.store.index.aries;

import java.util.HashMap;
import java.util.Map;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.tx.thread.Latch;
import org.brackit.server.tx.thread.LatchFactory;

/**
 * Container for (index) tree latches. For every index there is at most one
 * latch at a time mapped to its index number.
 * 
 * @author Sebastian Baechle
 * 
 */
public final class TreeLatch {
	private static final Logger log = Logger.getLogger(TreeLatch.class
			.getName());

	private final Map<PageID, Latch> latches = new HashMap<PageID, Latch>();

	public final boolean latchS(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s shared.", rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		latch.latchS();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s shared OK.", rootPageID));
		}

		return true;
	}

	public final boolean latchSI(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s sharedInstant.", rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		latch.latchSI();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s sharedInstant OK.", rootPageID));
		}

		return true;
	}

	public final boolean latchX(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s exclusive.", rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		latch.latchX();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s exclusive OK.", rootPageID));
		}

		return true;
	}

	public final boolean latchU(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s update.", rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		latch.latchU();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s update OK.", rootPageID));
		}

		return true;
	}

	public final boolean latchSC(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log
					.trace(String.format("Latch %s shared conditional.",
							rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		boolean latched = latch.latchSC();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s shared conditional %s.",
					rootPageID, (latched ? "OK" : "NOK")));
		}

		return latched;
	}

	public final boolean latchXC(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s exclusive conditional.",
					rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		boolean latched = latch.latchXC();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s exclusive conditional %s.",
					rootPageID, (latched ? "OK" : "NOK")));
		}

		return latched;
	}

	public final boolean latchUC(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log
					.trace(String.format("Latch %s update conditional.",
							rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		boolean latched = latch.latchUC();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Latch %s update conditional %s.",
					rootPageID, (latched ? "OK" : "NOK")));
		}

		return latched;
	}

	public final void upX(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Upgrade latch %s exclusive.", rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		latch.upX();
	}

	public final void downS(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Downgrade latch %s shared.", rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		latch.downS();
	}

	public final boolean unlatch(PageID rootPageID) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("Unlatch %s.", rootPageID));
		}

		Latch latch = getLatch(rootPageID);
		latch.unlatch();

		if (log.isTraceEnabled()) {
			log.trace(String.format("Unlatch %s OK.", rootPageID));
		}

		return false;
	}

	private final synchronized Latch getLatch(PageID rootPageID) {
		Latch latch = latches.get(rootPageID);

		if (latch == null) {
			latch = LatchFactory.create();
			latches.put(rootPageID, latch);
		}

		return latch;
	}

	public boolean isLatchedX(PageID rootPageID) {
		Latch latch = getLatch(rootPageID);

		return latch.isLatchedX();
	}
}