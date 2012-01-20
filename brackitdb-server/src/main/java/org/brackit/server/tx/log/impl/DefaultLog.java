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
package org.brackit.server.tx.log.impl;

import org.brackit.server.tx.log.impl.virtual.RAFSegmentHelper;
import org.brackit.server.tx.log.impl.virtual.SegmentLogFileHelper;
import org.brackit.xquery.util.Cfg;

/**
 * @author Sebastian Baechle
 * 
 */
public class DefaultLog extends SimpleLog {

	public static final String LOGFILE_DIRECTORY = "org.brackit.server.tx.taMgr.logDirectory";
	public static final String LOGFILE_BASENAME = "org.brackit.server.tx.taMgr.logBasename";
	// max size of log file segments: x * 1KB (1KB = 1024 bytes)
	public static final String LOGFILE_SEGMENTSIZE = "org.brackit.server.tx.taMgr.logSegmentSize";

	public DefaultLog() {
		this(Cfg.asString(LOGFILE_DIRECTORY, "log"), Cfg.asString(
				LOGFILE_BASENAME, "tx"),
				Cfg.asLong(LOGFILE_SEGMENTSIZE, 10000) * 1024);
	}

	public DefaultLog(String directory, String basename, long segmentSize) {
		this(new LogRecordHelper(), new SimpleLogBuffer(),
				new RAFSegmentHelper(directory, basename), segmentSize,
				new LogMonitor());
	}

	protected DefaultLog(LogRecordHelper logRecordHelper, LogBuffer logBuffer,
			SegmentLogFileHelper helper, long segmentSize, LogMonitor logMonitor) {
		super(logRecordHelper, logBuffer, helper, segmentSize, logMonitor);
	}
}
