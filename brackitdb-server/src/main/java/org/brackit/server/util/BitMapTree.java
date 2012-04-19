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
package org.brackit.server.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * A BitMap implementation based on a TreeMap. The encapsulated BitMap is
 * partitioned into so-called "sections". However, sections are only physically
 * stored, if they include at least one set bit. This makes this implementation
 * more efficient than the BitArrayWrapper in terms of storage consumption, if
 * only a small portion of bits are set.
 * 
 * @author Martin Hiller
 * 
 */
public class BitMapTree implements BitMap {

	private final static boolean DEBUG = false;
	private final static boolean CHECK_BOUNDS = false;

	private final static int SECTION_SIZE = 32;
	private final static int SECTION_SIZE_IN_BYTES = SECTION_SIZE / Byte.SIZE;

	private final static int LEFT_BIT = (1 << (Byte.SIZE - 1));

	private class Section {
		public final byte[] bits;

		public Section() {
			this.bits = new byte[SECTION_SIZE_IN_BYTES];
		}
	}

	private final TreeMap<Integer, Section> sectionMap;
	private int logicalSize;

	public BitMapTree(int logicalSize) {
		this.logicalSize = logicalSize;
		this.sectionMap = new TreeMap<Integer, Section>();
	}
	
	public BitMapTree() {
		this.logicalSize = 0;
		this.sectionMap = new TreeMap<Integer, Section>();
	}

	@Override
	public void clear(int bitIndex) {
		if (CHECK_BOUNDS) {
			if (bitIndex < 0 || bitIndex >= logicalSize)
				throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);
		}

		// determine corresponding section
		int sectionNo = bitIndex / SECTION_SIZE;

		// look for section
		Section section = sectionMap.get(sectionNo);
		if (section == null) {
			// nothing to clear
			return;
		}

		// clear bit
		int sectionOffset = bitIndex % SECTION_SIZE;
		int byteNo = sectionOffset / Byte.SIZE;
		int byteOffset = sectionOffset % Byte.SIZE;

		section.bits[byteNo] = (byte) (section.bits[byteNo] & ~(LEFT_BIT >>> byteOffset));

		// check whether this section can be removed
		boolean remove = true;
		for (int i = 0; i < section.bits.length; i++) {
			if (section.bits[i] != 0) {
				remove = false;
				break;
			}
		}

