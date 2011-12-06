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
package org.brackit.server.tx.log;

/**
 * The Log is a sequence of log entries which are uniquely identified by a
 * monotonously increasing LSN (log sequence number).
 * 
 * For better performance, log entries appended to a Log are not necessarily
 * persisted immediately. There are three typical cases where we should ensure
 * that (i.e., flush them), so that they reach the non-volatile storage.
 * 
 * 1. Before the buffer manager replaces a dirty page (WAL); 2. At commit of a
 * transaction (force-log-at-commit); 3. The log buffer is full (initiated by an
 * append operation).
 * 
 * @author Ou Yi
 * @author Sebastian Baechle
 * 
 */
public interface Log {
	/**
	 * Appends a Loggable to the end of the log and assigns an LSN to this
	 * LogEntry. The logEntry is also appended to the transaction for the
	 * purpose of online recovery (i.e., rollback), etc.
	 * 
	 * @param loggable
	 *            the loggable to be logged.
	 * @return LSN
	 * @throws LogException
	 */
	public long append(Loggable loggable) throws LogException;

	/**
	 * Returns the first logged loggable.
	 * 
	 * @return the first logged loggable
	 * @throws LogException
	 *             iff the first logged loggable could not be returned.
	 */
	public Loggable first() throws LogException;

	/**
	 * Returns the loggable with the given LSN.
	 * 
	 * @param lsn
	 *            the LSN of the loggable
	 * @return the loggable with the given LSN
	 * @throws LogException
	 *             iff the loggable with the given LSN could not be returned.
	 */
	public Loggable get(long lsn) throws LogException;

	/**
	 * Returns the next loggable recorded after the given one
	 * 
	 * @param loggable
	 *            a logged loggable
	 * @return next loggable recorded after the given one
	 * @throws LogException
	 *             iff the next loggable after the given one could not be
	 *             returned.
	 */
	public Loggable next(Loggable loggable) throws LogException;

	/**
	 * Returns the used LoggableHelper.
	 * 
	 * @return the used LoggableHelper
	 */
	public LoggableHelper getLoggableHelper();

	/**
	 * Flushes all the buffered log entries, whose LSN's are less than or equal
	 * to the parameter lsn, to the underlying non-volatile storage.
	 * 
	 * @param lsn
	 * @throws LogException
	 */
	public void flush(long lsn) throws LogException;

	/**
	 * Flushes all the buffered log entries.
	 * 
	 * @throws LogException
	 */
	public void flushAll() throws LogException;

	/**
	 * Opens the log to accept new log appends.
	 * 
	 * @throws LogException
	 */
	public void open() throws LogException;

	/**
	 * Stops accepting new log appends and do a flushAll.
	 * 
	 * @throws LogException
	 */
	public void close() throws LogException;

	/**
	 * Deletes all persistent log files.
	 * 
	 * @throws LogException
	 */
	public void clear() throws LogException;

	/**
	 * Returns the LSN that will be assigned to the next appended log record.
	 * 
	 * @return the LSN that will be assigned to the next appended log record
	 */
	public long getNextLSN();

	/**
	 * Truncates the log file (at most!) to the given LSN.
	 * 
	 * @param minLSN
	 * @throws LogException
	 */
	public void truncateTo(long minLSN) throws LogException;
}
