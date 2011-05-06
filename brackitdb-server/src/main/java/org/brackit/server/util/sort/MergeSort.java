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
package org.brackit.server.util.sort;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.log4j.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.store.Field;
import org.brackit.xquery.util.Cfg;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Stream;

/**
 * Combination of main memory and external merge sort. The implementation is I/O
 * robust w.r.t. pre-sorted input, few inputs and performs well for large main
 * memory buffer sizes.
 * 
 * @author Sebastian Baechle
 * 
 */
public class MergeSort implements Sort {
	private static final Logger log = Logger.getLogger(MergeSort.class);

	private final long maxSize;

	private final Comparator<SortItem> comparator;

	private final File sortDir = new File(Cfg.asString("java.io.tmpdir"));

	private File[] runs;

	private SortItem[] buffer;

	private byte[] mergeBuffer;

	private int count;

	private int runCount;

	private long size;

	private OutputStream currentRun;

	private SortItem lastInRun;

	// statistics
	long leftMergeItemCount;

	long rightMergeItemCount;

	int mergeCount;

	private long directMergeSize;

	private int initialRuns;

	public MergeSort(final Field keyType, final Field valueType) {
		this(SortDirection.ASC, keyType, valueType, 10 * 1024 * 1024);
	}

	public MergeSort(final Field keyType, final Field valueType, long maxSize) {
		this(SortDirection.ASC, keyType, valueType, maxSize);
	}

	public MergeSort(final SortDirection sortType, final Field keyType,
			final Field valueType, long maxSize) {
		this.maxSize = maxSize;
		this.runs = new File[2];

		if (sortType == SortDirection.ASC) {
			this.comparator = new Comparator<SortItem>() {
				@Override
				public int compare(SortItem o1, SortItem o2) {
					return o1.compareDeepTo(o2, keyType, valueType);
				}
			};
		} else {
			this.comparator = new Comparator<SortItem>() {
				@Override
				public int compare(SortItem o1, SortItem o2) {
					return -o1.compareDeepTo(o2, keyType, valueType);
				}
			};
		}

		buffer = new SortItem[300];
	}

	public void add(SortItem item) throws ServerException {
		long itemSize = getSize(item);
		if (size + itemSize > maxSize) {
			writeRun();
		}

		if (count == buffer.length) {
			buffer = Arrays.copyOf(buffer, ((buffer.length * 3) / 2) + 1);
		}

		buffer[count++] = item;
		size += itemSize;
	}

	private long getSize(SortItem item) throws ServerException {
		int size = 2;
		byte[] key = item.key;
		byte[] value = item.value;
		int keyLength = key.length;

		if (keyLength >= 254) {
			size += 2;

			if (keyLength > 65534) {
				throw new ServerException(
						"Key sizes > 65534 are not supported yet.");
			}
		}
		size += keyLength;

		if (value != null) {
			int valueLength = value.length;
			if (valueLength >= 254) {
				size += 2;

				if (valueLength > 65534) {
					throw new ServerException(
							"Value sizes > 65534 are not supported yet.");
				}
			}
			size += valueLength;
		}

		return size;
	}

	private void writeRun() throws ServerException {
		sortBuffer();

		if ((lastInRun != null)
				&& (comparator.compare(lastInRun, buffer[0]) <= 0)) {
			System.out.println("append to run");
			appendToRun();
			return;
		}

		try {
			if (currentRun != null) {
				currentRun.close();
			}

			File run = File.createTempFile("sort", ".run", sortDir);
			run.deleteOnExit();

			if (log.isDebugEnabled()) {
				log.debug(String.format("Writing new run '%s'", run));
			}

			currentRun = new BufferedOutputStream(new FileOutputStream(run));

			for (int i = 0; i < count; i++) {
				lastInRun = buffer[i];
				writeItem(currentRun, lastInRun);
			}

			if (log.isDebugEnabled()) {
				log.debug(String.format("Wrote run '%s'", run));
			}

			if (runCount == runs.length) {
				runs = Arrays.copyOf(runs, ((runs.length * 3) / 2) + 1);
			}
			runs[runCount++] = run;
			size = 0;
			count = 0;
			initialRuns++;
		} catch (IOException e) {
			errorCleanup();
			throw new ServerException(e);
		}
	}

	private void sortBuffer() {
		if (log.isDebugEnabled()) {
			log.debug(String.format("Start main memory sort of %s items.'",
					count));
		}

		Arrays.sort(buffer, 0, count, comparator);

		if (log.isDebugEnabled()) {
			log.debug(String.format("Finished main memory sort of %s items",
					count));
		}
	}

