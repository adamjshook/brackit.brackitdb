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
package org.brackit.server.store.page.bracket;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.Handle;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.page.BasePage;

/**
 * Page class that is used for the leaf pages of the document index. DeweyIDs of
 * the contained nodes are stored prefix-compressed, i.e. they are represented
 * by one or more consecutively stored {@link BracketKey BracketKeys}.
 * 
 * <p>
 * The overall format of a BracketPage can be divided into three parts:
 * <table>
 * <tr>
 * <td>1.</td>
 * <td><b>Header</b> (containing only fixed-length fields)</td>
 * </tr>
 * <tr>
 * <td>2.</td>
 * <td><b>Key Area</b> (containing all BracketKeys and internal data references)
 * </td>
 * </tr>
 * <tr>
 * <td>3.</td>
 * <td><b>Data Area</b> (containing all data records)</td>
 * </tr>
 * </table>
 * </p>
 * 
 * <p>
 * <b>Header layout:</b> <br/>
 * <table border="1">
 * <tr>
 * <th>Field</th>
 * <th>Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>byte 0-1</td>
 * <td>context data offset</td>
 * <td>offset pointing to a special data record that can be used by the page
 * context to store variable length data (e.g. the high key)</td>
 * </tr>
 * <tr>
 * <td>byte 2-3</td>
 * <td>key area end offset</td>
 * <td>offset pointing to the first byte that is not used for the key area
 * anymore</td>
 * </tr>
 * <tr>
 * <td>byte 4</td>
 * <td>low key type</td>
 * <td>{@link BracketKey.Type Type} of the first node stored in this page</td>
 * </tr>
 * <tr>
 * <td>byte 5</td>
 * <td>low key length</td>
 * <td>Length of the low key (which is stored as uncompressed
 * {@link org.brackit.server.node.XTCdeweyID DeweyID})</td>
 * </tr>
 * </table>
 * </p>
 * 
 * <p>
 * <b>Key area layout:</b>
 * <table>
 * <tr>
 * <td>1.</td>
 * <td>Low key (variable length; length specified in header)</td>
 * </tr>
 * <tr>
 * <td>2.</td>
 * <td>Low key data reference (if present; depends on low key type)</td>
 * </tr>
 * <tr>
 * <td>3.</td>
 * <td>Bracket keys for the remaining nodes. Nodes containing a data record
 * (non-empty elements, attributes and text nodes) have an internal data
 * reference stored directly after their bracket key, pointing to the beginning
 * of their data record.</td>
 * </tr>
 * </table>
 * </p>
 * 
 * <p>
 * <b>Data area layout:</b><br/>
 * The remaining space (between key area and the end of the page) is used to
 * store data records for non-element nodes. In order to keep the costs for
 * inserts/deletions low, these data records do not need to be stored densely.
 * Instead, the data area is only defragmented if necessary.
 * </p>
 * 
 * @author Martin Hiller
 * 
 */
public class BracketPage extends BasePage {

	private static final int RESERVED_FOR_CONTEXT = 0;
	private static final int CONTEXT_DATA_FIELD_NO = BASE_PAGE_START_OFFSET
			+ RESERVED_FOR_CONTEXT;
	private static final int KEY_AREA_END_FIELD_NO = CONTEXT_DATA_FIELD_NO + 2;
	private static final int LOW_KEY_TYPE_FIELD_NO = KEY_AREA_END_FIELD_NO + 2;
	private static final int LOW_KEY_LENGTH_FIELD_NO = LOW_KEY_TYPE_FIELD_NO + 1;
	private static final int LOW_KEY_START_FIELD_NO = LOW_KEY_TYPE_FIELD_NO + 1;

	// special key offsets
	public static final int LOW_KEY_OFFSET = -1;
	public static final int BEFORE_LOW_KEY_OFFSET = -2;
	public static final int KEY_AREA_END_OFFSET = -3;

	// special return values for insertions
	public static final int INSERTION_NO_SPACE = -2;
	public static final int INSERTION_DUPLICATE = -3;

	private final byte[] page;

	/**
	 * Creates a bracket page.
	 * 
	 * @param buffer
	 * @param pageHandle
	 * @param reservedForContext
	 *            specifies how many bytes in the header need to be reserved for
	 *            the upper layer (-> page context)
	 */
	public BracketPage(Buffer buffer, Handle pageHandle, int reservedForContext) {
		super(buffer, pageHandle, LOW_KEY_START_FIELD_NO
				- BASE_PAGE_START_OFFSET);
		this.page = pageHandle.page;
	}

	/**
	 * Formats/initializes the BracketPage.
	 * 
	 * @param basePageID
	 */
	public void format(PageID basePageID) {
		setBasePageID(basePageID);
		clear();
	}

