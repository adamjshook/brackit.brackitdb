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
package org.brackit.server.store.index.blink.page;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.blob.impl.SimpleBlobStore;
import org.brackit.server.store.index.blink.IndexOperationException;
import org.brackit.server.store.index.blink.PageType;
import org.brackit.server.store.index.blink.log.BlinkIndexLogOperation;
import org.brackit.server.store.index.blink.log.BlinkIndexLogOperationHelper;
import org.brackit.server.store.page.BufferedPage;
import org.brackit.server.store.page.RecordFlag;
import org.brackit.server.store.page.keyvalue.KeyValuePage;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.TxException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.SizeConstants;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class DirectKeyValuePageContext extends SimpleBlobStore implements
		PageContext {
	private static final Logger log = Logger
			.getLogger(DirectKeyValuePageContext.class);

	private static final int FLAG_FIELD_NO = 0;
	private static final int PAGE_TYPE_FIELD_NO = 1;
	private static final int KEY_TYPE_FIELD_NO = 2;
	private static final int VALUE_TYPE_FIELD_NO = 3;
	private static final int UNIT_ID_FIELD_NO = 4;
	private static final int HEIGHT_FIELD_NO = 5;
	private static final int PREV_PAGE_FIELD_NO = 5 + SizeConstants.INT_SIZE;
	private static final int LOW_PAGE_FIELD_NO = PREV_PAGE_FIELD_NO
			+ PageID.getSize();

	private static final byte UNIQUE_FLAG = 1;
	private static final byte LAST_IN_LEVEL_FLAG = 2;
	private static final byte COMPRESSED_FLAG = 8;

	public static final int RESERVED_SIZE = LOW_PAGE_FIELD_NO
			+ PageID.getSize();

	protected final KeyValuePage page;

	protected int currentPos;

	protected Field keyType = Field.NULL;

	protected Field valueType = Field.NULL;

	protected int pageType;

	protected final Tx transaction;

	protected int reservedOffset;

	protected int entryCount;

	public DirectKeyValuePageContext(BufferMgr bufferMgr, Tx transaction,
			KeyValuePage page) {
		super(bufferMgr);
		this.transaction = transaction;
		this.page = page;
		this.reservedOffset = page.getReservedOffset();
		init();
	}

	public void init() {
		currentPos = 0;
		entryCount = page.getRecordCount();
		byte[] value = page.getHandle().page;
		pageType = value[reservedOffset + PAGE_TYPE_FIELD_NO];
		keyType = Field.fromId(value[reservedOffset + KEY_TYPE_FIELD_NO]);
		valueType = Field.fromId(value[reservedOffset + VALUE_TYPE_FIELD_NO]);
	}

	@Override
	public int getEntryCount() {
		return entryCount;
	}

	@Override
	public int getUnitID() {
		byte[] buffer = page.getHandle().page;
		int offset = reservedOffset + UNIT_ID_FIELD_NO;
		int id = ((buffer[offset] & 255) << 24)
				| ((buffer[offset + 1] & 255) << 16)
				| ((buffer[offset + 2] & 255) << 8)
				| (buffer[offset + 3] & 255);
		return id;
	}

	private void setUnitID(int id) {
		byte[] buffer = page.getHandle().page;
		int offset = reservedOffset + UNIT_ID_FIELD_NO;
		buffer[offset] = (byte) ((id >> 24) & 255);
		buffer[offset + 1] = (byte) ((id >> 16) & 255);
		buffer[offset + 2] = (byte) ((id >> 8) & 255);
		buffer[offset + 3] = (byte) (id & 255);
	}

	@Override
	public int getPageType() {
		return pageType;
	}

	private void setPageType(int pageType) {
		byte[] value = page.getHandle().page;
		value[reservedOffset + PAGE_TYPE_FIELD_NO] = (byte) pageType;
		this.pageType = pageType;
	}

	@Override
	public int getHeight() {
		byte[] value = page.getHandle().page;
		return value[reservedOffset + HEIGHT_FIELD_NO] & 255;
	}

	protected void setHeight(int height) {
		byte[] value = page.getHandle().page;
		value[reservedOffset + HEIGHT_FIELD_NO] = (byte) height;
	}

	@Override
	public boolean isUnique() {
		return checkFlag(UNIQUE_FLAG);
	}

	private void setUnique(boolean unique) {
		setFlag(UNIQUE_FLAG, unique);
	}

	@Override
	public boolean isCompressed() {
		return checkFlag(COMPRESSED_FLAG);
	}

	public void setCompressed(boolean compressed) {
		setFlag(COMPRESSED_FLAG, compressed);
	}

	@Override
	public boolean isLastInLevel() {
		return checkFlag(LAST_IN_LEVEL_FLAG);
	}

	@Override
	public void setLastInLevel(boolean last) {
		setFlag(LAST_IN_LEVEL_FLAG, last);
	}

	private void setFlag(byte flagMask, boolean flag) {
		byte[] flags = page.getHandle().page;

		if (flag) {
			flags[reservedOffset + FLAG_FIELD_NO] = (byte) (flagMask | flags[reservedOffset
					+ FLAG_FIELD_NO]);
		} else {
			flags[reservedOffset + FLAG_FIELD_NO] = (byte) (~flagMask & flags[reservedOffset
					+ FLAG_FIELD_NO]);
		}
	}

	private boolean checkFlag(byte flagMask) {
		byte[] value = page.getHandle().page;
		return ((value[reservedOffset + FLAG_FIELD_NO] & flagMask) != 0);
	}

	@Override
	public Field getKeyType() {
		return keyType;
	}

	private void setKeyType(Field keyType) {
		byte[] value = page.getHandle().page;
		value[reservedOffset + KEY_TYPE_FIELD_NO] = (byte) keyType.ID;
		this.keyType = keyType;
	}

	@Override
	public Field getValueType() {
		return valueType;
	}

	private void setValueType(Field valueType) {
		byte[] value = page.getHandle().page;
		value[reservedOffset + VALUE_TYPE_FIELD_NO] = (byte) valueType.ID;
		this.valueType = valueType;
	}

	@Override
	public PageID getPrevPageID() {
		byte[] value = page.getHandle().page;
		return PageID.fromBytes(value, reservedOffset + PREV_PAGE_FIELD_NO);
	}

	@Override
	public void setPrevPageID(PageID prevPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = BlinkIndexLogOperationHelper
					.createrPointerLogOperation(
							BlinkIndexLogOperation.PREV_PAGE, getPageID(),
							getRootPageID(), getLowPageID(), prevPageID);
		}

		byte[] value = page.getHandle().page;
		if (prevPageID != null) {
			prevPageID.toBytes(value, reservedOffset + PREV_PAGE_FIELD_NO);
		} else {
			PageID.noPageToBytes(value, reservedOffset + PREV_PAGE_FIELD_NO);
		}

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setModified(true); // not covered by pageID to
			// bytes
			page.getHandle().setAssignedTo(transaction);
		}
	}

	@Override
	public PageID getLowPageID() {
		byte[] value = page.getHandle().page;
		return PageID.fromBytes(value, reservedOffset + LOW_PAGE_FIELD_NO);
	}

	@Override
	public void setLowPageID(PageID beforePageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = BlinkIndexLogOperationHelper
					.createrPointerLogOperation(
							BlinkIndexLogOperation.BEFORE_PAGE, getPageID(),
							getRootPageID(), getLowPageID(), beforePageID);
		}

		byte[] value = page.getHandle().page;
		if (beforePageID != null) {
			beforePageID.toBytes(value, reservedOffset + LOW_PAGE_FIELD_NO);
		} else {
			PageID.noPageToBytes(value, reservedOffset + LOW_PAGE_FIELD_NO);
		}

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setModified(true); // not covered by pageID to
			// bytes
			page.getHandle().setAssignedTo(transaction);
		}
	}

	@Override
	public int calcMaxInlineValueSize(int maxKeySize) {
		return page.calcMaxInlineValueSize(3, maxKeySize);
	}

	@Override
	public int calcMaxKeySize() {
		throw new RuntimeException("Not implemented yet");
	}

	@Override
	public boolean insert(byte[] key, byte[] value,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		LogOperation operation = null;
		byte type = (isStructureModification) ? BlinkIndexLogOperation.SMO_INSERT
				: BlinkIndexLogOperation.USER_INSERT;
		boolean externalize = externalizeValue(value);

		// TODO if externalize check if insert of pointer will succeed

		// if ((getKey() != null) && (getKeyType().compare(getKey(), key) < 0))
		// {
		// System.out.println(dump("Tried to insert " +
		// getKeyType().valueToString(key) + " before " +
		// getKeyType().valueToString(getKey())));
		// throw new RuntimeException();
		// }
		//		
		// if ((currentPos > 0) && (getKeyType().compare(page.getKey(currentPos
		// - 1), key) > 0))
		// {
		// System.out.println(dump("Tried to insert " +
		// getKeyType().valueToString(key) + " after " +
		// getKeyType().valueToString(page.getKey(currentPos - 1))));
		// throw new RuntimeException();
		// }

		if (logged) {
			operation = BlinkIndexLogOperationHelper.createUpdateLogOperation(
					type, getPageID(), getRootPageID(), key, value, null);
		}

		if (externalize) {
			value = externalize(value);
		}

		if (!page.insert(currentPos, key, value, isCompressed())) {
			return false;
		}

		if (externalize) {
			page.setFlag(currentPos, RecordFlag.EXTERNALIZED, true);
		}

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(transaction);
		}
		entryCount++;

		return true;
	}

	private boolean externalizeValue(byte[] value) {
		return (value.length > page.getUsableSpace() / 6);
	}

	private byte[] externalize(byte[] value) throws IndexOperationException {
		try {
			PageID blobPageID = create(transaction, page.getPageID()
					.getContainerNo());
			write(transaction, blobPageID, value, false);
			value = blobPageID.getBytes();
		} catch (BlobStoreAccessException e) {
			throw new IndexOperationException(e,
					"Error writing large value to overflow blob");
		}
		return value;
	}

	@Override
	public void setValue(byte[] value, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;
		byte type = (isStructureModification) ? BlinkIndexLogOperation.SMO_UPDATE
				: BlinkIndexLogOperation.USER_UPDATE;

		if (logged) {
			operation = BlinkIndexLogOperationHelper.createUpdateLogOperation(
					type, getPageID(), getRootPageID(), getKey(), value,
					getValue());
		}

		byte[] oldValue = page.getValue(currentPos);

		boolean externalize = externalizeValue(value);

		if (externalize) {
			value = externalize(value);
		}

		if (!page.setValue(currentPos, value)) {
			throw new RuntimeException();
		}

		if (page.checkFlag(currentPos, RecordFlag.EXTERNALIZED)) {
			deleteExternalized(oldValue);

			if (!externalize) {
				page.setFlag(currentPos, RecordFlag.EXTERNALIZED, false);
			}
		} else if (externalize) {
			page.setFlag(currentPos, RecordFlag.EXTERNALIZED, true);
		}

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(transaction);
		}
	}

	@Override
	public void delete(boolean isStructureModification, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;
		byte type = (isStructureModification) ? BlinkIndexLogOperation.SMO_DELETE
				: BlinkIndexLogOperation.USER_DELETE;

		if (logged) {
			operation = BlinkIndexLogOperationHelper.createUpdateLogOperation(
					type, getPageID(), getRootPageID(), getKey(), getValue(),
					null);
		}

		if (page.checkFlag(currentPos, RecordFlag.EXTERNALIZED)) {
			byte[] oldValue = page.getValue(currentPos);
			deleteExternalized(oldValue);
		}

		page.delete(currentPos);

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(transaction);
		}
		entryCount--;
	}

	private void deleteExternalized(byte[] oldValue)
			throws IndexOperationException {
		try {
			PageID blobPageID = PageID.fromBytes(oldValue);
			drop(transaction, blobPageID);
		} catch (BlobStoreAccessException e) {
			throw new IndexOperationException(e,
					"Error deleting overflow value.");
		}
	}

	@Override
	public void format(int unitID, int pageType, PageID rootPageID,
			Field keyType, Field valueType, int height, boolean unique,
			boolean compressed, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = BlinkIndexLogOperationHelper.createFormatLogOperation(
					getPageID(), getUnitID(), unitID, rootPageID,
					getPageType(), pageType, getKeyType(), keyType,
					getValueType(), valueType, getHeight(), height, isUnique(),
					unique, isCompressed(), compressed);
		}

		/*
		 * Change page type, init free space info management, and reset entry
		 * counter.
		 */
		page.clear();

		/*
		 * Set index specific fields.
		 */
		// byte[] key = new byte[0];
		// byte[] value = new byte[4 + ((pageType == PageType.INDEX_LEAF) ? 2 :
		// 1)* PageID.getSize()];
		// page.insert(0, key, value, compressed);

		setUnitID(unitID);
		setPageType(pageType);
		setHeight(height);
		page.setBasePageID(rootPageID);
		setKeyType(keyType);
		setValueType(valueType);
		setUnique(unique);
		setCompressed(compressed);

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(transaction);
		}
	}

	@Override
	public PageID getRootPageID() {
		return page.getBasePageID();
	}

	@Override
	public byte[] getKey() {
		return (currentPos < entryCount) ? page.getKey(currentPos) : null;
	}

	@Override
	public byte[] getValue() throws IndexOperationException {
		return getValue(currentPos);
	}

	private final byte[] getValue(int pos) throws IndexOperationException {
		if ((pos < 0) || (pos >= entryCount)) {
			return null;
		}

		byte[] value = page.getValue(pos);

		if (page.checkFlag(pos, RecordFlag.EXTERNALIZED)) {
			PageID blobPageID = PageID.fromBytes(value);

			try {
				value = read(transaction, blobPageID);
			} catch (BlobStoreAccessException e) {
				throw new IndexOperationException(
						e,
						"Error reading externalized value at position %s from blob %s",
						pos, blobPageID);
			}
		}

		return value;
	}

	@Override
	public PageID getValueAsPageID() throws IndexOperationException {
		byte[] value = getValue();
		return (value != null) ? PageID.fromBytes(value) : null;
	}

	@Override
	public void setPageIDAsValue(PageID pageID, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = BlinkIndexLogOperationHelper.createUpdateLogOperation(
					BlinkIndexLogOperation.SMO_UPDATE, getPageID(),
					getRootPageID(), getKey(), pageID.getBytes(), getValue());
		}

		byte[] value = (pageID != null) ? pageID.getBytes() : PageID
				.noPageBytes();
		setValue(value, true, logged, undoNextLSN);

		if (logged) {
			log(transaction, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(transaction);
		}
	}

	@Override
	public boolean hasNext() {
		if (currentPos < entryCount - 1) {
			currentPos++;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveNext() {
		if (currentPos < entryCount) {
			currentPos++;
			return (currentPos < entryCount);
		} else {
			return false;
		}
	}

	@Override
	public boolean isExternalized(int position) {
		position -= 1; // external view is currentPos +1
		return page.checkFlag(position, RecordFlag.EXTERNALIZED);
	}

	@Override
	public int getPosition() {
		return currentPos + 1; // external view is currentPos +1
	}

	@Override
	public boolean moveTo(int position) {
		position -= 1; // external view is currentPos +1
		if ((position >= 0) && (position <= entryCount)) {
			currentPos = position;
			return true;
		}

		return false;
	}

	@Override
	public boolean hasPrevious() {
		if (currentPos > 0) {
			currentPos--;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void moveAfterLast() {
		currentPos = entryCount;
	}

	@Override
	public boolean moveFirst() {
		currentPos = 0;
		return (entryCount > 0);
	}

	@Override
	public void moveLast() {
		currentPos = (entryCount > 0) ? entryCount - 1 : 0;
	}

	@Override
	public boolean isAfterLast() {
		return (currentPos >= entryCount);
	}

	@Override
	public int search(SearchMode searchMode, byte[] searchKey,
			byte[] searchValue) throws IndexOperationException {
		Field keyType = getKeyType();
		Field valueType = getValueType();
		int recordCount = page.getRecordCount();
		int minSlotNo = 0;
		int maxSlotNo = recordCount - 1;
		int result = 0;

		if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Searching for %s (%s, %s) in page %s.",
					searchMode, keyType.toString(searchKey), valueType
							.toString(searchValue), getPageID()));
		}

		if (searchMode.isRandom()) {
			int nextSlot = minSlotNo + searchMode.nextInt(maxSlotNo);

			if (nextSlot == minSlotNo) {
				result = 0;
			} else if (nextSlot == maxSlotNo) {
				result = searchMode.nextInt(1);
			} else {
				result = -searchMode.nextInt(1);
			}

			currentPos = nextSlot;
		} else if (recordCount > 0) {
			int slotNo = findPos(searchMode, keyType, 0, searchKey, minSlotNo,
					maxSlotNo);

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("Initial binary search for"
						+ " %s (%s, %s) in page %s "
						+ "returned slot %s of %s : (%s, %s).", searchMode,
						keyType.toString(searchKey), valueType
								.toString(searchValue), getPageID(), slotNo,
						maxSlotNo, keyType.toString(page.getKey(slotNo)),
						valueType.toString(getValue(slotNo))));
			}

			if (searchMode.findGreatestInside()) {
				if ((isUnique()) || (searchValue == null)) {
					// either we have found a non-first record or it does
					// fulfill the search criteria
					// the value must not be regarded
					boolean foundRecordInside = ((minSlotNo < slotNo) || (searchMode
							.isInside(keyType, page.getKey(slotNo), searchKey)));
					result = foundRecordInside ? 0 : -1;
				} else if (!searchMode.isInside(keyType, page.getKey(slotNo),
						searchKey)) {
					// the key is not inside hence we must be at the first
					// entry, but it does not fulfill the search criteria
					result = -1;
				} else if (searchMode.isInside(valueType, getValue(slotNo),
						searchValue)) {
					// we are at the greatest entry with the key inside and its
					// value fulfills the search criteria
					result = 0;
				} else {
					// slotNo is at the greatest entry where the key fulfills
					// the search criteria but its value does not.
					// Perform a second level search to find the smallest entry
					// with the same key in the page
					int minSlotNoWithSameKey = findPos(
							SearchMode.GREATER_OR_EQUAL, keyType, 0, page
									.getKey(slotNo), minSlotNo, slotNo);

					if (searchMode.isInside(valueType,
							getValue(minSlotNoWithSameKey), searchValue)) {
						// value of the greatest entry with same key is inside
						// the search space.
						// Perform third level search to find the greatest entry
						// with same key having value inside search space
						slotNo = findPos(searchMode, valueType, 1, searchValue,
								minSlotNoWithSameKey, slotNo);
						result = 0;
					} else if (minSlotNoWithSameKey > 1) {
						// the next smallest entry has a smaller key and is
						// therefore also inside the search space
						// the value must not be regarded
						slotNo = minSlotNoWithSameKey - 1;
						result = 0;
					} else {
						// there is no next smallest entry, but the current does
						// not fulfill the search criteria
						slotNo = minSlotNoWithSameKey;
						result = -1;
					}
				}

				currentPos = slotNo;
			} else {
				if ((isUnique()) || (searchValue == null)) {
					// either the key a of non-last record or the last record
					// fulfills the search criteria
					// the value must not be regarded
					boolean foundRecordInside = ((slotNo < maxSlotNo) || (searchMode
							.isInside(keyType, page.getKey(slotNo), searchKey)));
					result = foundRecordInside ? 0 : 1;
				} else if (!searchMode.isInside(keyType, page.getKey(slotNo),
						searchKey)) {
					// the key is not inside hence we must be at the last entry,
					// but it does not fulfill the search criteria
					result = 1;
				} else if (searchMode.isInside(valueType, getValue(slotNo),
						searchValue)) {
					// we are at the smallest entry with the key inside and its
					// value fulfills the search criteria
					result = 0;
				} else if (SearchMode.GREATER.isInside(keyType, page
						.getKey(slotNo), searchKey)) {
					// we are at the smallest entry with the key inside and its
					// value fulfills the search criteria
					result = 0;
				} else {
					// slotNo is at the smallest entry where the key fulfills
					// the search criteria but its value does not.
					// Perform a second level search to find the greatest entry
					// with this key in the page
					int maxSlotNoWithSameKey = findPos(
							SearchMode.LESS_OR_EQUAL, keyType, 0, page
									.getKey(slotNo), slotNo, maxSlotNo);

					if (searchMode.isInside(valueType,
							getValue(maxSlotNoWithSameKey), searchValue)) {
						// value of the greatest entry with same key is inside
						// the search space.
						// Perform third level search to find the smallest entry
						// with same key having value inside search space
						slotNo = findPos(searchMode, valueType, 1, searchValue,
								slotNo, maxSlotNoWithSameKey);
						result = 0;
					} else if (maxSlotNoWithSameKey < maxSlotNo) {
						// the next greatest entry has a greater key and is
						// therefore also inside the search space
						// the value must not be regarded
						slotNo = maxSlotNoWithSameKey + 1;
						result = 0;
					} else {
						// there is no next greatest entry, but the current does
						// not fulfill the search criteria
						slotNo = maxSlotNoWithSameKey;
						result = 1;
					}
				}

				if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
					log.trace(String.format("Search for %s (%s, %s)"
							+ " in page %s returns %s " + "at %s->(%s, %s).",
							searchMode, keyType.toString(searchKey), valueType
									.toString(searchValue), getPageID(),
							result, slotNo, keyType.toString(page
									.getKey(slotNo)), valueType
									.toString(getValue(slotNo))));
				}

				currentPos = slotNo;
			}
		}

		return result;
	}

	@Override
	public PageID searchNextPageID(SearchMode searchMode, byte[] searchKey)
			throws IndexOperationException {
		int recordCount = page.getRecordCount();
		int minSlotNo = 0;
		int maxSlotNo = recordCount - 1;
		Field keyType = getKeyType();

		if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Determining next child"
					+ " page searching for %s %s in page %s.", searchMode,
					keyType.toString(searchKey), getPageID()));
		}

		if (recordCount == 0) {
			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("Determining before page "
						+ "id of emptied page search "
						+ "for %s %s in page %s.", searchMode, keyType
						.toString(searchKey), getPageID()));
			}
			return getLowPageID();
		}

		if (searchMode.isRandom()) {
			int nextSlot = searchMode.nextInt(recordCount);

			if (nextSlot <= maxSlotNo) {
				currentPos = nextSlot;
				return PageID.fromBytes(page.getValue(nextSlot - 1));
			} else {
				currentPos = maxSlotNo;
				return getValueAsPageID();
			}
		} else {
			int slotNo = findPos(searchMode, keyType, 0, searchKey, minSlotNo,
					maxSlotNo);

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("Initial binary search "
						+ "for %s (%s) in page %s "
						+ "returned slot %s of %s : (%s, %s).", searchMode,
						keyType.toString(searchKey), getPageID(), slotNo,
						maxSlotNo, keyType.toString(page.getKey(slotNo)),
						valueType.toString(getValue(slotNo))));
			}

			PageID computed = null;

			boolean isInside = searchMode.isInside(keyType,
					page.getKey(slotNo), searchKey);

			if (((isInside) && (searchMode.findGreatestInside()))
					|| ((!isInside) && (!searchMode.findGreatestInside()))) {
				currentPos = slotNo;
				computed = getValueAsPageID();
			} else {
				currentPos = slotNo;
				computed = (currentPos > 0) ? PageID.fromBytes(page
						.getValue(currentPos - 1)) : getLowPageID();
			}

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				String prevKey = (currentPos > 0) ? PageID.fromBytes(
						page.getValue(currentPos - 1)).toString() : "NULL";
				log.trace(String.format("Descending from %s to %s "
						+ "between %s and %s searching for %s %s", getPageID(),
						computed, prevKey, keyType.toString(getKey()),
						searchMode, keyType.toString(searchKey)));
			}

			return computed;
		}
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
	 * @throws IndexOperationException
	 */
	public int findPos(SearchMode searchMode, Field type, int fieldNo,
			byte[] searchValue, int minSlotNo, int maxSlotNo)
			throws IndexOperationException {
		int pos = 0;
		int lower = minSlotNo;
		int upper = maxSlotNo;
		boolean findGreatestInside = searchMode.findGreatestInside();
		boolean isInside = false;
		byte[] compareValue = null;

		if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Searching %s %s in "
					+ "interval [%s, %s] with "
					+ "bounds lower=%s and upper=%s", searchMode, type
					.toString(searchValue), type.toString((fieldNo == 0) ? page
					.getKey(lower) : getValue(lower)), type
					.toString((fieldNo == 0) ? page.getKey(upper)
							: getValue(upper)), lower, upper));
		}

		while (lower < upper) {
			pos = (findGreatestInside) ? (lower + (upper - lower + 1) / 2)
					: (lower + (upper - lower) / 2);
			compareValue = (fieldNo == 0) ? page.getKey(pos) : getValue(pos);

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("Do search %s %s in "
						+ "interval [%s, %s] with "
						+ "bounds lower=%s and upper=%s and pos=%s",
						searchMode, type.toString(searchValue), type
								.toString((fieldNo == 0) ? page.getKey(lower)
										: getValue(lower)), type
								.toString((fieldNo == 0) ? page.getKey(upper)
										: getValue(upper)), lower, upper, pos));
			}

			isInside = searchMode.isInside(type, compareValue, searchValue);

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
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

	@Override
	public String dump(String pageTitle) {
		StringBuffer out = new StringBuffer();
		Field keyType = getKeyType();
		Field valueType = getValueType();
		out
				.append("\n########################################################\n");
		out.append(String.format("Dumping content of \"%s\":\n", pageTitle));
		PageID pageNumber = page.getPageID();
		int pageType = getPageType();
		long LSN = getLSN();
		int freeSpace = getFreeSpace();
		int recordCount = page.getRecordCount();
		out.append(String.format("Number=%s (page=%s)\n", pageNumber, page
				.getHandle()));
		out
				.append(String
						.format(
								"Type=%s RootPageNo=%s Height=%s LastInLevel=%s KeyType=%s ValueType=%s\n",
								pageType, getRootPageID(), getHeight(),
								isLastInLevel(), getKeyType(), getValueType()));
		out.append(String.format("LSN=%s\n", LSN));
		out.append(String.format("FreeSpace=%s\n", freeSpace));
		out.append(String.format("Entry Count=%s\n", recordCount));

		try {
			out.append(String.format("Low Page=>%s\n", getLowPageID()));
			out
					.append("--------------------------------------------------------\n");

			for (int i = 0; i < recordCount; i++) {
				if ((i == recordCount - 1) && (!isLastInLevel())) {
					out.append(i + 1);
					out.append(": [");
					out.append((page.getKey(i) != null) ? keyType.toString(page
							.getKey(i)) : null);
					out.append("=>");
					out.append(PageID.fromBytes(page.getValue(i)));
					out.append("]\n");
				} else {
					out.append(i + 1);
					out.append(": [");
					out.append((page.getKey(i) != null) ? keyType.toString(page
							.getKey(i)) : null);
					out.append((pageType == PageType.LEAF) ? " -> " : "=>");
					if (!page.checkFlag(i, RecordFlag.EXTERNALIZED)) {
						out.append((page.getValue(i) != null) ? valueType
								.toString(page.getValue(i)) : null);
					} else {
						out.append((page.getValue(i) != null) ? "@"
								+ PageID.fromBytes(page.getValue(i)) : null);
					}
					out.append("]\n");
				}

				// System.out.println(String.format("%s: [%s -> %s]",
				// context.offset, ((context.key != null) ?
				// keyType.valueToString(context.key) : null), ((value != null)
				// ? valueType.valueToString(value) : null)));
			}
		} catch (RuntimeException e) {
			out.append("\n Error dumping page: " + e.getMessage());
			log.error(out.toString(), e);
			e.printStackTrace();

			throw e;
		}

		out
				.append("--------------------------------------------------------\n");
		out.append("########################################################");
		return out.toString();
	}

	protected void log(Tx transaction, LogOperation operation, long undoNextLSN)
			throws IndexOperationException {
		try {
			long lsn = (undoNextLSN == -1) ? transaction.logUpdate(operation)
					: transaction.logCLR(operation, undoNextLSN);
			page.setLSN(lsn);
		} catch (TxException e) {
			throw new IndexOperationException(e,
					"Could not write changes to log.");
		}
	}

	@Override
	public PageContext createClone() throws IndexOperationException {
		DirectKeyValuePageContext clone = new DirectKeyValuePageContext(
				bufferMgr, transaction, page);
		return clone;
	}

	@Override
	public void cleanup() {
		page.cleanup();
	}

	@Override
	public void deletePage() throws IndexOperationException {
		try {
			PageID pageID = page.getPageID();
			page.cleanup();
			page.getBuffer().deletePage(transaction, pageID, true, -1);
		} catch (BufferException e) {
			throw new IndexOperationException(e, "Error deleting page");
		}
	}

	@Override
	public int getSize() {
		return page.getSize();
	}

	@Override
	public int getFreeSpace() {
		return page.getFreeSpace();
	}

	@Override
	public long getLSN() {
		return page.getLSN();
	}

	@Override
	public PageID getPageID() {
		return page.getPageID();
	}

	@Override
	public int getUsedSpace() {
		return page.getUsedSpace();
	}

	@Override
	public int getUsedSpace(int position) {
		return page.getUsedSpace(position);
	}

	public void downS() {
		page.downS();
	}

	public int getMode() {
		return page.getMode();
	}

	public String info() {
		return page.info();
	}

	public boolean isLatchedX() {
		return page.isLatchedX();
	}

	public boolean isLatchedS() {
		return page.isLatchedS();
	}

	public boolean isLatchedU() {
		return page.isLatchedU();
	}

	public void latchX() {
		page.latchX();
	}

	public boolean latchXC() {
		return page.latchXC();
	}

	public void latchS() {
		page.latchS();
	}

	public boolean latchSC() {
		return page.latchSC();
	}

	public void latchSI() {
		page.latchSI();
	}

	public void latchU() {
		page.latchU();
	}

	public boolean latchUC() {
		return page.latchUC();
	}

	public void unlatch() {
		page.unlatch();
	}

	public void upX() {
		page.upX();
	}

	@Override
	public String toString() {
		return page.getHandle().toString();
	}
}