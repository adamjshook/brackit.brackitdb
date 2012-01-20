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

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Comparator;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.impl.RAFLogFile;

/**
 * @author Sebastian Baechle
 * 
 */
public class RAFSegmentHelper implements SegmentLogFileHelper {
	private static final String FILENAME_FORMAT = "%s" + File.separator
			+ "%s%012d.log";

	private static final Logger log = Logger.getLogger(RAFSegmentHelper.class);

	private final String directory;

	private final String basename;

	private class FileComparator implements Comparator<File> {
		@Override
		public int compare(File o1, File o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}

	private class LogFileFilter implements FileFilter {
		final String basename;

		LogFileFilter(String basename) {
			this.basename = basename;
		}

		@Override
		public boolean accept(File pathname) {
			return pathname.getName().startsWith(basename);
		}
	}

	public RAFSegmentHelper(String directory, String basename) {
		this.directory = directory;
		this.basename = basename;
	}

	@Override
	public SegmentLogFile append(long segmentNo, long segmentStart)
			throws LogException {
		return createSegment(buildName(segmentNo), segmentNo, segmentStart);
	}

	@Override
	public SegmentLogFile open() throws LogException {
		File dir = new File(directory);

		if (!dir.exists()) {
			if (log.isInfoEnabled()) {
				log.info(String.format("Creating log directory %s.", dir
						.getAbsolutePath()));
			}

			dir.mkdirs();
		}

		File[] files = dir.listFiles(new LogFileFilter(basename));
		SegmentLogFile tail = null;

		if (files.length != 0) {
			Arrays.sort(files, new FileComparator());

			for (File file : files) {
				String filename = file.getAbsolutePath();
				SegmentLogFile segment = openSegment(filename);

				if (tail != null) {
					tail.setNext(segment);
					segment.setPrevious(tail);
				}

				tail = segment;
			}
		} else {
			tail = createSegment(buildName(0), 0, 0);
		}

		return tail;
	}

	@Override
	public void delete() throws LogException {
		File logDir = new File(directory);

		if (!logDir.exists()) {
			return;
		}

		try {
			File[] files = logDir.listFiles(new LogFileFilter(basename));

			for (File file : files) {
				if (log.isDebugEnabled()) {
					log.debug(String.format("Deleting log segment %s.", file));
				}

				file.delete();
			}
		} catch (Exception e) {
			throw new LogException(e, "Error deleting log segments %s.",
					toString());
		}
	}

	private SegmentLogFile openSegment(String filename) throws LogException {
		try {
			SegmentLogFile segment = new SegmentLogFile(new RAFLogFile(
					filename, false));
			segment.open();

			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Opened log segment %s (#%s) with start position %s.",
						filename, segment.getSegmentNo(), segment
								.getSegmentStart()));
			}

			return segment;
		} catch (FileNotFoundException e) {
			throw new LogException(e, "Could not open log segment file %s.",
					filename);
		}
	}

	private SegmentLogFile createSegment(String filename, long segmentNo,
			long segmentStart) throws LogException {
		try {
			SegmentLogFile segment = new SegmentLogFile(new RAFLogFile(
					filename, false));
			segment.open();

			segment.format(segmentNo, segmentStart);

			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Created log segment %s (#%s) with start position %s.",
						filename, segment.getSegmentNo(), segment
								.getSegmentStart()));
			}

			return segment;
		} catch (FileNotFoundException e) {
			throw new LogException(e, "Could not create log segment file %s.",
					filename);
		}
	}

	private String buildName(long segmentNo) {
		return String.format(FILENAME_FORMAT, directory, basename, segmentNo);
	}

	@Override
	public String toString() {
		return directory + File.separator + basename + "*";
	}
}
