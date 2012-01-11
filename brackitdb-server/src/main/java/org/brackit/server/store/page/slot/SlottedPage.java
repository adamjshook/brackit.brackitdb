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
package org.brackit.server.store.page.slot;

import java.io.PrintStream;
import java.util.Arrays;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.Field;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.RecordFlag;

/**
 * <p>
 * A slot holds a tuple. Each slot consists of a fixed length slot header
 * containing metadata about the stored data and a varying-length record
 * representing the tuple values themselves.
 * </p>
 * 
 * <p>
 * <b>Slot header layout:</b> <br/>
 * <table border="1">
 * <tr>
 * <th>Field</th>
 * <th>Size</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>byte 0</td>
 * <td>byte</td>
 * <td>general flags</td>
 * </tr>
 * <tr>
 * <td>byte 1</td>
 * <td>byte</td>
 * <td>number of fields (max 255)</td>
 * </tr>
 * <tr>
 * <td>byte 2-3</td>
 * <td>short</td>
 * <td>record offset (pageSize <= 64K (65536 bytes)</td>
 * </tr>
 * <tr>
 * <td>byte 2-5</td>
 * <td>int</td>
 * <td>record offset (pageSize > 64K (65536 bytes)</td>
 * </tr>
 * </table>
 * </p>
 * 
 * <p>
 * <b>General flags table:</b> <br/>
 * <table border="1">
 * <tr>
 * <th>Flag</th>
 * <th>Mask</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>deleted</td>
 * <td align="right">128 = 1000 0000</td>
 * <td>true iff this record is deleted and space can be reclaimed if necessary</td>
 * </tr>
 * <tr>
 * <td>first present</td>
 * <td align="right">64 = 0100 0000</td>
 * <td>true iff overflow set, but first field of tuple, i.e. the key is present
 * in this page</td>
 * </tr>
 * <tr>
 * <td>prefix-compression</td>
 * <td align="right">32 = 0010 0000</td>
 * <td>true iff first field of tuple is prefix-compressed</td>
 * </tr>
 * <tr>
 * <td>overflow</td>
 * <td align="right">16 = 0001 0000</td>
 * <td>large record was (partially) stored in blob</td>
 * </tr>
 * </table>
 * </p>
 * 
 * <p>
 * The record itself is a sequence of fields, where each field consists of a
 * value length descriptor and a byte[] value. To distinguish an empty value (
 * <code>byte[0]</code>) from <code>null</code> the value of the length
 * descriptor is defined as <code>value.length + 1</code>. Hence, the value
 * <code>null</code> has the length <code>0</code> and the empty value
 * <code>byte[0]</code> has the length <code>1</code> and so on.
 * </p>
 * 
 * <p>
 * <b>Length descriptor layout:</b> <br/>
 * <table border="1">
 * <tr>
 * <th>Field</th>
 * <th>Size</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>byte 0</td>
 * <td>byte</td>
 * <td>length of field (0 < length < 255)</td>
 * </tr>
 * <tr>
 * <td>byte 1-2</td>
 * <td>short</td>
 * <td>optional length of field (255 <= length < 65536 || length == 0)</td>
 * </tr>
 * </table>
 * </p
 * 
 * <p>
 * Slots support to store the first field with prefix-compression, which
 * requires the value of the first field of the predecessor slot for
 * encoding/decoding. When prefix-compression is used, the length descriptor of
 * the first field is formatted differently.
 * <p>
 * 
 * <p>
 * <b>Length descriptor layout for prefix-compressed field:</b> <br/>
 * <table border="1">
 * <tr>
 * <th>Field</th>
 * <th>Mask</th>
 * <th>Size</th>
 * <th>Description</th>
 * <th>cutoff length</th>
 * <th>diffkey length</th>
 * </tr>
 * <tr>
 * <td>byte 0</td>
 * <td align="right">240 = 1111 0000</td>
 * <td>byte</td>
 * <td>cutoff length</td>
 * <td>0 <= l < 15</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>byte 0</td>
 * <td align="right">15 = 0000 1111</td>
 * <td>byte</td>
 * <td>diffkey length</td>
 * <td>0 <= l < 15</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>byte 1</td>
 * <td align="right"></td>
 * <td>byte</td>
 * <td>optional cutoff length</td>
 * <td>15 <= l < 255</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>byte 1</td>
 * <td align="right"></td>
 * <td>byte</td>
 * <td>optional diffkey length</td>
 * <td>0 <= l < 15</td>
 * <td>15 <= l</td>
 * </tr>
 * <tr>
 * <td>byte 2</td>
 * <td align="right"></td>
 * <td>byte</td>
 * <td>optional diffkey length</td>
 * <td>15 <= l < 255</td>
 * <td>15 <= l < 255</td>
 * </tr>
 * <tr>
 * <td>byte 2-3</td>
 * <td align="right"></td>
 * <td>short</td>
 * <td>optional cutoff length</td>
 * <td>255 <= l < 65535</td>
 * <td>0 <= l < 15</td>
 * </tr>
 * <tr>
 * <td>byte 2-3</td>
 * <td align="right"></td>
 * <td>short</td>
 * <td>optional diffkey length</td>
 * <td>0 <= l < 15</td>
 * <td>255 <= l < 65535</td>
 * </tr>
 * <tr>
 * <td>byte 3-4</td>
 * <td align="right"></td>
 * <td>short</td>
 * <td>optional cutoff length</td>
 * <td>255 <= l < 65535</td>
 * <td>15 <= l</td>
 * </tr>
 * <tr>
 * <td>byte 3-4</td>
 * <td align="right"></td>
 * <td>short</td>
 * <td>optional diffkey length</td>
 * <td>15 <= l < 255</td>
 * <td>255 <= l < 65535</td>
 * </tr>
 * <tr>
 * <td>byte 5-6</td>
 * <td align="right"></td>
 * <td>short</td>
 * <td>optional diffkey length</td>
 * <td>255 <= l < 65535</td>
 * <td>255 <= l < 65535</td>
 * </tr>
 * </table>
 * <p>
 * 
 * <p>
 * To distinguish an empty value (<code>byte[0]</code>) from <code>null</code>
 * the value of the diffkey length descriptor is defined as
 * <code>diffKey.length + 1</code>. Hence, the value <code>null</code> has the
 * length <code>0</code> and the empty value <code>byte[0]</code> has the length
 * <code>1</code> and so on.
 * </p>
 * 
 * @author Sebastian Baechle
 * 
 */
