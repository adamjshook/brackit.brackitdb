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
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.server.node.el.index.ElPlaceHolderHelper;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.page.LeafBPContext;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.bracket.navigation.NavigationProfiles;
import org.brackit.server.store.page.bracket.navigation.NavigationProperties;
import org.brackit.server.store.page.bracket.navigation.NavigationProperties.NavigationTarget;
import org.brackit.server.store.page.bracket.navigation.NavigationResult;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;

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

	private static final int RESERVED_FOR_CONTEXT = LeafBPContext.RESERVED_SIZE;
	private static final int CONTEXT_DATA_FIELD_NO = BASE_PAGE_START_OFFSET
			+ RESERVED_FOR_CONTEXT;
	private static final int KEY_AREA_END_FIELD_NO = CONTEXT_DATA_FIELD_NO + 2;
	private static final int LOW_KEY_TYPE_FIELD_NO = KEY_AREA_END_FIELD_NO + 2;
	private static final int LOW_KEY_LENGTH_FIELD_NO = LOW_KEY_TYPE_FIELD_NO + 1;
	private static final int LOW_KEY_START_FIELD_NO = LOW_KEY_LENGTH_FIELD_NO + 1;

	// special key offsets
	public static final int LOW_KEY_OFFSET = -1;
	public static final int BEFORE_LOW_KEY_OFFSET = -2;
	public static final int KEY_AREA_END_OFFSET = -3;

	// special return values for insertions
	public static final int INSERTION_NO_SPACE = -2;
	public static final int INSERTION_DUPLICATE = -3;

	private static ElPlaceHolderHelper placeHolderHelper = new ElRecordAccess();

	private final byte[] page;

	private class InternalValue {
		public int totalValueLength;
		public boolean externalized;
		public byte[] value;
	}

	/**
	 * Creates a bracket page.
	 * 
	 * @param buffer
	 * @param pageHandle
	 */
	public BracketPage(Buffer buffer, Handle pageHandle) {
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

		// buffer context data
		byte[] contextData = getContextData();

		super.clear();
		setFreeSpaceOffset(handle.getPageSize());
		page[LOW_KEY_LENGTH_FIELD_NO] = (byte) 0;
		setContextDataOffset(0);
		setKeyAreaEndOffset(getKeyAreaStartOffset());

		if (contextData != null) {
			setContextData(contextData);
		}
	}

	private int getKeyAreaStartOffset() {
		return LOW_KEY_START_FIELD_NO + (page[LOW_KEY_LENGTH_FIELD_NO] & 255);
	}

	private int getContextDataOffset() {
		return ((page[CONTEXT_DATA_FIELD_NO] & 255) << 8)
				| (page[CONTEXT_DATA_FIELD_NO + 1] & 255);
	}

	private void setContextDataOffset(int contextDataOffset) {
		page[CONTEXT_DATA_FIELD_NO] = (byte) ((contextDataOffset >> 8) & 255);
		page[CONTEXT_DATA_FIELD_NO + 1] = (byte) (contextDataOffset & 255);
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
			if (getLowKeyType().hasDataReference()) {
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
		byte[][] valuePart = getValueParts(getValueOffset(valueRefOffset));

		// check if externalized
		boolean externalized = (valuePart[0].length == 3
				&& valuePart[0][1] == (byte) 255 && valuePart[0][2] == (byte) 255);

		BracketValue result = new BracketValue(externalized, valuePart[1]);

		return result;
	}

	/**
	 * Returns the value parts (valueLength field and value field) for the value
	 * at 'valueOffset'.
	 * 
	 * @param valueOffset
	 *            the offset where the value starts
	 * @return value part (first element: valueLength field, second element:
	 *         value field)
	 */
	private byte[][] getValueParts(int valueOffset) {

		byte[][] result = new byte[2][];

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
	 * 
	 * @param value
	 *            the value to generate the length field for
	 * @param externalized
	 *            indicates whether the value is an external PageID
	 * @return the value length field
	 */
	protected static byte[] getValueLengthField(byte[] value,
			boolean externalized) {

		byte[] valueLengthField = null;

		if (externalized) {
			valueLengthField = new byte[] { (byte) 255, (byte) 255, (byte) 255 };
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

	/**
	 * Loads the value stored at 'valueOffset' into an internal value
	 * representation.
	 * 
	 * @param val
	 *            the object to store the value into
	 * @param valueOffset
	 *            the offset where the stored value begins
	 */
	private void loadInternalValue(InternalValue val, int valueOffset) {

		val.totalValueLength = 0;
		val.externalized = false;
		val.value = null;

		int valueLength = page[valueOffset++] & 255;
		val.totalValueLength++;

		if (valueLength == 255) {

			int byte1 = page[valueOffset++] & 255;
			int byte2 = page[valueOffset++] & 255;

			if (byte1 == 255 && byte2 == 255) {
				val.externalized = true;
				valueLength = PageID.getSize();
			} else {
				valueLength = (byte1 << 8) | byte2;
			}

			val.totalValueLength += 2;
		}

		val.totalValueLength += valueLength;

		val.value = new byte[valueLength];
		System.arraycopy(page, valueOffset, val.value, 0, valueLength);
	}

	/**
	 * Generic navigation that is used by most of the navigation operations. If
	 * successful, the current DeweyID buffer will contain the DeweyID of the
	 * found node.
	 * 
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @param keyPos
	 *            the offset where the bracket key of the reference node starts
	 * @param prop
	 *            the navigation properties
	 * @param considerOverflowNodes
	 *            indicates whether overflow nodes/keys should also be
	 *            considered as qualifying nodes
	 * @return the navigation result
	 */
	private NavigationResult navigateGeneric(
			final DeweyIDBuffer currentDeweyID, int keyPos,
			final NavigationProperties prop, final boolean considerOverflowNodes) {

		// initialize levelDiff
		int levelDiff = 0;

		// initialize result object
		NavigationResult result = new NavigationResult();
		result.status = NavigationStatus.AFTER_LAST;
		result.breakConditionFulfilled = false;

		// currently processed bracket key
		BracketKey currentKey = new BracketKey();
		BracketKey.Type currentKeyType = null;

		// check if lowKey qualifies
		if (keyPos == BEFORE_LOW_KEY_OFFSET) {
			keyPos = LOW_KEY_OFFSET;

			// backup reference DeweyID
			currentDeweyID.backup();

			currentKeyType = getLowKeyType();
			currentDeweyID.setTo(getLowKey());
			if (prop.ignoreAttributes) {
				// set DeweyID to related element DeweyID
				currentDeweyID.setAttributeToRelatedElement();
			}
			levelDiff -= currentDeweyID.getLevelDifferenceTo(currentDeweyID
					.getBackupAsSimpleDeweyID());
			currentDeweyID.resetBackup();

			// if lowKey is not an attribute
			if (!prop.ignoreAttributes
					|| currentKeyType != BracketKey.Type.ATTRIBUTE) {

				// check break- and successCondition
				if (prop.breakCondition.checkCondition(levelDiff,
						currentDeweyID, currentKeyType, keyPos)) {
					result.status = NavigationStatus.NOT_EXISTENT;
					result.breakConditionFulfilled = true;
					return result;
				} else if (prop.successCondition.checkCondition(levelDiff,
						currentDeweyID, currentKeyType, keyPos)) {
					// current node has qualified
					result.status = NavigationStatus.FOUND;
					result.keyOffset = keyPos;
					result.keyType = currentKeyType;
					result.levelDiff = levelDiff;

					if (prop.target == NavigationTarget.FIRST) {
						// first node that fulfills the success condition found
						return result;
					} else {
						// backup DeweyID and continue searching
						currentDeweyID.backup();
					}
				}
			}
		}

		// determine the key offset for the next bracket key
		if (keyPos == LOW_KEY_OFFSET) {
			keyPos = getKeyAreaStartOffset()
					+ getLowKeyType().getDataReferenceLength();
		} else {
			currentKey.load(page, keyPos);
			keyPos += BracketKey.PHYSICAL_LENGTH
					+ currentKey.getDataReferenceLength();
		}

		// navigation over key storage
		int keyAreaEndOffset = getKeyAreaEndOffset();
		while (keyPos < keyAreaEndOffset) {

			// load next key from keyStorage
			currentKey.load(page, keyPos);
			currentKeyType = currentKey.type;

			if (currentKeyType != BracketKey.Type.ATTRIBUTE
					|| !prop.ignoreAttributes) {

				// refresh DeweyID
				currentDeweyID.update(currentKey, prop.ignoreAttributes);

				// decrease level difference
				levelDiff -= currentKey.roundBrackets;

				// increment level difference
				if (currentKeyType.opensNewSubtree()) {
					// increment level difference (-> opening bracket at the end
					// of the bracket key)
					levelDiff++;
				}

				if (currentKeyType != BracketKey.Type.OVERFLOW
						|| considerOverflowNodes) {

					// check break condition
					if (prop.breakCondition.checkCondition(levelDiff,
							currentDeweyID, currentKeyType, keyPos)) {
						if (result.status != NavigationStatus.FOUND) {
							result.status = NavigationStatus.NOT_EXISTENT;
						}
						result.breakConditionFulfilled = true;
						break;
					}

					// check success condition
					if (prop.successCondition.checkCondition(levelDiff,
							currentDeweyID, currentKeyType, keyPos)) {
						// current node has qualified
						result.status = NavigationStatus.FOUND;
						result.keyOffset = keyPos;
						result.keyType = currentKeyType;
						result.levelDiff = levelDiff;

						if (prop.target == NavigationTarget.FIRST) {
							// first node that fulfills the success condition
							// found
							break;
						} else {
							// backup DeweyID and continue searching
							currentDeweyID.backup();
						}
					}
				}
			}

			// set keyPos to the offset of the next key
			keyPos += BracketKey.PHYSICAL_LENGTH
					+ currentKeyType.getDataReferenceLength();
		}

		if (prop.target == NavigationTarget.LAST
				&& result.status == NavigationStatus.FOUND) {
			// restore last found node
			currentDeweyID.restore(false);
		} else {
			currentDeweyID.resetBackup();
		}

		return result;
	}

	/**
	 * Inserts a new node (incl. required ancestors) after the current one. If
	 * successful, the currentDeweyID buffer will contain the new node's
	 * DeweyID.
	 * 
	 * @param key
	 *            DeweyID of the new key
	 * @param value
	 *            the value to insert
	 * @param ancestorsToInsert
	 *            number of ancestors to insert implicitly
	 * @param externalized
	 *            indicates whether the value is an external PageID
	 * @param currentOffset
	 *            offset of the current node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the current node
	 * @return offset of the newly inserted node or an errorcode (if new node is
	 *         a duplicate or there is not enough space)
	 */
	public int insertAfter(XTCdeweyID key, byte[] value, int ancestorsToInsert,
			boolean externalized, int currentOffset,
			DeweyIDBuffer currentDeweyID) {

		byte[] valueLengthField = getValueLengthField(value, externalized);

		int newKeyOffset = 0;

		if (currentOffset == BEFORE_LOW_KEY_OFFSET) {
			// new node becomes the lowID
			newKeyOffset = insertLowIDRecord(key, valueLengthField, value,
					ancestorsToInsert);
		} else {
			newKeyOffset = insertNormalRecord(key, valueLengthField, value,
					currentOffset, currentDeweyID);
		}

		// set current DeweyID to inserted key
		if (newKeyOffset != INSERTION_NO_SPACE) {
			currentDeweyID.setTo(key);
		}

		return newKeyOffset;
	}

	private int insertLowIDRecord(XTCdeweyID key, byte[] valueLengthField,
			byte[] value, int ancestorsToInsert) {

		// determine required space
		int requiredSpace = BracketKey.DATA_REF_LENGTH
				+ valueLengthField.length + value.length;

		// determine size of the new lowID
		XTCdeweyID lowID = getAncestorKey(key, ancestorsToInsert);
		byte[] physicalLowID = lowID.toBytes();
		requiredSpace += physicalLowID.length;

		// determine beforeKeys (bracket keys between new LowID and the key to
		// insert)
		byte[] beforeKeys = null;
		if (ancestorsToInsert > 0) {
			beforeKeys = BracketKey.generateBracketKeys(lowID, key);
			requiredSpace += beforeKeys.length;
		}

		BracketKey.Type keyType = key.isAttribute() ? BracketKey.Type.ATTRIBUTE
				: BracketKey.Type.DATA;
		BracketKey.Type lowIDType = (ancestorsToInsert > 0) ? BracketKey.Type.NODATA
				: keyType;

		byte[] afterKeys = null;
		if (getRecordCount() > 0) {
			// there is already a low ID stored

			// calculate keys between new key and current low key
			afterKeys = BracketKey.generateBracketKeys(key, getLowKey());

			// adjust required space
			requiredSpace += afterKeys.length
					- (page[LOW_KEY_LENGTH_FIELD_NO] & 255);

			// determine whether new low ID needs a data reference
			if (key.isPrefixOf(getLowKey())) {
				keyType = BracketKey.Type.NODATA;
				requiredSpace -= (BracketKey.DATA_REF_LENGTH
						+ valueLengthField.length + value.length);
			}
		}

		// allocate required space
		int reservedSpace = 2 * key.toBytes().length;
		if (!allocateRequiredSpace(requiredSpace, reservedSpace)) {
			return INSERTION_NO_SPACE;
		}

		// check whether a defragmentation is needed
		if (requiredSpace > getFreeSpaceOffset() - getKeyAreaEndOffset()) {
			// defragmentation
			defragment(0);
		}

		// increase record counter
		setEntryCount((short) (getRecordCount() + 1 + ancestorsToInsert));

		// buffer key area
		byte[] bufferedKeys = null;
		if (afterKeys != null) {
			bufferedKeys = new byte[getKeyAreaEndOffset()
					- getKeyAreaStartOffset()];
			for (int i = 0; i < bufferedKeys.length; i++) {
				bufferedKeys[i] = page[getKeyAreaStartOffset() + i];
			}
		}

		// buffer old lowIDType
		BracketKey.Type oldLowIDType = getLowKeyType();

		// write lowID
		initializeLowKey(lowIDType, physicalLowID, lowID);

		int returnOffset = LOW_KEY_OFFSET;
		int currentOffset = getKeyAreaStartOffset();

		// write beforeKeys if necessary
		if (beforeKeys != null) {
			System.arraycopy(beforeKeys, 0, page, currentOffset,
					beforeKeys.length);
			currentOffset += beforeKeys.length;
			returnOffset = currentOffset - BracketKey.PHYSICAL_LENGTH;
		}

		// store value and write value reference
		if (keyType.hasDataReference()) {
			writeValueReference(currentOffset,
					storeValue(valueLengthField, value));
			currentOffset += BracketKey.DATA_REF_LENGTH;
		}

		// write afterKeys
		if (afterKeys != null) {
			// count new records between new lowKey and old lowKey
			int numberRecords = -1;
			for (int i = 0; i < afterKeys.length; i += BracketKey.PHYSICAL_LENGTH) {
				if (BracketKey.loadType(afterKeys, i) != BracketKey.Type.OVERFLOW) {
					numberRecords++;
				}
			}

			// write keys
			System.arraycopy(afterKeys, 0, page, currentOffset,
					afterKeys.length);
			currentOffset += afterKeys.length;

			// change record count
			setEntryCount((short) (getRecordCount() + numberRecords));

			// change last bracket key type
			BracketKey.updateType(oldLowIDType, page, currentOffset
					- BracketKey.PHYSICAL_LENGTH);

			// write back buffered keys
			System.arraycopy(bufferedKeys, 0, page, currentOffset,
					bufferedKeys.length);

			currentOffset += bufferedKeys.length;
		}

		// adjust key area end offset
		setKeyAreaEndOffset(currentOffset);

		return returnOffset;
	}

	private int insertNormalRecord(XTCdeweyID key, byte[] valueLengthField,
			byte[] value, int currentOffset, DeweyIDBuffer currentDeweyID) {

		// determine required space
		int requiredSpace = BracketKey.DATA_REF_LENGTH
				+ valueLengthField.length + value.length;

		// check whether previous data record can be removed
		boolean removeDataRecord = false;
		boolean useRemovedRecordSpace = false;
		int currentValueOffset = 0;
		int currentRecordLength = 0;
		int currentValueRefOffset = 0;

		boolean currentNodeIsLowKey = (currentOffset == LOW_KEY_OFFSET);

		// read current bracket key type
		BracketKey.Type currentKeyType = (currentNodeIsLowKey ? getLowKeyType()
				: BracketKey.loadType(page, currentOffset));

		// check bracket key type of current node
		if (currentKeyType == BracketKey.Type.DATA
				&& currentDeweyID.isPrefixOf(key)) {

			// data record can be removed
			removeDataRecord = true;

			// determine current record length
			currentValueRefOffset = currentNodeIsLowKey ? getKeyAreaStartOffset()
					: currentOffset + BracketKey.PHYSICAL_LENGTH;
			currentValueOffset = getValueOffset(currentValueRefOffset);
			currentRecordLength = getValueLength(currentValueOffset, true);

			if (currentRecordLength >= valueLengthField.length + value.length) {
				// the new gap in the value area can be used to store the new
				// value
				useRemovedRecordSpace = true;
			}

			// adjust required space
			requiredSpace -= (BracketKey.DATA_REF_LENGTH + currentRecordLength);
		}

		// calculate bracket key between current node and the new one
		byte[] beforeKeys = BracketKey.generateBracketKeys(currentDeweyID, key);
		// adjust required space
		requiredSpace += beforeKeys.length;

		// determine offset for the next key
		int nextKeyOffset = (currentNodeIsLowKey ? getKeyAreaStartOffset()
				: currentOffset + BracketKey.PHYSICAL_LENGTH)
				+ currentKeyType.getDataReferenceLength();

		// calculate key between new node and next node
		BracketKey afterKey = null;
		if (nextKeyOffset < getKeyAreaEndOffset()) {
			// determine next node's DeweyID
			currentDeweyID.backup();
			currentDeweyID.update(BracketKey.loadNew(page, nextKeyOffset),
					false);

			afterKey = BracketKey.generateBracketKey(key, currentDeweyID);
			currentDeweyID.restore(false);
		}

		// allocate required space
		int reservedSpace = 2 * key.toBytes().length;
		if (!allocateRequiredSpace(requiredSpace, reservedSpace)) {
			return INSERTION_NO_SPACE;
		}

		// check whether a defragmentation is needed
		int reqSpaceBetweenKeyAndValueArea = requiredSpace
				+ (removeDataRecord ? currentRecordLength : 0)
				- (useRemovedRecordSpace ? (valueLengthField.length + value.length)
						: 0);
		if (reqSpaceBetweenKeyAndValueArea > getFreeSpaceOffset()
				- getKeyAreaEndOffset()) {
			// defragmentation
			defragment(removeDataRecord ? currentValueRefOffset : 0);
			useRemovedRecordSpace = false;
		}

		// store value
		int valueOffset = 0;
		if (removeDataRecord && useRemovedRecordSpace) {
			valueOffset = storeValue(valueLengthField, value,
					currentValueOffset);
		} else {
			valueOffset = storeValue(valueLengthField, value);
		}

		// change current node's key type to NODATA, if necessary
		if (removeDataRecord) {
			currentKeyType = BracketKey.Type.NODATA;
			if (currentNodeIsLowKey) {
				setLowKeyType(currentKeyType);
			} else {
				BracketKey.updateType(currentKeyType, page, currentOffset);
			}
		}

		// insert the new key, write the value reference, update and shift the
		// following keys
		return insertKeys(beforeKeys, afterKey, nextKeyOffset, valueOffset,
				removeDataRecord);
	}

	/**
	 * Returns the ancestor's DeweyID.
	 * 
	 * @param key
	 *            the key to insert
	 * @param ancestorsToInsert
	 *            the number of ancestors to be inserted implicitly
	 * @return the ancestor's DeweyID
	 */
	public static XTCdeweyID getAncestorKey(XTCdeweyID key,
			int ancestorsToInsert) {
		return (ancestorsToInsert == 0) ? key : key.getAncestor(key.getLevel()
				- ancestorsToInsert - (key.isAttribute() ? 1 : 0));
	}

	/**
	 * Checks whether there is enough free space and then allocates the required
	 * space.
	 * 
	 * @param requiredSpace
	 *            the space to allocate
	 * @param reservedSpace
	 *            space that is reserved (for later usage), but not allocated
	 *            yet
	 * @return true if the allocation succeeded
	 */
	private boolean allocateRequiredSpace(int requiredSpace, int reservedSpace) {

		if (requiredSpace == 0) {
			return true;
		}

		// check free space
		if (requiredSpace + reservedSpace > getFreeSpace()) {
			return false;
		}

		// allocate needed space
		if (requiredSpace < 0) {
			freeSpace(-requiredSpace);
		} else {
			allocateSpace(requiredSpace);
		}

		return true;
	}

	/**
	 * Defragments the data area.
	 * 
	 * @param skipValueRefOffset
	 *            the value which is referenced at the 'skipValueRefOffset' will
	 *            not be defragmented and therefore will be lost after the
	 *            defragmentation
	 */
	private void defragment(int skipValueRefOffset) {

		if (getRecordCount() == 0) {
			return;
		}

		boolean skipOneRecord = (skipValueRefOffset != 0);

		// buffer page size
		int pageSize = handle.getPageSize();

		// buffer page free space pointer
		int oldFreeSpacePointer = getFreeSpaceOffset();

		// determine size of the value area in the page
		int valueAreaSize = pageSize - oldFreeSpacePointer;

		// allocate buffer for value area
		byte[] buffer = new byte[valueAreaSize];

		// fill buffer with value area
		System.arraycopy(page, oldFreeSpacePointer, buffer, 0, valueAreaSize);

		// fill the page from behind
		int newFreeSpacePointer = pageSize;

		// write context data, if present
		int contextDataOffset = getContextDataOffset();
		if (contextDataOffset != 0) {
			// jump to offset where the value is located in the buffer
			contextDataOffset -= oldFreeSpacePointer;
			// determine value length
			int contextDataLength = getValueLength(contextDataOffset, true,
					buffer);
			// write data incl. length field into the page
			newFreeSpacePointer -= contextDataLength;
			System.arraycopy(buffer, contextDataOffset, page,
					newFreeSpacePointer, contextDataLength);

			// refresh reference
			setContextDataOffset(newFreeSpacePointer);
		}

		// start with lowID
		int currentOffset = LOW_KEY_OFFSET;

		// iterate over all value references
		while ((currentOffset = getNextValueRefOffset(currentOffset)) != 0) {

			if (!skipOneRecord || skipValueRefOffset != currentOffset) {

				// jump to offset where the value is located in the buffer
				int valueOffset = getValueOffset(currentOffset)
						- oldFreeSpacePointer;

				// determine value length
				int valueLength = getValueLength(valueOffset, true, buffer);

				// write value incl. length field into the page
				newFreeSpacePointer -= valueLength;
				System.arraycopy(buffer, valueOffset, page,
						newFreeSpacePointer, valueLength);

				// refresh value reference
				writeValueReference(currentOffset, newFreeSpacePointer);

			} else {
				skipOneRecord = false;
			}

			// increase currentOffset
			currentOffset += BracketKey.DATA_REF_LENGTH;
		}

		// update free space offset
		setFreeSpaceOffset(newFreeSpacePointer);
	}

	/**
	 * Writes the value reference beginning at 'valueRefOffset' so that it
	 * refers to the value at 'valueOffset'.
	 * 
	 * @param valueRefOffset
	 *            the offset of the value reference
	 * @param valueOffset
	 *            the offset of the value
	 */
	private void writeValueReference(int valueRefOffset, int valueOffset) {
		page[valueRefOffset] = (byte) ((valueOffset >> 8) & 255);
		page[valueRefOffset + 1] = (byte) (valueOffset & 255);
	}

	/**
	 * Sets the lowID to the specified one and adjusts some fields in the page
	 * header.
	 * 
	 * @param lowIDType
	 *            the bracket key type of the lowID
	 * @param physicalLowID
	 *            the lowID in physical format
	 */
	private void initializeLowKey(BracketKey.Type lowIDType,
			byte[] physicalLowID, XTCdeweyID lowID) {

		// set lowID type
		setLowKeyType(lowIDType);

		// set lowID length
		if (physicalLowID.length > 255) {
			new RuntimeException("The physical format for the DeweyID " + lowID
					+ " is too long to be stored as LowKey.");
		}
		page[LOW_KEY_LENGTH_FIELD_NO] = (byte) physicalLowID.length;

		// write lowKey
		System.arraycopy(physicalLowID, 0, page, LOW_KEY_START_FIELD_NO,
				physicalLowID.length);

		// adjust end of key area
		setKeyAreaEndOffset(getKeyAreaStartOffset());
	}

	private void setLowKeyType(BracketKey.Type type) {
		page[LOW_KEY_TYPE_FIELD_NO] = (byte) (type.getPhysicalValue());
	}

	/**
	 * Stores the given value in front of the last stored value.
	 * 
	 * @param valueLengthField
	 *            the length field
	 * @param value
	 *            the actual value
	 * @return the offset where the stored value starts
	 */
	private int storeValue(byte[] valueLengthField, byte[] value) {

		int valuePointer = getFreeSpaceOffset() - value.length;
		System.arraycopy(value, 0, page, valuePointer, value.length);
		valuePointer -= valueLengthField.length;
		System.arraycopy(valueLengthField, 0, page, valuePointer,
				valueLengthField.length);

		// update free space pointer in page header
		setFreeSpaceOffset(valuePointer);

		return valuePointer;
	}

	/**
	 * Stores the given value at the specified value offset.
	 * 
	 * @param valueLengthField
	 *            the length field
	 * @param value
	 *            the actual value
	 * @param valueOffset
	 *            the offset where the value shall be stored
	 * @return the offset where the stored value starts
	 */
	private int storeValue(byte[] valueLengthField, byte[] value,
			int valueOffset) {

		System.arraycopy(valueLengthField, 0, page, valueOffset,
				valueLengthField.length);
		System.arraycopy(value, 0, page, valueOffset + valueLengthField.length,
				value.length);

		return valueOffset;
	}

	private int insertKeys(byte[] beforeKeys, BracketKey afterKey,
			int nextKeyOffset, int valueOffset, boolean previousValueRemoved) {

		// buffer remaining keys if necessary
		byte[] keyBuffer = null;
		if (afterKey != null) {
			keyBuffer = new byte[getKeyAreaEndOffset() - nextKeyOffset];
			// fill the buffer
			System.arraycopy(page, nextKeyOffset, keyBuffer, 0,
					keyBuffer.length);

			// update the afterKey in the buffer
			afterKey.store(keyBuffer, 0, true);
		}

		// determine startOffset
		int startOffset = nextKeyOffset
				- (previousValueRemoved ? BracketKey.DATA_REF_LENGTH : 0);
		int currentOffset = startOffset;

		// write beforeKeys
		System.arraycopy(beforeKeys, 0, page, currentOffset, beforeKeys.length);
		currentOffset += beforeKeys.length;

		// count newly inserted nodes
		for (int i = 0; i < beforeKeys.length; i += BracketKey.PHYSICAL_LENGTH) {
			if (BracketKey.loadType(beforeKeys, i) != BracketKey.Type.OVERFLOW) {
				addRecord();
			}
		}

		int newKeyOffset = currentOffset - BracketKey.PHYSICAL_LENGTH;

		// write value reference
		writeValueReference(currentOffset, valueOffset);
		currentOffset += BracketKey.DATA_REF_LENGTH;

		// copy back the key buffer content
		if (keyBuffer != null) {
			System.arraycopy(keyBuffer, 0, page, currentOffset,
					keyBuffer.length);
		}

		// adjust keyAreaEndOffset
		increaseKeyAreaEndOffset(currentOffset - nextKeyOffset);

		return newKeyOffset;
	}

	/**
	 * Inserts the given node at the correct position in page.
	 * 
	 * @param key
	 *            DeweyID of the new key
	 * @param value
	 *            the value to insert
	 * @param ancestorsToInsert
	 *            number of ancestors to insert implicitly
	 * @param externalized
	 *            indicates whether the value is an external PageID
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the current node
	 * @return offset of the newly inserted node or an errorcode (if new node is
	 *         a duplicate or there is not enough space)
	 */
	public int insert(XTCdeweyID key, byte[] value, int ancestorsToInsert,
			boolean externalized, DeweyIDBuffer currentDeweyID) {

		int result = 0;

		NavigationResult insertPos = this.navigateToInsertPos(key,
				currentDeweyID);

		if (insertPos.status == NavigationStatus.NOT_EXISTENT) {
			// duplicate detected
			result = INSERTION_DUPLICATE;
		} else if (insertPos.status == NavigationStatus.FOUND) {
			// actual insertion
			result = insertAfter(key, value, ancestorsToInsert, externalized,
					insertPos.keyOffset, currentDeweyID);
		} else {
			throw new RuntimeException("Not possible!");
		}

		return result;
	}

	/**
	 * Takes a value from the storage (beginning at the given offset) and stores
	 * it in front of the last stored value.
	 * 
	 * @param storage
	 *            the storage to read the value from
	 * @param offset
	 *            the offset at which the value begins
	 * @param valueLength
	 *            the length of the value (incl. length field) or -1, if unknown
	 * @return the offset where the newly stored value starts
	 */
	private int copyValue(byte[] storage, int offset, int valueLength) {

		if (valueLength == -1) {
			valueLength = getValueLength(offset, true, storage);
		}

		int valuePointer = getFreeSpaceOffset() - valueLength;
		System.arraycopy(storage, offset, page, valuePointer, valueLength);

		// update free space pointer in page header
		setFreeSpaceOffset(valuePointer);

		return valuePointer;
	}

	/**
	 * Stores the given context data in this page. Is usually used by the page
	 * context to store the high key.
	 * 
	 * @param contextData
	 *            the data to store or null, if the old context data is supposed
	 *            to be deleted
	 * @return true if successful, false if there is not enough space left
	 */
	public boolean setContextData(byte[] contextData) {

		if (contextData == null) {
			// context data is supposed to be deleted
			int oldOffset = getContextDataOffset();
			if (oldOffset != 0) {
				allocateRequiredSpace(-getValueLength(oldOffset, true), 0);
				setContextDataOffset(0);
			}
			return true;
		}

		byte[] contextDataLength = getValueLengthField(contextData, false);
		int newLength = contextDataLength.length + contextData.length;
		int requiredSpace = newLength;

		boolean storeAtOldOffset = false;
		int oldOffset = getContextDataOffset();
		if (oldOffset != 0) {
			// there is already context data stored in this page
			int oldLength = getValueLength(oldOffset, true);
			requiredSpace -= oldLength;
			if (newLength <= oldLength) {
				storeAtOldOffset = true;
			}
		}

		// allocate space
		if (!allocateRequiredSpace(requiredSpace, 0)) {
			return false;
		}

		// store context data
		int valueOffset = 0;
		if (storeAtOldOffset) {
			valueOffset = storeValue(contextDataLength, contextData, oldOffset);
		} else {

			if (getFreeSpaceOffset() - getKeyAreaEndOffset() < newLength) {
				defragment(0);
			}

			valueOffset = storeValue(contextDataLength, contextData);
		}

		// set reference
		setContextDataOffset(valueOffset);

		handle.setModified(true);
		return true;
	}

	/**
	 * Returns the context data (as byte array) stored in this page. If there is
	 * no context data, null will be returned.
	 * 
	 * @return context data or null, if not present
	 */
	public byte[] getContextData() {

		int contextDataOffset = getContextDataOffset();
		if (contextDataOffset == 0) {
			return null;
		}

		byte[][] valueParts = getValueParts(contextDataOffset);
		return valueParts[1];
	}

	/**
	 * Returns the context data (interpreted as XTCdeweyID). If there is no
	 * context data, null will be returned.
	 * 
	 * @return context data as DeweyID or null, if not present
	 */
	public XTCdeweyID getContextDataAsDeweyID() {

		int contextDataOffset = getContextDataOffset();
		if (contextDataOffset == 0) {
			return null;
		}

		byte[][] valueParts = getValueParts(contextDataOffset);

		// load DocID
		DocID docID = DocID.fromBytes(page, BASE_PAGE_NO_OFFSET);

		return new XTCdeweyID(docID, valueParts[1]);
	}

	@Override
	public String toString() {

		StringBuilder out = new StringBuilder();

		out.append("PageHandle Information:\n");
		out.append("\tPageID: ");
		out.append(handle.getPageID());
		out.append("\n\tLSN: ");
		out.append(handle.getLSN());

		out.append("\nBase Page Information:\n");
		out.append("\tBasePageID: ");
		out.append(getBasePageID());
		out.append("\n\tRecord Count: ");
		out.append(getRecordCount());
		out.append("\n\tUsed Space: ");
		out.append(getUsedSpace());
		out.append("\n\tFree Space: ");
		out.append(getFreeSpace());
		out.append("\n\tFree Space Pointer: ");
		out.append(getFreeSpaceOffset());

		out.append("\nPage Context Information:\n");
		LeafBPContext.appendPageContextInfo(page, this, out);

		out.append("\nBracket Page Information:\n");
		out.append("\tKey Area End: ");
		out.append(getKeyAreaEndOffset());
		out.append("\n\tLowKey Type: ");
		out.append(getLowKeyType());
		out.append("\n\tLowKey Length: ");
		out.append(page[LOW_KEY_LENGTH_FIELD_NO] & 255);
		out.append("\nPage Content:\n");

		KeyValueTuple[] records = getAllNodes();
		for (KeyValueTuple record : records) {
			out.append("\t");
			out.append(record.key);
			out.append("\n");
		}

		return out.toString();
	}

	/**
	 * Method to retrieve all (logical) nodes in this page as an array of
	 * KeyValueTuples.
	 * 
	 * @return all records stored in this page
	 */
	public KeyValueTuple[] getAllNodes() {

		int recordCount = getRecordCount();

		KeyValueTuple[] result = new KeyValueTuple[recordCount];

		if (recordCount == 0) {
			return result;
		}

		// load lowID record
		XTCdeweyID lowID = getLowKey();
		result[0] = new KeyValueTuple(lowID, getValue(LOW_KEY_OFFSET).value);

		// prepare DeweyID buffers
		DeweyIDBuffer currentDeweyID = new DeweyIDBuffer(null, lowID);

		// navigate over all records
		NavigationResult navRes = new NavigationResult();
		navRes.keyOffset = LOW_KEY_OFFSET;
		for (int i = 1; i < recordCount; i++) {
			navRes = navigateGeneric(currentDeweyID, navRes.keyOffset,
					NavigationProfiles.NEXT_NODE, false);
			result[i] = new KeyValueTuple(currentDeweyID.getDeweyID(),
					getValue(navRes.keyOffset).value);
		}

		return result;
	}

	/**
	 * Navigates to the specified DeweyID.
	 * 
	 * @param key
	 *            the DeweyID to navigate to
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigateToKey(XTCdeweyID key,
			DeweyIDBuffer currentDeweyID) {

		if (getRecordCount() == 0) {
			return new NavigationResult();
		}

		NavigationResult navRes = null;

		// initialize buffer
		currentDeweyID.setTo(getLowKey());
		currentDeweyID.enableCompareMode(key);

		if (currentDeweyID.compare() == 0) {
			// key is lowKey
			navRes = new NavigationResult();
			navRes.status = NavigationStatus.FOUND;
			navRes.keyOffset = LOW_KEY_OFFSET;
			navRes.keyType = getLowKeyType();
		} else if (currentDeweyID.compare() > 0) {
			navRes = new NavigationResult();
			navRes.status = NavigationStatus.BEFORE_FIRST;
		} else {
			// navigate to key
			navRes = navigateGeneric(currentDeweyID, LOW_KEY_OFFSET,
					NavigationProfiles.BY_DEWEYID, false);
		}

		currentDeweyID.disableCompareMode();
		return navRes;
	}

	/**
	 * Navigates to first DeweyID that is grater or equal to the specified
	 * DeweyID.
	 * 
	 * @param key
	 *            the DeweyID to navigate to
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigateGreaterOrEqual(XTCdeweyID key,
			DeweyIDBuffer currentDeweyID) {

		if (getRecordCount() == 0) {
			return new NavigationResult();
		}

		// initialize buffer
		currentDeweyID.setTo(getLowKey());
		currentDeweyID.enableCompareMode(key);

		NavigationResult navRes = navigateGeneric(currentDeweyID,
				BEFORE_LOW_KEY_OFFSET, NavigationProfiles.GREATER_OR_EQUAL,
				false);

		currentDeweyID.disableCompareMode();
		return navRes;
	}

	/**
	 * Navigates to the next node in this page.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @param currentKeyType
	 *            bracket key type of the reference node; this is just an
	 *            optimization and may be null, if not known.
	 * @return the navigation result
	 */
	public NavigationResult navigateNext(int currentOffset,
			DeweyIDBuffer currentDeweyID, BracketKey.Type currentKeyType) {

		if (currentOffset == BEFORE_LOW_KEY_OFFSET) {
			return navigateFirstCF(currentDeweyID);
		} else {

			int keyAreaEndOffset = getKeyAreaEndOffset();
			BracketKey currentKey = new BracketKey();

			if (currentOffset >= keyAreaEndOffset) {
				return new NavigationResult();
			}

			if (currentOffset == LOW_KEY_OFFSET) {
				currentOffset = getKeyAreaStartOffset()
						+ getLowKeyType().getDataReferenceLength();
			} else {
				if (currentKeyType == null) {
					currentKey.load(page, currentOffset);
					currentKeyType = currentKey.type;
				}
				currentOffset += BracketKey.PHYSICAL_LENGTH
						+ currentKeyType.getDataReferenceLength();
			}

			NavigationResult navRes = new NavigationResult();
			int levelDiff = 0;
			while (currentOffset < keyAreaEndOffset) {

				currentKey.load(page, currentOffset);
				currentKeyType = currentKey.type;

				currentDeweyID.update(currentKey, false);

				levelDiff -= currentKey.roundBrackets;
				if (currentKeyType != BracketKey.Type.ATTRIBUTE
						&& currentKeyType != BracketKey.Type.OVERFLOW) {
					levelDiff++;
				}

				if (currentKeyType != BracketKey.Type.OVERFLOW) {
					navRes.status = NavigationStatus.FOUND;
					navRes.keyOffset = currentOffset;
					navRes.keyType = currentKeyType;
					navRes.levelDiff = levelDiff;

					break;
				}

				// set keyPos to the offset of the next key
				currentOffset += BracketKey.PHYSICAL_LENGTH
						+ currentKeyType.getDataReferenceLength();
			}
			return navRes;
		}
	}

	/**
	 * Navigates to the first node in this page.
	 * 
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateFirstCF(DeweyIDBuffer currentDeweyID) {

		if (getRecordCount() == 0) {
			return new NavigationResult();
		}

		NavigationResult navRes = new NavigationResult();
		navRes.status = NavigationStatus.FOUND;
		navRes.keyOffset = LOW_KEY_OFFSET;
		navRes.keyType = getLowKeyType();

		currentDeweyID.setTo(getLowKey());

		return navRes;
	}

	/**
	 * Navigates to the previous node.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigatePrevious(int currentOffset,
			DeweyIDBuffer currentDeweyID) {

		if (currentOffset == BEFORE_LOW_KEY_OFFSET
				|| currentDeweyID.getNumberOfDivisions() == 1) {
			// current node is root or current offset is an invalid position
			return new NavigationResult();
		}

		if (currentOffset == LOW_KEY_OFFSET) {
			// previous key is located before the low key
			NavigationResult navRes = new NavigationResult();
			navRes.status = NavigationStatus.BEFORE_FIRST;
			return navRes;
		}

		// initialize buffer
		currentDeweyID.setTo(getLowKey());

		// determine navigation properties
		NavigationProperties prop = NavigationProfiles
				.getPreviousByKeyOffset(currentOffset);

		NavigationResult navRes = navigateGeneric(currentDeweyID,
				LOW_KEY_OFFSET, prop, false);

		if (navRes.status != NavigationStatus.FOUND) {
			// previous key is the low key
			navRes.status = NavigationStatus.FOUND;
			navRes.keyOffset = LOW_KEY_OFFSET;
			navRes.keyType = getLowKeyType();
		}

		return navRes;
	}

	/**
	 * Navigates to the next sibling.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @param currentKeyType
	 *            bracket key type of the reference node; this is just an
	 *            optimization and may be null, if not known.
	 * @return the navigation result
	 */
	public NavigationResult navigateNextSibling(int currentOffset,
			DeweyIDBuffer currentDeweyID, BracketKey.Type currentKeyType) {

		// initialize result object
		NavigationResult result = new NavigationResult();
		result.status = NavigationStatus.AFTER_LAST;
		result.breakConditionFulfilled = false;

		int levelDiff = 0;
		int overflowDiff = 0;
		boolean firstUpdate = false;

		// currently processed bracket key
		BracketKey currentKey = new BracketKey();

		// determine the key offset for the next bracket key		
		if (currentOffset == LOW_KEY_OFFSET) {
			currentOffset = getKeyAreaStartOffset()
					+ getLowKeyType().getDataReferenceLength();
		} else {
			if (currentKeyType == null) {
				currentKey.load(page, currentOffset);
				currentKeyType = currentKey.type;
			}
			currentOffset += BracketKey.PHYSICAL_LENGTH
					+ currentKeyType.getDataReferenceLength();
		}

		// navigation over key storage
		int keyAreaEndOffset = getKeyAreaEndOffset();
		while (currentOffset < keyAreaEndOffset) {

			// load next key from keyStorage
			currentKey.loadWithoutGaps(page, currentOffset);
			currentKeyType = currentKey.type;

			if (currentKeyType != BracketKey.Type.ATTRIBUTE) {

				// decrease level difference
				levelDiff -= currentKey.roundBrackets;
				overflowDiff -= currentKey.angleBrackets;

				if (levelDiff < -1) {
					// break condition
					result.status = NavigationStatus.NOT_EXISTENT;
					result.breakConditionFulfilled = true;
					break;
				} else if (levelDiff == -1) {
					// update of current DeweyID necessary
					currentKey.loadGaps(page, currentOffset);
					if (!firstUpdate) {
						currentKey.setBrackets(1, -overflowDiff);
						firstUpdate = true;
					}
					currentDeweyID.update(currentKey, true);
				}

				if (currentKeyType == BracketKey.Type.OVERFLOW) {
					overflowDiff++;
				} else {
					// increment level difference (-> opening bracket at the end
					// of the bracket key)
					levelDiff++;

					// success condition
					if (firstUpdate) {
						// current node has qualified
						result.status = NavigationStatus.FOUND;
						result.keyOffset = currentOffset;
						result.keyType = currentKeyType;
						result.levelDiff = levelDiff;
						break;
					}
				}
			}

			// set keyPos to the offset of the next key
			currentOffset += BracketKey.PHYSICAL_LENGTH
					+ currentKeyType.getDataReferenceLength();
		}
		return result;
	}

	/**
	 * Navigates to the next sibling of the specified reference node.
	 * 
	 * @param referenceDeweyID
	 *            DeweyID of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateNextSiblingCF(XTCdeweyID referenceDeweyID,
			DeweyIDBuffer currentDeweyID) {

		// look for reference node in this page
		NavigationResult refNode = this.navigateToKey(referenceDeweyID,
				currentDeweyID);
		NavigationResult navRes = null;

		if (refNode.status == NavigationStatus.FOUND) {
			// invoke navigation method
			navRes = navigateNextSibling(refNode.keyOffset, currentDeweyID, null);
		} else if (refNode.status == NavigationStatus.BEFORE_FIRST) {
			// continue navigation

			// determine level difference to low key
			currentDeweyID.setTo(referenceDeweyID);
			navRes = navigateGeneric(currentDeweyID, BEFORE_LOW_KEY_OFFSET,
					NavigationProfiles.NEXT_SIBLING, false);

		} else {
			// reference key does not exist or is located after the high key
			throw new RuntimeException("Wrong usage of this method!");
		}

		return navRes;
	}

	/**
	 * Navigates to the previous sibling.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigatePreviousSibling(int currentOffset,
			DeweyIDBuffer currentDeweyID) {

		NavigationResult parentOrSibling = navigateGeneric(currentDeweyID,
				BEFORE_LOW_KEY_OFFSET,
				NavigationProfiles.getParentOrSibling(currentOffset), false);

		if (parentOrSibling.status == NavigationStatus.FOUND) {

			if (parentOrSibling.levelDiff == -1) {
				// parent found -> there is no previous sibling
				parentOrSibling.status = NavigationStatus.NOT_EXISTENT;
			} // else: previous sibling found

		} else {
			// no parent or previous sibling found -> before lowkey
			parentOrSibling.status = NavigationStatus.BEFORE_FIRST;
		}

		return parentOrSibling;
	}

	/**
	 * Navigates to the previous sibling of the specified reference node.
	 * 
	 * @param referenceDeweyID
	 *            DeweyID of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigatePreviousSiblingCF(
			XTCdeweyID referenceDeweyID, DeweyIDBuffer currentDeweyID) {

		currentDeweyID.setTo(referenceDeweyID);
		currentDeweyID.enableCompareMode(referenceDeweyID);
		NavigationResult parentOrSibling = navigateGeneric(currentDeweyID,
				BEFORE_LOW_KEY_OFFSET, NavigationProfiles.PARENT_OR_SIBLING,
				false);

		if (parentOrSibling.status == NavigationStatus.FOUND) {

			if (parentOrSibling.levelDiff == -1) {
				// parent found -> there is no previous sibling
				parentOrSibling.status = NavigationStatus.NOT_EXISTENT;
			} // else: previous sibling found

		} else {
			// no parent or previous sibling found -> before lowkey
			parentOrSibling.status = NavigationStatus.BEFORE_FIRST;
		}

		currentDeweyID.disableCompareMode();

		return parentOrSibling;
	}

	/**
	 * Navigates to the first child.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigateFirstChild(int currentOffset,
			DeweyIDBuffer currentDeweyID) {
		return navigateGeneric(currentDeweyID, currentOffset,
				NavigationProfiles.FIRST_CHILD, false);
	}

	/**
	 * Navigates to the first child of the specified reference node.
	 * 
	 * @param referenceDeweyID
	 *            DeweyID of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateFirstChildCF(XTCdeweyID referenceDeweyID,
			DeweyIDBuffer currentDeweyID) {

		// look for reference node in this page
		NavigationResult refNode = this.navigateToKey(referenceDeweyID,
				currentDeweyID);
		NavigationResult navRes = null;

		if (refNode.status == NavigationStatus.FOUND) {
			// invoke navigation method
			navRes = navigateFirstChild(refNode.keyOffset, currentDeweyID);
		} else if (refNode.status == NavigationStatus.BEFORE_FIRST) {
			// continue navigation

			// look for the first child
			currentDeweyID.setTo(referenceDeweyID);
			navRes = navigateGeneric(currentDeweyID, BEFORE_LOW_KEY_OFFSET,
					NavigationProfiles.FIRST_CHILD, false);

		} else {
			// reference key does not exist or is located after the high key
			throw new RuntimeException("Wrong usage of this method!");
		}

		return navRes;
	}

	/**
	 * Navigates to the last child.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigateLastChild(int currentOffset,
			DeweyIDBuffer currentDeweyID) {

		NavigationResult lastChild = navigateGeneric(currentDeweyID,
				currentOffset, NavigationProfiles.LAST_CHILD, false);

		if (lastChild.status == NavigationStatus.FOUND
				&& !lastChild.breakConditionFulfilled) {
			lastChild.status = NavigationStatus.POSSIBLY_FOUND;
		}

		return lastChild;
	}

	/**
	 * Navigates to the last child of the specified reference node.
	 * 
	 * @param referenceDeweyID
	 *            DeweyID of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateLastChildCF(XTCdeweyID referenceDeweyID,
			DeweyIDBuffer currentDeweyID) {

		// look for reference node in this page
		NavigationResult refNode = this.navigateToKey(referenceDeweyID,
				currentDeweyID);
		NavigationResult navRes = null;

		if (refNode.status == NavigationStatus.FOUND) {
			// invoke navigation method
			navRes = navigateLastChild(refNode.keyOffset, currentDeweyID);
		} else if (refNode.status == NavigationStatus.BEFORE_FIRST) {
			// continue navigation

			// determine level difference to low key
			currentDeweyID.setTo(referenceDeweyID);
			navRes = navigateGeneric(currentDeweyID, BEFORE_LOW_KEY_OFFSET,
					NavigationProfiles.LAST_CHILD, false);

			if (navRes.status == NavigationStatus.FOUND
					&& !navRes.breakConditionFulfilled) {
				navRes.status = NavigationStatus.POSSIBLY_FOUND;
			}

			if (navRes.status == NavigationStatus.NOT_EXISTENT) {
				navRes.status = NavigationStatus.BEFORE_FIRST;
			}

		} else {
			// reference key does not exist or is located after the high key
			throw new RuntimeException("Wrong usage of this method!");
		}

		return navRes;
	}

	/**
	 * Navigates to the parent.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigateParent(DeweyIDBuffer currentDeweyID) {

		// create result object
		NavigationResult navRes = null;

		// determine parent DeweyID
		if (!currentDeweyID.setToParent()) {
			// if parent does not exist
			return new NavigationResult();
		}

		// compare parent ID with lowID
		currentDeweyID.enableCompareMode(getLowKey());
		int compareValue = currentDeweyID.compare();
		if (compareValue < 0) {
			navRes = new NavigationResult();
			navRes.status = NavigationStatus.BEFORE_FIRST;
		} else if (compareValue == 0) {
			// lowKey is parent
			navRes = new NavigationResult();
			navRes.status = NavigationStatus.FOUND;
			navRes.keyOffset = LOW_KEY_OFFSET;
			navRes.keyType = getLowKeyType();
			navRes.levelDiff = -1;
		} else {
			// parent lies between lowKey and given offset
			currentDeweyID.backup();
			currentDeweyID.setTo(getLowKey());
			currentDeweyID.enableCompareMode(currentDeweyID
					.getBackupAsSimpleDeweyID());
			currentDeweyID.resetBackup();
			navRes = navigateGeneric(currentDeweyID, LOW_KEY_OFFSET,
					NavigationProfiles.BY_DEWEYID, false);
			currentDeweyID.disableCompareMode();
		}
		currentDeweyID.disableCompareMode();

		return navRes;
	}

	/**
	 * Navigates to the parent of the specified reference node.
	 * 
	 * @param referenceDeweyID
	 *            DeweyID of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateParentCF(XTCdeweyID referenceDeweyID,
			DeweyIDBuffer currentDeweyID) {
		currentDeweyID.setTo(referenceDeweyID);
		return navigateParent(currentDeweyID);
	}

	/**
	 * Navigates to the last node of this page.
	 * 
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateLastCF(DeweyIDBuffer currentDeweyID) {

		NavigationResult navRes = new NavigationResult();

		if (getRecordCount() == 1) {
			navRes.status = NavigationStatus.FOUND;
			navRes.keyOffset = LOW_KEY_OFFSET;
			navRes.keyType = getLowKeyType();
			currentDeweyID.setTo(getLowKey());
		} else {

			// navigate to last node in this page
			currentDeweyID.setTo(getLowKey());
			navRes = navigateGeneric(currentDeweyID, LOW_KEY_OFFSET,
					NavigationProfiles.LAST_NODE, false);

		}

		return navRes;
	}

	/**
	 * Navigates to the correct insertion position, if the DeweyID 'key' will be
	 * inserted.
	 * 
	 * @param key
	 *            the DeweyID to insert in the future
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateToInsertPos(XTCdeweyID key,
			DeweyIDBuffer currentDeweyID) {

		NavigationResult navRes = null;

		boolean beforeFirst = false;
		if (getRecordCount() == 0) {
			beforeFirst = true;
		} else {

			currentDeweyID.setTo(getLowKey());
			currentDeweyID.enableCompareMode(key);

			if (currentDeweyID.compare() == 0) {
				// key is low key
				// duplicate detected
				currentDeweyID.disableCompareMode();
				return new NavigationResult();
			} else if (currentDeweyID.compare() > 0) {
				beforeFirst = true;
			} else {

				// look for correct insertion position
				navRes = navigateGeneric(currentDeweyID, LOW_KEY_OFFSET,
						NavigationProfiles.TO_INSERT_POS, true);

				if (navRes.status != NavigationStatus.FOUND) {
					// new record has to be inserted after the lowKey
					navRes.status = NavigationStatus.FOUND;
					navRes.keyOffset = LOW_KEY_OFFSET;
					navRes.keyType = getLowKeyType();
				} else {
					// insertion position found

					if (currentDeweyID.compare() == 0) {
						// duplicate detected
						currentDeweyID.disableCompareMode();
						return new NavigationResult();
					}
				}
			}

			currentDeweyID.disableCompareMode();
		}

		if (beforeFirst) {
			// key has to be inserted before the low key
			navRes = new NavigationResult();
			navRes.status = NavigationStatus.FOUND;
			navRes.keyOffset = BEFORE_LOW_KEY_OFFSET;
		}

		return navRes;
	}

	/**
	 * Updates the value of the node at the given key offset.
	 * 
	 * @param newValue
	 *            the new value of the node
	 * @param externalized
	 *            indicates whether the value is an external PageID
	 * @param currentOffset
	 *            offset where the bracket key of the current node starts
	 * @return true if successful, false if there is not enough space
	 */
	public boolean update(byte[] newValue, boolean externalized,
			int currentOffset) {

		// determine currentKeyType
		BracketKey.Type currentKeyType = null;
		if (currentOffset != BEFORE_LOW_KEY_OFFSET) {
			currentKeyType = (currentOffset == LOW_KEY_OFFSET ? getLowKeyType()
					: BracketKey.loadNew(page, currentOffset).type);
		}

		// check validity of key type
		if (currentKeyType == null || currentKeyType == BracketKey.Type.NODATA
				|| currentKeyType == BracketKey.Type.OVERFLOW) {
			throw new RuntimeException(
					"Update not possible at the given offset!");
		}

		// create new length field
		byte[] valueLengthField = getValueLengthField(newValue, externalized);

		// set offset to value reference
		currentOffset += BracketKey.PHYSICAL_LENGTH;

		// determine value offset
		int valueOffset = getValueOffset(currentOffset);

		// determine old value length
		int oldValueLength = getValueLength(valueOffset, true);

		// calculate required space
		int requiredSpace = valueLengthField.length + newValue.length
				- oldValueLength;

		if (requiredSpace <= 0) {
			// store new value into the gap

			// free space
			if (requiredSpace < 0) {
				freeSpace(-requiredSpace);
			}

			// overwrite old value
			System.arraycopy(valueLengthField, 0, page, valueOffset,
					valueLengthField.length);
			System.arraycopy(newValue, 0, page, valueOffset
					+ valueLengthField.length, newValue.length);

		} else {

			// allocate space
			if (!allocateRequiredSpace(requiredSpace, 0)) {
				return false;
			}

			// check whether a defragmentation is needed
			if (requiredSpace > getFreeSpaceOffset() - getKeyAreaEndOffset()) {
				// defragmentation
				defragment(0);
			}

			// store new value in front of the value area
			valueOffset = storeValue(valueLengthField, newValue);

			// update value reference
			writeValueReference(currentOffset, valueOffset);

		}

		handle.setModified(true);

		return true;
	}

	/**
	 * Checks whether node at current offset is the last node in this page.
	 * 
	 * @param currentOffset
	 *            the offset where the bracket key starts
	 * @return true if this is the last node
	 */
	public boolean isLast(int currentOffset) {
		return (currentOffset == BEFORE_LOW_KEY_OFFSET && getRecordCount() == 0
				|| currentOffset == LOW_KEY_OFFSET && getRecordCount() == 1 || currentOffset
				+ BracketKey.PHYSICAL_LENGTH + BracketKey.DATA_REF_LENGTH == getKeyAreaEndOffset());
	}

	/**
	 * Navigates to the next attribute.
	 * 
	 * @param currentOffset
	 *            the offset of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the reference node
	 * @return the navigation result
	 */
	public NavigationResult navigateNextAttribute(int currentOffset,
			DeweyIDBuffer currentDeweyID) {
		return navigateGeneric(currentDeweyID, currentOffset,
				NavigationProfiles.NEXT_ATTRIBUTE, false);
	}

	/**
	 * Navigates to the next attribute of the specified reference node.
	 * 
	 * @param referenceDeweyID
	 *            DeweyID of the reference node
	 * @param currentDeweyID
	 *            DeweyIDBuffer that will contain the DeweyID of the requested
	 *            node, if found
	 * @return the navigation result
	 */
	public NavigationResult navigateNextAttributeCF(
			XTCdeweyID referenceDeweyID, DeweyIDBuffer currentDeweyID) {

		// look for reference node in this page
		NavigationResult refNode = navigateToKey(referenceDeweyID,
				currentDeweyID);
		NavigationResult navRes = null;

		if (refNode.status == NavigationStatus.FOUND) {
			// invoke navigation method
			navRes = navigateNextAttribute(refNode.keyOffset, currentDeweyID);
		} else if (refNode.status == NavigationStatus.BEFORE_FIRST) {
			// continue navigation

			if (getLowKeyType() == BracketKey.Type.ATTRIBUTE) {
				// lowID is the next attribute
				navRes = new NavigationResult();
				navRes.status = NavigationStatus.FOUND;
				navRes.keyOffset = LOW_KEY_OFFSET;
				navRes.keyType = getLowKeyType();
				currentDeweyID.setTo(getLowKey());
			} else {
				// no next attribute exists
				navRes = new NavigationResult();
			}

		} else {
			// reference key does not exist or is located after the high key
			throw new RuntimeException("Wrong usage of this method!");
		}

		return navRes;
	}

	/**
	 * Creates a bracket node sequence.
	 * 
	 * @param startDeweyID
	 *            the DeweyID of the first node in the sequence
	 * @param startOffset
	 *            the key offset of the first node in the page
	 * @param endOffset
	 *            the key offset where the sequence ends (exclusive the node
	 *            beginning at the endOffset)
	 * @return the bracket node sequence
	 */
	public BracketNodeSequence getBracketNodeSequence(XTCdeweyID startDeweyID,
			int startOffset, int endOffset) {
		return getBracketNodeSequence(startDeweyID, startOffset, endOffset, -1,
				-1, -1);
	}

	/**
	 * Creates a bracket node sequence from the given DeletePreparation object.
	 * 
	 * @param delPrep
	 *            the delete preparation
	 * @return the bracket node sequence
	 */
	public BracketNodeSequence getBracketNodeSequence(DeletePreparation delPrep) {
		return getBracketNodeSequence(delPrep.startDeleteDeweyID,
				delPrep.startDeleteOffset, delPrep.endDeleteOffset,
				delPrep.numberOfDataRecords, delPrep.dataRecordSize,
				delPrep.finalOverflowKeys);
	}

	/**
	 * Creates a bracket node sequence.
	 * 
	 * @param startDeweyID
	 *            the DeweyID of the first node in the sequence
	 * @param startOffset
	 *            the key offset of the first node in the page
	 * @param endOffset
	 *            the key offset where the sequence ends (exclusive the node
	 *            beginning at the endOffset)
	 * @param numberOfDataRecords
	 *            number if data records within the bracket node sequence (-1
	 *            for unknown)
	 * @param dataRecordSize
	 *            total data record size in the bracket node sequence (-1 for
	 *            unknown)
	 * @param finalOverflowKeys
	 *            overflow keys between the last (logical) node and the end
	 *            offset (-1 for unknown)
	 * @return the bracket node sequence
	 */
	private BracketNodeSequence getBracketNodeSequence(XTCdeweyID startDeweyID,
			int startOffset, int endOffset, int numberOfDataRecords,
			int dataRecordSize, int finalOverflowKeys) {
		// assert(startOffset >= LOW_ID_KEYOFFSET)
		// assert(startOffset <= endOffset || endOffset == PAGE_END_KEYOFFSET)
		// assert(endOffset <= keyAreaEndOffset)
		// assert(last specified node has a data set).

		if (endOffset == KEY_AREA_END_OFFSET) {
			endOffset = getKeyAreaEndOffset();
		}

		if (startOffset == endOffset) {
			return new BracketNodeSequence(null, 0);
		}

		byte[] lowIDBytes = startDeweyID.toBytes();
		int lowIDLength = lowIDBytes.length;

		if (lowIDLength > 255) {
			throw new RuntimeException("StartDeweyID is too long!");
		}

		BracketKey.Type lowIDType = null;
		if (startOffset == LOW_KEY_OFFSET) {
			lowIDType = getLowKeyType();
			startOffset = getKeyAreaStartOffset();
		} else {
			lowIDType = BracketKey.loadType(page, startOffset);
			startOffset += BracketKey.PHYSICAL_LENGTH;
		}
		int currentOffset = startOffset;

		// determine length of resulting byte array
		int resultLength = 1 /* LowID Length */+ lowIDLength /* LowID */+ 1 /*
																			 * LowID
																			 * Type
																			 */;

		// if numberOfDataRecords, dataRecordSize and finalOverflowKeys are
		// given, the computation of the result length can be simplified
		boolean simpleComputation = (numberOfDataRecords != -1);

		if (simpleComputation) {
			resultLength += (endOffset - startOffset) /*
													 * Bracket Keys incl. data
													 * references
													 */
					- numberOfDataRecords * BracketKey.DATA_REF_LENGTH /*
																		 * data
																		 * references
																		 */
					+ dataRecordSize /* data records */
					- finalOverflowKeys * BracketKey.PHYSICAL_LENGTH; /*
																	 * overflow
																	 * keys at
																	 * the end
																	 */
		} else {

			/* LowID Data */
			if (lowIDType.hasDataReference()) {
				resultLength += getValueLength(getValueOffset(currentOffset),
						true);
				currentOffset += lowIDType.getDataReferenceLength();
			}

			// count the number of overflow keys at the end of the specified key
			// area
			int overflowKeys = 0;

			while (currentOffset < endOffset) {
				boolean hasDataRef = BracketKey.hasDataReference(page,
						currentOffset);
				currentOffset += BracketKey.PHYSICAL_LENGTH;
				resultLength += BracketKey.PHYSICAL_LENGTH;

				if (hasDataRef) {
					resultLength += getValueLength(
							getValueOffset(currentOffset), true);
					currentOffset += BracketKey.DATA_REF_LENGTH;
					overflowKeys = 0;
				} else {
					overflowKeys++;
				}
			}

			// cut off the last overflowKeys if necessary
			resultLength -= (overflowKeys * BracketKey.PHYSICAL_LENGTH);
		}

		// result length determined

		// create result array
		byte[] result = new byte[resultLength];

		// fill result array
		int numberDataRecords = 0;
		int resultOffset = 0;
		currentOffset = startOffset;
		// LowID Length Field
		result[resultOffset] = (byte) lowIDLength;
		resultOffset++;
		// LowID Field
		System.arraycopy(lowIDBytes, 0, result, resultOffset, lowIDLength);
		resultOffset += lowIDLength;
		// LowID Type Field
		result[resultOffset] = (byte) lowIDType.getPhysicalValue();
		resultOffset++;
		// LowID Data
		if (lowIDType.hasDataReference()) {
			numberDataRecords++;
			int valueOffset = getValueOffset(currentOffset);
			int valueLength = getValueLength(valueOffset, true);

			System.arraycopy(page, valueOffset, result, resultOffset,
					valueLength);

			currentOffset += lowIDType.getDataReferenceLength();
			resultOffset += valueLength;
		}
		// remaining bracket keys + data
		while (resultOffset < resultLength) {
			boolean hasDataRef = BracketKey.hasDataReference(page,
					currentOffset);

			System.arraycopy(page, currentOffset, result, resultOffset,
					BracketKey.PHYSICAL_LENGTH);

			currentOffset += BracketKey.PHYSICAL_LENGTH;
			resultOffset += BracketKey.PHYSICAL_LENGTH;

			if (hasDataRef) {
				numberDataRecords++;
				int valueOffset = getValueOffset(currentOffset);
				int valueLength = getValueLength(valueOffset, true);

				System.arraycopy(page, valueOffset, result, resultOffset,
						valueLength);

				currentOffset += BracketKey.DATA_REF_LENGTH;
				resultOffset += valueLength;
			}
		}

		return new BracketNodeSequence(result, numberDataRecords);
	}

	/**
	 * Prepares the deletion of a node sequence (given by the first and the last
	 * DeweyID that are supposed to be deleted)
	 * 
	 * @param leftBorderDeweyID
	 *            leftmost DeweyID to be deleted
	 * @param rightBorderDeweyID
	 *            rightmost DeweyID to be deleted
	 * @param tempDeweyID
	 *            DeweyIDBuffer for temporary DeweyIDs (original Buffer value
	 *            will NOT be preserved!)
	 * @return the delete sequence preparation result
	 * @throws BracketPageException
	 *             if an error occurs
	 */
	public DeleteSequencePreparation deleteSequencePrepare(
			XTCdeweyID leftBorderDeweyID, XTCdeweyID rightBorderDeweyID,
			DeweyIDBuffer tempDeweyID) throws BracketPageException {

		if (getRecordCount() == 0) {
			// this is an empty page
			return new DeleteSequencePreparation(new DeleteSequenceInfo(false,
					false, true), null);
		}

		XTCdeweyID lowKey = getLowKey();
		tempDeweyID.setTo(lowKey);

		int previousOffset = BEFORE_LOW_KEY_OFFSET;
		XTCdeweyID previousDeweyID = null;
		int startDeleteOffset = BEFORE_LOW_KEY_OFFSET;
		XTCdeweyID startDeleteDeweyID = null;
		int endDeleteOffset = BEFORE_LOW_KEY_OFFSET;
		XTCdeweyID endDeleteDeweyID = null;
		// determine size/number of data records to be deleted
		int dataRecordSize = 0;
		int numberOfDataRecords = 0;
		// count number of nodes to be deleted
		int numberOfNodes = 0;
		// count number of overflow keys between the last data record and the
		// endDeleteOffset
		int finalOverflowKeys = 0;

		boolean checkLeftNeighbor = false;
		boolean checkRightNeighbor = false;

		// check whether left border is (possibly) included in this page
		int compareLeftToLowKey = leftBorderDeweyID.compareDivisions(lowKey);
		boolean leftIncluded = (compareLeftToLowKey >= 0);
		int compareRightToLowKey = rightBorderDeweyID.compareDivisions(lowKey);
		boolean rightIncluded = (compareRightToLowKey >= 0);

		// both the left and the right border are located in one of the previous
		// pages
		if (!leftIncluded && !rightIncluded) {
			return new DeleteSequencePreparation(new DeleteSequenceInfo(true,
					false, false), null);
		}

		if (compareLeftToLowKey <= 0) {
			// start with the lowKey to delete
			startDeleteOffset = LOW_KEY_OFFSET;
			startDeleteDeweyID = lowKey;
			if (compareLeftToLowKey < 0) {
				// sequence might start in the previous page
				checkLeftNeighbor = true;
			}
		} else {
			// look for the left border within this page
			tempDeweyID.enableCompareMode(leftBorderDeweyID);

			previousOffset = LOW_KEY_OFFSET;
			tempDeweyID.backup();

			int currentOffset = getKeyAreaStartOffset()
					+ getLowKeyType().getDataReferenceLength();
			int keyAreaEndOffset = getKeyAreaEndOffset();
			BracketKey currentKey = new BracketKey();
			boolean found = false;
			while (currentOffset < keyAreaEndOffset) {
				currentKey.load(page, currentOffset);
				tempDeweyID.update(currentKey, false);
				if (tempDeweyID.compare() >= 0) {
					found = true;
					break;
				}
				// buffer current node's information
				previousOffset = currentOffset;
				tempDeweyID.backup();
				currentOffset += BracketKey.PHYSICAL_LENGTH
						+ currentKey.getDataReferenceLength();
			}

			previousDeweyID = tempDeweyID.getBackupDeweyID();
			int compareValue = tempDeweyID.compare();
			tempDeweyID.resetBackup();
			tempDeweyID.disableCompareMode();

			if (!found) {
				// left border not included in page
				return new DeleteSequencePreparation(new DeleteSequenceInfo(
						false, true, false), null);
			} else if (compareValue > 0) {
				// left border DeweyID does not exist!
				throw new BracketPageException(String.format(
						"LeftBorderDeweyID %s does not exist!",
						leftBorderDeweyID));
			}

			startDeleteDeweyID = tempDeweyID.getDeweyID();
			startDeleteOffset = currentOffset;
		}

		// start position of deletion found -> process page from this position
		// on
		tempDeweyID.enableCompareMode(rightBorderDeweyID);
		int currentOffset = startDeleteOffset;
		BracketKey.Type currentType = null;

		boolean rightBorderFound = false;

		if (currentOffset == LOW_KEY_OFFSET) {
			// separate handling for the lowkey
			currentType = getLowKeyType();
			currentOffset = getKeyAreaStartOffset();
			numberOfNodes++;

			if (currentType.hasDataReference()) {
				numberOfDataRecords++;
				dataRecordSize += getValueLength(getValueOffset(currentOffset),
						true);
				currentOffset += BracketKey.DATA_REF_LENGTH;
			}

			if (compareRightToLowKey == 0) {
				// only the lowkey should be deleted
				rightBorderFound = true;
			}
		}

		BracketKey currentKey = new BracketKey();
		int keyAreaEndOffset = getKeyAreaEndOffset();
		// navigate to the first node that is not supposed to be deleted
		while (currentOffset < keyAreaEndOffset) {
			// load next key from keyStorage
			currentKey.load(page, currentOffset);
			currentType = currentKey.type;
			// refresh DeweyID
			tempDeweyID.update(currentKey, false);

			if (currentType != BracketKey.Type.OVERFLOW) {
				// check break condition -> DeweyID larger than right border
				if (tempDeweyID.compare() == 0) {
					rightBorderFound = true;
				} else if (tempDeweyID.compare() > 0) {
					endDeleteOffset = currentOffset;
					endDeleteDeweyID = tempDeweyID.getDeweyID();
					break;
				}
				currentOffset += BracketKey.PHYSICAL_LENGTH;
				numberOfNodes++;
				finalOverflowKeys = 0;

				if (currentType != BracketKey.Type.NODATA) {
					numberOfDataRecords++;
					dataRecordSize += getValueLength(
							getValueOffset(currentOffset), true);
					currentOffset += BracketKey.DATA_REF_LENGTH;
				}
			} else {
				currentOffset += BracketKey.PHYSICAL_LENGTH;
				finalOverflowKeys++;
			}
		}

		// check whether endDeleteNode was found
		if (endDeleteDeweyID == null) {
			endDeleteOffset = KEY_AREA_END_OFFSET;
			if (!rightBorderFound) {
				checkRightNeighbor = true;
			}
		} else if (!rightBorderFound) {
			// right border DeweyID does not exist!
			throw new BracketPageException(
					String.format("RightBorderDeweyID %s does not exist!",
							rightBorderDeweyID));
		}

		boolean producesEmptyLeaf = (startDeleteOffset == LOW_KEY_OFFSET && endDeleteOffset == KEY_AREA_END_OFFSET);
		return new DeleteSequencePreparation(new DeleteSequenceInfo(
				checkLeftNeighbor, checkRightNeighbor, producesEmptyLeaf),
				new DeletePreparation(previousDeweyID, previousOffset,
						startDeleteDeweyID, startDeleteOffset,
						endDeleteDeweyID, endDeleteOffset, numberOfNodes,
						numberOfDataRecords, dataRecordSize, finalOverflowKeys));
	}

	/**
	 * Prepares the deletion of a remaining subtree (i.e. the subtree root is
	 * not located in this page).
	 * 
	 * @param subtreeRoot
	 *            DeweyID of the subtree root
	 * @param tempDeweyID
	 *            DeweyIDBuffer for temporary DeweyIDs (original Buffer value
	 *            will NOT be preserved!)
	 * @param delPrepLis
	 *            a delete prepare listener that is notified about all the nodes
	 *            that are supposed to be deleted
	 * @return the delete preparation result
	 * @throws IndexOperationException
	 */
	public DeletePreparation deleteSubtreeEndPrepare(XTCdeweyID subtreeRoot,
			DeweyIDBuffer tempDeweyID, DeletePrepareListener delPrepLis)
			throws BracketPageException {

		if (getRecordCount() == 0) {
			return null;
		}

		XTCdeweyID lowKey = getLowKey();

		if (!subtreeRoot.isPrefixOf(lowKey)) {
			// nothing to delete
			delPrepLis.subtreeEnd();
			return null;
		}

		int startDeleteOffset = BracketPage.LOW_KEY_OFFSET;
		XTCdeweyID startDeleteDeweyID = lowKey;
		int endDeleteOffset = 0;
		XTCdeweyID endDeleteDeweyID = null;
		// determine size/number of data records to be deleted
		int dataRecordSize = 0;
		int numberOfDataRecords = 0;
		// count number of nodes to be deleted
		int numberOfNodes = 1;
		// count number of overflow keys between the last data record and the
		// endDeleteOffset
		int finalOverflowKeys = 0;

		tempDeweyID.setTo(lowKey);
		int levelDiff = -tempDeweyID.getLevelDifferenceTo(subtreeRoot);
		InternalValue currentValue = new InternalValue();

		int currentOffset = getKeyAreaStartOffset();
		BracketKey.Type currentType = getLowKeyType();
		if (currentType.hasDataReference()) {
			numberOfDataRecords++;
			loadInternalValue(currentValue, getValueOffset(currentOffset));
			dataRecordSize += currentValue.totalValueLength;
			if (currentValue.externalized) {
				delPrepLis.externalNode(startDeleteDeweyID,
						PageID.fromBytes(currentValue.value), levelDiff);
			} else {
				delPrepLis.node(startDeleteDeweyID, currentValue.value,
						levelDiff);
			}
			currentOffset += BracketKey.DATA_REF_LENGTH;
		} else {
			delPrepLis.node(startDeleteDeweyID, null, levelDiff);
		}

		BracketKey currentKey = new BracketKey();
		int keyAreaEndOffset = getKeyAreaEndOffset();
		// navigate to the first node that is not supposed to be deleted
		while (currentOffset < keyAreaEndOffset) {
			// load next key from keyStorage
			currentKey.load(page, currentOffset);
			currentType = currentKey.type;
			// refresh DeweyID
			tempDeweyID.update(currentKey, false);
			// decrease level difference
			levelDiff -= currentKey.roundBrackets;
			// increment level difference
			if (currentType.opensNewSubtree()) {
				// increment level difference (-> opening bracket at the end of
				// the bracket key)
				levelDiff++;
			}

			if (currentType != BracketKey.Type.OVERFLOW) {
				// check success condition
				if (currentType != BracketKey.Type.ATTRIBUTE && levelDiff <= 0) {
					endDeleteOffset = currentOffset;
					endDeleteDeweyID = tempDeweyID.getDeweyID();
					delPrepLis.subtreeEnd();
					break;
				}
				currentOffset += BracketKey.PHYSICAL_LENGTH;
				numberOfNodes++;
				finalOverflowKeys = 0;

				if (currentType != BracketKey.Type.NODATA) {
					numberOfDataRecords++;
					loadInternalValue(currentValue,
							getValueOffset(currentOffset));
					dataRecordSize += currentValue.totalValueLength;
					if (currentValue.externalized) {
						delPrepLis
								.externalNode(tempDeweyID.getDeweyID(),
										PageID.fromBytes(currentValue.value),
										levelDiff);
					} else {
						delPrepLis.node(tempDeweyID.getDeweyID(),
								currentValue.value, levelDiff);
					}
					currentOffset += BracketKey.DATA_REF_LENGTH;
				} else {
					delPrepLis.node(tempDeweyID.getDeweyID(), null, levelDiff);
				}

			} else {
				currentOffset += BracketKey.PHYSICAL_LENGTH;
				finalOverflowKeys++;
			}
		}

		// check whether endDeleteNode was found
		if (endDeleteDeweyID == null) {
			endDeleteOffset = KEY_AREA_END_OFFSET;
		}

		return new DeletePreparation(null, BracketPage.BEFORE_LOW_KEY_OFFSET,
				startDeleteDeweyID, startDeleteOffset, endDeleteDeweyID,
				endDeleteOffset, numberOfNodes, numberOfDataRecords,
				dataRecordSize, finalOverflowKeys);
	}

	/**
	 * Prepares the deletion of a subtree.
	 * 
	 * @param currentOffset
	 *            the key offset of the subtree root
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the subtree root
	 * @param delPrepLis
	 *            a delete prepare listener that is notified about all the nodes
	 *            that are supposed to be deleted
	 * @return the delete preparation result
	 * @throws IndexOperationException
	 */
	public DeletePreparation deleteSubtreeStartPrepare(int currentOffset,
			DeweyIDBuffer currentDeweyID, DeletePrepareListener delPrepLis)
			throws BracketPageException {
		if (currentOffset == BEFORE_LOW_KEY_OFFSET) {
			throw new IllegalArgumentException();
		}

		boolean currentIsLowKey = (currentOffset == LOW_KEY_OFFSET);

		// determine type of the node to be deleted
		BracketKey.Type currentType = currentIsLowKey ? getLowKeyType()
				: BracketKey.loadType(page, currentOffset);

		int startDeleteOffset = currentOffset;
		XTCdeweyID startDeleteDeweyID = currentDeweyID.getDeweyID();
		int endDeleteOffset = 0;
		XTCdeweyID endDeleteDeweyID = null;
		// determine size/number of data records to be deleted
		int dataRecordSize = 0;
		int numberOfDataRecords = 0;
		// count number of nodes to be deleted
		int numberOfNodes = 1;
		// count number of overflow keys between the last data record and the
		// endDeleteOffset
		int finalOverflowKeys = 0;

		InternalValue currentValue = new InternalValue();

		int previousOffset = 0;
		XTCdeweyID previousDeweyID = null;
		if (!currentIsLowKey) {
			// navigate to previous node
			NavigationResult previous = navigatePrevious(currentOffset,
					currentDeweyID);
			previousOffset = previous.keyOffset;
			previousDeweyID = currentDeweyID.getDeweyID();
			currentDeweyID.setTo(startDeleteDeweyID);
		}

		if (currentType == BracketKey.Type.ATTRIBUTE) {
			// only delete current node
			currentOffset = currentIsLowKey ? getKeyAreaStartOffset()
					: currentOffset + BracketKey.PHYSICAL_LENGTH;
			numberOfDataRecords = 1;

			loadInternalValue(currentValue, getValueOffset(currentOffset));
			dataRecordSize += currentValue.totalValueLength;
			if (currentValue.externalized) {
				delPrepLis.externalNode(startDeleteDeweyID,
						PageID.fromBytes(currentValue.value), 0);
			} else {
				delPrepLis.node(startDeleteDeweyID, currentValue.value, 0);
			}
			endDeleteOffset = currentOffset + BracketKey.DATA_REF_LENGTH;

			if (endDeleteOffset < getKeyAreaEndOffset()) {
				// determine DeweyID
				currentDeweyID.backup();
				currentDeweyID.update(
						BracketKey.loadNew(page, endDeleteOffset), false);
				endDeleteDeweyID = currentDeweyID.getDeweyID();
				currentDeweyID.restore(false);
			} else {
				endDeleteOffset = KEY_AREA_END_OFFSET;
			}
		} else {
			// assert(currentType != BracketKey.Type.OVERFLOW)

			int levelDiff = 0;
			currentOffset = currentIsLowKey ? getKeyAreaStartOffset()
					: (startDeleteOffset + BracketKey.PHYSICAL_LENGTH);
			if (currentType.hasDataReference()) {
				numberOfDataRecords++;
				loadInternalValue(currentValue, getValueOffset(currentOffset));
				dataRecordSize += currentValue.totalValueLength;
				if (currentValue.externalized) {
					delPrepLis.externalNode(startDeleteDeweyID,
							PageID.fromBytes(currentValue.value), levelDiff);
				} else {
					delPrepLis.node(startDeleteDeweyID, currentValue.value,
							levelDiff);
				}
				currentOffset += BracketKey.DATA_REF_LENGTH;
			} else {
				delPrepLis.node(startDeleteDeweyID, null, levelDiff);
			}

			currentDeweyID.backup();
			BracketKey currentKey = new BracketKey();
			int keyAreaEndOffset = getKeyAreaEndOffset();
			// navigate to the first node that is not supposed to be deleted
			while (currentOffset < keyAreaEndOffset) {
				// load next key from keyStorage
				currentKey.load(page, currentOffset);
				currentType = currentKey.type;
				// refresh DeweyID
				currentDeweyID.update(currentKey, false);
				// decrease level difference
				levelDiff -= currentKey.roundBrackets;
				// increment level difference
				if (currentType.opensNewSubtree()) {
					// increment level difference (-> opening bracket at the end
					// of the bracket key)
					levelDiff++;
				}

				if (currentType != BracketKey.Type.OVERFLOW) {
					// check success condition
					if (currentType != BracketKey.Type.ATTRIBUTE
							&& levelDiff <= 0) {
						endDeleteOffset = currentOffset;
						endDeleteDeweyID = currentDeweyID.getDeweyID();
						delPrepLis.subtreeEnd();
						break;
					}
					currentOffset += BracketKey.PHYSICAL_LENGTH;
					numberOfNodes++;
					finalOverflowKeys = 0;

					if (currentType != BracketKey.Type.NODATA) {
						numberOfDataRecords++;
						loadInternalValue(currentValue,
								getValueOffset(currentOffset));
						dataRecordSize += currentValue.totalValueLength;
						if (currentValue.externalized) {
							delPrepLis.externalNode(
									currentDeweyID.getDeweyID(),
									PageID.fromBytes(currentValue.value),
									levelDiff);
						} else {
							delPrepLis.node(currentDeweyID.getDeweyID(),
									currentValue.value, levelDiff);
						}
						currentOffset += BracketKey.DATA_REF_LENGTH;
					} else {
						delPrepLis.node(currentDeweyID.getDeweyID(), null,
								levelDiff);
					}

				} else {
					currentOffset += BracketKey.PHYSICAL_LENGTH;
					finalOverflowKeys++;
				}
			}
			currentDeweyID.restore(false);

			// check whether endDeleteNode was found
			if (endDeleteDeweyID == null) {
				endDeleteOffset = KEY_AREA_END_OFFSET;
			}
		}

		return new DeletePreparation(previousDeweyID, previousOffset,
				startDeleteDeweyID, startDeleteOffset, endDeleteDeweyID,
				endDeleteOffset, numberOfNodes, numberOfDataRecords,
				dataRecordSize, finalOverflowKeys);
	}

	/**
	 * Prepares a split operation. The split is done after the current node.
	 * 
	 * @param currentOffset
	 *            key offset of current node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the current node
	 * @return the delete preparation object for the nodes that are shifted to
	 *         the right page
	 */
	public DeletePreparation splitAfterCurrentPrepare(int currentOffset,
			DeweyIDBuffer currentDeweyID) {
		if (currentOffset == BEFORE_LOW_KEY_OFFSET) {
			throw new IllegalArgumentException();
		}

		boolean currentIsLowKey = (currentOffset == LOW_KEY_OFFSET);

		// determine type of the node to be deleted
		BracketKey.Type currentType = currentIsLowKey ? getLowKeyType()
				: BracketKey.loadType(page, currentOffset);

		int previousOffset = currentOffset;
		XTCdeweyID previousDeweyID = currentDeweyID.getDeweyID();

		BracketKey currentKey = new BracketKey();
		int keyAreaEndOffset = getKeyAreaEndOffset();

		// navigate to next regular node

		currentDeweyID.backup();
		int startDeleteOffset = (currentIsLowKey ? getKeyAreaStartOffset()
				: (currentOffset + BracketKey.PHYSICAL_LENGTH))
				+ currentType.getDataReferenceLength();
		while (startDeleteOffset < keyAreaEndOffset) {
			currentKey.load(page, startDeleteOffset);
			currentType = currentKey.type;
			currentDeweyID.update(currentKey, false);
			if (currentType != BracketKey.Type.OVERFLOW) {
				break;
			}
			startDeleteOffset += BracketKey.PHYSICAL_LENGTH;
		}

		if (startDeleteOffset == keyAreaEndOffset) {
			// no nodes are to be deleted
			currentDeweyID.restore(false);
			return null;
		}

		XTCdeweyID startDeleteDeweyID = currentDeweyID.getDeweyID();
		currentDeweyID.restore(false);

		int endDeleteOffset = KEY_AREA_END_OFFSET;
		XTCdeweyID endDeleteDeweyID = null;
		// determine size/number of data records to be deleted
		int dataRecordSize = 0;
		int numberOfDataRecords = 0;
		// count number of nodes to be deleted
		int numberOfNodes = 1;

		currentOffset = startDeleteOffset + BracketKey.PHYSICAL_LENGTH;
		if (currentType.hasDataReference()) {
			numberOfDataRecords++;
			dataRecordSize += getValueLength(getValueOffset(currentOffset),
					true);
			currentOffset += BracketKey.DATA_REF_LENGTH;
		}

		// iterate over remaining key area
		while (currentOffset < keyAreaEndOffset) {
			// load next key from keyStorage
			currentKey.load(page, currentOffset);
			currentType = currentKey.type;

			if (currentType != BracketKey.Type.OVERFLOW) {
				numberOfNodes++;
			}

			currentOffset += BracketKey.PHYSICAL_LENGTH;
			if (currentType.hasDataReference()) {
				numberOfDataRecords++;
				dataRecordSize += getValueLength(getValueOffset(currentOffset),
						true);
				currentOffset += BracketKey.DATA_REF_LENGTH;
			}
		}

		return new DeletePreparation(previousDeweyID, previousOffset,
				startDeleteDeweyID, startDeleteOffset, endDeleteDeweyID,
				endDeleteOffset, numberOfNodes, numberOfDataRecords,
				dataRecordSize, 0);
	}

	/**
	 * Deletes the nodes specified in the DeletePreparation object.
	 * 
	 * @param delPrep
	 *            the result of the delete preparation step
	 * @param externalValueLoader
	 *            if a placeholder is needed that can not be generated due to an
	 *            externalized value, this interface is used to load the
	 *            external value
	 * @throws BracketPageException
	 */
	public void delete(DeletePreparation delPrep,
			ExternalValueLoader externalValueLoader)
			throws BracketPageException {

		if (delPrep.startDeleteOffset == LOW_KEY_OFFSET) {
			// LowKey has to be changed

			if (delPrep.endDeleteOffset == KEY_AREA_END_OFFSET) {
				// produces empty page
				clear();
				return;
			}

			byte[] newLowID = delPrep.endDeleteDeweyID.toBytes();
			int releasedSpace = (page[LOW_KEY_LENGTH_FIELD_NO] & 255) // old
																		// lowID
					+ (delPrep.endDeleteOffset + BracketKey.PHYSICAL_LENGTH - getKeyAreaStartOffset()) // deleted
																										// keys
					+ delPrep.dataRecordSize // deleted records
					- newLowID.length; // new lowID
			if (releasedSpace < 0) {
				throw new RuntimeException();
			}

			freeSpace(releasedSpace);

			// determine new LowID's type
			BracketKey.Type newLowIDType = BracketKey.loadType(page,
					delPrep.endDeleteOffset);

			// buffer remaining keys
			byte[] keyBuffer = new byte[getKeyAreaEndOffset()
					- (delPrep.endDeleteOffset + BracketKey.PHYSICAL_LENGTH)];
			System.arraycopy(page, delPrep.endDeleteOffset
					+ BracketKey.PHYSICAL_LENGTH, keyBuffer, 0,
					keyBuffer.length);

			// create new LowID
			initializeLowKey(newLowIDType, newLowID, delPrep.endDeleteDeweyID);

			// write keybuffer
			System.arraycopy(keyBuffer, 0, page, getKeyAreaStartOffset(),
					keyBuffer.length);

			setKeyAreaEndOffset(getKeyAreaStartOffset() + keyBuffer.length);

		} else {

			BracketKey.Type previousType = (delPrep.previousOffset == LOW_KEY_OFFSET) ? getLowKeyType()
					: BracketKey.loadType(page, delPrep.previousOffset);
			int startDeleteOffset = ((delPrep.previousOffset == LOW_KEY_OFFSET) ? getKeyAreaStartOffset()
					: delPrep.previousOffset)
					+ BracketKey.PHYSICAL_LENGTH
					+ previousType.getDataReferenceLength();
			int endDeleteOffset = (delPrep.endDeleteOffset == KEY_AREA_END_OFFSET) ? getKeyAreaEndOffset()
					: delPrep.endDeleteOffset;

			int releasedKeySpace = endDeleteOffset - startDeleteOffset;

			// check whether previous node needs a placeholder value
			boolean placeholderNeeded = (previousType == BracketKey.Type.NODATA);

			// calculate new keys between previous and next node
			byte[] newKeys = null;
			if (delPrep.endDeleteOffset != KEY_AREA_END_OFFSET) {
				newKeys = BracketKey.generateBracketKeys(
						delPrep.previousDeweyID, delPrep.endDeleteDeweyID);
				// update key type
				BracketKey.updateType(
						BracketKey.loadType(page, delPrep.endDeleteOffset),
						newKeys, newKeys.length - BracketKey.PHYSICAL_LENGTH);

				releasedKeySpace += BracketKey.PHYSICAL_LENGTH /* old key */
						- newKeys.length /* new key(s) */;

				if (placeholderNeeded
						&& delPrep.previousDeweyID
								.isPrefixOf(delPrep.endDeleteDeweyID)) {
					// placeholder not needed, since there is still a descendant
					// stored
					placeholderNeeded = false;
				}
			}

			// create placeholder
			byte[] placeholder = null;
			byte[] placeholderLength = null;
			if (placeholderNeeded) {
				// fetch next value
				BracketValue nextValue = getValue(delPrep.startDeleteOffset);

				byte[] value = !nextValue.externalized ? nextValue.value
						: externalValueLoader.loadExternalValue(PageID
								.fromBytes(nextValue.value));
				placeholder = placeHolderHelper.createPlaceHolderValue(value);
				placeholderLength = getValueLengthField(placeholder, false);

				// additional data reference needed
				releasedKeySpace -= BracketKey.DATA_REF_LENGTH;

				// due to placeholder generation, a defragmentation might be
				// necessary
				if (getKeyAreaEndOffset() - releasedKeySpace
						+ placeholderLength.length + placeholder.length > getFreeSpaceOffset()) {
					defragment(0);
				}
			}

			freeSpace(releasedKeySpace
					+ delPrep.dataRecordSize
					- (placeholderNeeded ? placeholderLength.length
							+ placeholder.length : 0));

			// write placeholder, if needed
			if (placeholderNeeded) {
				// change keytype
				if (delPrep.previousOffset == LOW_KEY_OFFSET) {
					setLowKeyType(BracketKey.Type.DATA);
				} else {
					BracketKey.updateType(BracketKey.Type.DATA, page,
							delPrep.previousOffset);
				}
				// write value
				writeValueReference(startDeleteOffset,
						storeValue(placeholderLength, placeholder));
				startDeleteOffset += BracketKey.DATA_REF_LENGTH;
			}

			int newEndOffset = startDeleteOffset;

			if (newKeys != null) {
				// shift remaining keys

				// buffer remaining keys
				byte[] keyBuffer = new byte[getKeyAreaEndOffset()
						- (endDeleteOffset + BracketKey.PHYSICAL_LENGTH)];
				System.arraycopy(page, endDeleteOffset
						+ BracketKey.PHYSICAL_LENGTH, keyBuffer, 0,
						keyBuffer.length);

				// write new keys
				System.arraycopy(newKeys, 0, page, newEndOffset, newKeys.length);
				newEndOffset += newKeys.length;

				// shift remaining keys
				System.arraycopy(keyBuffer, 0, page, newEndOffset,
						keyBuffer.length);
				newEndOffset += keyBuffer.length;
			}

			setKeyAreaEndOffset(newEndOffset);

		}

		setEntryCount((short) (getRecordCount() - delPrep.numberOfNodes));

	}

	/**
	 * Prepares a split operation. The split position is determined by the
	 * desired occupancy rate in this page.
	 * 
	 * @param occupancyRate
	 *            the desired occupancy rate
	 * @param tempDeweyID
	 *            DeweyIDBuffer for temporary DeweyIDs (the original content of
	 *            tempDeweyID will be preserved!)
	 * @return the delete preparation object for the nodes that are shifted to
	 *         the right page
	 */
	public DeletePreparation splitPrepare(final float occupancyRate,
			DeweyIDBuffer tempDeweyID) {
		// assert(0 <= occupancyRate <= 1)

		if (getRecordCount() < 2) {
			new RuntimeException(
					"Splitting of page with less than two nodes not supported!");
		}

		// find split position
		final int neededDataVolume = (int) Math.floor(occupancyRate
				* getUsedSpace());
		int currentDataVolume = page[LOW_KEY_LENGTH_FIELD_NO] & 255;
		int currentOffset = getKeyAreaStartOffset();

		XTCdeweyID previousDeweyID = null;
		int previousOffset = 0;
		XTCdeweyID splitDeweyID = null;
		int splitOffset = 0;

		// LowID data
		if (getLowKeyType().hasDataReference()) {
			currentDataVolume += BracketKey.DATA_REF_LENGTH
					+ getValueLength(getValueOffset(currentOffset), true);
			currentOffset += BracketKey.DATA_REF_LENGTH;

			if (currentDataVolume >= neededDataVolume) {
				// split after LowID
				previousDeweyID = getLowKey();
				previousOffset = LOW_KEY_OFFSET;
			}
		}

		tempDeweyID.backup();
		tempDeweyID.setTo(getLowKey());
		BracketKey currentKey = new BracketKey();

		// traverse page until the split position is found
		int keyAreaEndOffset = getKeyAreaEndOffset();
		while (currentOffset < keyAreaEndOffset) {
			currentKey.load(page, currentOffset);
			tempDeweyID.update(currentKey, false);

			if (previousDeweyID != null) {
				// previous node already found; navigate to the actual split key
				if (currentKey.type != BracketKey.Type.OVERFLOW) {
					splitDeweyID = tempDeweyID.getDeweyID();
					splitOffset = currentOffset;
					break;
				}
				currentOffset += BracketKey.PHYSICAL_LENGTH;
			} else {
				// look for the previous node

				currentDataVolume += BracketKey.PHYSICAL_LENGTH;
				currentOffset += BracketKey.PHYSICAL_LENGTH;

				if (currentKey.type.hasDataReference()) {
					currentDataVolume += BracketKey.DATA_REF_LENGTH
							+ getValueLength(getValueOffset(currentOffset),
									true);

					// check possible split position
					if (currentDataVolume >= neededDataVolume) {
						previousDeweyID = tempDeweyID.getDeweyID();
						previousOffset = currentOffset
								- BracketKey.PHYSICAL_LENGTH;
					}

					currentOffset += BracketKey.DATA_REF_LENGTH;
				}
			}
		}

		if (splitDeweyID == null) {
			// previous node is the last node of this page -> move at least ONE
			// node to the new page

			// buffer backup DeweyID, since the following navigation will
			// destroy the backup
			XTCdeweyID backupDeweyID = tempDeweyID.getBackupDeweyID();
			tempDeweyID.resetBackup();

			NavigationResult previous = navigatePrevious(previousOffset,
					tempDeweyID);

			splitDeweyID = previousDeweyID;
			splitOffset = previousOffset;
			previousDeweyID = tempDeweyID.getDeweyID();
			previousOffset = previous.keyOffset;

			// restore original DeweyID
			tempDeweyID.setTo(backupDeweyID);
		} else {
			// restore original DeweyID
			tempDeweyID.restore(false);
		}

		// split position determined; create DeletePreparation
		int numberOfNodes = 0;
		int numberOfDataRecords = 0;
		int dataRecordSize = 0;

		currentOffset = splitOffset;
		while (currentOffset < keyAreaEndOffset) {
			BracketKey.Type currentType = BracketKey.loadType(page,
					currentOffset);
			currentOffset += BracketKey.PHYSICAL_LENGTH;

			if (currentType != BracketKey.Type.OVERFLOW) {
				numberOfNodes++;
				if (currentType != BracketKey.Type.NODATA) {
					numberOfDataRecords++;
					dataRecordSize += getValueLength(
							getValueOffset(currentOffset), true);
					currentOffset += BracketKey.DATA_REF_LENGTH;
				}
			}
		}

		return new DeletePreparation(previousDeweyID, previousOffset,
				splitDeweyID, splitOffset, null, KEY_AREA_END_OFFSET,
				numberOfNodes, numberOfDataRecords, dataRecordSize, 0);
	}

	/**
	 * Checks the integrity of this bracket page. Throws a RuntimeException if
	 * there is something wrong.
	 */
	private void checkPageIntegrity() {

		if (this.getRecordCount() == 0) {
			if (getKeyAreaStartOffset() != getKeyAreaEndOffset()) {
				throw new RuntimeException(
						"StartKeyOffset and/or KeyAreaEndOffset have wrong values!");
			}
			return;
		} else {
			if (getKeyAreaStartOffset() >= getKeyAreaEndOffset()) {
				throw new RuntimeException("KeyAreaEndOffset is too low!");
			}
		}

		DeweyIDBuffer buffer = new DeweyIDBuffer(null, getLowKey());

		BracketKey.Type currentType = getLowKeyType();
		BracketKey currentKey = new BracketKey();
		int currentOffset = getKeyAreaStartOffset();

		int numberOfNodes = 0;

		int dataSize = (page[LOW_KEY_LENGTH_FIELD_NO] & 255);
		int contextDataOffset = getContextDataOffset();
		if (contextDataOffset != 0) {
			dataSize += getValueLength(contextDataOffset, true);
		}

		if (currentType == BracketKey.Type.OVERFLOW) {
			throw new RuntimeException("Page begins with an overflow key!");
		} else {
			numberOfNodes++;
		}

		if (currentType.hasDataReference()) {
			dataSize += BracketKey.DATA_REF_LENGTH
					+ getValueLength(getValueOffset(currentOffset), true);
			currentOffset += BracketKey.DATA_REF_LENGTH;
		}

		int keyAreaEndOffset = getKeyAreaEndOffset();
		while (currentOffset < keyAreaEndOffset) {
			currentKey.load(page, currentOffset);
			currentType = currentKey.type;
			buffer.update(currentKey, false);
			dataSize += BracketKey.PHYSICAL_LENGTH;
			currentOffset += BracketKey.PHYSICAL_LENGTH;
			if (currentType != BracketKey.Type.OVERFLOW) {
				numberOfNodes++;
				if (currentType != BracketKey.Type.NODATA) {
					dataSize += BracketKey.DATA_REF_LENGTH
							+ getValueLength(getValueOffset(currentOffset),
									true);
					currentOffset += BracketKey.DATA_REF_LENGTH;
				}
			}
		}

		if (currentOffset != keyAreaEndOffset) {
			throw new RuntimeException("KeyAreaEndOffset has a wrong value!");
		}

		if (currentType == BracketKey.Type.OVERFLOW) {
			throw new RuntimeException("Page ends with an overflow key!");
		}

		if (numberOfNodes != getRecordCount()) {
			throw new RuntimeException("Wrong record count!");
		}

		if (dataSize != getUsedSpace()) {
			throw new RuntimeException("UsedSpace has a wrong value! ("
					+ getUsedSpace() + " instead of " + dataSize + ")");
		}
	}

	/**
	 * Inserts a sequence of bracket nodes. If successful, the tempDeweyID
	 * buffer will contain the DeweyID of the last inserted node.
	 * 
	 * @param nodes
	 *            the nodes to insert
	 * @param tempDeweyID
	 *            DeweyIDBuffer for temporary DeweyIDs (original Buffer value
	 *            will NOT be preserved!)
	 * @return offset of the LAST inserted node or an errorcode (if new node is
	 *         a duplicate or there is not enough space)
	 */
	public int insertSequence(BracketNodeSequence nodes,
			DeweyIDBuffer tempDeweyID) {

		final byte[] data = nodes.getData();
		int currentOffset = BEFORE_LOW_KEY_OFFSET;
		
		if (data == null || data.length == 0) {
			return currentOffset;
		}
		
		XTCdeweyID dataLowKey = nodes.getLowKey();
		
		// look for insertion position of the node sequence
		NavigationResult navRes = navigateToInsertPos(dataLowKey, tempDeweyID);
		
		if (navRes.status != NavigationStatus.FOUND) {
			// duplicate detected
			return INSERTION_DUPLICATE;
		}
		
		// insert sequence after the found position
		currentOffset = navRes.keyOffset;
		
		return insertSequenceAfter(nodes, currentOffset, tempDeweyID);		
	}

	/**
	 * Inserts a sequence of bracket nodes after the current one. If successful,
	 * the currentDeweyID buffer will contain the DeweyID of the last inserted
	 * node.
	 * 
	 * @param nodes
	 *            the nodes to insert
	 * @param currentOffset
	 *            offset of the current node
	 * @param currentDeweyID
	 *            DeweyIDBuffer containing the DeweyID of the current node
	 * @return offset of the LAST inserted node or an errorcode (if new node is
	 *         a duplicate or there is not enough space)
	 */
	public int insertSequenceAfter(BracketNodeSequence nodes,
			int currentOffset, DeweyIDBuffer currentDeweyID) {

		final byte[] data = nodes.getData();

		if (data == null || data.length == 0) {
			return currentOffset;
		}

		final XTCdeweyID dataLowID = nodes.getLowKey();
		final BracketKey.Type dataLowIDType = nodes.getLowKeyType();

		currentDeweyID.backup();
		final int lastNodeOffset = nodes.setToLastNode(currentDeweyID);
		final XTCdeweyID lastNodeDeweyID = currentDeweyID.getDeweyID();
		currentDeweyID.restore(false);
		boolean removeLastDataRecord = false;
		boolean removeCurrentDataRecord = false;

		final boolean insertAtBeginning = (currentOffset == BEFORE_LOW_KEY_OFFSET);
		byte[] beforeKeys = null;
		byte[] afterKeys = null;
		int currentValueOffset = 0;
		int currentRecordLength = 0;
		int currentValueRefOffset = 0;
		int nextKeyOffset = 0;

		final boolean currentNodeIsLowKey = (currentOffset == LOW_KEY_OFFSET);

		// read current bracket key type
		BracketKey.Type currentKeyType = insertAtBeginning ? null
				: (currentNodeIsLowKey ? getLowKeyType() : BracketKey.loadType(
						page, currentOffset));

		// determine required space
		int requiredSpace = 0;

		if (insertAtBeginning) {

			requiredSpace = data.length - 2 /* LowID length + LowID type */
					+ nodes.getNumberOfDataRecords()
					* BracketKey.DATA_REF_LENGTH /* for data references */;

			if (this.getRecordCount() > 0) {
				// there are already records stored in this page

				// calculate keys between last DeweyID and current low key
				afterKeys = BracketKey.generateBracketKeys(lastNodeDeweyID,
						getLowKey());
				BracketKey.updateType(getLowKeyType(), afterKeys,
						afterKeys.length - BracketKey.PHYSICAL_LENGTH);

				// adjust required space
				requiredSpace += afterKeys.length
						- (page[LOW_KEY_LENGTH_FIELD_NO] & 255);

				// determine whether last data record is necessary
				if (lastNodeDeweyID.isPrefixOf(getLowKey())) {
					removeLastDataRecord = true;
					requiredSpace -= (BracketKey.DATA_REF_LENGTH + nodes
							.getValueLength(lastNodeOffset));
				}
			}
		} else {
			// insert node chain between two nodes

			// calculate beforeKeys
			beforeKeys = BracketKey.generateBracketKeys(currentDeweyID,
					dataLowID);
			// update last bracket key's type
			BracketKey.updateType(dataLowIDType, beforeKeys, beforeKeys.length
					- BracketKey.PHYSICAL_LENGTH);

			requiredSpace = beforeKeys.length + data.length
					- nodes.getStartOffset() /* bracket keys + data records */
					+ nodes.getNumberOfDataRecords()
					* BracketKey.DATA_REF_LENGTH /* data references */;

			// check whether current record can be removed
			if (currentKeyType == BracketKey.Type.DATA
					&& currentDeweyID.isPrefixOf(dataLowID)) {

				// data record can be removed
				removeCurrentDataRecord = true;

				// determine current record length
				currentValueRefOffset = currentNodeIsLowKey ? getKeyAreaStartOffset()
						: currentOffset + BracketKey.PHYSICAL_LENGTH;
				currentValueOffset = getValueOffset(currentValueRefOffset);
				currentRecordLength = getValueLength(currentValueOffset, true);

				// adjust required space
				requiredSpace -= (BracketKey.DATA_REF_LENGTH + currentRecordLength);

			}

			// determine offset for the next key
			nextKeyOffset = (currentNodeIsLowKey ? getKeyAreaStartOffset()
					: currentOffset + BracketKey.PHYSICAL_LENGTH)
					+ currentKeyType.getDataReferenceLength();

			// calculate afterKey(s)
			if (nextKeyOffset < getKeyAreaEndOffset()) {
				// determine next node's DeweyID
				currentDeweyID.backup();
				currentDeweyID.update(BracketKey.loadNew(page, nextKeyOffset),
						false);

				afterKeys = BracketKey.generateBracketKeys(lastNodeDeweyID,
						currentDeweyID);
				currentDeweyID.restore(false);
			}

		}

		// required space determined

		// reservedSpace for the highKey
		int reservedSpace = 2 * lastNodeDeweyID.toBytes().length;
		// allocate required space
		if (!allocateRequiredSpace(requiredSpace, reservedSpace)) {
			// page is full
			return INSERTION_NO_SPACE;
		}

		// check whether a defragmentation is needed
		int reqSpaceBetweenKeyAndValueArea = requiredSpace
				+ (removeCurrentDataRecord ? currentRecordLength : 0);
		if (reqSpaceBetweenKeyAndValueArea > getFreeSpaceOffset()
				- getKeyAreaEndOffset()) {
			// defragmentation
			defragment(removeCurrentDataRecord ? currentValueRefOffset : 0);
		}

		// buffer key area
		byte[] keyBuffer = null;
		if (afterKeys != null) {
			int startIndex = insertAtBeginning ? getKeyAreaStartOffset()
					: nextKeyOffset;

			keyBuffer = new byte[getKeyAreaEndOffset() - startIndex];
			System.arraycopy(page, startIndex, keyBuffer, 0, keyBuffer.length);

			if (!insertAtBeginning) {
				// update the afterKey in the buffer
				BracketKey.loadNew(afterKeys, 0).store(keyBuffer, 0, true);
			}
		}

		// write new LowID / write beforeKeys
		int pageOffset = 0;
		int returnOffset = 0;
		if (insertAtBeginning) {
			BracketKey.Type newLowIDType = (removeLastDataRecord && lastNodeOffset == BracketNodeSequence.LOW_KEY_OFFSET) ? BracketKey.Type.NODATA
					: dataLowIDType;
			// write lowID
			initializeLowKey(newLowIDType, dataLowID.toBytes(), dataLowID);
			pageOffset = getKeyAreaStartOffset();
			returnOffset = LOW_KEY_OFFSET;
			addRecord(); /* LowID added */
		} else {
			// change current node's key type to NODATA, if necessary
			if (removeCurrentDataRecord) {
				currentKeyType = BracketKey.Type.NODATA;
				if (currentNodeIsLowKey) {
					setLowKeyType(currentKeyType);
				} else {
					BracketKey.updateType(currentKeyType, page, currentOffset);
				}
				pageOffset = currentValueRefOffset;
			} else {
				pageOffset = nextKeyOffset;
			}

			// write beforeKeys
			System.arraycopy(beforeKeys, 0, page, pageOffset, beforeKeys.length);
			pageOffset += beforeKeys.length;

			returnOffset = pageOffset - BracketKey.PHYSICAL_LENGTH;

			// count newly inserted nodes
			for (int i = 0; i < beforeKeys.length; i += BracketKey.PHYSICAL_LENGTH) {
				if (BracketKey.loadType(beforeKeys, i) != BracketKey.Type.OVERFLOW) {
					addRecord();
				}
			}
		}

		// beforeKeys written; copy BracketNodeSequence
		int dataOffset = nodes.getStartOffset();
		if (dataLowIDType.hasDataReference()) {
			// copy first data record
			if (removeLastDataRecord
					&& lastNodeOffset == BracketNodeSequence.LOW_KEY_OFFSET) {
				dataOffset = data.length;
			} else {
				int valueLength = getValueLength(dataOffset, true, data);
				writeValueReference(pageOffset,
						copyValue(data, dataOffset, valueLength));
				pageOffset += BracketKey.DATA_REF_LENGTH;
				dataOffset += valueLength;
			}
		}
		// copy remaining keys + records
		BracketKey.Type keyType = null;
		while (dataOffset < data.length) {
			// write bracket key
			System.arraycopy(data, dataOffset, page, pageOffset,
					BracketKey.PHYSICAL_LENGTH);
			keyType = BracketKey.loadType(data, dataOffset);
			returnOffset = pageOffset;
			pageOffset += BracketKey.PHYSICAL_LENGTH;
			dataOffset += BracketKey.PHYSICAL_LENGTH;
			if (keyType != BracketKey.Type.OVERFLOW) {
				addRecord();
				if (keyType != BracketKey.Type.NODATA) {
					// write data record
					if (removeLastDataRecord && lastNodeOffset <= dataOffset) {
						// do not copy record; update last written key to NODATA
						BracketKey.updateType(BracketKey.Type.NODATA, page,
								pageOffset - BracketKey.PHYSICAL_LENGTH);
						dataOffset = data.length;
					} else {
						int valueLength = getValueLength(dataOffset, true, data);
						writeValueReference(pageOffset,
								copyValue(data, dataOffset, valueLength));
						pageOffset += BracketKey.DATA_REF_LENGTH;
						dataOffset += valueLength;
					}
				}
			}
		}

		// bracket node chain stored -> deal with afterKeys
		if (insertAtBeginning && afterKeys != null) {
			System.arraycopy(afterKeys, 0, page, pageOffset, afterKeys.length);
			pageOffset += afterKeys.length;
		}

		// write back the buffered keys
		if (keyBuffer != null) {
			System.arraycopy(keyBuffer, 0, page, pageOffset, keyBuffer.length);
			pageOffset += keyBuffer.length;
		}

		// adjust key area end offset
		setKeyAreaEndOffset(pageOffset);

		currentDeweyID.setTo(lastNodeDeweyID);
		return returnOffset;
	}

}
