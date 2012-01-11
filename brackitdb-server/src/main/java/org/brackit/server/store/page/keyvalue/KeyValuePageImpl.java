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
package org.brackit.server.store.page.keyvalue;

import java.util.Arrays;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.RecordFlag;

/**
 * @author Sebastian Baechle
 * 
 */
public class KeyValuePageImpl extends BasePage implements KeyValuePage {
	protected static final byte PREFIX_COMPRESSION_FLAG = RecordFlag.PREFIX_COMPRESSION.mask;

	private static final Logger log = Logger.getLogger(KeyValuePageImpl.class);

	private static final int HEADER_SIZE = 1;

	private final byte[] page;

	public KeyValuePageImpl(Buffer buffer, Handle handle, int reserved) {
		super(buffer, handle, reserved);
		this.page = handle.page;
	}

	public KeyValuePageImpl(Buffer buffer, Handle handle) {
		super(buffer, handle, 0);
		this.page = handle.page;
	}

	@Override
	public boolean checkFlag(int pos, RecordFlag flag) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		return getFlag(getHeaderOffset(pos), flag.mask);
	}

	@Override
	public void setFlag(int pos, RecordFlag flag, boolean value) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		setFlag(getHeaderOffset(pos), flag.mask, value);
	}

	private boolean getFlag(int hOffset, byte flagMask) {
		return ((page[hOffset] & flagMask) != 0);
	}

	private byte getFlags(int hOffset) {
		return page[hOffset];
	}

	private void setFlag(int hOffset, byte flagMask, boolean flag) {

		if (flag) {
			page[hOffset] = (byte) (flagMask | page[hOffset]);
		} else {
			page[hOffset] = (byte) (~flagMask & page[hOffset]);
		}
	}

	@Override
	public void format(PageID basePageID) {
		clear();
		setBasePageID(basePageID);
		setFreeSpaceOffset(handle.getPageSize());
		handle.setModified(true);
	}

	@Override
	public int calcMaxInlineValueSize(int minNoOfEntries, int maxKeySize) {
		int maxRequiredBytesForKey = (maxKeySize < 15) ? 1
				: (maxKeySize < 255) ? 3 : 5;
		int leftForValuesTotal = getUsableSpace()
				- (minNoOfEntries * (HEADER_SIZE + maxRequiredBytesForKey));
		int leftForValue = leftForValuesTotal / 3;
		leftForValue -= (leftForValue < 255) ? 1 : 3;
		return leftForValue;
	}

	@Override
	public byte[] getKey(int pos) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		int offset = getHeaderOffset(pos);
		boolean compression = getFlag(offset, PREFIX_COMPRESSION_FLAG);
		offset += HEADER_SIZE;

		if (compression) {
			byte[] previousKey = (pos > 0) ? getKey(pos - 1) : null;
			return readDiffValue(offset, previousKey);
		} else {
			return readUncompressedValue(offset);
		}
	}

	@Override
	public byte[] getValue(int pos) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		int offset = getHeaderOffset(pos);
		boolean compression = getFlag(offset, PREFIX_COMPRESSION_FLAG);
		offset += HEADER_SIZE;

		if (compression) {
			offset = advanceOverCompressedValue(offset);
		} else {
			offset = advanceOverUncompressedValue(offset);
		}

		return readUncompressedValue(offset);
	}

	private int advanceOverUncompressedValue(int offset) {

		int valueLength = page[offset++] & 255;

		if (valueLength == 255) {
			valueLength = (((page[offset++] & 255) << 8) | (page[offset++] & 255));
		}

		valueLength--; // correct value length

		if (valueLength > 0) {
			offset += valueLength;
		}

		return offset;
	}

	private int advanceOverCompressedValue(int offset) {
		int cutOffLength = (page[offset] >> 4) & 15;
		int diffValueLength = page[offset] & 15;
		offset += 1;

		if (cutOffLength >= 15) {
			cutOffLength = page[offset++] & 255;

			if (cutOffLength >= 255) {
				cutOffLength = ((page[offset++] & 255) << 8)
						| (page[offset++] & 255);
			}
		}

		if (diffValueLength >= 15) {
			diffValueLength = page[offset++] & 255;

			if (diffValueLength >= 255) {
				diffValueLength = ((page[offset++] & 255) << 8)
						| (page[offset++] & 255);
			}
		}

		diffValueLength--; // correct the diff value length

		if (diffValueLength > 0) {
			offset += diffValueLength;
		}

		return offset;
	}

	@Override
	public boolean insert(int pos, byte[] key, byte[] value, boolean compressed) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		boolean success = (compressed) ? insertCompressed(pos, key, value)
				: insertUncompressed(pos, key, value);

		if (success) {
			handle.setModified(true);
		}

		return success;
	}

	public int getUsedSpace(int pos) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		int offset = getHeaderOffset(pos);
		return calcLengthFromEntry(offset);
	}

	private boolean prepareForInsert(int pos, int offset, byte[] insertKey,
			byte[] insertValue, int requiredSpace) {
		boolean insertLast = (pos == getRecordCount());

		if ((!insertLast) && getFlag(offset, PREFIX_COMPRESSION_FLAG)) {
			byte[] nextKey = getKey(pos);
			int oldNextKeyLength = (advanceOverCompressedValue(offset
					+ HEADER_SIZE) - (offset + HEADER_SIZE));
			int newNextKeyLength = calcRequiredCompressedValueSpace(insertKey,
					nextKey);
			int delta = newNextKeyLength - oldNextKeyLength;
			int nettoRequiredSpace = requiredSpace + delta;

			if (nettoRequiredSpace > getFreeSpace()) {
				return false;
			}

			byte flags = getFlags(offset);
			adjustSpace(offset, nettoRequiredSpace, nettoRequiredSpace);

			// write header and key of next record again
			int newNextHeaderOffset = offset + requiredSpace;
			int lengthOfNextEntry = calcLengthFromEntry(offset
					+ nettoRequiredSpace);
			offset = updateHeader(pos, newNextHeaderOffset, lengthOfNextEntry
					+ delta, flags);
			offset = writeCompressedValue(offset, nextKey, insertKey);

			return true;
		} else if (requiredSpace <= getFreeSpace()) {
			adjustSpace(offset, requiredSpace, !insertLast ? requiredSpace : 0);

			return true;
		} else {
			return false;
		}
	}

	protected int updateHeader(int pos, int newOffset, int newLength, byte flags) {
		return writeHeader(pos, newOffset, newLength, flags);
	}

	protected int insertHeader(int pos, int offset, int length, byte flags) {
		return writeHeader(pos, offset, length, flags);
	}

	private boolean prepareForUpdate(int pos, int offset, byte[] updateKey,
			byte[] updateValue, int requiredSpace) {
		int nextOffset = getHeaderOffset(pos + 1);
		boolean updateLast = (pos == getRecordCount() - 1);

		if ((!updateLast) && getFlag(nextOffset, PREFIX_COMPRESSION_FLAG)) {
			byte[] nextKey = getKey(pos + 1);
			int oldNextKeyLength = (advanceOverCompressedValue(nextOffset
					+ HEADER_SIZE) - (nextOffset + HEADER_SIZE));
			int newNextLength = calcRequiredCompressedValueSpace(updateKey,
					nextKey);
			int delta = newNextLength - oldNextKeyLength;
			requiredSpace += delta;

			if (requiredSpace > getFreeSpace()) {
				return false;
			}

			byte flags = getFlags(nextOffset);
			adjustSpace(offset, requiredSpace, requiredSpace);

			// write header and key of next record again
			nextOffset = updateHeader(pos, nextOffset + requiredSpace,
					calcLengthFromEntry(nextOffset + requiredSpace) + delta,
					flags);
			nextOffset = writeCompressedValue(nextOffset, nextKey, updateKey);

			return true;
		} else if (requiredSpace <= getFreeSpace()) {
			adjustSpace(offset, requiredSpace, !updateLast ? requiredSpace : 0);

			return true;
		} else {
			return false;
		}
	}

	private void adjustSpace(int offset, int requiredSpace, int shift) {
		if (requiredSpace > 0) {
			allocateSpace(requiredSpace);
		} else if (requiredSpace < 0) {
			freeSpace(-requiredSpace);
		}

		if (shift > 0) {
			if (DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("Creating gap from %s to %s", offset,
						offset + shift));
			}

			// make room
			System.arraycopy(handle.page, offset, handle.page, offset + shift,
					handle.page.length - (offset + shift));
		} else if (shift < 0) {
			if (DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("Closing gap from %s to %s", offset,
						offset - shift));
			}

			// claim room
			System.arraycopy(handle.page, offset - shift, handle.page, offset,
					handle.page.length - (offset - shift));
		}
	}

	private boolean insertCompressed(int pos, byte[] key, byte[] value) {
		byte[] previousKey = (pos > 0) ? getKey(pos - 1) : null;
		int compressedKeyLength = calcRequiredCompressedValueSpace(previousKey,
				key);
		int valueLength = calcRequiredUncompressedValueSpace(value);
		int requiredSpace = HEADER_SIZE + compressedKeyLength + valueLength;
		int offset = getHeaderOffset(pos);

		if (!prepareForInsert(pos, offset, key, value, requiredSpace)) {
			return false;
		}

		addRecord();

		// write header, key, and value of new record
		offset = insertHeader(pos, offset, requiredSpace,
				PREFIX_COMPRESSION_FLAG);
		offset = writeCompressedValue(offset, key, previousKey);
		offset = writeUncompressedValue(offset, value);

		return true;
	}

	private int writeHeader(int pos, int offset, int length, byte flags) {
		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Writing header of %s (length=%s, flags=%s) to %s", pos,
					length, flags, offset));
		}

		handle.page[offset] = flags;
		return offset + HEADER_SIZE;
	}

	protected int getHeaderOffset(int pos) {
		if (pos > 0) {
			int previousHeaderOffset = getHeaderOffset(pos - 1);
			int previousLength = calcLengthFromEntry(previousHeaderOffset);

			return previousHeaderOffset + previousLength;
		} else {
			return getStartOffset();
		}
	}

	protected final int calcLengthFromEntry(int offset) {
		int currentOffset = offset;
		boolean compression = getFlag(offset, PREFIX_COMPRESSION_FLAG);
		currentOffset += HEADER_SIZE;

		if (compression) {
			currentOffset = advanceOverCompressedValue(currentOffset);
		} else {
			currentOffset = advanceOverUncompressedValue(currentOffset);
		}

		currentOffset = advanceOverUncompressedValue(currentOffset);

		return (currentOffset - offset);
	}

	private int writeUncompressedValue(int offset, byte[] value) {
		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Writing uncompressed value %s from %s",
					(value != null) ? Arrays.toString(value) : null, offset));
		}

		int valueLength = (value != null) ? value.length : 0;

		if (value != null) {
			int encodedValueLength = valueLength + 1;

			if (encodedValueLength < 255) {
				page[offset++] = (byte) encodedValueLength;
			} else {
				page[offset++] = (byte) 255;
				page[offset++] = (byte) ((encodedValueLength >> 8) & 255);
				page[offset++] = (byte) (encodedValueLength & 255);
			}

			System.arraycopy(value, 0, page, offset, valueLength);
			offset += valueLength;
		} else {
			page[offset++] = 0;
		}

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("End of uncompressed value %s is %s",
					(value != null) ? Arrays.toString(value) : null, offset));
		}

		return offset;
	}

	private byte[] readUncompressedValue(int offset) {
		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Reading uncompressed value from %s",
					offset));
		}

		byte[] value = null;

		int valueLength = page[offset++] & 255;

		if (valueLength == 255) {
			valueLength = (((page[offset++] & 255) << 8) | (page[offset++] & 255));
		}

		valueLength--; // correct value length

		if (valueLength >= 0) {
			value = new byte[valueLength];

			if (valueLength > 0) {
				System.arraycopy(page, offset, value, 0, valueLength);
			}
		}

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("End of uncompressed value %s is %s",
					(value != null) ? Arrays.toString(value) : null, offset
							+ valueLength));
		}

		return value;
	}

	private int writeCompressedValue(int offset, byte[] value,
			byte[] previousValue) {
		int valueLength = (value != null) ? value.length : 0;
		int previousValueLength = (previousValue != null) ? previousValue.length
				: 0;
		int diffValuePos = 0;

		while ((diffValuePos < previousValueLength)
				&& (diffValuePos < valueLength)
				&& (previousValue[diffValuePos] == value[diffValuePos]))
			diffValuePos++;

		int diffValueLength = valueLength - diffValuePos;
		int cutOffLength = previousValueLength - diffValuePos;

		return writeDiffValue(offset, cutOffLength, diffValueLength,
				diffValuePos, value);
	}

	private byte[] readDiffValue(int offset, byte[] previousValue) {
		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Reading compressed value after %s from %s",
					(previousValue != null) ? Arrays.toString(previousValue)
							: null, offset));
		}

		byte[] decompressedValue = null;
		int previousValueLength = (previousValue != null) ? previousValue.length
				: 0;

		int cutOffLength = (page[offset] >> 4) & 15;
		int diffValueLength = page[offset] & 15;
		int pos = offset + 1;

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

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("End of compressed value %s is %s",
					(decompressedValue != null) ? Arrays
							.toString(decompressedValue) : null, pos
							+ diffValueLength));
		}

		return decompressedValue;
	}

	private int writeDiffValue(int offset, int cutOffLength,
			int diffValueLength, int diffValuePos, byte[] value) {
		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format(
					"Writing compressed value %s with diffValuePos %s from %s",
					(value != null) ? Arrays.toString(value) : null,
					diffValuePos, offset));
		}

		int pos = offset + 1;

		if (cutOffLength >= 15) {
			page[offset] = (byte) 240;

			if (cutOffLength < 255) {
				page[pos++] = (byte) cutOffLength;
			} else {
				page[pos++] = (byte) 255;
				page[pos++] = (byte) ((cutOffLength >> 8) & 255);
				page[pos++] = (byte) (cutOffLength & 255);
			}
		} else {
			page[offset] = (byte) (cutOffLength << 4);
		}

		if (value != null) {
			int encodedDiffValueLength = diffValueLength + 1;

			if (encodedDiffValueLength >= 15) {
				page[offset] |= (byte) 15;

				if (encodedDiffValueLength < 255) {
					page[pos++] = (byte) encodedDiffValueLength;
				} else {
					page[pos++] = (byte) 255;
					page[pos++] = (byte) ((encodedDiffValueLength >> 8) & 255);
					page[pos++] = (byte) (encodedDiffValueLength & 255);
				}
			} else {
				page[offset] |= (byte) (encodedDiffValueLength);
			}

			System.arraycopy(value, diffValuePos, page, pos, diffValueLength);
			offset = pos + diffValueLength;
		}

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("End of compressed value %s is %s",
					(value != null) ? Arrays.toString(value) : null, offset));
		}

		return offset;
	}

	private boolean insertUncompressed(int pos, byte[] key, byte[] value) {
		int insertKeyLength = calcRequiredUncompressedValueSpace(key);
		int insertValueLength = calcRequiredUncompressedValueSpace(value);
		int requiredSpace = HEADER_SIZE + insertKeyLength + insertValueLength;
		int offset = getHeaderOffset(pos);

		if (!prepareForInsert(pos, offset, key, value, requiredSpace)) {
			return false;
		}

		addRecord();

		// write header, key, and value of new record
		offset = insertHeader(pos, offset, requiredSpace, (byte) 0);
		offset = writeUncompressedValue(offset, key);
		offset = writeUncompressedValue(offset, value);

		return true;
	}

	@Override
	public void delete(int pos) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		int offset = getHeaderOffset(pos);
		boolean compressed = getFlag(offset, PREFIX_COMPRESSION_FLAG);

		if (compressed) {
			deleteCompressed(pos, offset);
		} else {
			deleteUncompressed(pos, offset);
		}
		handle.setModified(true);
	}

	private void deleteUncompressed(int pos, int offset) {
		int free = calcLengthFromEntry(offset);
		boolean deleteLast = (pos == getRecordCount() - 1);

		adjustSpace(offset, -free, !deleteLast ? -free : 0);
		removeRecord();
	}

	private void deleteCompressed(int pos, int offset) {
		int free = calcLengthFromEntry(offset);
		boolean deleteLast = (pos == getRecordCount() - 1);

		if (DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Deleting pos %s from %s to %s", pos,
					offset, free));
		}

		if (!deleteLast) {
			int nextOffset = offset + free;
			boolean nextCompressed = getFlag(nextOffset,
					PREFIX_COMPRESSION_FLAG);

			if (nextCompressed) {
				byte flags = getFlags(nextOffset);
				byte[] previousKey = (pos > 0) ? getKey(pos - 1) : null;
				byte[] nextKey = getKey(pos + 1);
				int nextValueOffset = advanceOverCompressedValue(nextOffset
						+ HEADER_SIZE);
				int oldNextKeyLength = (nextValueOffset - (nextOffset + HEADER_SIZE));
				int newNextKeyLength = calcRequiredCompressedValueSpace(
						previousKey, nextKey);
				int delta = newNextKeyLength - oldNextKeyLength;
				free -= delta;

				offset = updateHeader(pos + 1, offset,
						calcLengthFromEntry(nextOffset) + delta, flags);
				offset = writeCompressedValue(offset, nextKey, previousKey);
			}
		}

		adjustSpace(offset, -free, !deleteLast ? -free : 0);
		removeRecord();
	}

	@Override
	public boolean setValue(int pos, byte[] value) {
		return update(pos, getKey(pos), value);
	}

	@Override
	public boolean setKey(int pos, byte[] key) {
		return update(pos, key, getValue(pos));
	}

	@Override
	public boolean update(int pos, byte[] key, byte[] value) {
		if ((pos < 0) || (pos >= getRecordCount() + 1)) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		int offset = getHeaderOffset(pos);
		boolean compressed = getFlag(offset, PREFIX_COMPRESSION_FLAG);

		boolean success = (compressed) ? updateCompressed(pos, key, value)
				: updateUncompressed(pos, key, value);

		if (success) {
			handle.setModified(true);
		}

		return success;
	}

	private boolean updateUncompressed(int pos, byte[] key, byte[] value) {
		int offset = getHeaderOffset(pos);
		int currentLength = calcLengthFromEntry(offset);
		int keyLength = calcRequiredUncompressedValueSpace(key);
		int valueLength = calcRequiredUncompressedValueSpace(value);
		int newLength = HEADER_SIZE + keyLength + valueLength;
		int requiredSpace = newLength - currentLength;
		byte flags = getFlags(offset);

		if (!prepareForUpdate(pos, offset, key, value, requiredSpace)) {
			return false;
		}

		// write header, key, and value of updated record
		offset = updateHeader(pos, offset, newLength, flags);
		offset = writeUncompressedValue(offset, key);
		offset = writeUncompressedValue(offset, value);

		return true;
	}

	private boolean updateCompressed(int pos, byte[] key, byte[] value) {
		int offset = getHeaderOffset(pos);
		int currentLength = calcLengthFromEntry(offset);
		byte[] previousKey = (pos > 0) ? getKey(pos - 1) : null;
		int compressedKeyLength = calcRequiredCompressedValueSpace(previousKey,
				key);
		int valueLength = calcRequiredUncompressedValueSpace(value);
		int newLength = HEADER_SIZE + compressedKeyLength + valueLength;
		int requiredSpace = newLength - currentLength;
		byte flags = getFlags(offset);

		if (!prepareForUpdate(pos, offset, key, value, requiredSpace)) {
			return false;
		}

		// write header, key, and value of updated record
		offset = updateHeader(pos, offset, newLength, flags);
		offset = writeCompressedValue(offset, key, previousKey);
		offset = writeUncompressedValue(offset, value);

		return true;
	}

	private byte[] getKey(boolean compressed, int diffKeyOffset,
			int cutOffLength, int diffKeyLength, byte[] prevKey) {
		if (compressed) {
			int prevKeyLength = (prevKey != null) ? prevKey.length : 0;

			byte[] key = new byte[diffKeyLength + prevKeyLength - cutOffLength];

			if (prevKey != null) {
				System.arraycopy(prevKey, 0, key, 0, prevKeyLength
						- cutOffLength);
			}

			System.arraycopy(handle.page, diffKeyOffset, key, prevKeyLength
					- cutOffLength, diffKeyLength);
			return key;
		} else {
			byte[] key = new byte[diffKeyLength];
			System.arraycopy(handle.page, diffKeyOffset, key, 0, diffKeyLength);
			return key;
		}
	}

	private byte[] getValue(int valueOffset, int valueLength) {
		byte[] value = new byte[valueLength];
		System.arraycopy(handle.page, valueOffset, value, 0, valueLength);
		return value;
	}

	public int requiredSpaceForInsert(int pos, byte[] insertKey,
			byte[] insertValue, boolean compressed) {
		if ((pos < 0) || (pos > getRecordCount())) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		if (compressed) {
			return calcRequiredSpaceInsertCompressed(pos, insertKey,
					insertValue);
		} else {
			return calcRequiredSpaceInsertUncompressed(insertKey, insertValue);
		}
	}

	@Override
	public int requiredSpaceForUpdate(int pos, byte[] key, byte[] value,
			boolean compressed) {
		if ((pos < 0) || (pos >= getRecordCount() + 1)) {
			throw new IllegalArgumentException(String.format(
					"Invalid pos number: %s", pos));
		}

		if (compressed) {
			int newLength = calcRequiredSpaceUpdateCompressed(pos, key, value);
			int offset = getHeaderOffset(pos);
			int oldLength = calcLengthFromEntry(offset);

			return (newLength - oldLength);
		} else {
			int newLength = calcRequiredSpaceUpdateUncompressed(key, value);
			int offset = getHeaderOffset(pos);
			int oldLength = calcLengthFromEntry(offset);

			return (newLength - oldLength);
		}
	}

	private int calcDiffValueLength(byte[] previousKey, int previousKeyLength,
			byte[] key, int keyLength) {
		int diffKeyPos = 0;

		while ((diffKeyPos < previousKeyLength) && (diffKeyPos < keyLength)
				&& (previousKey[diffKeyPos] == key[diffKeyPos]))
			diffKeyPos++;

		return (keyLength - diffKeyPos);
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

	private int calcRequiredUncompressedValueSpace(byte[] value) {
		int length = 0;

		if (value != null) {
			int descriptorLength = 1;
			int valueLength = value.length;
			int encodedValueLength = valueLength + 1;

			if (encodedValueLength >= 255) {
				descriptorLength += 2;
			}

			length += descriptorLength;
			length += valueLength;
		} else {
			length += 1;
		}

		return length;
	}

	private int calcRequiredSpaceInsertCompressed(int pos, byte[] insertKey,
			byte[] insertValue) {
		byte[] previousKey = (pos > 0) ? getKey(pos - 1) : null;
		int insertKeyLength = calcRequiredCompressedValueSpace(previousKey,
				insertKey);
		int insertValueLength = calcRequiredUncompressedValueSpace(insertValue);
		int length = HEADER_SIZE + insertKeyLength + insertValueLength;

		if (pos < getRecordCount()) {
			byte[] nextKey = getKey(pos);

			int oldNextKeyLength = calcRequiredCompressedValueSpace(
					previousKey, nextKey);
			int newNextKeyLength = calcRequiredCompressedValueSpace(insertKey,
					nextKey);

			length += (newNextKeyLength - oldNextKeyLength);
		}

		return length;
	}

	private int calcRequiredSpaceUpdateCompressed(int pos, byte[] insertKey,
			byte[] insertValue) {
		byte[] previousKey = (pos > 0) ? getKey(pos - 1) : null;
		int insertKeyLength = calcRequiredCompressedValueSpace(previousKey,
				insertKey);
		int insertValueLength = calcRequiredUncompressedValueSpace(insertValue);
		int length = HEADER_SIZE + insertKeyLength + insertValueLength;

		if (pos < getRecordCount() - 1) {
			byte[] currentKey = getKey(pos);
			byte[] nextKey = getKey(pos + 1);

			int oldNextKeyLength = calcRequiredCompressedValueSpace(currentKey,
					nextKey);
			int newNextKeyLength = calcRequiredCompressedValueSpace(insertKey,
					nextKey);

			length += (newNextKeyLength - oldNextKeyLength);
		}

		return length;
	}

	private int calcRequiredSpaceInsertUncompressed(byte[] insertKey,
			byte[] insertValue) {
		int insertKeyLength = calcRequiredUncompressedValueSpace(insertKey);
		int insertValueLength = calcRequiredUncompressedValueSpace(insertValue);

		return HEADER_SIZE + insertKeyLength + insertValueLength;
	}

	private int calcRequiredSpaceUpdateUncompressed(byte[] insertKey,
			byte[] insertValue) {
		int insertKeyLength = calcRequiredUncompressedValueSpace(insertKey);
		int insertValueLength = calcRequiredUncompressedValueSpace(insertValue);

		return HEADER_SIZE + insertKeyLength + insertValueLength;
	}

	@Override
	public String toString() {
		return handle.toString();
	}
}