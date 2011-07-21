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
package org.brackit.server.metadata.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 
 * @author Sebastian Baechle
 * 
 * @param <K>
 * @param <V>
 */
public class HookedCache<K, V> {
	protected final Map<K, CachedObject<V>> mapping;

	private static final class CachedObject<V> implements CachedObjectHook {
		V value;
		int referenceCount;
		boolean hold;

		private CachedObject(V value) {
			this.value = value;
		}

		@Override
		public synchronized void release() {
			if (referenceCount == 0) {
				throw new IllegalStateException();
			}

			referenceCount--;
		}

		private synchronized void acquire() {
			referenceCount++;
		}

		private synchronized int getReferenceCount() {
			return referenceCount;
		}
	}

	public HookedCache() {
		mapping = new HashMap<K, CachedObject<V>>();
	}

	public V get(CachedObjectUser tx, K key) {
		synchronized (mapping) {
			CachedObject<V> cachedObj = mapping.get(key);

			if (cachedObj == null) {
				return null;
			}

			cachedObj.acquire();
			tx.addHook(cachedObj);

			return cachedObj.value;
		}
	}

	public V putIfAbsent(CachedObjectUser tx, K key, V value) {
		synchronized (mapping) {
			CachedObject<V> cachedObj = mapping.get(key);

			if (cachedObj == null) {
				// key not mapped in cache
				cachedObj = new CachedObject<V>(value);
				mapping.put(key, cachedObj);
			}

			cachedObj.acquire();
			tx.addHook(cachedObj);

			return cachedObj.value;
		}
	}

	public V putIfAbsent(CachedObjectUser tx, K key, V value, boolean permanent) {
		synchronized (mapping) {
			CachedObject<V> cachedObj = mapping.get(key);

			if (cachedObj == null) {
				// key not mapped in cache
				cachedObj = new CachedObject<V>(value);
				cachedObj.hold = permanent;
				mapping.put(key, cachedObj);
			}

			cachedObj.acquire();
			tx.addHook(cachedObj);

			return cachedObj.value;
		}
	}

	public boolean tryRemove(K key) {
		synchronized (mapping) {
			CachedObject<V> cachedObj = mapping.get(key);

			if ((cachedObj == null) || (cachedObj.getReferenceCount() > 0)
					|| (cachedObj.hold)) {
				return false;
			}

			mapping.remove(key);
			return true;
		}
	}

	public V remove(K key) {
		synchronized (mapping) {
			CachedObject<V> removed = mapping.remove(key);
			return (removed != null) ? removed.value : null;
		}
	}

	public Map<K, V> getAll(CachedObjectUser user) {
		synchronized (mapping) {
			Map<K, V> all = new HashMap<K, V>();

			for (Entry<K, CachedObject<V>> entry : mapping.entrySet()) {
				K key = entry.getKey();
				CachedObject<V> cachedObj = entry.getValue();

				cachedObj.acquire();
				user.addHook(cachedObj);

				all.put(key, cachedObj.value);
			}

			return all;
		}
	}

	public int getSize() {
		synchronized (mapping) {
			return mapping.size();
		}
	}

	public int shrink() {
		int removed = 0;

		synchronized (mapping) {
			for (K key : mapping.keySet()) {
				if (tryRemove(key)) {
					removed++;
				}
			}
		}

		return removed;
	}

	public void clear() {
		synchronized (mapping) {
			mapping.clear();
		}
	}
}
