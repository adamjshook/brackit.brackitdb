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
package org.brackit.server.tx.locking.table;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.brackit.server.tx.XTClock;
import org.brackit.server.tx.locking.LockName;
import org.brackit.server.tx.locking.protocol.LockMode;

/**
 * Table for lock entries.
 * 
 * Design is based on java.util.ConcurrentHashMap from OpenJDK, but uses locks
 * on segments for both read and write operations.
 * 
 * @author Sebastian Baechle
 * 
 */
public class LockTable<T extends LockMode<T>> {
	private static final Logger log = Logger.getLogger(LockTable.class);

	static final int MAX_SIZE = 1 << 30;

	private static class LockEntry<T extends LockMode<T>> extends Header<T> {
		final int hash;
		LockEntry<T> next;

		public LockEntry(LockName name, int hash, LockEntry<T> next) {
			super(name);
			this.hash = hash;
			this.next = next;
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}

	private class LockSegment<T extends LockMode<T>> extends ReentrantLock {
		final float loadFactor;
		LockEntry<T>[] table;
		int count;
		int threshold;

		LockSegment(int initialCapacity, float loadFactor) {
			this.loadFactor = loadFactor;
			table = new LockEntry[initialCapacity];
			threshold = (int) (table.length * loadFactor);
		}

		Header<T> find(LockName key, int hash) {
			lock();
			try {
				LockEntry<T>[] tab = table;
				LockEntry<T> e = tab[hash & (tab.length - 1)];

				while (e != null) {
					if ((e.hash == hash) && (key.equals(e.name))) {
						e.latchX();
						// check if header is still valid
						if (e.getQueue() == null) {
							e.unlatch();
							e = null;
						}
						return e;
					}
					e = e.next;
				}
				return null;
			} finally {
				unlock();
			}
		}

		Header<T> allocate(LockName key, int hash) {
			lock();
			try {
				int c = count;
				if (++c >= threshold) {
					rehash();
				}
				LockEntry<T>[] tab = table;
				int index = hash & (tab.length - 1);
				LockEntry<T> first = tab[index];
				LockEntry<T> e = first;
				while (e != null) {
					if ((e.hash == hash) && (key.equals(e.name))) {
						e.latchX();
						return e;
					}
					e = e.next;
				}
				e = new LockEntry<T>(key, hash, first);
				e.latchX();
				tab[index] = e;
				count = c;
				return e;
			} finally {
				unlock();
			}
		}

		@SuppressWarnings("unchecked")
		private void rehash() {
			// unlock();
			// System.out.println("Rehash");
			// System.out.println("Before");
			// statistics(System.out);
			LockEntry<T>[] oldTab = table;
			int oldLen = oldTab.length;
			int newLen = oldLen << 1;
			if (newLen >= MAX_SIZE) {
				return;
			}
			LockEntry<T>[] newTab = new LockEntry[newLen];
			for (int i = 0; i < oldLen; i++) {
				LockEntry<T> e = oldTab[i];
				while (e != null) {
					int idx = e.hash & (newLen - 1);
					LockEntry<T> tmp = e.next;
					e.next = newTab[idx];
					newTab[idx] = e;
					e = tmp;
				}
			}
			threshold = (int) (newLen * loadFactor);
			table = newTab;
			// System.out.println("After");
			// statistics(System.out);
			// lock();
		}

		boolean drop(LockName key, int hash) {
			lock();
			try {
				LockEntry<T>[] tab = table;
				int index = hash & (tab.length - 1);
				LockEntry<T> first = tab[index];
				LockEntry<T> p = null;
				LockEntry<T> e = first;

				while ((e != null)
						&& (e.hashCode() != hash || !key.equals(e.name))) {
					p = e;
					e = e.next;
				}

				if (e == null) {
					return false;
				}
				e.latchX();
				try {
					// check if entry really
					// should be deleted
					if (e.getQueue() == null) {
						if (p == null) {
							tab[index] = e.next;
						} else {
							p.next = e.next;
						}
						count--;
						return true;
					}
					return false;
				} finally {
					e.unlatch();
				}
			} finally {
				unlock();
			}
		}
	}

	final int mask;

	final int shift;

	final LockSegment<T>[] segments;

	public LockTable(int maxLocks, int maxTransactions) {
		this(16, Math.min(maxLocks / 16, 1024), 0.75f);
	}

	public LockTable(int noOfSegments, int initialSegmentSize, float loadFactor) {
		// only use segment count
		// as power of two
		int sshift = 0;
		int segs = 1;
		while (segs < noOfSegments) {
			segs <<= 1;
			++sshift;
		}
		shift = 32 - sshift;
		mask = segs - 1;
		noOfSegments = segs;
		segments = new LockSegment[noOfSegments];
		for (int i = 0; i < noOfSegments; i++) {
			segments[i] = new LockSegment(initialSegmentSize, loadFactor);
		}
	}

	private int hash(int h) {
		// spread bits with Wang/Jenkins hash
		// borrowed from
		// java.util.concurrent.ConcurrentHashMap
		h += (h << 15) ^ 0xffffcd7d;
		h ^= (h >>> 10);
		h += (h << 3);
		h ^= (h >>> 6);
		h += (h << 2) + (h << 14);
		return h ^ (h >>> 16);
	}

