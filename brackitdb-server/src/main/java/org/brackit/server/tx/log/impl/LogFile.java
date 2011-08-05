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

import org.brackit.server.tx.log.LogException;

/**
 * Interface of an append-only log file. If supported by the concrete
 * implementation, a log file can be truncated from the head to a specific
 * position. Access to the truncated area will not be accessible anymore and
 * result in an error.
 * 
 * @author Sebastian Baechle
 * 
 */
public interface LogFile {
	long getLength() throws LogException;

	long truncateTo(long pos) throws LogException;

	long getFilePointer() throws LogException;

	void sync() throws LogException;

	void open() throws LogException;

	void close() throws LogException;

	long seekHead() throws LogException;

	void seek(long pos) throws LogException;

	void write(byte[] b) throws LogException;

	int read(byte[] b) throws LogException;

	int readInt() throws LogException;

	void writeInt(int i) throws LogException;

	long readLong() throws LogException;

	void writeLong(long l) throws LogException;

	void delete() throws LogException;
}
