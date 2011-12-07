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

import java.util.Arrays;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.page.bracket.BracketKey.Type;

/**
 * 
 * This DeweyID class is used to process bracket keys efficiently without the
 * need to create a new instance of XTCdeweyID for every bracket key. When the
 * processing is over, this DeweyID can be reconverted to an XTCdeweyID.
 * 
 * @author Martin Hiller
 * 
 */
public final class DeweyIDBuffer implements SimpleDeweyID {

	private static final int minBufferSize = 16;
	private DocID docID;
	private PageID assignedPage;

	private int[] currentBuffer;
	private int currentLength;

	private boolean backupMode;
	private int[] backupBuffer;
	private int backupLength;

	private boolean compareMode;
	private int[] compareDivisions;
	private int comparePrefix;
	private int compareValue;

	private XTCdeweyID bufferedKey = null;

	/**
	 * Constructor for DeweyIDBuffer.
	 * 
	 * @param deweyID
	 *            the DeweyID this instance is representing
	 */
	public DeweyIDBuffer(XTCdeweyID deweyID) {
		this();
		setTo(deweyID);
	}
	
	public DeweyIDBuffer(DeweyIDBuffer other) {
		this();
		setTo(other);
	}

	/**
	 * Constructor for DeweyIDBuffer.
	 */
	public DeweyIDBuffer() {
		this.backupMode = false;
		this.compareMode = false;
	}

	/**
	 * Removes the given number of last currentBuffer.
	 * 
	 * @param numberDivisions
	 *            number of currentBuffer to remove
	 */
	private void removeLastDivisions(int numberDivisions) {
		currentLength -= numberDivisions;

		if (compareMode) {
			comparePrefix = (currentLength < comparePrefix) ? currentLength
					: comparePrefix;
		}
	}

	/**
	 * Sets the last division to the next odd number that is GREATER than the
	 * current last division.
	 */
	private void setLastDivToNextOdd() {
		currentBuffer[currentLength - 1] += ((currentBuffer[currentLength - 1] & 1) + 1);

		if (compareMode) {
			lastDivisionChanged();
		}
	}

	/**
	 * Increases the last division by the given value.
	 * 
	 * @param value
	 *            the increase
	 */
	private void increaseLastDivision(int value) {
		currentBuffer[currentLength - 1] += value;

		if (compareMode && value != 0) {
			lastDivisionChanged();
		}
	}

	/**
	 * Decreases the last division by the given value.
	 * 
	 * @param value
	 *            the decrease
	 */
	private void decreaseLastDivision(int value) {
		currentBuffer[currentLength - 1] -= value;

		if (compareMode && value != 0) {
			lastDivisionChanged();
		}
	}

	/**
	 * Appends a new division at the end of this DeweyID.
	 * 
	 * @param divValue
	 *            the new division's value
	 */
	private void appendDivision(int divValue) {

		// check whether the division buffer has to be extended
		if (currentLength == currentBuffer.length) {
			int[] newBuffer = new int[(currentBuffer.length * 3) / 2 + 1];
			System.arraycopy(currentBuffer, 0, newBuffer, 0,
					currentBuffer.length);
			currentBuffer = newBuffer;
		}

		currentBuffer[currentLength] = divValue;
		currentLength++;

		if (compareMode) {
			lastDivisionChanged();
		}
	}

	/**
	 * Converts this DeweyID back to an XTCdeweyID.
	 * 
	 * @return the converted XTCdeweyID
	 */
	public XTCdeweyID getDeweyID() {
		if (bufferedKey != null) {
			return bufferedKey;
		}
		if (currentBuffer == null) {
			return null;
		}
		bufferedKey = new XTCdeweyID(docID, currentLength, currentBuffer);
		return bufferedKey;
	}

	/**
	 * Returns the backup DeweyID as SimpleDeweyID.
	 * 
	 * @return backup DeweyID or null, if not in backup mode
	 */
	public SimpleDeweyID getBackupAsSimpleDeweyID() {
		return backupMode ? new SimpleDeweyIDImpl(backupBuffer, backupLength)
				: null;
	}