	private void appendToRun() throws ServerException {
		try {
			for (int i = 0; i < count; i++) {
				lastInRun = buffer[i];
				writeItem(currentRun, lastInRun);
			}
			count = 0;
			size = 0;
		} catch (IOException e) {
			errorCleanup();
			throw new ServerException(e);
		}
	}

	public void errorCleanup() {
		if (currentRun != null) {
			try {
				currentRun.close();
			} catch (IOException e1) {
				log.error(e1);
			}
		}

		for (File run : runs) {
			if ((run != null) && (run.exists())) {
				run.delete();
			}
		}
	}

	private SortItem readItem(InputStream in) throws IOException {
		byte[] key = null;
		byte[] value = null;

		if (in.available() == 0) {
			return null;
		}

		int length = in.read() & 255;

		if (length == 255) {
			length = ((in.read() & 255) << 8) | (in.read() & 255) - 1;
		}

		if (length > 0) {
			key = new byte[length - 1];
			in.read(key);
		}

		length = in.read() & 255;

		if (length == 255) {
			length = ((in.read() & 255) << 8) | (in.read() & 255) - 1;
		}

		if (length > 0) {
			value = new byte[length - 1];
			in.read(value);
		}

		return new SortItem(key, value);
	}

	private void writeItem(OutputStream out, SortItem item) throws IOException {
		byte[] key = item.key;
		byte[] value = item.value;
		int keyLength = key.length + 1;

		if (keyLength < 255) {
			out.write(keyLength);
		} else {
			out.write(255);
			out.write((keyLength >> 8) & 255);
			out.write(keyLength & 255);
		}
		out.write(key);

		if (value != null) {
			int valueLength = value.length + 1;
			if (valueLength < 255) {
				out.write(valueLength);
			} else {
				out.write(255);
				out.write((valueLength >> 8) & 255);
				out.write(valueLength & 255);
			}
			out.write(value);
		} else {
			out.write(0);
		}
	}

	public Stream<? extends SortItem> sort() throws ServerException {
		if (runCount == 0) {
			return mainMemorySortOnly();
		}

		closeLastRun();
		mergeRuns();
		return mergeFinalRunAndBuffer();
	}

	private void closeLastRun() throws ServerException {
		try {
			currentRun.close();
			lastInRun = null;
		} catch (IOException e) {
			errorCleanup();
			throw new ServerException(e);
		}
	}

	private Stream<? extends SortItem> mergeFinalRunAndBuffer() {
		sortBuffer();
		final SortItem[] sBuffer = buffer;
		final int sInBuffer = count;

		return new Stream<SortItem>() {
			private final Comparator<SortItem> cmp = comparator;

			private final SortItem[] sortedBuffer = sBuffer;

			private final int sortedInBuffer = sInBuffer;

			private InputStream sorted;

			private SortItem left;

			private SortItem right;

			private int pos;

			@Override
			public void close() {
				try {
					sorted.close();
					runs[0].delete();
				} catch (IOException e) {
					log.error(e);
				}
			}

			@Override
			public SortItem next() throws DocumentException {
				try {
					SortItem next;
					if (sorted == null) {
						sorted = new BufferedInputStream(new FileInputStream(
								runs[0]));
						left = readItem(sorted);
					}

					if (pos == 0) {
						right = (pos < count) ? sortedBuffer[pos++] : null;
					}

					if (left == null) {
						next = right;
						right = (pos < count) ? sortedBuffer[pos++] : null;
						return next;
					}

					if (right == null) {
						next = left;
						left = readItem(sorted);
						return next;
					}

					if (cmp.compare(left, right) <= 0) {
						next = left;
						left = readItem(sorted);
					} else {
						next = right;
						right = (pos < count) ? sortedBuffer[pos++] : null;
					}

					return next;
				} catch (IOException e) {
					throw new DocumentException(e);
				}
			}
		};
	}

