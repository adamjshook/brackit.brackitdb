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
package org.brackit.server.util;

import java.io.PrintStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple concurrent hash-based mapping of objects to positive integers. Design
 * is based on java.util.ConcurrentHashMap from OpenJDK.
 * 
 * @author Sebastian Baechle
 * 
 */
public class IntValueMap<E> {

	static final int MAX_SIZE = 1 << 30;

	private static class Entry<K> {
		final Entry<K> next;
		final int hash;
		final K k;
		volatile int v; // -1 indicates deletion

		public Entry(int hash, K k, int v, Entry<K> next) {
			this.hash = hash;
			this.k = k;
			this.v = v;
			this.next = next;
		}
	}

	private static class Segment<E> extends ReentrantLock {

		final float loadFactor;
		volatile Entry<E>[] table;
		volatile int count;
		// non-volatile: only used under lock
		int threshold;

		public Segment(int initialSize, float loadFactor) {
			this.loadFactor = loadFactor;
			this.table = new Entry[initialSize];
			this.threshold = (int) (table.length * loadFactor);
		}

		int get(E k, int hash) {
			Entry<E>[] tab = table; // volatile read
			Entry<E> e = tab[hash & (tab.length - 1)];
			// scan chain for key
			while (e != null) {
				if ((e.hash == hash) && (e.k.equals(k))) {
					int v = e.v; // volatile read
					if (v == -1) {
						// re-check under lock
						// should not happen but
						// see Java's ConcurrentHashMap
						// for details
						lock();
						try {
							v = e.v;
						} finally {
							unlock();
						}
					}
					return v;
				}
				e = e.next;
			}
			return -1;
		}

		int put(E k, int v, int hash, boolean force) {
			lock();
			try {
				int c = count; // volatile read
				if (++c >= threshold) {
					rehash();
				}
				Entry<E>[] tab = table; // volatile read
				int index = hash & (tab.length - 1);
				Entry<E> first = tab[index];
				Entry<E> e = first;
				int len = 0;
				// scan chain for key
				while (e != null) {
					len++;
					if ((e.hash == hash) && (e.k.equals(k))) {
						int old = e.v; // volatile read
						if (!force) {
							return old;
						}
						e.v = v; // update value
						return old;
					}
					e = e.next;
				}
				// if (len > 0) {
				// System.out.println("Chain length is " + len);
				// }
				// key not present
				tab[index] = new Entry<E>(hash, k, v, first);
				count = c;
				return -1;
			} finally {
				unlock();
			}
		}

		@SuppressWarnings("unchecked")
		private void rehash() {
			// System.out.println("REHASH");
			Entry<E>[] oldTab = table; // volatile read
			int oldLen = oldTab.length;
			int newLen = oldLen << 1;
			if (newLen >= MAX_SIZE) {
				return;
			}
			Entry<E>[] newTab = new Entry[newLen];
			for (int i = 0; i < oldLen; i++) {
				Entry<E> e = oldTab[i];
				while (e != null) {
					int idx = e.hash & (newLen - 1);
					newTab[idx] = new Entry(e.hash, e.k, e.v, newTab[idx]);
					e = e.next;
				}
			}
			threshold = (int) (newLen * loadFactor);
			table = newTab;
		}
	}

	private final Segment<E>[] segments;

	private final int mask;

	private final int shift;

	public IntValueMap(int initialCapacity) {
		this(16, initialCapacity / 16, 0.75f);
	}

	public IntValueMap(int noOfSegments, int initialSegmentSize,
			float loadFactor) {
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
		segments = new Segment[noOfSegments];
		for (int i = 0; i < noOfSegments; i++) {
			segments[i] = new Segment<E>(initialSegmentSize, loadFactor);
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
		// return hash & (segments.length - 1);
	}

	public int get(E k) {
		int hash = hash(k.hashCode());
		return segments[index(hash)].get(k, hash);
	}

	public void put(E k, int v) {
		if (v < 0) {
			throw new IllegalArgumentException();
		}
		int hash = hash(k.hashCode());
		segments[index(hash)].put(k, v, hash, true);
	}

	public int putIfAbsent(E k, int v) {
		if (v < 0) {
			throw new IllegalArgumentException();
		}
		int hash = hash(k.hashCode());
		return segments[index(hash)].put(k, v, hash, false);
	}

	public void statistics(PrintStream out) {
		int count = 0;
		int registered = 0;
		for (int i = 0; i < segments.length; i++) {
			out.print(i);
			Segment<E> s = segments[i];
			registered += s.count;
			s.lock();
			try {
				out.print('\t');
				out.print(s.count);
				Entry<E>[] tab = s.table;
				for (int j = 0; j < tab.length; j++) {
					int len = 0;
					Entry<E> e = tab[j];
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
}