	/**
	 * Removes all nodes from this page. Higher layer information like the high
	 * key is however preserved.
	 */
	@Override
	public void clear() {

		// TODO: buffer context data

		// // buffer highKey
		// XTCdeweyID highKey = getHighKey();

		super.clear();
		setFreeSpaceOffset(handle.getPageSize());
		page[LOW_KEY_LENGTH_FIELD_NO] = (byte) 0;
		setContextDataOffset(0);
		setKeyAreaEndOffset(getKeyAreaStartOffset());

		// if (highKey != null) {
		// setHighKey(highKey);
		// }
	}

	private int getKeyAreaStartOffset() {
		return LOW_KEY_START_FIELD_NO + (page[LOW_KEY_LENGTH_FIELD_NO] & 255);
	}

	private int getContextDataOffset() {
		return ((page[CONTEXT_DATA_FIELD_NO] & 255) << 8)
				| (page[CONTEXT_DATA_FIELD_NO + 1] & 255);
	}

	private void setContextDataOffset(int keyAreaEndOffset) {
		page[CONTEXT_DATA_FIELD_NO] = (byte) ((keyAreaEndOffset >> 8) & 255);
		page[CONTEXT_DATA_FIELD_NO + 1] = (byte) (keyAreaEndOffset & 255);
	}

	private int getKeyAreaEndOffset() {
		return ((page[KEY_AREA_END_FIELD_NO] & 255) << 8)
				| (page[KEY_AREA_END_FIELD_NO + 1] & 255);
	}

	private void setKeyAreaEndOffset(int keyAreaEndOffset) {
		page[KEY_AREA_END_FIELD_NO] = (byte) ((keyAreaEndOffset >> 8) & 255);
		page[KEY_AREA_END_FIELD_NO + 1] = (byte) (keyAreaEndOffset & 255);
	}

	/**
	 * Increases the KeyAreaEndOffset by the given value (negative values for
	 * decreasing).
	 * 
	 * @param increase
	 *            the increase value
	 */
	private void increaseKeyAreaEndOffset(int increase) {
		setKeyAreaEndOffset(getKeyAreaEndOffset() + increase);
	}

	/**
	 * Returns the low key (i.e. the DeweyID of the first node stored in this
	 * page). If this page is empty, null will be returned.
	 * 
	 * @return the low key or null, if the page is empty
	 */
	public XTCdeweyID getLowKey() {

		if (getRecordCount() == 0) {
			return null;
		}

		int lowIDLength = page[LOW_KEY_LENGTH_FIELD_NO] & 255;

		// load DocID
		DocID docID = DocID.fromBytes(page, BASE_PAGE_NO_OFFSET);

		// load lowID from page
		byte[] lowIDBytes = new byte[lowIDLength];
		System.arraycopy(page, LOW_KEY_START_FIELD_NO, lowIDBytes, 0,
				lowIDLength);

		return new XTCdeweyID(docID, lowIDBytes);
	}

	/**
	 * Returns the encoded low key (i.e. the DeweyID of the first node stored in
	 * this page). If this page is empty, null will be returned.
	 * 
	 * @return the encoded low key (as byte array) or null, if the page is empty
	 */
	public byte[] getLowKeyBytes() {

		if (getRecordCount() == 0) {
			return null;
		}

		int lowIDLength = page[LOW_KEY_LENGTH_FIELD_NO] & 255;

		// load lowID from page
		byte[] lowIDBytes = new byte[lowIDLength];
		System.arraycopy(page, LOW_KEY_START_FIELD_NO, lowIDBytes, 0,
				lowIDLength);

		return lowIDBytes;
	}

	private BracketKey.Type getLowKeyType() {
		return BracketKey.Type.getByPhysicalValue(page[LOW_KEY_TYPE_FIELD_NO]);
	}

	/**
	 * Returns the offset where the next value reference is located.
	 * 
	 * @param keyOffset
	 *            the key offset to start the search
	 * @return offset of next value reference or 0, if the given key offset is
	 *         not pointing to the key area
	 */
	private int getNextValueRefOffset(int keyOffset) {

		// if key offset belongs to lowID
		if (keyOffset == LOW_KEY_OFFSET) {
			keyOffset = getKeyAreaStartOffset();
			if (getLowKeyType().getDataReferenceLength() > 0) {
				return keyOffset;
			}
		}

		// if key offset is pointing to the end of the key area
		if (keyOffset >= getKeyAreaEndOffset()) {
			return 0;
		}

		while (!BracketKey.hasDataReference(page, keyOffset)) {
			keyOffset += BracketKey.PHYSICAL_LENGTH;
		}
		keyOffset += BracketKey.PHYSICAL_LENGTH;

		return keyOffset;
	}

