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
package org.brackit.server.store.index.bracket.page;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.store.Field;
import org.brackit.server.store.SearchMode;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.log.BracketIndexLogOperation;
import org.brackit.server.store.index.bracket.log.BranchUpdateLogOperation;
import org.brackit.server.store.index.bracket.log.BranchUpdateLogOperation.ActionType;
import org.brackit.server.store.index.bracket.log.PointerLogOperation;
import org.brackit.server.store.index.bracket.log.PointerLogOperation.PointerField;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.BufferedPage;
import org.brackit.server.store.page.RecordFlag;
import org.brackit.server.store.page.keyvalue.KeyValuePage;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogOperation;

/**
 * @author Martin Hiller
 * 
 */
public class BranchBPContext extends AbstractBPContext implements Branch {

	private static final Logger log = Logger.getLogger(BranchBPContext.class);

	private static final int LOW_PAGE_FIELD_NO = AbstractBPContext.RESERVED_SIZE;
	private static final int FLAG_FIELD_NO = LOW_PAGE_FIELD_NO
			+ PageID.getSize();
	private static final int HEIGHT_FIELD_NO = FLAG_FIELD_NO + 1;
	public static final int RESERVED_SIZE = HEIGHT_FIELD_NO + 1;

	private static final byte LAST_IN_LEVEL_FLAG = 2;
	private static final byte COMPRESSED_FLAG = 8;

	private static final Field KEY_TYPE = Field.DEWEYID;
	private static final Field VALUE_TYPE = Field.PAGEID;

	protected final KeyValuePage page;

	protected int currentPos;

	protected int entryCount;

	public BranchBPContext(BufferMgr bufferMgr, Tx tx, KeyValuePage page) {
		super(bufferMgr, tx, (BasePage) page);
		this.page = page;
		currentPos = 0;
		entryCount = super.page.getRecordCount();
	}

	@Override
	public void format(int unitID, PageID rootPageID, int height,
			boolean compressed, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		this.format(false, unitID, rootPageID, height, compressed, logged,
				undoNextLSN);
	}

	@Override
	public int getEntryCount() {
		return entryCount;
	}

	@Override
	public boolean isCompressed() {
		return checkFlag(COMPRESSED_FLAG);
	}

	public void setCompressed(boolean compressed) {
		setFlag(COMPRESSED_FLAG, compressed);
	}

	private void setFlag(byte flagMask, boolean flag) {
		byte[] flags = page.getHandle().page;

		if (flag) {
			flags[BasePage.BASE_PAGE_START_OFFSET + FLAG_FIELD_NO] = (byte) (flagMask | flags[BasePage.BASE_PAGE_START_OFFSET
					+ FLAG_FIELD_NO]);
		} else {
			flags[BasePage.BASE_PAGE_START_OFFSET + FLAG_FIELD_NO] = (byte) (~flagMask & flags[BasePage.BASE_PAGE_START_OFFSET
					+ FLAG_FIELD_NO]);
		}
	}

	private boolean checkFlag(byte flagMask) {
		byte[] value = page.getHandle().page;
		return ((value[BasePage.BASE_PAGE_START_OFFSET + FLAG_FIELD_NO] & flagMask) != 0);
	}

	@Override
	public boolean isLastInLevel() {
		return checkFlag(LAST_IN_LEVEL_FLAG);
	}

	@Override
	public void setLastInLevel(boolean last) {
		setFlag(LAST_IN_LEVEL_FLAG, last);
	}

	@Override
	public PageID getLowPageID() {
		byte[] value = page.getHandle().page;
		return PageID.fromBytes(value, BasePage.BASE_PAGE_START_OFFSET
				+ LOW_PAGE_FIELD_NO);
	}

	@Override
	public void setLowPageID(PageID beforePageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = new PointerLogOperation(PointerField.LOW, getPageID(),
					getRootPageID(), getLowPageID(), beforePageID);
		}

