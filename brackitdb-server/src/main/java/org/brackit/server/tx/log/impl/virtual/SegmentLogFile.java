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
package org.brackit.server.tx.log.impl.virtual;

import org.apache.log4j.Logger;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.impl.LogFile;

/**
 * @author Sebastian Baechle
 * 
 */
final class SegmentLogFile implements LogFile {
	private static final Logger log = Logger.getLogger(SegmentLogFile.class);

	/**
	 * Meta data in segment header - segment No - segment Start A - startOffset
	 * A - segment Start B - startOffset B
	 */
	static final int HEADER_SIZE = 5 * Long.SIZE / 8;

	private final LogFile segment;

	private long segmentNo;

	private long segmentStart;

	private long startOffset;

	private SegmentLogFile previous;

	private SegmentLogFile next;

	public SegmentLogFile(LogFile segment) {
		this.segment = segment;
	}

	public void close() throws LogException {
		segment.close();
	}

	public void delete() throws LogException {
		segment.delete();
	}

	public long getFilePointer() throws LogException {
		return segment.getFilePointer() - startOffset;
	}

	public long getAbsoluteFilePointer() throws LogException {
		return segmentStart + segment.getFilePointer() - startOffset;
	}

	public long getLength() throws LogException {
		return segment.getLength() - startOffset;
	}

	public void open() throws LogException {
		segment.open();
		segment.seekHead();

		if (segment.getLength() >= HEADER_SIZE) {
			segmentNo = segment.readLong();
			segmentStart = segment.readLong();
			startOffset = segment.readLong();

			long segmentStartB = segment.readLong();
			long startOffsetB = segment.readLong();

			if (segmentStart != segmentStartB) {
				log
						.warn(String
								.format(
										"Found mismatch of meta data fields and their backup fields in header of %s possibly caused by a systeme crash. I'll repair it.",
										segment));

				// new values might be corrupt -> use backup fields
				segmentStart = segmentStartB;
				startOffset = startOffsetB;

				segment.seek(1 * Long.SIZE / 8);
				segment.writeLong(segmentStart);
				segment.writeLong(startOffset);
				segment.sync();
			}

			seekHead();
		}
	}

	public int read(byte[] b) throws LogException {
		return segment.read(b);
	}

	public int readInt() throws LogException {
		return segment.readInt();
	}

	public void writeInt(int i) throws LogException {
		segment.writeInt(i);
	}

	public long readLong() throws LogException {
		return segment.readLong();
	}

	public void writeLong(long l) throws LogException {
		segment.writeLong(l);
	}

	public void seek(long pos) throws LogException {
		if (pos < 0) {
			throw new LogException("Invalid seek position: %s.", pos);
		}

		segment.seek(startOffset + pos);
	}

	public long seekHead() throws LogException {
		segment.seek(startOffset);
		return 0;
	}

	public void sync() throws LogException {
		segment.sync();
	}

	public long truncateTo(long pos) throws LogException {
		long length = getLength();

		if ((pos < 0) || (pos > length)) {
			throw new LogException(
					"Cannot truncate segment %s covering range logical range %s-%s in physical range %s-%s physical to %s.",
					segment, segmentStart, segmentStart + length, startOffset,
					startOffset + length, pos);
		}

		long newSegmentStart = segmentStart + pos; // new segment start
		long newStartOffset = startOffset + pos;

		// update master fields
		segment.seek(1 * Long.SIZE / 8);
		segment.writeLong(newSegmentStart);
		segment.writeLong(newStartOffset);
		segment.sync();

		// verify that master fields were correctly written
		segment.seek(1 * Long.SIZE / 8);
		long writtenSegmentStart = segment.readLong();
		long writtenStartOffset = segment.readLong();
		boolean correctlyWritten = ((writtenSegmentStart == newSegmentStart) && (writtenStartOffset == newStartOffset));

		if (!correctlyWritten) {
			throw new LogException("Master fields not correctly updated.");
		}

		// update header fields
		segment.writeLong(newSegmentStart);
		segment.writeLong(newStartOffset);
		segment.sync();

		segmentStart = newSegmentStart;
		startOffset = newStartOffset;

		seekHead();

		return pos;
	}

	public void write(byte[] b) throws LogException {
		segment.write(b);
	}

	public long getSegmentStart() {
		return segmentStart;
	}

	public void format(long segmentNo, long segmentStart) throws LogException {
		segment.seekHead();
		segment.writeLong(segmentNo);
		segment.writeLong(segmentStart);
		segment.writeLong(HEADER_SIZE);
		segment.writeLong(segmentStart);
		segment.writeLong(HEADER_SIZE);
		this.segmentNo = segmentNo;
		this.segmentStart = segmentStart;
		this.startOffset = HEADER_SIZE;
		segment.sync();
	}

	public SegmentLogFile getPrevious() {
		return previous;
	}

	public void setPrevious(SegmentLogFile previous) {
		this.previous = previous;
	}

	public SegmentLogFile getNext() {
		return next;
	}

	public void setNext(SegmentLogFile next) {
		this.next = next;
	}

	public long getSegmentNo() {
		return segmentNo;
	}

	public LogFile getSegment() {
		return segment;
	}

	@Override
	public String toString() {
		return String.format("LogSegment[%s]", segment);
	}
}