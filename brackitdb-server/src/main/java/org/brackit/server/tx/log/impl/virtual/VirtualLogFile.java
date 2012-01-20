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

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.impl.LogFile;
import org.brackit.server.util.Calc;

/**
 * A virtual log file logically mimics a (practically) infinite log file
 * consisting of multiple log files, e.g., on the file system. Log file
 * switching/rotating is therefore transparent to the caller. The virtual log
 * file supports truncation from the beginning, i.e., parts of the physical
 * files can so be removed, but not accessed anymore.
 * 
 * @author Sebastian Baechle
 * 
 */
public final class VirtualLogFile implements LogFile {
	private static final Logger log = Logger.getLogger(VirtualLogFile.class);

	private static final long DEFAULT_SEGMENT_SIZE = 500;

	private final long segmentSize;

	private final SegmentLogFileHelper helper;

	private SegmentLogFile current;

	private SegmentLogFile tail;

	public VirtualLogFile(SegmentLogFileHelper helper, long segmentSize) {
		if (segmentSize <= SegmentLogFile.HEADER_SIZE) {
			log
					.warn(String
							.format(
									"Log segment size (%s bytes) is too small. Switching to default value %s",
									segmentSize, DEFAULT_SEGMENT_SIZE));
			segmentSize = DEFAULT_SEGMENT_SIZE;
		}

		this.helper = helper;
		this.segmentSize = segmentSize;
	}

	@Override
	public void open() throws LogException {
		if (current != null) {
			throw new LogException("Log file %s already openend.", toString());
		}

		tail = helper.open();
		current = tail;
	}

	@Override
	public void close() throws LogException {
		checkOpen();

		try {
			for (SegmentLogFile segment = tail; segment != null; segment = segment
					.getPrevious()) {
				segment.close();
			}
		} finally {
			current = null;
			tail = null;
		}
	}

	@Override
	public void delete() throws LogException {
		if (current != null) {
			throw new LogException("Cannot delete opened virtual log file %s.",
					toString());
		}

		helper.delete();
	}

	@Override
	public long getFilePointer() throws LogException {
		checkOpen();

		return current.getAbsoluteFilePointer();
	}

	@Override
	public long getLength() throws LogException {
		checkOpen();

		return tail.getSegmentStart() + tail.getLength();
	}

	@Override
	public int read(byte[] b) throws LogException {
		checkOpen();

		int read = current.read(b);

		if ((read != b.length) && (current.getNext() != null)) {
			current = current.getNext();
			current.seekHead();
			byte[] partTwo = new byte[b.length - read];
			long readPartTwo = read(partTwo);
			System.arraycopy(partTwo, 0, b, read, partTwo.length);
			read += readPartTwo;
		}

		return read;
	}

	@Override
	public int readInt() throws LogException {
		checkOpen();

		long currentLength = current.getLength();
		long currentFilePointer = current.getFilePointer();

		int availableInCurrent = (int) (currentLength - currentFilePointer);

		if (Integer.SIZE <= availableInCurrent) {
			return current.readInt();
		} else {
			byte[] intBytes = new byte[Integer.SIZE / 8];
			read(intBytes);
			return Calc.toUIntVar(intBytes);
		}
	}

	@Override
	public long readLong() throws LogException {
		checkOpen();

		long currentLength = current.getLength();
		long currentFilePointer = current.getFilePointer();

		int availableInCurrent = (int) (currentLength - currentFilePointer);

		if (Long.SIZE <= availableInCurrent) {
			return current.readLong();
		} else {
			byte[] longBytes = new byte[Long.SIZE / 8];
			read(longBytes);
			return Calc.toLong(longBytes);
		}
	}