	/**
	 * Returns the length of the value beginning at 'valueOffset'.
	 * 
	 * @param valueOffset
	 *            the offset where the value starts
	 * @param inclusiveLengthField
	 *            true if the length of the length field shall be added to the
	 *            result
	 * @return value length (in bytes)
	 */
	private int getValueLength(int valueOffset,
			final boolean inclusiveLengthField) {
		return getValueLength(valueOffset, inclusiveLengthField, page);
	}

	/**
	 * Returns the length of the value beginning at 'valueOffset'.
	 * 
	 * @param valueOffset
	 *            the offset where the value starts
	 * @param inclusiveLengthField
	 *            true if the length of the length field shall be added to the
	 *            result
	 * @param storage
	 *            the byte array to read from (usually the page array)
	 * @return value length (in bytes)
	 */
	protected static int getValueLength(int valueOffset,
			final boolean inclusiveLengthField, byte[] storage) {

		int result = 0;

		int valueLength = storage[valueOffset++] & 255;
		if (inclusiveLengthField) {
			result++;
		}

		if (valueLength == 255) {

			int byte1 = storage[valueOffset++] & 255;
			int byte2 = storage[valueOffset++] & 255;

			if (byte1 == 255 && byte2 == 255) {
				valueLength = PageID.getSize();
			} else {
				valueLength = (byte1 << 8) | byte2;
			}

			if (inclusiveLengthField) {
				result += 2;
			}

		}

		result += valueLength;

		return result;
	}

	/**
	 * Returns the value for the key beginning at 'keyOffset'.
	 * 
	 * @param keyOffset
	 *            the offset where the key starts
	 * @return value
	 */
	public BracketValue getValue(int keyOffset) {

		if (keyOffset == BEFORE_LOW_KEY_OFFSET) {
			return new BracketValue(false, null);
		}

		// look for next value reference
		int valueRefOffset = getNextValueRefOffset(keyOffset);

		// load value part
		byte[][] valuePart = getValueParts(valueRefOffset);

		// check if externalized
		boolean externalized = (valuePart[0].length == 3
				&& valuePart[0][1] == (byte) 255 && valuePart[0][2] == (byte) 255);

		BracketValue result = new BracketValue(externalized, valuePart[1]);

		return result;
	}

	/**
	 * Returns the value parts (valueLength field and value field) for the value
	 * referenced at 'valueRefOffset'.
	 * 
	 * @param valueRefOffset
	 *            the offset where the reference to the value starts
	 * @return value part (first element: valueLength field, second element:
	 *         value field)
	 */
	private byte[][] getValueParts(int valueRefOffset) {

		byte[][] result = new byte[2][];

		// jump to offset where the value is located
		int valueOffset = getValueOffset(valueRefOffset);
		int currentOffset = valueOffset;

		// determine value length
		int valueLength = page[currentOffset++] & 255;
		int valueLengthLength = 1;

		if (valueLength == 255) {

			int byte1 = page[currentOffset++] & 255;
			int byte2 = page[currentOffset++] & 255;

			if (byte1 == 255 && byte2 == 255) {
				valueLength = PageID.getSize();
			} else {
				valueLength = (byte1 << 8) | byte2;
			}

			valueLengthLength += 2;
		}

		// copy value length field
		result[0] = new byte[valueLengthLength];
		System.arraycopy(page, valueOffset, result[0], 0, valueLengthLength);

		// copy value field
		result[1] = new byte[valueLength];
		System.arraycopy(page, currentOffset, result[1], 0, valueLength);

		return result;
	}

	/**
	 * Returns the value offset of the given value reference offset.
	 * 
	 * @param valueRefOffset
	 *            the offset where the value reference is stored
	 * @return the offset where the value (data record) starts
	 */
	private int getValueOffset(int valueRefOffset) {
		return ((page[valueRefOffset] & 255) << 8)
				| (page[valueRefOffset + 1] & 255);
	}
	
	/**
	 * Generates the value length field for a given value. 
	 * @param value the value to generate the length field for
	 * @param externalized indicates whether the value is an external PageID
	 * @return the value length field
	 */
	protected static byte[] getValueLengthField(byte[] value, boolean externalized) {
		
		byte[] valueLengthField = null;
		
		if (externalized) {
			valueLengthField = new byte[]{(byte) 255, (byte) 255, (byte) 255};
		} else {	
			if (value.length < 255) {
				valueLengthField = new byte[1];
				valueLengthField[0] = (byte) value.length;
			} else {
				valueLengthField = new byte[3];
				valueLengthField[0] = (byte) 255;
				valueLengthField[1] = (byte) ((value.length >> 8) & 255);
				valueLengthField[2] = (byte) ((value.length) & 255);
			}	
		}
		
		return valueLengthField;
	}
}
