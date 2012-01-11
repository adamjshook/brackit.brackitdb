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
package org.brackit.server.tx.log.impl.virtual;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.impl.LogFileMockup;
import org.brackit.server.util.ByteSequence;

/**
 * @author Sebastian Baechle
 * 
 */
public class MockupSegmentHelper implements SegmentLogFileHelper {
	private static final String FILENAME_FORMAT = "%s" + File.separator
			+ "%s%012d.log";

	private static final Logger log = Logger
			.getLogger(MockupSegmentHelper.class);

	private final String directory;

	private final String basename;

	private HashMap<String, ByteSequence> files;

	public MockupSegmentHelper(String directory, String basename) {
		this.directory = directory;
		this.basename = basename;
		files = new LinkedHashMap<String, ByteSequence>();
	}

	@Override
	public SegmentLogFile append(long segmentNo, long segmentStart)
			throws LogException {
		return createSegment(buildName(segmentNo), segmentNo, segmentStart);
	}

	@Override
	public SegmentLogFile open() throws LogException {
		SegmentLogFile tail = null;

		if (files.size() != 0) {
			for (String file : files.keySet()) {
				if (files.get(file).getLength() > 0) {
					SegmentLogFile segment = openSegment(file);

					if (tail != null) {
						tail.setNext(segment);
						segment.setPrevious(tail);
					}

					tail = segment;
				} else {
					System.out.println("Dropping file " + file);
					files.remove(file);
				}
			}
		} else {
			tail = createSegment(buildName(0), 0, 0);
		}

		return tail;
	}

	@Override
	public void delete() throws LogException {
		files.clear();
	}

	private SegmentLogFile openSegment(String filename) throws LogException {
		ByteSequence byteSequence = files.get(filename);

		if (byteSequence == null) {
			throw new LogException("Dummy file %s does not exist.", filename);
		}

		LogFileMockup logFile = new LogFileMockup(filename, byteSequence);
		SegmentLogFile segment = new SegmentLogFile(logFile);
		segment.open();

		if (log.isDebugEnabled()) {
			log
					.debug(String
							.format(
									"Opened log segment %s (#%s) with start position %s.",
									filename, segment.getSegmentNo(), segment
											.getSegmentStart()));
		}

		return segment;
	}

	private SegmentLogFile createSegment(String filename, long segmentNo,
			long segmentStart) throws LogException {
		LogFileMockup logFile = new LogFileMockup(filename);
		SegmentLogFile segment = new SegmentLogFile(logFile);
		segment.open();
		files.put(filename, logFile.getByteSequence());

		segment.format(segmentNo, segmentStart);

		if (log.isDebugEnabled()) {
			log
					.debug(String
							.format(
									"Created log segment %s (#%s) with start position %s.",
									filename, segment.getSegmentNo(), segment
											.getSegmentStart()));
		}

		return segment;
	}

	private String buildName(long segmentNo) {
		return String.format(FILENAME_FORMAT, directory, basename, segmentNo);
	}

	@Override
	public String toString() {
		return directory + File.separator + basename + "*";
	}
}