	@Override
	public void seek(long pos) throws LogException {
		checkOpen();

		if (pos >= (current.getSegmentStart() + current.getLength())) {
			if (pos > tail.getSegmentStart() + tail.getLength()) {
				System.out.println(tail);
				throw new LogException(
						"Cannot seek to position %s in log file %s.", pos,
						toString());
			}

			// seek from tail because we usually expect to seek primarily
			// recently written stuff
			for (current = tail; (pos < current.getSegmentStart()); current = current
					.getPrevious())
				;
		} else if (pos < current.getSegmentStart()) {
			SegmentLogFile previous;

			for (previous = current.getPrevious(); ((previous != null) && (pos < previous
					.getSegmentStart())); previous = previous.getPrevious())
				;
			if (previous == null) {
				throw new LogException(
						"Position %s is not available anymore in virtual log file %s.",
						pos, toString());
			}

			current = previous;
		}

		current.seek(pos - current.getSegmentStart());
	}

	@Override
	public long seekHead() throws LogException {
		checkOpen();

		for (; (current.getPrevious() != null); current = current.getPrevious())
			;

		return current.seekHead() + current.getSegmentStart();
	}

	@Override
	public void sync() throws LogException {
		checkOpen();

		for (SegmentLogFile segment = tail; segment != null; segment = segment
				.getPrevious()) {
			segment.sync();
		}
	}

	@Override
	public long truncateTo(long pos) throws LogException {
		checkOpen();

		long truncatedTo = tail.getSegmentStart();

		for (SegmentLogFile candidate = tail; candidate != null; candidate = candidate
				.getPrevious()) {
			if ((candidate.getSegmentStart() <= pos)
					&& (pos <= candidate.getSegmentStart()
							+ candidate.getLength())) {
				final long trunkSegmentTo = pos - candidate.getSegmentStart();
				candidate.truncateTo(trunkSegmentTo);
				truncatedTo = candidate.getSegmentStart();

				for (SegmentLogFile oldSegment = candidate.getPrevious(); oldSegment != null; oldSegment = oldSegment
						.getPrevious()) {
					oldSegment.close();
					oldSegment.delete();
					oldSegment.getNext().setPrevious(null);
				}

				break;
			}
		}

		return truncatedTo;
	}

	@Override
	public void write(byte[] b) throws LogException {
		checkOpen();

		while (true) {
			int leftInSegment = (int) (segmentSize - current.getSegment()
					.getLength());

			if (current != tail) {
				throw new LogException(
						"Virtual log files do not support writes in old segments.");
			}

			if (b.length <= leftInSegment) {
				current.write(b);
				break;
			} else {

				byte[] partOne = new byte[leftInSegment];
				byte[] partTwo = new byte[b.length - leftInSegment];
				System.arraycopy(b, 0, partOne, 0, partOne.length);
				System.arraycopy(b, partOne.length, partTwo, 0, partTwo.length);
				current.write(partOne);

				current = helper.append(current.getSegmentNo() + 1, current
						.getSegmentStart()
						+ current.getFilePointer());
				current.setPrevious(tail);
				tail.setNext(current);
				tail = current;

				b = partTwo;
			}
		}
	}

	@Override
	public void writeInt(int i) throws LogException {
		checkOpen();

		int leftInSegment = (int) (segmentSize - current.getSegment()
				.getLength());

		if (current != tail) {
			throw new LogException(
					"Virtual log files do not support writes in sbetween.");
		}

		if (Integer.SIZE / 8 <= leftInSegment) {
			current.writeInt(i);
		} else {
			write(Calc.fromUIntVar(i));
		}
	}

	@Override
	public void writeLong(long l) throws LogException {
		checkOpen();

		int leftInSegment = (int) (segmentSize - current.getSegment()
				.getLength());

		if (current != tail) {
			throw new LogException(
					"Virtual log files do not support writes in sbetween.");
		}

		if (Long.SIZE / 8 <= leftInSegment) {
			current.writeLong(l);
		} else {
			write(Calc.fromLong(l));
		}
	}

	private final void checkOpen() throws LogException {
		if (current == null) {
			throw new LogException("Log file %s is not open.", toString());
		}
	}

	@Override
	public String toString() {
		return helper.toString();
	}
}