public class SlottedPage extends BasePage {
	private static final Logger log = Logger.getLogger(SlottedPage.class);

	protected static final int SLOT_HEADER_LENGTH_LOE_64K = 4;

	protected static final int SLOT_HEADER_LENGTH_GT_64K = 6;

	private static final byte PREFIX_COMPRESSION_FLAG = RecordFlag.PREFIX_COMPRESSION.mask;

	protected static final Projection DEFAULT_PROJECTION = new Projection() {
		public boolean selectField(int slotNo) {
			return true;
		}
	};

	protected final int headerSize;

	public SlottedPage(Buffer buffer, Handle handle) {
		this(buffer, handle, 0);
	}

	public SlottedPage(Buffer buffer, Handle handle, int reserved) {
		super(buffer, handle, reserved);
		this.headerSize = (handle.getPageSize() <= 65535) ? SLOT_HEADER_LENGTH_LOE_64K
				: SLOT_HEADER_LENGTH_GT_64K;
	}

	/**
	 * Sets the BasePageID, initializes free space info management, and resets
	 * entry counter.
	 * 
	 * @param basePageID
	 */
	public void format(PageID basePageID) {
		clear();
		setBasePageID(basePageID);
		setFreeSpaceOffset(handle.getPageSize());
		handle.setModified(true);
	}

	public Tuple read(int slotNo) {
		return read(slotNo, DEFAULT_PROJECTION, null);
	}

