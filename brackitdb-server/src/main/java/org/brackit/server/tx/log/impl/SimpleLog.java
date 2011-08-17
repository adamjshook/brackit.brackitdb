/*
 * [New BSD License]
 * Copyright (c) 2011, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
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
package org.brackit.server.tx.log.impl;

import java.nio.ByteBuffer;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.log.Log;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogProperties;
import org.brackit.server.tx.log.Loggable;
import org.brackit.server.tx.log.LoggableHelper;
import org.brackit.server.tx.log.impl.virtual.SegmentLogFileHelper;
import org.brackit.server.tx.log.impl.virtual.VirtualLogFile;
import org.brackit.xquery.util.Cfg;

/**
 * A primitive Log implementation.
 * 
 * @author Ou Yi
 * @author Sebastian Baechle
 * 
 */
public abstract class SimpleLog implements Log {
	private static final Logger log = Logger.getLogger(SimpleLog.class);

	protected final LoggableHelper loggableHelper;

	protected final LogBuffer logBuffer;

	protected long lastLSN;

	protected long nextLSN;

	protected final LogFile raf;

	protected boolean closed;

	protected final LogMonitor logMonitor;

	private boolean flushEnabled;

	private boolean appendEnabled;

	public SimpleLog(LoggableHelper loggableHelper, LogBuffer logBuffer,
			SegmentLogFileHelper helper, long segmentSize, LogMonitor logMonitor) {
		super();
		this.loggableHelper = loggableHelper;
		this.logBuffer = logBuffer;
		this.raf = new VirtualLogFile(helper, segmentSize);
		this.logMonitor = logMonitor;
		this.flushEnabled = Cfg.asBool(LogProperties.FLUSH_ENABLE, true);
		this.appendEnabled = Cfg.asBool(LogProperties.APPEND_ENABLE, true);

		if (!flushEnabled) {
			log.warn("log flushing deactivated");
		}
		if (!appendEnabled) {
			log.warn("log appending deactivated");
		}

		closed = true;
	}

	@Override
	public LoggableHelper getLoggableHelper() {
		return loggableHelper;
	}

	@Override
	public synchronized Loggable get(long lsn) throws LogException {
		Loggable loggable = getInternal(lsn);

		if (loggable == null) {
			// loggable = get(lsn); //this recursive call leads to
			// StackOverflowError
			throw new LogException("Loggable with LSN %s not found.", lsn);
		}

		return loggable;
	}

	private Loggable getInternal(long lsn) throws LogException {
		Loggable loggable = logBuffer.get(lsn);

		if (loggable == null) {
			// lsn must have been already flushed to disk
			loggable = read(lsn);
		}
		return loggable;
	}

	@Override
	public synchronized Loggable first() throws LogException {
		final long head = raf.seekHead();
		return getInternal(head);
	}

	@Override
	public synchronized Loggable next(Loggable loggable) throws LogException {
		return getInternal(loggable.getLSN() + Constants.FIELD_LENGTH_LEN
				+ loggable.getSize());
	}

	private Loggable read(long lsn) throws LogException {
		if (lsn >= raf.getLength()) {
			return null;
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format("Trying to read LSN %s from stable log.",
					lsn));
		}

		long pos = raf.getFilePointer();

		try {
			raf.seek(lsn);
			int length = raf.readInt();
			byte[] record = new byte[length];
			raf.read(record);

			Loggable loggable = loggableHelper.fromBytes(ByteBuffer
					.wrap(record));

			loggable.setLSN(lsn);

			return loggable;
		} finally {
			raf.seek(pos);
		}
	}

	public synchronized void open() throws LogException {
		if (!closed) {
			return;
		}

		raf.open();
		long length = raf.getLength();
		nextLSN = length;
		raf.seek(length);
		closed = false;
	}

	@Override
	public synchronized long append(Loggable loggable) throws LogException {
		if (closed) {
			throw new LogException("Illegal state, log already closed.");
		}

		loggable.setLSN(nextLSN);
		lastLSN = nextLSN;
		nextLSN += Constants.FIELD_LENGTH_LEN + loggable.getSize();

		if (!appendEnabled) {
			return loggable.getLSN();
		}

		boolean appended = logBuffer.add(loggable);

		if (!appended) // logBuffer capacity reached
		{
			flushAll(); // try to get some space

			if (!logBuffer.add(loggable)) // second chance
			{
				throw new LogException(
						"Problem adding a new log entry to the log buffer, try a larger log buffer capacity.");
			}
		}

		// logMonitor.logAppended(logEntry.getLoggable().getSizeInBytes());

		if (log.isDebugEnabled()) {
			log.debug(String.format(
					"Logged LSN %s for TX %s: type=%s logOp=%s.", loggable
							.getLSN(), loggable.getTxID(), loggable.getType(),
					loggable.getLogOperation()));
		}

		return loggable.getLSN();
	}

	@Override
	public synchronized void flush(long lsn) throws LogException {
		byte[] bytesToFlush = logBuffer.pollToFlush(lsn);

		if (bytesToFlush != null) {
			if (log.isDebugEnabled()) {
				log
						.debug(String
								.format(
										"Flushing %6.3f kb including at least LSN %s to stable log.",
										(double) bytesToFlush.length / 1000,
										lsn));
			}

			if (flushEnabled) {
				write(bytesToFlush);

				logMonitor.logFlushed(bytesToFlush.length);
			}
		}
	}

	private void write(byte[] bytesToFlush) throws LogException {
		if (raf.getFilePointer() != raf.getLength()) {
			raf.seek(raf.getLength());
		}

		raf.write(bytesToFlush);
		raf.sync();
	}

	@Override
	public synchronized void flushAll() throws LogException {
		flush(lastLSN);
	}

	@Override
	public void close() throws LogException {
		synchronized (this) {
			if (closed) {
				return;
			}
		}

		flushAll();

		try {
			raf.close();
		} finally {
			closed = true;
		}
	}

	long getRunningLSN() {
		return nextLSN;
	}

	boolean isClosed() {
		return closed;
	}

	public void clear() throws LogException {
		raf.delete();
	}

	@Override
	public synchronized long getNextLSN() {
		return nextLSN;
	}

	@Override
	public synchronized void truncateTo(long minLSN) throws LogException {
		if (closed) {
			throw new LogException("Cannot truncate closed log");
		}

		minLSN = Math.min(raf.getLength(), minLSN);
		raf.truncateTo(minLSN);
	}
}