	/**
	 * Returns the backup DeweyID as XTCdeweyID.
	 * 
	 * @return backup DeweyID or null, if not in backup mode
	 */
	public XTCdeweyID getBackupDeweyID() {
		return backupMode ? new XTCdeweyID(docID, backupLength, backupBuffer)
				: null;
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#isAttribute()
	 */
	@Override
	public boolean isAttribute() {
		return currentLength > 2 && currentBuffer[currentLength - 2] == 1;
	}

	/**
	 * Compares this DeweyID with the comparison DeweyID given in the
	 * constructor. Returns -1 if this DeweyID is smaller than the other, 0 if
	 * they are the same and +1 if this DeweyID is greater. If no comparison
	 * DeweyID was given in the constructor, 0 will be returned.
	 * 
	 * @return the comparison value
	 */
	public int compare() {
		return compareValue;
	}

	/**
	 * Refreshes the compare value to the compare DeweyID. Only invoked if an
	 * update of the buffer is done.
	 */
	private void determineCompareValue() {

		if (comparePrefix == currentLength
				|| comparePrefix == compareDivisions.length) {
			// one DeweyID is an ancestor of the other (or they are the same)
			if (currentLength < compareDivisions.length) {
				compareValue = -1;
			} else if (currentLength == compareDivisions.length) {
				compareValue = 0;
			} else {
				compareValue = 1;
			}
		} else {
			compareValue = (currentBuffer[comparePrefix] < compareDivisions[comparePrefix]) ? -1
					: 1;
		}

	}

	/**
	 * Refreshes the value of comparePrefix. Is invoked when the last division
	 * of this DeweyID was changed.
	 */
	private void lastDivisionChanged() {

		if (comparePrefix == currentLength) {
			// last division was changed: comparePrefix has to be decremented
			comparePrefix--;
		} else if (comparePrefix == currentLength - 1) {
			// comparePrefix may have to be incremented
			if (comparePrefix < compareDivisions.length
					&& currentBuffer[comparePrefix] == compareDivisions[comparePrefix]) {
				comparePrefix++;
			}
		}

	}

	/**
	 * Sets the content of this buffer to the specified XTCdeweyID.
	 * 
	 * @param other
	 *            the XTCdeweyID to take the content from
	 */
	public void setTo(XTCdeweyID other) {
		this.docID = other.getDocID();

		int[] otherDivisions = other.getDivisionValues();

		if (this.currentBuffer == null
				|| this.currentBuffer.length < otherDivisions.length) {
			// a new array has to be allocated
			this.currentBuffer = new int[Math.max(minBufferSize,
					(otherDivisions.length * 3) / 2 + 1)];
		}
		// copy currentBuffer
		System.arraycopy(otherDivisions, 0, this.currentBuffer, 0,
				otherDivisions.length);

		this.currentLength = otherDivisions.length;
		this.bufferedKey = other;

		if (compareMode) {
			comparePrefix = getCommonPrefixLength(compareDivisions);
			determineCompareValue();
		}
	}
	
	/**
	 * Copies content from the other DeweyIDBuffer.
	 */
	public void setTo(DeweyIDBuffer other) {
		if (other.currentBuffer == null) {
			//other buffer not initialized
			return;
		}
		
		this.docID = other.docID;
		
		if (this.currentBuffer == null
				|| this.currentBuffer.length < other.currentLength) {
			// a new array has to be allocated
			this.currentBuffer = new int[Math.max(minBufferSize,
					(other.currentLength * 3) / 2 + 1)];
		}
		// copy currentBuffer
		System.arraycopy(other.currentBuffer, 0, this.currentBuffer, 0,
				other.currentLength);

		this.currentLength = other.currentLength;
		this.bufferedKey = other.bufferedKey;

		if (compareMode) {
			comparePrefix = getCommonPrefixLength(compareDivisions);
			determineCompareValue();
		}
	}

	/**
	 * Allows the efficient comparison with the specified DeweyID.
	 * 
	 * @param comparisonDeweyID
	 *            the DeweyID to compare with
	 */
	public void enableCompareMode(SimpleDeweyID comparisonDeweyID) {
		if (this.currentBuffer == null) {
			throw new RuntimeException(
					"DeweyIDBuffer needs to be instantiated before enabling the CompareMode!");
		}
		// use a copy of the given DeweyID
		compareDivisions = Arrays.copyOf(comparisonDeweyID.getDivisionValues(),
				comparisonDeweyID.getNumberOfDivisions());
		comparePrefix = getCommonPrefixLength(comparisonDeweyID);
		compareMode = true;
		determineCompareValue();
	}

	/**
	 * Disables the comparison mode and releases the needed resources.
	 */
	public void disableCompareMode() {
		if (compareMode) {
			compareDivisions = null;
			comparePrefix = 0;
			compareMode = false;
			compareValue = 0;
		}
	}

	/**
	 * Returns the length (in number of divisions) of the common prefix.
	 * 
	 * @param other
	 *            the other DeweyID to compare with
	 * @return length of common prefix
	 */
	private int getCommonPrefixLength(SimpleDeweyID other) {
		int otherLength = other.getNumberOfDivisions();
		int upperBound = (currentLength < otherLength) ? currentLength
				: otherLength;

		int[] otherDivisions = other.getDivisionValues();
		int commonPrefix = 0;
		while (commonPrefix < upperBound) {
			if (this.currentBuffer[commonPrefix] != otherDivisions[commonPrefix]) {
				break;
			} else {
				commonPrefix++;
			}
		}

		return commonPrefix;
	}

	/**
	 * Returns the length (in number of divisions) of the common prefix.
	 * 
	 * @param otherDivisions
	 *            the currentBuffer of the other DeweyID
	 * @return length of common prefix
	 */
	private int getCommonPrefixLength(int[] otherDivisions) {
		int upperBound = (currentLength < otherDivisions.length) ? currentLength
				: otherDivisions.length;

		int commonPrefix = 0;
		while (commonPrefix < upperBound) {
			if (this.currentBuffer[commonPrefix] != otherDivisions[commonPrefix]) {
				break;
			} else {
				commonPrefix++;
			}
		}

		return commonPrefix;
	}

	/**
	 * Sets the CompareDeweyID to the one from the other DeweyIDBuffer.
	 * 
	 * @param other
	 *            the other DeweyIDBuffer
	 */
	public void useCompareDeweyIDFrom(DeweyIDBuffer other) {
		this.compareMode = other.compareMode;
		this.compareDivisions = other.compareDivisions;
		if (compareMode) {
			comparePrefix = getCommonPrefixLength(compareDivisions);
			determineCompareValue();
		}
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#getDivisionValues()
	 */
	@Override
	public int[] getDivisionValues() {
		return currentBuffer;
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#getNumberOfDivisions()
	 */
	@Override
	public int getNumberOfDivisions() {
		return currentLength;
	}

	/**
	 * Checks whether this DeweyID is a prefix of the other.
	 * 
	 * @param other
	 *            the other DeweyID
	 * @return true if this DeweyID is a prefix of the other DeweyID
	 */
	public boolean isPrefixOf(SimpleDeweyID other) {

		int[] otherDivisions = other.getDivisionValues();

		if (other.getNumberOfDivisions() < this.currentLength) {
			return false;
		}

		for (int i = 0; i < this.currentLength; i++) {
			if (this.currentBuffer[i] != otherDivisions[i]) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Returns the logical level of the given DeweyID. In this context
	 * attributes have the same level like the corresponding element.
	 * 
	 * @param deweyID
	 *            the DeweyID
	 * @return level of the DeweyID
	 */
	private static int getLevel(SimpleDeweyID deweyID) {

		int[] divisions = deweyID.getDivisionValues();
		int length = deweyID.getNumberOfDivisions();

		int level = 0;

		for (int i = 0; i < length; i++) {
			if (divisions[i] % 2 == 1) {
				level++;
			}
		}

		if (deweyID.isAttribute()) {
			level -= 2;
		}

		return level;
	}

	/**
	 * Returns the logical level difference between the DeweyID in this buffer
	 * and the given DeweyID.
	 * 
	 * @param other
	 *            the other DeweyID
	 * @return the level difference
	 */
	public int getLevelDifferenceTo(SimpleDeweyID other) {
		return getLevel(other) - getLevel(this);
	}
	
	public int getLevel() {
		
		int level = 0;

		for (int i = 0; i < currentLength; i++) {
			if (currentBuffer[i] % 2 == 1) {
				level++;
			}
		}

		if (isAttribute()) {
			level -= 2;
		}

		return level;
	}

	/**
	 * Sets this buffer to its parent DeweyID. For attributes the corresponding
	 * element DeweyID is returned.
	 * 
	 * @return false if the current DeweyID is the root DeweyID
	 */
	public boolean setToParent() {

		if (currentLength == 1) {
			// current node is the root node
			return false;
		}

		int newLength = currentLength - 1;

		if (currentBuffer[newLength - 1] == 1 && newLength > 1) {
			// skip attribute root
			newLength--;
		}

		// skip all even currentBuffer
		while (currentBuffer[newLength - 1] % 2 == 0) {
			newLength--;
		}

		removeLastDivisions(currentLength - newLength);

		bufferedKey = null;
		return true;
	}

	/**
	 * Updates this DeweyID according to the given BracketKey.
	 * 
	 * @param key
	 *            the BracketKey to "apply" on this DeweyID
	 * @param ignoreAttributes
	 *            guarantees that neither this buffer nor the given BracketKey
	 *            is an attribute
	 */
	public void update(final BracketKey key, boolean ignoreAttributes) {

		if (ignoreAttributes) {
			// optimization if attributes are irrelevant

			if (key.roundBrackets == 0) {
				appendDivision(3);
			} else {
				removeLastDivisions(key.roundBrackets + key.angleBrackets - 1);
				setLastDivToNextOdd();
			}

		} else {

			final boolean previousIsAttribute = isAttribute();
			final boolean currentIsAttribute = (key.type == Type.ATTRIBUTE);

			if (previousIsAttribute && currentIsAttribute) {
				// previous node and current node are attributes for the same
				// node
				setLastDivToNextOdd();
			} else {

				if (previousIsAttribute) {
					// remove currentBuffer used for the attribute
					removeLastDivisions(2);
				}

				if (key.roundBrackets == 0) {
					// this is the first attribute/child node of the current
					// subtree
					if (currentIsAttribute) {
						// first append the attribute division 1
						appendDivision(1);
					}
					appendDivision(3);
				} else {
					removeLastDivisions(key.roundBrackets + key.angleBrackets
							- 1);
					setLastDivToNextOdd();
				}
			}
		}

		// increase last division due to DeweyID gaps
		increaseLastDivision(2 * key.idGaps);

		// if this bracket key represents an overflow area
		if (key.type == Type.OVERFLOW) {
			decreaseLastDivision(1);
		}

		if (compareMode) {
			determineCompareValue();
		}

		bufferedKey = null;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();

		if (currentBuffer == null) {
			out.append("Not initialized!");
		} else {

			if (compareMode) {
				out.append("DeweyID:    ");
			}

			out.append(this.docID);
			out.append(XTCdeweyID.documentSeparator);

			for (int i = 0; i < currentLength; i++) {
				if (i != 0)
					out.append(XTCdeweyID.divisionSeparator);
				out.append(currentBuffer[i]);
			}

			if (backupMode) {
				out.append("\nBackup ID:  ");
				if (backupBuffer == null) {
					out.append("null");
				} else {
					for (int i = 0; i < backupLength; i++) {
						if (i != 0)
							out.append(XTCdeweyID.divisionSeparator);
						out.append(backupBuffer[i]);
					}
				}
			}

			if (compareMode) {
				out.append("\nCompare ID: ");
				for (int i = 0; i < compareDivisions.length; i++) {
					if (i != 0)
						out.append(XTCdeweyID.divisionSeparator);
					out.append(compareDivisions[i]);
				}
			}

		}

		return out.toString();
	}

	/**
	 * If this buffer contains an attribute DeweyID, it will be set to the
	 * related element DeweyID.
	 */
	public void setAttributeToRelatedElement() {
		if (isAttribute()) {
			removeLastDivisions(2);
			bufferedKey = null;
		}
	}
	
	/**
	 * If this buffer contains an attribute DeweyID, it will be set to the
	 * related element DeweyID.
	 */
	public void removeTwoDivisions() {
		removeLastDivisions(2);
		bufferedKey = null;
	}

	/**
	 * Buffers the current value of the DeweyIDBuffer, so that it can be
	 * restored later on.
	 */
	protected void backup() {

		if (currentBuffer == null) {
			// Buffer not initialized yet
			backupBuffer = null;
			backupLength = 0;
			backupMode = true;
			return;
		}

		// copy all used divisions
		if (backupBuffer == null || backupBuffer.length < currentLength) {
			// allocate new backup buffer
			backupBuffer = new int[currentBuffer.length];
		}
		// copy divisions
		System.arraycopy(currentBuffer, 0, backupBuffer, 0, currentLength);

		backupLength = currentLength;
		backupMode = true;
	}

	/**
	 * Called if the stored backup is not needed anymore.
	 */
	protected void resetBackup() {
		// do not release the actual int array, since it can be reused for later
		// backups
		backupMode = false;
		backupLength = 0;
		bufferedKey = null;
	}

	/**
	 * Restores the stored backup.
	 * 
	 * @param keepBackup
	 *            is the backup supposed to be kept or thrown away (more
	 *            efficient)
	 */
	protected void restore(boolean keepBackup) {

		bufferedKey = null;
		
		if (!backupMode) {
			throw new RuntimeException("There is no backup to be restored!");
		}

		if (backupBuffer == null) {
			// nothing to restore
			if (!keepBackup) {
				backupMode = false;
			}
			return;
		}

		if (keepBackup) {
			// copy back the backup to the current buffer
			System.arraycopy(backupBuffer, 0, currentBuffer, 0, backupLength);
			currentLength = backupLength;
		} else {
			// switch buffers
			int[] temp = currentBuffer;
			currentBuffer = backupBuffer;
			currentLength = backupLength;
			backupBuffer = temp;
			backupMode = false;
			backupLength = 0;
		}

		if (compareMode) {
			// compare values changed
			comparePrefix = getCommonPrefixLength(compareDivisions);
			determineCompareValue();
		}
	}

	/**
	 * Assigns this DeweyIDBuffer to a certain page.
	 * 
	 * @param pageID
	 *            the PageID of the corresponding page
	 */
	public void assignToPage(PageID pageID) {
		if (assignedPage != null) {
			if (pageID.equals(assignedPage)) {
				throw new RuntimeException(
						String.format(
								"Assign Error: DeweyIDBuffer is already assigned to Page %s.",
								assignedPage));
			} else {
				throw new RuntimeException(
						String.format(
								"Assign Error: DeweyIDBuffer can not be assigned to Page %s, since it is used by Page %s.",
								pageID, assignedPage));
			}
		}

		assignedPage = pageID;
	}

	/**
	 * Deassigns this DeweyIDBuffer from the given page.
	 * 
	 * @param pageID
	 *            the PageID of the corresponding page
	 */
	public void deassignFromPage(PageID pageID) {
		if (assignedPage == null) {
			// nothing to deassign
			return;
		}

		if (!assignedPage.equals(pageID)) {
			throw new RuntimeException(
					String.format(
							"Assign Error: DeweyIDBuffer can not be deassigned from Page %s, since it is assigned to Page %s.",
							pageID, assignedPage));
		}

		assignedPage = null;
	}
}