	public Tuple read(int slotNo, Projection projection, Tuple tuple) {
		byte[] page = handle.page;
		int pos = 0;

		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo >= getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		// Read information from slot header
		int hOffset = calcHeaderOffset(slotNo);

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Reading header of slot %s starting at offset %s.", slotNo,
					hOffset));
		}

		boolean prefixCompression = getFlag(page, hOffset,
				PREFIX_COMPRESSION_FLAG);
		byte fieldCount = getFieldCount(page, hOffset);
		int fOffset = getFieldOffset(page, hOffset);

		if (tuple == null) {
			tuple = new ArrayTuple(fieldCount);
		}

		// Sequentially decode length fields to advance
		// offset. Read the values themselves only when
		// requested by the projection specification.
		for (int fieldNo = 0; fieldNo < fieldCount; fieldNo++) {
			if (projection.selectField(pos)) {
				if (DEVEL_MODE && log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Projecting field %s of slot %s starting at offset %s to pos %s.",
											fieldNo, slotNo, fOffset, pos));
				}

				if ((fieldNo == 0) && (prefixCompression)) {
					fOffset = readCompressedValueIntoTuple(page, slotNo,
							fieldNo, fOffset, tuple, pos);
				} else {
					fOffset = readUncompressedValueIntoTuple(page, slotNo,
							fieldNo, fOffset, tuple, pos);
				}

				pos++;
			} else {
				if (DEVEL_MODE && log.isTraceEnabled()) {
					log
							.trace(String
									.format(
											"Skipping field %s of slot %s starting at offset %s.",
											fieldNo, slotNo, fOffset));
				}

				fOffset = advanceOverFields(page, fieldNo, 1, fOffset,
						prefixCompression);
			}
		}

		return tuple;
	}

	private int readCompressedValueIntoTuple(byte[] page, int slotNo,
			int fieldNo, int fOffset, Tuple tuple, int pos) {
		byte[] decompressedValue = null;
		byte[] previousValue = (slotNo > 0) ? readField(slotNo - 1, fieldNo)
				: null;
		int previousValueLength = (previousValue != null) ? previousValue.length
				: 0;

		int cutOffLength = (page[fOffset] >> 4) & 15;
		int diffValueLength = page[fOffset] & 15;
		int fOffset2 = fOffset + 1;

		if (cutOffLength >= 15) {
			cutOffLength = page[fOffset2++] & 255;

			if (cutOffLength >= 255) {
				cutOffLength = ((page[fOffset2++] & 255) << 8)
						| (page[fOffset2++] & 255);
			}
		}

		if (diffValueLength >= 15) {
			diffValueLength = page[fOffset2++] & 255;

			if (diffValueLength >= 255) {
				diffValueLength = ((page[fOffset2++] & 255) << 8)
						| (page[fOffset2++] & 255);
			}
		}

		diffValueLength--; // correct diff value length

		if (diffValueLength >= 0) {
			int decompressedValueLength = diffValueLength + previousValueLength
					- cutOffLength;

			decompressedValue = new byte[decompressedValueLength];

			if (decompressedValueLength > 0) {
				if (previousValue != null) {
					try {
						System.arraycopy(previousValue, 0, decompressedValue,
								0, previousValueLength - cutOffLength);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						System.out.println(previousValue.length);
						System.out.println(decompressedValue.length);
						System.out.println(previousValueLength);
						System.out.println(cutOffLength);
						e.printStackTrace();
					}
				}

				System.arraycopy(page, fOffset2, decompressedValue,
						previousValueLength - cutOffLength, diffValueLength);

				fOffset2 += diffValueLength;
			}
		}

		tuple.set(pos, decompressedValue);

		return fOffset2;
	}

	private int readUncompressedValueIntoTuple(byte[] page, int slotNo,
			int fieldNo, int fOffset, Tuple tuple, int pos) {
		byte[] value = null;
		int valueLength = page[fOffset++] & 255;

		if (valueLength == 255) {
			valueLength = (((page[fOffset++] & 255) << 8) | (page[fOffset++] & 255));
		}

		valueLength--; // correct value length

		if (valueLength >= 0) {
			value = new byte[valueLength];

			if (valueLength > 0) {
				System.arraycopy(page, fOffset, value, 0, valueLength);
				fOffset += valueLength;
			}
		}

		tuple.set(pos, value);
		return fOffset;
	}

	public boolean checkFlag(int slotNo, RecordFlag flag) {
		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo >= getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		return getFlag(handle.page, calcHeaderOffset(slotNo), flag.mask);
	}

	public void setFlag(int slotNo, RecordFlag flag, boolean value) {
		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo >= getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		setFlag(handle.page, calcHeaderOffset(slotNo), flag.mask, value);
	}

	private boolean getFlag(byte[] page, int hOffset, byte flagMask) {
		return ((page[hOffset] & flagMask) != 0);
	}

	private void setFlag(byte[] page, int hOffset, byte flagMask, boolean flag) {
		if (flag) {
			page[hOffset] = (byte) (flagMask | page[hOffset]);
		} else {
			page[hOffset] = (byte) (~flagMask & page[hOffset]);
		}
	}

	private byte getFieldCount(byte[] page, int hOffset) {
		return page[hOffset + 1];
	}

	private void setFieldCount(byte[] page, int hOffset, byte fieldCount) {
		page[hOffset + 1] = fieldCount;
	}

	private int getFieldOffset(byte[] page, int hOffset) {
		hOffset += 2;
		int fOffset = -1;

		if (page.length <= 65535) {
			fOffset = ((page[hOffset++] & 255) << 8) | (page[hOffset++] & 255);
		} else {
			fOffset = ((page[hOffset++] & 255) << 24)
					| ((page[hOffset++] & 255) << 16)
					| ((page[hOffset++] & 255) << 8) | (page[hOffset++] & 255);
		}

		return fOffset;
	}

	private void setFieldOffset(byte[] page, int hOffset, int fOffset) {
		hOffset += 2;

		if (page.length <= 65535) {
			page[hOffset++] = (byte) ((fOffset >> 8) & 255);
			page[hOffset++] = (byte) ((fOffset) & 255);
		} else {
			page[hOffset++] = (byte) ((fOffset >> 24) & 255);
			page[hOffset++] = (byte) ((fOffset >> 16) & 255);
			page[hOffset++] = (byte) ((fOffset >> 8) & 255);
			page[hOffset++] = (byte) ((fOffset) & 255);
		}
	}

	public boolean write(int slotNo, Tuple tuple, boolean update) {
		return write(slotNo, tuple, update, false);
	}

	public boolean write(int slotNo, Tuple tuple, boolean update,
			boolean prefixCompression) {
		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Attempting to store %s in slot %s of page %s.", tuple,
					slotNo, getHandle()));
		}

		byte[] page = handle.page;
		int freeSpace = getFreeSpace();
		int recordCount = getRecordCount();

		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo > getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		// Calculate space for new tuple
		int fieldSize = calcFieldSizeForTuple(tuple, slotNo, prefixCompression);
		int requiredSpace = headerSize + fieldSize;
		int hOffset = calcHeaderOffset(slotNo);

		if (slotNo < recordCount) {
			if (update) {
				int oldFoffset = getFieldOffset(page, hOffset);
				int oldFieldCount = getFieldCount(page, hOffset);
				boolean oldPrefixCompression = getFlag(page, hOffset,
						PREFIX_COMPRESSION_FLAG);
				int oldFieldSize = calcFieldSizeFromField(page, oldFoffset,
						oldFieldCount, oldPrefixCompression);

				if ((fieldSize - oldFieldSize) > freeSpace) {
					if (DEVEL_MODE && log.isTraceEnabled()) {
						log
								.trace(String
										.format(
												"%s bytes freespace is not enough to update entry from %s field bytes to %s in page %s.",
												freeSpace, oldFieldSize,
												fieldSize, getHandle()));
					}

					return false;
				}

				if (slotNo < recordCount - 1) {
					int nextHOffset = calcHeaderOffset(slotNo + 1);

					if (getFlag(page, nextHOffset, PREFIX_COMPRESSION_FLAG)) {
						// check if space is enough for update and for the
						// update of the following record
						int nextFOffset = getFieldOffset(page, nextHOffset);
						int nextFieldCount = getFieldCount(page, nextHOffset);
						byte[] newNextValue = readCompressedValue(page,
								slotNo + 1, 0, nextFOffset);

						if (!updateCompressedValueForPreviousValue(page,
								slotNo + 1, nextFieldCount, 0, nextFOffset,
								newNextValue, tuple.get(0), fieldSize
										- oldFieldSize, false)) {
							return false;
						}

						freeSpace = getFreeSpace();
					}
				}

				if (slotNo + 1 < recordCount) {
					// new tuple will fit in page -> delete current
					int numberOfFollowingEntries = recordCount - slotNo;
					int length = numberOfFollowingEntries * headerSize;
					System.arraycopy(page, hOffset + headerSize, page, hOffset,
							length);
				}

				freeSpace(headerSize + oldFieldSize);
				removeRecord();
				freeSpace += headerSize + oldFieldSize;
				recordCount--;
			} else if (getFlag(page, hOffset, PREFIX_COMPRESSION_FLAG)) {
				// check if space is enough for insert and for the update of the
				// (current) following record
				int fOffset = getFieldOffset(page, hOffset);
				int fieldCount = getFieldCount(page, hOffset);
				byte[] newNextValue = readCompressedValue(page, slotNo, 0,
						fOffset);

				if (!updateCompressedValueForPreviousValue(page, slotNo,
						fieldCount, 0, fOffset, newNextValue, tuple.get(0),
						requiredSpace, false)) {
					return false;
				}

				freeSpace = getFreeSpace();
			}
		}

		if (requiredSpace > freeSpace) {
			if (DEVEL_MODE && log.isTraceEnabled()) {
				log
						.trace(String
								.format(
										"%s bytes freespace is not enough to store %s header bytes and %s field bytes in page %s.",
										freeSpace, headerSize, fieldSize,
										getHandle()));
			}

			return false;
		}

		int freeSpaceOffset = getFreeSpaceOffset();

		if (freeSpaceOffset - calcHeaderOffset(recordCount + 1) < requiredSpace) {
			if (DEVEL_MODE && log.isTraceEnabled()) {
				log
						.trace(String
								.format(
										"Page defragmentation necessary because less than the required %s bytes for header + %s bytes are available in the free space area.",
										headerSize, fieldSize, getHandle()));
			}
			defragment(page, headerSize, recordCount, -1);
			freeSpaceOffset = getFreeSpaceOffset();
		}

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log
					.trace(String
							.format(
									"Allocating %s bytes for header + %s bytes for fields in page %s.",
									headerSize, fieldSize, getHandle()));
		}
		allocateSpace(requiredSpace);

		// Compute offset for fields and update freespace pointer
		int fOffset = freeSpaceOffset - fieldSize;
		setFreeSpaceOffset(fOffset);

		// right shift following headers if necessary
		if (slotNo < recordCount) {
			int length = (recordCount - slotNo + 1) * headerSize;
			int destPos = hOffset + headerSize;
			System.arraycopy(page, hOffset, page, destPos, length);
		}
		addRecord();

		// write header and fields
		writeHeader(page, hOffset, tuple.getSize(), fOffset, prefixCompression);
		writeFields(page, slotNo, fOffset, tuple, prefixCompression);
		handle.setModified(true);

		return true;
	}

	private void defragment(byte[] page, int headerSize, int recordCount,
			int slotNoToFront) {
		int usableSpace = getUsableSpace();
		int freeSpace = getFreeSpace();
		int usedSpace = getUsedSpace();
		int headerSectionLength = recordCount * headerSize;
		int fieldsSectionLength = usedSpace - headerSectionLength;
		byte[] tempFieldsSection = new byte[fieldsSectionLength];
		int tempFreeSpacePointer = tempFieldsSection.length;
		int newFreeSpaceOffset = handle.getPageSize() - fieldsSectionLength;

		for (int slotNo = 0; slotNo < recordCount; slotNo++) {
			int hOffset = calcHeaderOffset(slotNo);
			int fieldCount = getFieldCount(page, hOffset);
			int fOffset = getFieldOffset(page, hOffset);
			boolean prefixCompression = getFlag(page, hOffset,
					PREFIX_COMPRESSION_FLAG);
			int fieldLength = calcFieldSizeFromField(page, fOffset, fieldCount,
					prefixCompression);

			if (slotNo != slotNoToFront) {
				tempFreeSpacePointer -= fieldLength;
				System.arraycopy(page, fOffset, tempFieldsSection,
						tempFreeSpacePointer, fieldLength);

				setFieldOffset(page, hOffset, newFreeSpaceOffset
						+ tempFreeSpacePointer);
			} else {
				System.arraycopy(page, fOffset, tempFieldsSection, 0,
						fieldLength);
				setFieldOffset(page, hOffset, newFreeSpaceOffset);
			}
		}

		System.arraycopy(tempFieldsSection, 0, page, newFreeSpaceOffset,
				tempFieldsSection.length);
		setFreeSpaceOffset(newFreeSpaceOffset);
	}

	private void writeHeader(byte[] page, int hOffset, int fieldCount,
			int fOffset, boolean prefixCompression) {
		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Writing header starting at offset %s.",
					hOffset));
		}

		page[hOffset] = 0;
		setFlag(page, hOffset, PREFIX_COMPRESSION_FLAG, prefixCompression);
		setFieldCount(page, hOffset, (byte) fieldCount);
		setFieldOffset(page, hOffset, fOffset);
	}

	private void writeFields(byte[] page, int slotNo, int fOffset, Tuple tuple,
			boolean prefixCompression) {
		for (int i = 0; i < tuple.getSize(); i++) {
			byte[] value = tuple.get(i);

			if ((i == 0) && (prefixCompression)) {
				fOffset = writeCompressedValue(page, slotNo, i, fOffset, value);
			} else {
				fOffset = writeUncompressedValue(page, slotNo, i, fOffset,
						value);
			}
		}
	}

	private int writeCompressedValue(byte[] page, int slotNo, int fieldNo,
			int fOffset, byte[] value) {
		int valueLength = (value != null) ? value.length : 0;

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log
					.trace(String
							.format(
									"Writing compressed field %s of length %s starting at offset %s.",
									fieldNo, valueLength, fOffset));
		}

		byte[] previousValue = (slotNo > 0) ? readField(slotNo - 1, fieldNo)
				: null;
		int previousValueLength = (previousValue != null) ? previousValue.length
				: 0;
		int diffValuePos = 0;

		while ((diffValuePos < previousValueLength)
				&& (diffValuePos < valueLength)
				&& (previousValue[diffValuePos] == value[diffValuePos]))
			diffValuePos++;

		int diffValueLength = valueLength - diffValuePos;
		int cutOffLength = previousValueLength - diffValuePos;

		return writeDiffValue(page, fOffset, cutOffLength, diffValueLength,
				diffValuePos, value);
	}

	private int writeDiffValue(byte[] page, int fOffset, int cutOffLength,
			int diffValueLength, int diffValuePos, byte[] value) {
		int pos = fOffset + 1;

		if (cutOffLength >= 15) {
			page[fOffset] = (byte) 240;

			if (cutOffLength < 255) {
				page[pos++] = (byte) cutOffLength;
			} else {
				page[pos++] = (byte) 255;
				page[pos++] = (byte) ((cutOffLength >> 8) & 255);
				page[pos++] = (byte) (cutOffLength & 255);
			}
		} else {
			page[fOffset] = (byte) (cutOffLength << 4);
		}

		if (value != null) {
			int encodedDiffValueLength = diffValueLength + 1;

			if (encodedDiffValueLength >= 15) {
				page[fOffset] |= (byte) 15;

				if (encodedDiffValueLength < 255) {
					page[pos++] = (byte) encodedDiffValueLength;
				} else {
					page[pos++] = (byte) 255;
					page[pos++] = (byte) ((encodedDiffValueLength >> 8) & 255);
					page[pos++] = (byte) (encodedDiffValueLength & 255);
				}
			} else {
				page[fOffset] |= (byte) (encodedDiffValueLength);
			}

			System.arraycopy(value, diffValuePos, page, pos, diffValueLength);
		}

		return pos + diffValueLength;
	}

	private int writeUncompressedValue(byte[] page, int slotNo, int fieldNo,
			int fOffset, byte[] value) {
		int valueLength = (value != null) ? value.length : 0;

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Writing field %s of length %s starting at offset %s.",
					fieldNo, valueLength, fOffset));
		}

		if (value != null) {
			int encodedValueLength = valueLength + 1;

			if (encodedValueLength < 255) {
				page[fOffset++] = (byte) encodedValueLength;
			} else {
				page[fOffset++] = (byte) 255;
				page[fOffset++] = (byte) ((encodedValueLength >> 8) & 255);
				page[fOffset++] = (byte) (encodedValueLength & 255);
			}

			System.arraycopy(value, 0, page, fOffset, valueLength);
			fOffset += valueLength;
		} else {
			page[fOffset++] = 0;
		}

		return fOffset;
	}

	public void delete(int slotNo) {
		byte[] page = handle.page;
		int recordCount = getRecordCount();

		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo >= getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		// Read information from slot header
		int hOffset = calcHeaderOffset(slotNo);

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Deleting slot %s starting at offset %s.",
					slotNo, hOffset));
		}

		boolean prefixCompression = getFlag(page, hOffset,
				PREFIX_COMPRESSION_FLAG);
		byte fieldCount = getFieldCount(page, hOffset);
		int fOffset = getFieldOffset(page, hOffset);

		removeRecord();
		deleteHeader(page, slotNo, hOffset, recordCount);
		deleteFields(page, slotNo, fieldCount, fOffset, prefixCompression);

		handle.setModified(true);
	}

	private void deleteFields(byte[] page, int slotNo, int fieldCount,
			int fOffset, boolean prefixCompression) {
		int fieldSize = calcFieldSizeFromField(page, fOffset, fieldCount,
				prefixCompression);

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Deallocating %s bytes for fields olf slot %s in page %s.",
					fieldSize, slotNo, getHandle()));
		}
		freeSpace(fieldSize);

		if (prefixCompression) {
			propagateDeleteOfCompressedValue(page, slotNo, fOffset, 0);
		}
	}

	private void deleteHeader(byte[] page, int slotNo, int hOffset,
			int recordCount) {
		// left shift following headers if necessary
		if (slotNo + 1 < recordCount) {
			int numberOfFollowingEntries = recordCount - slotNo;
			int length = numberOfFollowingEntries * headerSize;
			System.arraycopy(page, hOffset + headerSize, page, hOffset, length);
		}

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Deallocating %s bytes for header of slot %s in page %s.",
					headerSize, slotNo, getHandle()));
		}
		freeSpace(headerSize);
	}

	public byte[] readField(int slotNo, int fieldNo) {
		byte[] page = handle.page;
		byte[] value = null;

		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo >= getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		// Read information from slot header
		int hOffset = calcHeaderOffset(slotNo);
		boolean prefixCompression = getFlag(page, hOffset,
				PREFIX_COMPRESSION_FLAG);
		int fOffset = getFieldOffset(page, hOffset);

		if ((DEVEL_MODE) && (getFieldCount(page, hOffset) < fieldNo)) {
			throw new RuntimeException(String.format(
					"Invalid field number: %s", fieldNo));
		}

		fOffset = advanceOverFields(page, 0, fieldNo, fOffset,
				prefixCompression);

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Reading field %s of slot %s starting at offset %s.",
					fieldNo, slotNo, fOffset));
		}

		if ((fieldNo == 0) && (prefixCompression)) {
			value = readCompressedValue(page, slotNo, fieldNo, fOffset);
		} else {
			value = readUncompressedValue(page, slotNo, fieldNo, fOffset);
		}

		return value;
	}

	private byte[] readCompressedValue(byte[] page, int slotNo, int fieldNo,
			int fOffset) {
		byte[] previousValue = (slotNo > 0) ? readField(slotNo - 1, fieldNo)
				: null;
		return readCompressedValueForPreviousValue(page, fOffset, previousValue);
	}

	private byte[] readCompressedValueForPreviousValue(byte[] page,
			int fOffset, byte[] previousValue) {
		byte[] decompressedValue = null;
		int previousValueLength = (previousValue != null) ? previousValue.length
				: 0;

		int cutOffLength = (page[fOffset] >> 4) & 15;
		int diffValueLength = page[fOffset] & 15;
		int pos = fOffset + 1;

		if (cutOffLength >= 15) {
			cutOffLength = page[pos++] & 255;

			if (cutOffLength >= 255) {
				cutOffLength = ((page[pos++] & 255) << 8) | (page[pos++] & 255);
			}
		}

		if (diffValueLength >= 15) {
			diffValueLength = page[pos++] & 255;

			if (diffValueLength >= 255) {
				diffValueLength = ((page[pos++] & 255) << 8)
						| (page[pos++] & 255);
			}
		}

		diffValueLength--; // correct diff value length

		if (diffValueLength >= 0) {
			int decompressedValueLength = diffValueLength + previousValueLength
					- cutOffLength;
			decompressedValue = new byte[decompressedValueLength];

			if (previousValue != null) {
				System.arraycopy(previousValue, 0, decompressedValue, 0,
						previousValueLength - cutOffLength);
			}

			System.arraycopy(page, pos, decompressedValue, previousValueLength
					- cutOffLength, diffValueLength);
		}

		return decompressedValue;
	}

	private byte[] readUncompressedValue(byte[] page, int slotNo, int fieldNo,
			int fOffset) {
		byte[] value = null;
		int valueLength = page[fOffset++] & 255;

		if (valueLength == 255) {
			valueLength = (((page[fOffset++] & 255) << 8) | (page[fOffset++] & 255));
		}

		valueLength--; // correct value length

		if (valueLength >= 0) {
			value = new byte[valueLength];

			if (valueLength > 0) {
				System.arraycopy(page, fOffset, value, 0, valueLength);
			}
		}

		return value;
	}

	private int advanceOverFields(byte[] page, int startFieldNo,
			int numberOfFields, int fOffset, boolean firstWithPrefixCompression) {
		int endFieldNo = startFieldNo + numberOfFields;

		for (int fieldNo = startFieldNo; fieldNo < endFieldNo; fieldNo++) {
			if ((fieldNo == 0) && (firstWithPrefixCompression)) {
				int cutOffLength = (page[fOffset] >> 4) & 15;
				int diffValueLength = page[fOffset] & 15;
				fOffset += 1;

				if (cutOffLength >= 15) {
					cutOffLength = page[fOffset++] & 255;

					if (cutOffLength >= 255) {
						cutOffLength = ((page[fOffset++] & 255) << 8)
								| (page[fOffset++] & 255);
					}
				}

				if (diffValueLength >= 15) {
					diffValueLength = page[fOffset++] & 255;

					if (diffValueLength >= 255) {
						diffValueLength = ((page[fOffset++] & 255) << 8)
								| (page[fOffset++] & 255);
					}
				}

				diffValueLength--; // correct the diff value length

				if (diffValueLength > 0) {
					fOffset += diffValueLength;
				}
			} else {
				int valueLength = page[fOffset++] & 255;

				if (valueLength == 255) {
					valueLength = (((page[fOffset++] & 255) << 8) | (page[fOffset++] & 255));
				}

				valueLength--; // correct value length

				if (valueLength > 0) {
					fOffset += valueLength;
				}
			}
		}
		return fOffset;
	}

	public boolean writeField(int slotNo, int fieldNo, byte[] newValue) {
		byte[] page = handle.page;

		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo >= getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		// Read information from slot header
		int hOffset = calcHeaderOffset(slotNo);
		boolean prefixCompression = getFlag(page, hOffset,
				PREFIX_COMPRESSION_FLAG);
		int fieldCount = getFieldCount(page, hOffset);
		int fOffset = getFieldOffset(page, hOffset);

		if ((DEVEL_MODE) && (fieldCount < fieldNo)) {
			throw new RuntimeException(String.format(
					"Invalid field number: %s", fieldNo));
		}

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Updating field %s of slot %s starting at offset %s.",
					fieldNo, slotNo, hOffset));
		}

		fOffset = advanceOverFields(page, 0, fieldNo, fOffset,
				prefixCompression);

		boolean success = ((fieldNo == 0) && (prefixCompression)) ? updateCompressedValue(
				page, slotNo, fieldCount, fieldNo, fOffset, newValue)
				: updateUncompressedValue(page, slotNo, fieldCount, fieldNo,
						fOffset, newValue);

		if (success) {
			handle.setModified(true);
		}

		return success;
	}

	private boolean updateCompressedValue(byte[] page, int slotNo,
			int fieldCount, int fieldNo, final int fOffset, byte[] newValue) {
		byte[] previousValue = (slotNo > 0) ? readField(slotNo - 1, fieldNo)
				: null;
		return updateCompressedValueForPreviousValue(page, slotNo, fieldCount,
				fieldNo, fOffset, newValue, previousValue, 0, true);
	}

	private boolean updateCompressedValueForPreviousValue(byte[] page,
			int slotNo, int fieldCount, int fieldNo, int fOffset,
			byte[] newValue, byte[] previousValue, int required,
			boolean propagate) {
		int previousValueLength = (previousValue != null) ? previousValue.length
				: 0;
		int newValueLength = (newValue != null) ? newValue.length : 0;
		int newDiffValuePos = 0;

		while ((newDiffValuePos < previousValueLength)
				&& (newDiffValuePos < newValueLength)
				&& (previousValue[newDiffValuePos] == newValue[newDiffValuePos]))
			newDiffValuePos++;

		int newDiffValueLength = newValueLength - newDiffValuePos;
		int newCutOffLength = previousValueLength + newDiffValueLength
				- newValueLength;
		int newFieldLength = 1 + newDiffValueLength;

		if (newCutOffLength >= 15) {
			newFieldLength += (newCutOffLength < 255) ? 1 : 3;
		}

		if (newDiffValueLength > 0) {
			int encodedNewDiffValueLength = newDiffValueLength + 1;

			if (encodedNewDiffValueLength >= 15) {
				newFieldLength += (encodedNewDiffValueLength < 255) ? 1 : 3;
			}
		}

		// compute field length of old value
		int oldCutOffLength = (page[fOffset] >> 4) & 15;
		int oldDiffValueLength = page[fOffset] & 15;
		int oldPos = fOffset + 1;

		if (oldCutOffLength >= 15) {
			oldCutOffLength = page[oldPos++] & 255;

			if (oldCutOffLength >= 255) {
				oldCutOffLength = ((page[oldPos++] & 255) << 8)
						| (page[oldPos++] & 255);
			}
		}

		if (oldDiffValueLength >= 15) {
			oldDiffValueLength = page[oldPos++] & 255;

			if (oldDiffValueLength >= 255) {
				oldDiffValueLength = ((page[oldPos++] & 255) << 8)
						| (page[oldPos++] & 255);
			}
		}

		oldDiffValueLength--; // correct diff value length

		int oldFieldLength = ((oldDiffValueLength > 0) ? oldDiffValueLength : 0)
				+ oldPos - fOffset;

		boolean enoughSpaceFree = ((required + (newFieldLength - oldFieldLength)) <= getFreeSpace());

		if (!enoughSpaceFree) {
			return false;
		}

		if (propagate) {
			if (!propagateUpdateOfCompressedValue(page, slotNo + 1, fieldNo,
					newValue, (required + (newFieldLength - oldFieldLength)))) {
				return false;
			}

			// propagate might have changed fOffset
			int hOffset = calcHeaderOffset(slotNo);
			fOffset = getFieldOffset(page, hOffset);
			fOffset = advanceOverFields(page, 0, fieldNo, fOffset, true);
		}

		int newFOffset = fOffset;

		if (newFieldLength < oldFieldLength) {
			newFOffset = shrinkRoomForField(page, fieldCount, fieldNo, fOffset,
					newFieldLength, oldFieldLength, true);
		} else if (newFieldLength > oldFieldLength) {
			newFOffset = growRoomForField(page, slotNo, fieldNo, fieldCount,
					fOffset, newFieldLength, oldFieldLength);
		}

		writeDiffValue(page, newFOffset, newCutOffLength, newDiffValueLength,
				newDiffValuePos, newValue);

		return true;
	}

	private int shrinkRoomForField(byte[] page, int fieldCount, int fieldNo,
			int fOffset, int newFieldLength, int oldFieldLength,
			boolean prefixCompression) {
		if (newFieldLength < oldFieldLength) {
			int numberOfTrailingFields = fieldCount - fieldNo - 1;

			if (numberOfTrailingFields > 0) {
				int fOffsetOfFollowingField = fOffset + oldFieldLength;
				int endFoffset = advanceOverFields(page, fieldNo + 1,
						fieldCount - fieldNo - 1, fOffsetOfFollowingField,
						prefixCompression);
				System.arraycopy(page, fOffsetOfFollowingField, page, fOffset
						+ newFieldLength, endFoffset - fOffsetOfFollowingField);
			}

			freeSpace(oldFieldLength - newFieldLength);
		}

		return fOffset;
	}

	private boolean propagateDeleteOfCompressedValue(byte[] page, int slotNo,
			int deleteFOffset, int fieldNo) {
		if (slotNo == getRecordCount()) {
			return true;
		}

		byte[] previousValue = (slotNo > 0) ? readField(slotNo - 1, fieldNo)
				: null;
		byte[] deletedValue = readCompressedValueForPreviousValue(page,
				deleteFOffset, previousValue);

		int hOffset = calcHeaderOffset(slotNo);
		int fOffset = getFieldOffset(page, hOffset);
		int fieldCount = getFieldCount(page, hOffset);

		fOffset = advanceOverFields(page, 0, fieldNo, fOffset, true);
		byte[] newValue = readCompressedValueForPreviousValue(page, fOffset,
				deletedValue);

		if (DEVEL_MODE && log.isTraceEnabled()) {
			System.out
					.println(String
							.format(
									"Propagating delete of a value to compressed value %s in following slot %s.",
									Arrays.toString(newValue), slotNo));
		}

		return updateCompressedValueForPreviousValue(page, slotNo, fieldCount,
				fieldNo, fOffset, newValue, previousValue, 0, false);
	}

	private boolean propagateUpdateOfCompressedValue(byte[] page, int slotNo,
			int fieldNo, byte[] previousValue, int required) {
		if (slotNo == getRecordCount()) {
			return true;
		}

		int hOffset = calcHeaderOffset(slotNo);
		int fOffset = getFieldOffset(page, hOffset);
		int fieldCount = getFieldCount(page, hOffset);

		fOffset = advanceOverFields(page, 0, fieldNo, fOffset, true);
		byte[] newValue = readCompressedValue(page, slotNo, fieldNo, fOffset);

		if (DEVEL_MODE && log.isTraceEnabled()) {
			System.out
					.println(String
							.format(
									"Propagating update of value %s to compressed value %s in following slot %s.",
									Arrays.toString(previousValue), Arrays
											.toString(newValue), slotNo));
		}

		return updateCompressedValueForPreviousValue(page, slotNo, fieldCount,
				fieldNo, fOffset, newValue, previousValue, required, false);
	}

	private int growRoomForField(byte[] page, int slotNo, int fieldNo,
			int fieldCount, int fOffset, int newFieldLength, int oldFieldLength) {
		// save old field information before taking action
		int additionalRequiredSpace = newFieldLength - oldFieldLength;
		int hOffset = calcHeaderOffset(slotNo);
		boolean prefixCompression = getFlag(page, hOffset,
				PREFIX_COMPRESSION_FLAG);
		int currentEndFoffset = advanceOverFields(page, fieldNo + 1, fieldCount
				- fieldNo - 1, fOffset + oldFieldLength, prefixCompression);
		int currentStartFoffset = getFieldOffset(page, hOffset);
		int oldFieldsSize = currentEndFoffset - currentStartFoffset;
		int newFieldsSize = oldFieldsSize + additionalRequiredSpace;
		int leadingFieldsLength = fOffset - currentStartFoffset;
		int trailingFieldsLength = oldFieldsSize
				- (leadingFieldsLength + oldFieldLength);

		// check if we can write new fields section in the free space area
		int freeSpaceOffset = getFreeSpaceOffset();
		int recordCount = getRecordCount();
		int newStartFoffset = -1;

		if (freeSpaceOffset - calcHeaderOffset(recordCount) < newFieldsSize) {
			if (DEVEL_MODE && log.isTraceEnabled()) {
				log
						.trace(String
								.format(
										"Page defragmentation necessary because less than the required %s bytes are available in the free space area.",
										newFieldsSize, getHandle()));
			}

			// advise defragment to move current record to the front of the used
			// space
			defragment(page, headerSize, recordCount, slotNo);
			currentStartFoffset = getFieldOffset(page, hOffset);
			newStartFoffset = currentStartFoffset - additionalRequiredSpace;
		} else {
			newStartFoffset = freeSpaceOffset - newFieldsSize;
		}

		// allocate the new space and adjust free space offset
		allocateSpace(additionalRequiredSpace);
		setFreeSpaceOffset(newStartFoffset);

		// Make room by copying old leading and old trailing fields, but leave
		// space for new field in between.
		System.arraycopy(page, currentStartFoffset, page, newStartFoffset,
				leadingFieldsLength);
		System.arraycopy(page, currentStartFoffset + leadingFieldsLength
				+ oldFieldLength, page, newStartFoffset + leadingFieldsLength
				+ newFieldLength, trailingFieldsLength);
		setFieldOffset(page, hOffset, newStartFoffset);
		return newStartFoffset + leadingFieldsLength;
	}

	private boolean updateUncompressedValue(byte[] page, int slotNo,
			int fieldCount, int fieldNo, final int fOffset, byte[] newValue) {
		int oldPos = fOffset;
		int oldValueLength = page[oldPos++] & 255;

		if (oldValueLength == 255) {
			oldValueLength = (((page[oldPos++] & 255) << 8) | (page[oldPos++] & 255));
		}

		oldValueLength--; // correct value length

		int oldFieldLength = ((oldValueLength > 0) ? oldValueLength : 0)
				+ oldPos - fOffset;

		int newValueLength = (newValue != null) ? newValue.length : 0;
		int newFieldLength = newValueLength + 1;

		if (newValueLength + 1 >= 255) {
			newFieldLength += 2;
		}

		if (newFieldLength <= oldFieldLength) {
			int newFOffset = shrinkRoomForField(page, fieldCount, fieldNo,
					fOffset, newFieldLength, oldFieldLength, false);
			writeUncompressedValue(page, slotNo, fieldNo, newFOffset, newValue);

			return true;
		} else if ((newFieldLength - oldFieldLength) <= getFreeSpace()) {
			int newFOffset = growRoomForField(page, slotNo, fieldNo,
					fieldCount, fOffset, newFieldLength, oldFieldLength);
			writeUncompressedValue(page, slotNo, fieldNo, newFOffset, newValue);

			return true;
		} else {
			return false;
		}
	}

	private int calcFieldSizeFromField(byte[] page, int fOffset,
			int fieldCount, boolean prefixCompression) {
		int fStartOffset = fOffset;
		int fEndOffset = advanceOverFields(page, 0, fieldCount, fOffset,
				prefixCompression);

		return fEndOffset - fStartOffset;
	}

	public int requiredSpaceForUpdate(int slotNo, Tuple tuple,
			boolean prefixCompression) {
		byte[] page = handle.page;
		int newFieldSize = calcFieldSizeForTuple(tuple, slotNo,
				prefixCompression);
		int hOffset = calcHeaderOffset(slotNo);
		int oldFieldCount = getFieldCount(page, hOffset);
		int oldFieldOffset = getFieldOffset(page, hOffset);
		boolean oldPrefixCompression = getFlag(page, hOffset,
				PREFIX_COMPRESSION_FLAG);
		int oldFieldSize = calcFieldSizeFromField(page, oldFieldOffset,
				oldFieldCount, oldPrefixCompression);

		if ((prefixCompression) && ((slotNo + 1) < getRecordCount())) {
			byte[] currentField = readField(slotNo, 0);
			byte[] nextField = readField(slotNo + 1, 0);

			int oldNextFieldLength = calcRequiredCompressedValueSpace(
					currentField, nextField);
			int newNextFieldLength = calcRequiredCompressedValueSpace(tuple
					.get(0), nextField);

			newFieldSize += (newNextFieldLength - oldNextFieldLength);
		}

		return (newFieldSize - oldFieldSize);
	}

	public int usedSpace(int slotNo) {
		if ((DEVEL_MODE) && ((slotNo < 0) || (slotNo >= getRecordCount()))) {
			throw new RuntimeException(String.format("Invalid slot number: %s",
					slotNo));
		}

		byte[] page = handle.page;
		int hOffset = calcHeaderOffset(slotNo);
		int fOffset = getFieldOffset(page, hOffset);
		int fieldCount = getFieldCount(page, hOffset);
		boolean prefixCompression = getFlag(page, hOffset,
				PREFIX_COMPRESSION_FLAG);
		int fieldSize = calcFieldSizeFromField(page, fOffset, fieldCount,
				prefixCompression);

		return (headerSize + fieldSize);
	}

	public int requiredSpaceForInsert(int slotNo, Tuple tuple,
			boolean prefixCompression) {
		int fieldSize = calcFieldSizeForTuple(tuple, slotNo, prefixCompression);

		if ((prefixCompression) && slotNo < getRecordCount()) {
			byte[] previousField = (slotNo > 0) ? readField(slotNo - 1, 0)
					: null;
			byte[] nextField = readField(slotNo, 0);

			int oldNextFieldLength = calcRequiredCompressedValueSpace(
					previousField, nextField);
			int newNextFieldLength = calcRequiredCompressedValueSpace(tuple
					.get(0), nextField);

			fieldSize += (newNextFieldLength - oldNextFieldLength);
		}

		return headerSize + fieldSize;
	}

	private int calcRequiredCompressedValueSpace(byte[] previousValue,
			byte[] value) {
		int previousValueLength = (previousValue != null) ? previousValue.length
				: 0;
		int insertValueLength = (value != null) ? value.length : 0;
		int diffValueLength = calcDiffValueLength(previousValue,
				previousValueLength, value, insertValueLength);
		int cutOffLength = previousValueLength + diffValueLength
				- insertValueLength;
		int length = 1;

		if (value != null) {
			int encodedDiffValueLength = diffValueLength + 1;

			if (encodedDiffValueLength >= 15) {
				length += (encodedDiffValueLength < 255) ? 1 : 3;
			}

			length += diffValueLength;
		}

		if (cutOffLength >= 15) {
			length += (cutOffLength < 255) ? 1 : 3;
		}

		return length;
	}

	private int calcDiffValueLength(byte[] previousKey, int previousKeyLength,
			byte[] key, int keyLength) {
		int diffKeyPos = 0;

		while ((diffKeyPos < previousKeyLength) && (diffKeyPos < keyLength)
				&& (previousKey[diffKeyPos] == key[diffKeyPos]))
			diffKeyPos++;

		return (keyLength - diffKeyPos);
	}

	private int calcHeaderOffset(int slotNo) {
		return getStartOffset() + slotNo * headerSize;
	}

	/**
	 * Performs a binary search in the given page.
	 * 
	 * @param searchMode
	 * @param fieldNo
	 *            TODO
	 * @param minSlotNo
	 *            TODO
	 * @param maxSlotNo
	 *            TODO
	 * @param filter
	 * @return
	 */
	public int findSlot(SearchMode searchMode, Field type, int fieldNo,
			byte[] searchValue, int minSlotNo, int maxSlotNo) {
		int pos = 0;
		int lower = minSlotNo;
		int upper = maxSlotNo;
		int comparison = 0;
		boolean findGreatestInside = searchMode.findGreatestInside();
		boolean isInside = false;
		byte[] compareValue = null;

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log
					.trace(String
							.format(
									"Searching %s %s in interval [%s, %s] with bounds lower=%s and upper=%s",
									searchMode, type.toString(searchValue),
									type.toString(readField(lower, fieldNo)),
									type.toString(readField(upper, fieldNo)),
									lower, upper));
		}

		while (lower < upper) {
			pos = (findGreatestInside) ? (lower + (upper - lower + 1) / 2)
					: (lower + (upper - lower) / 2);
			compareValue = readField(pos, fieldNo);

			if (DEVEL_MODE && log.isTraceEnabled()) {
				log
						.trace(String
								.format(
										"Do search %s %s in interval [%s, %s] with bounds lower=%s and upper=%s and pos=%s",
										searchMode, type.toString(searchValue),
										type
												.toString(readField(lower,
														fieldNo)), type
												.toString(readField(upper,
														fieldNo)), lower,
										upper, pos));
			}

			isInside = searchMode.isInside(type, compareValue, searchValue);

			if (DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("%s is indside %s %s : %s", type
						.toString(compareValue), searchMode, type
						.toString(searchValue), isInside));
			}

			if (findGreatestInside) {
				if (!isInside) {
					upper = pos - 1;
				} else {
					lower = pos;
				}
			} else {
				if (isInside) {
					upper = pos;
				} else {
					lower = pos + 1;
				}
			}
		}

		return lower;
	}

	protected int calcFieldSizeForTuple(Tuple tuple, int slotNo,
			boolean prefixCompression) {
		int length = 0;
		for (int i = 0; i < tuple.getSize(); i++) {
			byte[] value = tuple.get(i);

			if ((i == 0) && (prefixCompression)) {
				byte[] previousValue = (slotNo > 0) ? readField(slotNo - 1, i)
						: null;
				int previousValueLength = (previousValue != null) ? previousValue.length
						: 0;
				int valueLength = (value != null) ? value.length : 0;
				int diffValuePos = 0;

				while ((diffValuePos < previousValueLength)
						&& (diffValuePos < valueLength)
						&& (previousValue[diffValuePos] == value[diffValuePos]))
					diffValuePos++;

				int diffValueLength = valueLength - diffValuePos;
				int cutOffLength = previousValueLength - diffValuePos;
				int fieldLength = 1 + diffValueLength;

				if (cutOffLength >= 15) {
					fieldLength += (cutOffLength < 255) ? 1 : 3;
				}

				if (diffValueLength >= 0) {
					int encodedDiffValueLength = diffValueLength + 1;

					if (encodedDiffValueLength >= 15) {
						fieldLength += (encodedDiffValueLength < 255) ? 1 : 3;
					}
				}

				length += fieldLength;
			} else {
				if (value != null) {
					int descriptorLength = 1;
					int valueLength = value.length;

					if (valueLength + 1 >= 255) {
						descriptorLength += 2;
					}

					length += descriptorLength;
					length += valueLength;
				} else {
					length += 1;
				}
			}
		}
		return length;
	}

	public void dump(PrintStream out) {
		out.append(String.format("Dumping page %s:\n", handle.getPageID()));
		for (int slotNo = 0; slotNo < getRecordCount(); slotNo++) {
			Tuple tuple = read(slotNo);
			out.append(String.format("%7s@%-5s: %s\n", slotNo, getFieldOffset(
					handle.page, calcHeaderOffset(slotNo)), tuple));
		}
		out.flush();
	}
}