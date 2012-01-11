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
package org.brackit.server.store;

import java.util.Random;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.store.index.Index;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.thread.ThreadCB;

/**
 * SearchModes for searching values in searchable data structures, like, e.g.,
 * {@link Index} or {@link Heap}.
 * 
 * Each search mode is be evaluated relative to a given search key of a specific
 * {@link Field} that must allow to define an order between its values.
 * 
 * Relative to the search keys, the search space is divided in two halves, where
 * {{@link #isInside(Field, byte[], byte[])} evaluates to <code>true</code> in
 * one half and to <code>false</code> in the other. The result of a search must
 * return either the greatest existing value for that {
 * {@link #isInside(Field, byte[], byte[])} evaluates to <code>true</code> if
 * the right half of the search space evaluates to <code>true</code> as
 * indicated by {@link #findGreatestInside()}. If {@link #findGreatestInside()}
 * returns <code>false</code>, i.e. right half of the search space evaluates to
 * <code>false</code>, the search must return the smallest existing value for
 * that {{@link #isInside(Field, byte[], byte[])} evaluates to <code>true</code>
 * .
 * 
 * @author Sebastian Baechle
 */
public enum SearchMode {
	/**
	 * Search for the first entry
	 */
	FIRST(false, true, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return true;
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return false;
		}
	},

	/**
	 * Search for the entry with the greatest key
	 */
	LAST(false, false, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return false;
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return false;
		}
	},

	/**
	 * Search for the entry with greatest key that is less than the given key
	 */
	LESS(false, true, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return (keyType.compare(currentKey, searchKey) >= 0);
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compare(value1, value2) < 0);
		}
	},

	/**
	 * Search for the entry with greatest key that is less or equal than the
	 * given key
	 */
	LESS_OR_EQUAL(false, true, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return (keyType.compare(currentKey, searchKey) >= 0);
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compare(value1, value2) <= 0);
		}
	},

	/**
	 * Search for the entry with smallest key that is greater than the given key
	 */
	GREATER(true, false, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return (keyType.compare(currentKey, searchKey) >= 0);
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compare(value1, value2) > 0);
		}
	},

	/**
	 * Search for the entry with smallest key that is equal or greater than the
	 * given key
	 */
	GREATER_OR_EQUAL(true, false, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return (keyType.compare(currentKey, searchKey) >= 0);
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compare(value1, value2) >= 0);
		}
	},

	/**
	 * Search for the entry with the smallest key that has the given key as
	 * prefix (e.g. for
	 * {@link org.brackit.server.io.file.XTCrecordMgr#getFirstChild(Tx, org.brackit.server.io.file.XTCrecord)}
	 */
	LEAST_HAVING_PREFIX(true, false, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return (keyType.compare(currentKey, searchKey) >= 0);
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compareAsPrefix(value2, value1) <= 0);
		}
	},

	/**
	 * Search for the entry with the greatest key that has the given key as
	 * prefix (e.g. for
	 * {@link org.brackit.server.io.file.XTCrecordMgr#getLastChild(Tx, org.brackit.server.io.file.XTCrecord)}
	 */
	GREATEST_HAVING_PREFIX(false, true, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return keyType.compareAsPrefix(searchKey, currentKey) < 0;
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compareAsPrefix(value2, value1) >= 0);
		}
	},

	/**
	 * Search for the entry before the one with smallest key that has the given
	 * key as prefix (e.g. for
	 * {@link org.brackit.server.io.file.XTCrecordMgr#getPrevSibling(Tx, org.brackit.server.io.file.XTCrecord)}
	 */
	LEAST_HAVING_PREFIX_LEFT(true, true, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return (keyType.compare(currentKey, searchKey) >= 0);
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compareAsPrefix(value2, value1) > 0);
		}
	},

	/**
	 * Search for the entry after the one with greatest key that the given key
	 * as prefix (e.g. for
	 * {@link org.brackit.server.io.file.XTCrecordMgr#getNextSibling(Tx, org.brackit.server.io.file.XTCrecord)}
	 */
	GREATEST_HAVING_PREFIX_RIGHT(true, false, false) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return keyType.compareAsPrefix(searchKey, currentKey) < 0;
		}

		@Override
		public boolean isInside(Field type, byte[] value1, byte[] value2) {
			return (type.compareAsPrefix(value2, value1) < 0);
		}
	},

	/**
	 * Search for a random entry.
	 */
	RANDOM_THREAD(false, false, true) {
		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return false;
		}

		@Override
		public synchronized int nextInt(int n) {
			return ThreadCB.get().random.nextInt(n);
		}
	},

	/**
	 * Search for a random entry.
	 */
	RANDOM_SYSTEM(false, false, true) {
		private volatile Random rand = new Random();

		@Override
		public boolean nextChildSearch(Field keyType, byte[] searchKey,
				byte[] currentKey) {
			return false;
		}

		@Override
		public void setSeed(long seed) {
			if (log.isDebugEnabled()) {
				log.debug("Setting seed " + seed);
			}

			rand.setSeed(seed);
		}

		public void setRandom(Random rand) {
			this.rand = rand;
		}

		@Override
		public int nextInt(int n) {
			int res = rand.nextInt(n);

			if (log.isDebugEnabled()) {
				log.debug("nextInt returning " + res);
			}

			return res;
		}
	};

	private static final Logger log = Logger.getLogger(SearchMode.class);

	private final boolean moveAfterLast;

	private final boolean findGreatestInside;

	private final boolean isRandom;

	private SearchMode(boolean moveAfterLast, boolean findGreatestInside,
			boolean isRandom) {
		this.moveAfterLast = moveAfterLast;
		this.findGreatestInside = findGreatestInside;
		this.isRandom = isRandom;
	}

	public final boolean moveAfterLast() {
		return moveAfterLast;
	}

	public final boolean isRandom() {
		return isRandom;
	}

	/**
	 * Returns <code>true</code> if this search mode looks for the greatest
	 * value <code>value2</code> for that
	 * {@link #isInside(Field, byte[], byte[])} == <code>true</code>, and
	 * <code>false</code> if this search mode looks for the smallest value
	 * <code>value2</code> for that {@link #isInside(Field, byte[], byte[])} ==
	 * <code>true</code>.
	 * 
	 * @return
	 */
	public final boolean findGreatestInside() {
		return findGreatestInside;
	}

	public abstract boolean nextChildSearch(Field keyType, byte[] searchKey,
			byte[] currentKey);

	public int nextInt(int n) {
		log.warn("Default nextInt returning 0.");
		return 0;
	}

	public void setSeed(long s) {
		log.warn("Setting default seed has no effect.");
	}

	/**
	 * Checks if <code>value1</code> is inside the range of search mode
	 * regarding <code>value2</code>.
	 * 
	 * @param type
	 *            type of the values
	 * @param value1
	 *            the value to be checked
	 * @param value2
	 *            the search value
	 * @return true iff <code>value1</code> is outside the range of search mode
	 *         regarding <code>value2</code>.
	 */
	public boolean isInside(Field type, byte[] value1, byte[] value2) {
		throw new RuntimeException();
	}
}