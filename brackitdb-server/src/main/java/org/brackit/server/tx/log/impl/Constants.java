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
package org.brackit.server.tx.log.impl;

import org.brackit.server.tx.log.SizeConstants;

/**
 * Implementation-relevant constants. Thus non-public.
 * 
 * @author Ou Yi
 * 
 */
class Constants {
	private Constants() {
	}

	static final String LOG_FILE_NAME_PREFIX = "xtc";

	static final String LOG_FILE_NAME_SURFIX = ".log";

	// TODO: handle multiple log files
	static final String LOG_FILE_NAME = LOG_FILE_NAME_PREFIX
			+ LOG_FILE_NAME_SURFIX;

	/**
	 * asynchronous read and write, explicit sync necessary.
	 */
	static final String LOG_FILE_MODE = "rw";

	/**
	 * Not in use yet.
	 * 
	 * Max. possible LOG_FILE_SIZE == 2^32 == 4294967296 Bytes == 4 GB, because
	 * the second half (32 bits) of the LSN is used to address bytes within a
	 * log file.
	 * 
	 * here: 4096 * 4096 == 2^12 * 2^12 == 16777216 Bytes == 16 MB
	 */
	static final int LOG_FILE_SIZE = 4096 * 4096;

	static final int FIELD_LENGTH_LEN = SizeConstants.INT_SIZE;

	/**
	 * Capacity of the log buffer in bytes.
	 * 
	 * here: 16 MB
	 */
	static final int LOG_BUFFER_CAPACITY = 1024 * 1024 * 16;

	/**
	 * A block in the log file: 256 KB. This value should be close to an
	 * "erase unit" of flash disk. Experiments suggest a minimum of 64 KB for
	 * both hard disk and flash disk.
	 */
	static final int LOG_BUFFER_BLOCK_SIZE = 256 * 1024;

}