	private void mergeRuns() throws ServerException {
		File[] newRuns = null;
		int mergePhase = 0;
		boolean forward = true;

		while (runCount > 1) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("Starting merge phase %s type %s",
						mergePhase, forward));
			}

			if (mergeBuffer == null) {
				mergeBuffer = new byte[Math.max((int) maxSize / 20, 1024)];
			}

			if (log.isTraceEnabled()) {
				log.trace("Merge table");
				for (int i = 0; i < runCount; i++) {
					log.trace(String.format("%3s: %s", i, runs[i]));
				}
			}

			int merges = runCount / 2;
			boolean singleRun = runCount % 2 == 1;
			int newRunCount = merges + (singleRun ? 1 : 0);
			newRuns = new File[newRunCount];

			if (log.isDebugEnabled()) {
				log.debug(String.format("Merge %s -> %s (single run: %s)",
						runCount, newRunCount, singleRun));
			}

			try {
				if (forward) // all merge pairs sorted forwards, hottest at the
				// end
				{
					int pos = 0;

					if (singleRun) {
						for (int i = newRunCount - 1; i > 0; i--) {
							newRuns[pos++] = merge(runs[2 * i - 1], runs[2 * i]);
						}
						newRuns[newRunCount - 1] = runs[0];
					} else {
						for (int i = newRunCount - 1; i >= 0; i--) {
							newRuns[pos++] = merge(runs[2 * i], runs[2 * i + 1]);
						}
					}
				} else // all merge pairs sorted backwards, hottest at the end
				{
					int pos = 0;

					if (singleRun) {
						for (int i = newRunCount - 1; i > 0; i--) {
							newRuns[pos++] = merge(runs[2 * i], runs[2 * i - 1]);
						}
						newRuns[newRunCount - 1] = runs[0];
					} else {
						for (int i = newRunCount - 1; i >= 0; i--) {
							newRuns[pos++] = merge(runs[2 * i + 1], runs[2 * i]);
						}
					}
				}
			} catch (ServerException e) {
				for (File newRun : newRuns) {
					if ((newRun != null) && (newRun.exists())) {
						newRun.delete();
					}
				}
				throw e;
			}

			forward = !forward;
			runCount = newRunCount;
			runs = newRuns;

			if (log.isDebugEnabled()) {
				log.debug(String.format("Finished merge phase %s", mergePhase));
			}
			mergePhase++;
		}
	}

	private Stream<? extends SortItem> mainMemorySortOnly() {
		sortBuffer();
		final SortItem[] sorted = buffer;
		final int sortedCount = count;
		return new Stream<SortItem>() {
			private int pos;

			@Override
			public void close() {
			}

			@Override
			public SortItem next() throws DocumentException {
				return (pos < sortedCount) ? sorted[pos++] : null;
			}
		};
	}

	private File merge(File run1, File run2) throws ServerException {
		InputStream lIn = null;
		InputStream rIn = null;
		OutputStream out = null;

		try {
			mergeCount++;
			File run = File.createTempFile("sort", ".run", sortDir);
			run.deleteOnExit();

			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Merging run '%s' and '%s' in new run '%s'", run1,
						run2, run));
			}

			lIn = new BufferedInputStream(new FileInputStream(run1));
			rIn = new BufferedInputStream(new FileInputStream(run2));
			SortItem left = null;
			SortItem right = null;

			out = new BufferedOutputStream(new FileOutputStream(run));

			left = readItem(lIn);
			right = readItem(rIn);

			while ((left != null) && (right != null)) {
				if (comparator.compare(left, right) <= 0) {
					writeItem(out, left);
					left = readItem(lIn);
					leftMergeItemCount++;
				} else {
					writeItem(out, right);
					right = readItem(rIn);
					rightMergeItemCount++;
				}
			}

			SortItem pending = (left == null) ? right : left;
			InputStream pendingIn = (left == null) ? rIn : lIn;

			if (pending != null) {
				writeItem(out, pending);
			}

			int read;
			while ((read = pendingIn.read(mergeBuffer)) > 0) {
				out.write(mergeBuffer, 0, read);
				directMergeSize += read;
			}

			run1.delete();
			run2.delete();

			if (log.isDebugEnabled()) {
				log.debug(String.format("Wrote run '%s'", run));
			}

			return run;
		} catch (IOException e) {
			errorCleanup();
			throw new ServerException(e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e1) {
					log.error(e1);
				}
			}
			if (lIn != null) {
				try {
					lIn.close();
				} catch (IOException e1) {
					log.error(e1);
				}
			}
			if (rIn != null) {
				try {
					rIn.close();
				} catch (IOException e1) {
					log.error(e1);
				}
			}
		}
	}

	public String printStats() {
		StringBuilder out = new StringBuilder();
		out.append(String.format("# initial runs: %s # merges: %s",
				initialRuns, mergeCount));
		out.append("\n");
		out
				.append(String
						.format(
								"Total left merge items: %10s Avg. left merge items per merge: %10.3f",
								leftMergeItemCount, (double) leftMergeItemCount
										/ mergeCount));
		out.append("\n");
		out
				.append(String
						.format(
								"Total right merge items: %10s Avg. right merge items per merge: %10.3f",
								rightMergeItemCount,
								(double) rightMergeItemCount / mergeCount));
		out.append("\n");
		out.append(String.format("Directly merged %10.2f MB",
				(double) directMergeSize / (1024 * 1024)));
		out.append("\n");
		return out.toString();
	}
}