		byte[] value = page.getHandle().page;
		if (beforePageID != null) {
			beforePageID.toBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ LOW_PAGE_FIELD_NO);
		} else {
			PageID.noPageToBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ LOW_PAGE_FIELD_NO);
		}

		page.getHandle().setModified(true); // not covered by pageID to
		// bytes

		if (logged) {
			log(tx, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(tx);
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
	public boolean insert(byte[] key, byte[] value, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		LogOperation operation = null;

		boolean externalize = externalizeValue(value);

		// TODO if externalize check if insert of pointer will succeed

		// if ((getKey() != null) && (getKeyType().compare(getKey(), key) < 0))
		// {
		// System.out.println(dump("Tried to insert " +
		// getKeyType().toString(key) + " before " +
		// getKeyType().toString(getKey())));
		// throw new RuntimeException();
		// }
		//
		// if ((currentPos > 0) && (getKeyType().compare(page.getKey(currentPos
		// - 1), key) > 0))
		// {
		// System.out.println(dump("Tried to insert " +
		// getKeyType().toString(key) + " after " +
		// getKeyType().toString(page.getKey(currentPos - 1))));
		// throw new RuntimeException();
		// }

		if (logged) {
			operation = new BranchUpdateLogOperation(ActionType.INSERT,
					getPageID(), getRootPageID(), key, null, value);
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
			log(tx, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(tx);
		}
		entryCount++;

		return true;
	}

	@Override
	public boolean setValue(byte[] value, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = new BranchUpdateLogOperation(ActionType.UPDATE,
					getPageID(), getRootPageID(), getKey(), getValue(), value);
		}

		byte[] oldValue = page.getValue(currentPos);

		boolean externalize = externalizeValue(value);

		if (externalize) {
			value = externalize(value);
		}

		if (!page.setValue(currentPos, value)) {
			return false;
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
			log(tx, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(tx);
		}

		return true;
	}

	@Override
	public void delete(boolean logged, long undoNextLSN)
			throws IndexOperationException {
		LogOperation operation = null;

		if (logged) {
			operation = new BranchUpdateLogOperation(ActionType.DELETE,
					getPageID(), getRootPageID(), getKey(), null, getValue());
		}

		if (page.checkFlag(currentPos, RecordFlag.EXTERNALIZED)) {
			byte[] oldValue = page.getValue(currentPos);
			deleteExternalized(oldValue);
		}

		page.delete(currentPos);

		if (logged) {
			log(tx, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(tx);
		}
		entryCount--;
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
				value = read(tx, blobPageID);
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
			operation = new BranchUpdateLogOperation(ActionType.UPDATE,
					getPageID(), getRootPageID(), getKey(), getValue(),
					pageID.getBytes());
		}

		byte[] value = (pageID != null) ? pageID.getBytes() : PageID
				.noPageBytes();
		setValue(value, logged, undoNextLSN);

		if (logged) {
			log(tx, operation, undoNextLSN);
		} else {
			page.getHandle().setAssignedTo(tx);
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
	public boolean moveLast() {
		currentPos = (entryCount > 0) ? entryCount - 1 : 0;
		return true;
	}

	@Override
	public boolean isAfterLast() {
		return (currentPos >= entryCount);
	}

	@Override
	public int search(SearchMode searchMode, byte[] searchKey,
			byte[] searchValue) throws IndexOperationException {
		int recordCount = page.getRecordCount();
		int minSlotNo = 0;
		int maxSlotNo = recordCount - 1;
		int result = 0;

		if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String.format("Searching for %s (%s, %s) in page %s.",
					searchMode,
					(searchKey != null) ? KEY_TYPE.toString(searchKey) : null,
					(searchValue != null) ? VALUE_TYPE.toString(searchValue)
							: null, getPageID()));
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
			int slotNo = findPos(searchMode, KEY_TYPE, 0, searchKey, minSlotNo,
					maxSlotNo);

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String
						.format("Initial binary search for %s (%s, %s) in page %s returned slot %s of %s : (%s, %s).",
								searchMode,
								(searchKey != null) ? KEY_TYPE
										.toString(searchKey) : null,
								(searchValue != null) ? VALUE_TYPE
										.toString(searchValue) : null,
								getPageID(), slotNo, maxSlotNo, KEY_TYPE
										.toString(page.getKey(slotNo)),
								VALUE_TYPE.toString(getValue(slotNo))));
			}

			if (searchMode.findGreatestInside()) {
				boolean foundRecordInside = ((minSlotNo < slotNo) || (searchMode
						.isInside(KEY_TYPE, page.getKey(slotNo), searchKey)));
				result = foundRecordInside ? 0 : -1;
				currentPos = slotNo;
			} else {
				boolean foundRecordInside = ((slotNo < maxSlotNo) || (searchMode
						.isInside(KEY_TYPE, page.getKey(slotNo), searchKey)));
				result = foundRecordInside ? 0 : 1;

				if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
					log.trace(String
							.format("Search for %s (%s, %s) in page %s returns %s at %s->(%s, %s).",
									searchMode,
									(searchKey != null) ? KEY_TYPE
											.toString(searchKey) : null,
									(searchValue != null) ? VALUE_TYPE
											.toString(searchValue) : null,
									getPageID(), result, slotNo, KEY_TYPE
											.toString(page.getKey(slotNo)),
									VALUE_TYPE.toString(getValue(slotNo))));
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
		Field keyType = KEY_TYPE;

		if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
			log.trace(String
					.format("Determining next child page searching for %s %s in page %s.",
							searchMode,
							(searchKey != null) ? keyType.toString(searchKey)
									: null, getPageID()));
		}

		if (recordCount == 0) {
			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String
						.format("Determining before page id of emptied page search for %s %s in page %s.",
								searchMode,
								(searchKey != null) ? keyType
										.toString(searchKey) : null,
								getPageID()));
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
				log.trace(String
						.format("Initial binary search for %s (%s) in page %s returned slot %s of %s : (%s, %s).",
								searchMode,
								(searchKey != null) ? keyType
										.toString(searchKey) : null,
								getPageID(), slotNo, maxSlotNo, keyType
										.toString(page.getKey(slotNo)),
								VALUE_TYPE.toString(getValue(slotNo))));
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
				log.trace(String
						.format("Descending from %s to %s between %s and %s searching for %s %s",
								getPageID(), computed, prevKey,
								keyType.toString(getKey()), searchMode,
								keyType.toString(searchKey)));
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
			log.trace(String
					.format("Searching %s %s in interval [%s, %s] with bounds lower=%s and upper=%s",
							searchMode, type.toString(searchValue), type
									.toString((fieldNo == 0) ? page
											.getKey(lower) : getValue(lower)),
							type.toString((fieldNo == 0) ? page.getKey(upper)
									: getValue(upper)), lower, upper));
		}

		while (lower < upper) {
			pos = (findGreatestInside) ? (lower + (upper - lower + 1) / 2)
					: (lower + (upper - lower) / 2);
			compareValue = (fieldNo == 0) ? page.getKey(pos) : getValue(pos);

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String
						.format("Do search %s %s in interval [%s, %s] with bounds lower=%s and upper=%s and pos=%s",
								searchMode, type.toString(searchValue), type
										.toString((fieldNo == 0) ? page
												.getKey(lower)
												: getValue(lower)), type
										.toString((fieldNo == 0) ? page
												.getKey(upper)
												: getValue(upper)), lower,
								upper, pos));
			}

			isInside = searchMode.isInside(type, compareValue, searchValue);

			if (BufferedPage.DEVEL_MODE && log.isTraceEnabled()) {
				log.trace(String.format("%s is indside %s %s : %s",
						type.toString(compareValue), searchMode,
						type.toString(searchValue), isInside));
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
		Field keyType = KEY_TYPE;
		Field valueType = VALUE_TYPE;
		out.append("\n########################################################\n");
		out.append(String.format("Dumping content of \"%s\":\n", pageTitle));
		PageID pageNumber = page.getPageID();
		long LSN = getLSN();
		int freeSpace = getFreeSpace();
		int recordCount = page.getRecordCount();
		out.append(String.format("Number=%s (page=%s)\n", pageNumber,
				page.getHandle()));
		out.append(String
				.format("Type=BRANCH RootPageNo=%s Height=%s LastInLevel=%s KeyType=%s ValueType=%s\n",
						getRootPageID(), getHeight(), isLastInLevel(), keyType,
						valueType));
		out.append(String.format("LSN=%s\n", LSN));
		out.append(String.format("FreeSpace=%s\n", freeSpace));
		out.append(String.format("Entry Count=%s\n", recordCount));

		try {
			out.append(String.format("Low Page=>%s\n", getLowPageID()));
			out.append("--------------------------------------------------------\n");

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
					out.append("=>");
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
				// keyType.toString(context.key) : null), ((value != null)
				// ? valueType.toString(value) : null)));
			}
		} catch (RuntimeException e) {
			out.append("\n Error dumping page: " + e.getMessage());
			log.error(out.toString(), e);
			e.printStackTrace();

			throw e;
		}

		out.append("--------------------------------------------------------\n");
		out.append("########################################################");
		return out.toString();
	}

	@Override
	public BPContext createClone() throws IndexOperationException {
		BranchBPContext clone = new BranchBPContext(bufferMgr, tx, page);
		return clone;
	}

	@Override
	public int getUsedSpace(int position) {
		return page.getUsedSpace(position);
	}

	protected void setHeight(int height) {
		byte[] value = page.getHandle().page;
		value[BasePage.BASE_PAGE_START_OFFSET + HEIGHT_FIELD_NO] = (byte) height;
	}

	@Override
	public int getHeight() {
		byte[] value = page.getHandle().page;
		return value[BasePage.BASE_PAGE_START_OFFSET + HEIGHT_FIELD_NO] & 255;
	}

	@Override
	public boolean isLeaf() {
		return false;
	}

	@Override
	public boolean isLast() {
		return (currentPos == entryCount - 1);
	}
}
