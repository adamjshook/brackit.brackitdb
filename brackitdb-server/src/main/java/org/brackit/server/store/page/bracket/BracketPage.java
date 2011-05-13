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
	 * page).
	 * 
	 * @return the low key
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

	private BracketKey.Type getLowKeyType() {
		return BracketKey.Type.getByPhysicalValue(page[LOW_KEY_TYPE_FIELD_NO]);
	}

}
