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
package org.brackit.server.metadata.vocabulary;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.brackit.server.util.IntValueMap;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ConcurrentVocIDMapping implements VocIDMapping {

	private IntValueMap<String> smap;

	private volatile String[] imap;

	private AtomicInteger sequence = new AtomicInteger();

	private AtomicInteger size = new AtomicInteger();

	public ConcurrentVocIDMapping(int size) {
		smap = new IntValueMap<String>(size);
		imap = new String[size];
	}

	@Override
	public String resolve(int vocID) {
		String[] im = imap; // volatile read
		return ((vocID >= 0) && (vocID < im.length)) ? im[vocID] : null;
	}

	@Override
	public int translate(String string) {
		return smap.get(string);
	}

	@Override
	public int add(String string) {
		int vocID = smap.get(string);

		if ((vocID == -1) || (vocID == Integer.MAX_VALUE)) {
			if (smap.putIfAbsent(string, Integer.MAX_VALUE) == -1) {
				vocID = sequence.getAndIncrement();
				String[] im = imap; // volatile read
				if (vocID >= im.length) {
					// grow imap under lock
					synchronized (this) {
						im = Arrays.copyOf(im, ((im.length * 3) / 2) + 1);
						imap = im;
					}
				}
				im[vocID] = string;
				smap.put(string, vocID);
				size.incrementAndGet();
			} else {
				do {
					vocID = smap.get(string);
				} while (vocID == -1);
			}
			if ((resolve(vocID) == null) || (!resolve(vocID).equals(string))) {
				resolve(vocID);
				throw new RuntimeException();
			}
		}

		return vocID;
	}

	@Override
	public boolean exists(String string) {
		return (smap.get(string) != -1);
	}

	@Override
	public int size() {
		return size.get();
	}
}