		if (remove) {
			sectionMap.remove(sectionNo);
		}
	}

	@Override
	public void extendTo(int newLogicalSize) {
		this.logicalSize = newLogicalSize;
	}

	@Override
	public boolean get(int bitIndex) {
		if (CHECK_BOUNDS) {
			if (bitIndex < 0 || bitIndex >= logicalSize)
				throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);
		}

		// determine corresponding section
		int sectionNo = bitIndex / SECTION_SIZE;

		// look for section
		Section section = sectionMap.get(sectionNo);
		if (section == null) {
			// bit not set
			return false;
		}

		// retrieve bit
		int sectionOffset = bitIndex % SECTION_SIZE;
		int byteNo = sectionOffset / Byte.SIZE;
		int byteOffset = sectionOffset % Byte.SIZE;

		return ((section.bits[byteNo] & (LEFT_BIT >>> byteOffset)) != 0);
	}

	@Override
	public Iterator<Integer> getSetBits() {

		return new Iterator<Integer>() {

			private Iterator<Entry<Integer, Section>> iter = sectionMap
					.entrySet().iterator();

			private Entry<Integer, Section> currentEntry = null;
			private int byteNo = 0;
			private int byteOffset = 0;

			private Integer next = null;

			private void nextInternal() {

				while (true) {

					if (currentEntry == null) {
						// get next section from map
						if (!iter.hasNext()) {
							next = null;
							return;
						}
						currentEntry = iter.next();
					}

					int sectionNo = currentEntry.getKey();
					Section section = currentEntry.getValue();

					boolean bitIsSet = false;

					if ((section.bits[byteNo] & (LEFT_BIT >>> byteOffset)) != 0) {
						// bit is set
						bitIsSet = true;
						next = sectionNo * SECTION_SIZE + byteNo * Byte.SIZE
								+ byteOffset;
					}

					byteOffset++;
					if (byteOffset == Byte.SIZE) {
						byteNo++;
						byteOffset = 0;

						if (byteNo == section.bits.length) {
							currentEntry = null;
							byteNo = 0;
						}
					}

					if (bitIsSet) {
						return;
					}
				}
			}

			@Override
			public boolean hasNext() {

				if (next == null) {
					nextInternal();
				}
				return (next != null);
			}

			@Override
			public Integer next() {

				if (next == null) {
					nextInternal();
				}
				Integer res = next;
				next = null;
				return res;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public int logicalSize() {
		return logicalSize;
	}

	@Override
	public int nextClearBit(int fromIndex) {
		if (CHECK_BOUNDS) {
			if (fromIndex < 0 || fromIndex >= logicalSize)
				throw new IndexOutOfBoundsException("fromIndex: " + fromIndex);
		}

		// determine corresponding section
		int sectionNo = fromIndex / SECTION_SIZE;

		// look for section
		Section section = sectionMap.get(sectionNo);
		if (section == null) {
			// bit not set -> return fromIndex
			return fromIndex;
		}

		// retrieve bit
		int sectionOffset = fromIndex % SECTION_SIZE;
		int byteNo = sectionOffset / Byte.SIZE;
		int byteOffset = sectionOffset % Byte.SIZE;

		int index = fromIndex;

		while (true) {

			if ((section.bits[byteNo] & (LEFT_BIT >>> byteOffset)) == 0) {
				// clear bit found
				return index;
			}

			index++;
			if (index == logicalSize) {
				return -1;
			}

			byteOffset++;
			if (byteOffset == Byte.SIZE) {
				byteNo++;
				byteOffset = 0;

				if (byteNo == section.bits.length) {
					// get next section
					Entry<Integer, Section> nextEntry = sectionMap
							.higherEntry(sectionNo);

					if (nextEntry == null) {
						// no next entry -> only zeros remaining
						return index;
					}

					if (nextEntry.getKey() > sectionNo + 1) {
						// zeros in between
						return index;
					}

					sectionNo++;
					section = nextEntry.getValue();

					byteNo = 0;
				}
			}
		}
	}

	@Override
	public void set(int bitIndex) {
		if (CHECK_BOUNDS) {
			if (bitIndex < 0 || bitIndex >= logicalSize)
				throw new IndexOutOfBoundsException("bitIndex: " + bitIndex);
		}

		// determine corresponding section
		int sectionNo = bitIndex / SECTION_SIZE;

		// look for section
		Section section = sectionMap.get(sectionNo);
		if (section == null) {
			// add new section to map
			section = new Section();
			sectionMap.put(sectionNo, section);
		}

		// set bit
		int sectionOffset = bitIndex % SECTION_SIZE;
		int byteNo = sectionOffset / Byte.SIZE;
		int byteOffset = sectionOffset % Byte.SIZE;

		section.bits[byteNo] = (byte) (section.bits[byteNo] | (LEFT_BIT >>> byteOffset));
	}

	@Override
	public void write(DataOutput out) throws IOException {
		
		Set<Entry<Integer, Section>> entries = sectionMap.entrySet();
		ArrayList<Entry<Integer, Section>> toInsert = new ArrayList<Entry<Integer,Section>>();
		Iterator<Entry<Integer, Section>> iter = entries.iterator();
		
		out.writeInt(logicalSize);
		
		Entry<Integer, Section> entryStart = (iter.hasNext() ? iter.next() : null);
		
		while (entryStart != null) {
			int startSection = entryStart.getKey();
			int previousSection = startSection;
			
			toInsert.clear();
			toInsert.add(entryStart);
			entryStart = null;
			
			// count number of continuous sections (-> Zero means 'End of Unit')
			int contSections = 1;
			while (iter.hasNext()) {
				
				Entry<Integer, Section> entryCurrent = iter.next();
				
				int sectionNo = entryCurrent.getKey();
				if (sectionNo == previousSection + 1 && contSections < 255) {
					// continuous section number
					contSections++;
					toInsert.add(entryCurrent);
					previousSection = sectionNo;
				} else {
					// start at current entry in the next iteration
					entryStart = entryCurrent;
					break;
				}
			}
			
			// write continous sections
			
			// write #sections and start section number
			out.writeByte(contSections);
			out.writeByte(startSection >> 16);
			out.writeByte(startSection >> 8);
			out.writeByte(startSection);
			
			// write section bits
			for (Entry<Integer, Section> entry : toInsert) {
				out.write(entry.getValue().bits);
			}
		}
		
		// mark end of unit
		out.writeByte(0);
	}

	@Override
	public void read(DataInput in) throws IOException {
		
		logicalSize = in.readInt();
		sectionMap.clear();
		
		while (true) {
			
			int contSections = in.readByte() & 0xFF;
			if (contSections == 0) {
				// end of unit
				break;
			}
			
			int sectionNo = (in.readByte() & 0xFF) << 16;
			sectionNo |= (in.readByte() & 0xFF) << 8;
			sectionNo |= (in.readByte() & 0xFF);
			
			for (int i = 0; i < contSections; i++) {
				
				Section section = new Section();
				in.readFully(section.bits);
				
				sectionMap.put(sectionNo, section);
				
				sectionNo++;
			}
		}
	}
}
