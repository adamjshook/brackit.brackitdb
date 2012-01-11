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
package org.brackit.server.tx;

import java.util.Arrays;

/**
 * This class collects runtime statistics during a transaction. Each
 * participating code may increment/add statistics counter. For example,
 * #written blocks, #read blocks, #locks, b-tree statistics, timings, etc...
 * 
 * All statistic values and counter can be reseted, incremented, and retrieved
 * (get).
 * 
 * @author Karsten Schmidt
 * @since 2009-11-03
 */
public class TxStats {
	public static final int BTREE_ROOT_SPLITS = 0;

	public static final int BTREE_ROOT_COLLAPSES = 1;

	public static final int BTREE_LEAF_ALLOCATIONS = 2;

	public static final int BTREE_LEAF_DEALLOCATIONS = 3;

	public static final int BTREE_BRANCH_ALLOCATE_COUNT = 4;

	public static final int BTREE_BRANCH_DEALLOCATE_COUNT = 5;

	public static final int BTREE_INSERTS = 6;

	public static final int BTREE_DELETES = 7;

	public static final int BTREE_BRANCH_INSERTS = 8;

	public static final int BTREE_BRANCH_DELETES = 9;

	public static final int IO_FETCH_COUNT = 10;

	public static final int IO_WRITE_COUNT = 11;

	public static final int IO_DELETE_COUNT = 12;

	public static final int IO_ALLOCATE_COUNT = 14;

	public static final int LOCK_REQUEST_TIME = 0;

	public static final int IO_FETCH_TIME = 1;

	private final int[] counter = new int[15];

	private final long[] timer = new long[2];

	public TxStats() {
	}

	public void reset() {
		Arrays.fill(counter, 0);
		Arrays.fill(timer, 0);
	}

	public void reset(int category) {
		counter[category] = 0;
	}

	public void increment(int category) {
		counter[category]++;
	}

	public void decrement(int category) {
		counter[category]--;
	}

	public void addTime(int category, long start, long end) {
		timer[category] += (end - start);
	}

	public long getTime(int category) {
		return timer[category];
	}

	public int get(int category) {
		return counter[category];
	}
}
