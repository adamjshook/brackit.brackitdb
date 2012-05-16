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
package org.brackit.server.tx.log.impl.virtual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.impl.LogFile;
import org.junit.Test;

/**
 * @author Sebastian Baechle
 * 
 */
public class VirtualLogFileTest {
	private static final String BASENAME = "tx";

	private static final String LOG_DIR = "/media/ramdisk";

	private static final int MAX_SEGMENT_SIZE = SegmentLogFile.HEADER_SIZE + 32;

	private static final int MAX_WRITE_SIZE = 3 * MAX_SEGMENT_SIZE;

	private static final int NUMBER_OF_BYTES_TO_WRITE = 2048;

	private LogFile logFile;

	@Test
	public void testWriteAndSeek() throws LogException {
		for (int byteLength = 1; byteLength <= MAX_WRITE_SIZE; byteLength++) {
			logFile = new VirtualLogFile(new MockupSegmentHelper(LOG_DIR,
					BASENAME), MAX_SEGMENT_SIZE);
			logFile.delete();
			logFile.open();

			writeLog(byteLength, NUMBER_OF_BYTES_TO_WRITE);
			readLog(byteLength, NUMBER_OF_BYTES_TO_WRITE, 0);

			logFile.close();
			logFile.delete();
		}
	}

	@Test
	public void testWriteCloseAndRead() throws LogException {
		for (int byteLength = 1; byteLength <= MAX_WRITE_SIZE; byteLength++) {
			logFile = new VirtualLogFile(new MockupSegmentHelper(LOG_DIR,
					BASENAME), MAX_SEGMENT_SIZE);
			logFile.delete();
			logFile.open();

			writeLog(byteLength, NUMBER_OF_BYTES_TO_WRITE);

			logFile.close();
			logFile.open();

			readLog(byteLength, NUMBER_OF_BYTES_TO_WRITE, 0);

			logFile.close();
			logFile.delete();
		}
	}

	@Test
	public void testWriteTruncateAndRead() throws LogException {
		for (int byteLength = 1; byteLength <= MAX_WRITE_SIZE; byteLength++) {
			for (int truncateTo = 0; truncateTo < NUMBER_OF_BYTES_TO_WRITE
					/ byteLength; truncateTo++) {
				logFile = new VirtualLogFile(new MockupSegmentHelper(LOG_DIR,
						BASENAME), MAX_SEGMENT_SIZE);
				logFile.delete();
				logFile.open();

				writeLog(byteLength, NUMBER_OF_BYTES_TO_WRITE);

				long wasTruncatedTo = logFile.truncateTo(truncateTo);

				assertEquals("Log was truncated to desired position.",
						truncateTo, wasTruncatedTo);

//				System.out.println(String.format("Truncate to %5d => %5d",
//						truncateTo, wasTruncatedTo));

				readLog(byteLength, NUMBER_OF_BYTES_TO_WRITE, wasTruncatedTo);

				logFile.close();
				logFile.delete();
			}
		}
	}

	// @Test
	public void testWriteTruncateAndRead2() throws LogException {
		int byteLength = 1;

		for (int truncateTo = 0; truncateTo < NUMBER_OF_BYTES_TO_WRITE
				/ byteLength; truncateTo++) {
			logFile = new VirtualLogFile(
					new RAFSegmentHelper(LOG_DIR, BASENAME), 60);
			logFile.delete();
			logFile.open();

			writeLog(byteLength, NUMBER_OF_BYTES_TO_WRITE);

			long wasTruncatedTo = logFile.truncateTo(truncateTo);

			assertEquals("Log was truncated to desired position.",
					truncateTo, wasTruncatedTo);

//			System.out.println(String.format("Truncate to %5d => %5d",
//					truncateTo, wasTruncatedTo));

			readLog(byteLength, NUMBER_OF_BYTES_TO_WRITE, wasTruncatedTo);

			logFile.close();
			logFile.delete();
		}
	}

	private void readLog(int byteLength, int numberOfBytesToWrite,
			long wasTruncatedTo) throws LogException {
		byte[] writeBytes = new byte[byteLength];
		byte[] readBytes = new byte[byteLength];

		for (int i = 0; i < numberOfBytesToWrite / byteLength; i++) {
			long readFrom = (long) i * byteLength;
			long filePointerAfterSeek = -1;
			long filePointerAfterRead = -1;

			try {
				logFile.seek(0);

				if (wasTruncatedTo > 0) {
					fail(String
							.format(
									"Could seek to position %s although log was already truncated to %s.",
									0, wasTruncatedTo));
				}
			} catch (LogException e) {
				// ignore this if log file has been truncated once
				if (wasTruncatedTo <= 0) {
					throw e;
				}
			}

			try {
				logFile.seek(readFrom);

				if (wasTruncatedTo > readFrom) {
					fail(String
							.format(
									"Could seek to position %s although log was already truncated to %s.",
									0, wasTruncatedTo));
				}
			} catch (LogException e) {
				// ignore this if log file has been truncated once to this
				// postion
				if (wasTruncatedTo <= readFrom) {
					throw e;
				}
			}

			if (wasTruncatedTo <= readFrom) {
				filePointerAfterSeek = logFile.getFilePointer();
				assertEquals("File pointer correct positioned for read.",
						readFrom, filePointerAfterSeek);

				for (int pos = 0; pos < byteLength; pos++) {
					writeBytes[0] = (byte) (i % 256);
				}

				logFile.read(readBytes);
				filePointerAfterRead = logFile.getFilePointer();

				try {
					assertTrue("Written bytes are read correctly", Arrays
							.equals(readBytes, writeBytes));
				} catch (Error e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				assertEquals("File pointer correct positioned after read.",
						readFrom + byteLength, filePointerAfterRead);
			}
		}
	}

	private void writeLog(int byteLength, int numberOfBytesToWrite)
			throws LogException {
		byte[] writeBytes = new byte[byteLength];

		for (int i = 0; i < numberOfBytesToWrite / byteLength; i++) {
			long filePointerBefore = logFile.getFilePointer();

			for (int pos = 0; pos < byteLength; pos++) {
				writeBytes[0] = (byte) (i % 256);
			}

			logFile.write(writeBytes);

			long filePointerAfter = logFile.getFilePointer();
			assertEquals("File pointer correct after write.", filePointerBefore
					+ writeBytes.length, filePointerAfter);
			assertEquals("File size correct after write.", (long) (i + 1)
					* byteLength, logFile.getLength());
		}
	}
}