	private int index(int hash) {
		return (hash >>> shift) & mask;
	}

	public Header<T> allocate(LockName key) {
		int hash = hash(key.hashCode());
		return segments[index(hash)].allocate(key, hash);
	}

	public Header<T> find(LockName key) {
		int hash = hash(key.hashCode());
		return segments[index(hash)].find(key, hash);
	}

	public boolean remove(LockName key) {
		int hash = hash(key.hashCode());
		return segments[index(hash)].drop(key, hash);
	}

	public T getMode(LockName lockName) {
		T mode = null;

		Header<T> header = find(lockName);
		if (header != null) // locks for this object found
		{
			mode = header.getGrantedMode();
			header.unlatch();
		}

		return mode;
	}

	public List<XTClock> getLocks(LockName lockName) {
		ArrayList<XTClock> locks = new ArrayList<XTClock>();
		Header<T> h = find(lockName);

		if (h != null) {
			for (Request<T> r = h.getQueue(); r != null; r = r.getNext()) {
				locks.add(new XTClock(lockName, r.requestedBy().getID(), r
						.getMode(), r.getState(), r.getCount()));
			}

			h.unlatch();
		}

		return locks;
	}

	public List<LockName> getLockedResources() {
		ArrayList<LockName> list = new ArrayList<LockName>();

		for (LockSegment<T> s : segments) {
			s.lock();
			try {
				for (LockEntry<T> e : s.table) {
					if (e != null) {
						if (e != null) {
							e.latchS();
							try {
								list.add(e.getName());
							} finally {
								e.unlatch();
							}
						}
					}
				}
			} finally {
				s.unlock();
			}
		}

		return list;
	}

	public boolean isLocked(LockName lockName) {
		Header<T> header = find(lockName);

		if (header != null) {
			header.unlatch();
		}

		return (header != null);
	}

	public String listLocks() {
		StringBuilder buf = new StringBuilder();
		buf.append(String.format("%-20s | %-5s | %s", "Lock", "Mode", "Chain"));
		for (LockSegment<T> s : segments) {
			s.lock();
			try {
				for (LockEntry<T> e : s.table) {
					if (e != null) {
						if (e != null) {
							e.latchS();
							try {
								buf.append("\n");
								buf.append(String.format("%-20s | %-5s | %s", e
										.getName(), e.getGrantedMode(), e
										.printLockChain()));
							} finally {
								e.unlatch();
							}
						}
					}
				}
			} finally {
				s.unlock();
			}
		}

		return buf.toString();
	}

	public void statistics(PrintStream out) {
		int count = 0;
		int registered = 0;
		for (int i = 0; i < segments.length; i++) {
			out.print(i);
			LockSegment<T> s = segments[i];
			registered += s.count;
			s.lock();
			try {
				out.print('\t');
				out.print(s.count);
				LockEntry<T>[] tab = s.table;
				for (int j = 0; j < tab.length; j++) {
					int len = 0;
					LockEntry<T> e = tab[j];
					while (e != null) {
						len++;
						count++;
						e = e.next;
					}
					out.print('\t');
					out.print(len);
				}
				out.print('\n');
			} finally {
				s.unlock();
			}
		}
		out.print("Found: ");
		out.print(count);
		out.print('\t');
		out.print("Registered: ");
		out.print(registered);
		out.print('\n');
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();

		buf.append("| SegNo.  | #Bucket(B) | #Entry(E) "
				+ "| E/B  | E/EB | Max(E/B) | Min(E/B) "
				+ "| #B(#E=0) | #B(1<=#E<3) | #B(3<=#E<6) "
				+ "| #B(6<=#E<20) | #B(#E>20) |\n");

		for (int i = 0; i < segments.length; i++) {
			LockSegment<T> s = segments[i];
			s.lock();
			try {
				final int count = s.count;
				final int buckets = s.table.length;
				final double avgEPB = (double) count / (double) buckets;
				int minEPB = Integer.MAX_VALUE;
				int maxEBP = 0;
				int noEI[] = new int[5];

				for (LockEntry<T> entry : s.table) {
					int noEntries = 0;
					for (LockEntry<T> e = entry; e != null; e = e.next) {
						noEntries++;
					}
					minEPB = Math.min(minEPB, noEntries);
					maxEBP = Math.max(maxEBP, noEntries);
					if (noEntries > 20)
						noEI[4]++;
					else if (noEntries > 6)
						noEI[3]++;
					else if (noEntries > 3)
						noEI[2]++;
					else if (noEntries > 0)
						noEI[1]++;
					else
						noEI[0]++;
				}

				final double avgEPUB = (double) count
						/ (double) (buckets - noEI[0]);
				buf.append(String.format("|%8d | %10d |%10d |%3.3f "
						+ "|%3.3f |%9d |%9d |%9d |%12d "
						+ "|%12d |%13d |%10d |\n", i, buckets, count, avgEPB,
						avgEPUB, maxEBP, minEPB, noEI[0], noEI[1], noEI[2],
						noEI[3], noEI[4]));
			} finally {
				s.unlock();
			}
			i++;
		}

		return buf.toString();
	}
}