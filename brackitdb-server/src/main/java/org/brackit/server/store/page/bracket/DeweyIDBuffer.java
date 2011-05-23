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
public class DeweyIDBuffer implements SimpleDeweyID {

	private static final int minBufferSize = 16;
	private DocID docID;
	private int[] divisions;
	private int length;

	private boolean compareMode;
	private int[] compareDivisions;
	private int commonPrefix;
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

	/**
	 * Constructor for DeweyIDBuffer.
	 * 
	 * @param startDeweyID
	 *            the DeweyID this instance is representing
	 * @param comparisonDeweyID
	 *            a DeweyID to compare with (if a certain DeweyID shall be
	 *            looked up)
	 */
	public DeweyIDBuffer(XTCdeweyID startDeweyID, XTCdeweyID comparisonDeweyID) {
		this(startDeweyID);

		// if a comparison DeweyID is given
		if (comparisonDeweyID != null) {
			enableCompareMode(comparisonDeweyID);
		}
	}

	/**
	 * Default constructor.
	 */
	public DeweyIDBuffer() {
	}

	/**
	 * Removes the given number of last divisions.
	 * 
	 * @param numberDivisions
	 *            number of divisions to remove
	 */
	private void removeLastDivisions(int numberDivisions) {
		length -= numberDivisions;

		if (compareMode) {
			commonPrefix = (length < commonPrefix) ? length : commonPrefix;
		}
	}

	/**
	 * Sets the last division to the next odd number that is GREATER than the
	 * current last division.
	 */
	private void setLastDivToNextOdd() {
		divisions[length - 1] += (divisions[length - 1] % 2 + 1);

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
		divisions[length - 1] += value;

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
		divisions[length - 1] -= value;

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
		if (length == divisions.length) {
			int[] newBuffer = new int[(divisions.length * 3) / 2 + 1];
			System.arraycopy(divisions, 0, newBuffer, 0, divisions.length);
			divisions = newBuffer;
		}

		divisions[length] = divValue;
		length++;

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
		bufferedKey = new XTCdeweyID(docID, length, divisions);
		return bufferedKey;
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#isAttribute()
	 */
	@Override
	public boolean isAttribute() {
		return length > 2 && divisions[length - 2] == 1;
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

		if (commonPrefix == length || commonPrefix == compareDivisions.length) {
			// one DeweyID is an ancestor of the other (or they are the same)
			if (length < compareDivisions.length) {
				compareValue = -1;
			} else if (length == compareDivisions.length) {
				compareValue = 0;
			} else {
				compareValue = 1;
			}
		} else {
			compareValue = (divisions[commonPrefix] < compareDivisions[commonPrefix]) ? -1
					: 1;
		}

	}

	/**
	 * Refreshes the value of commonPrefix. Is invoked when the last division of
	 * this DeweyID was changed.
	 */
	private void lastDivisionChanged() {

		if (commonPrefix == length) {
			// last division was changed: commonPrefix has to be decremented
			commonPrefix--;
		} else if (commonPrefix == length - 1) {
			// commonPrefix may have to be incremented
			if (commonPrefix < compareDivisions.length
					&& divisions[commonPrefix] == compareDivisions[commonPrefix]) {
				commonPrefix++;
			}
		}

	}

	/**
	 * Sets the content of this buffer to the specified one.
	 * 
	 * @param other
	 *            the other buffer to take the content from
	 */
	public void setTo(DeweyIDBuffer other) {
		if (this == other || other.divisions == null) {
			return;
		}

		this.docID = other.docID;

		if (this.divisions == null || this.divisions.length < other.length) {
			// a new array has to be allocated
			this.divisions = new int[other.divisions.length];
		}
		// copy buffer content
		System.arraycopy(other.divisions, 0, this.divisions, 0, other.length);

		this.length = other.length;
		this.compareMode = other.compareMode;
		this.compareDivisions = other.compareDivisions;
		this.commonPrefix = other.commonPrefix;
		this.compareValue = other.compareValue;
		this.bufferedKey = other.bufferedKey;		
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

		if (this.divisions == null
				|| this.divisions.length < otherDivisions.length) {
			// a new array has to be allocated
			this.divisions = new int[Math.max(minBufferSize,
					(otherDivisions.length * 3) / 2 + 1)];
		}
		// copy divisions
		System.arraycopy(otherDivisions, 0, this.divisions, 0,
				otherDivisions.length);
			
		this.length = otherDivisions.length;
		this.bufferedKey = other;
		
		disableCompareMode();
	}

	/**
	 * Allows the efficient comparison with the specified DeweyID.
	 * 
	 * @param comparisonDeweyID
	 *            the DeweyID to compare with
	 */
	public void enableCompareMode(SimpleDeweyID comparisonDeweyID) {
		if (this.divisions == null) {
			throw new RuntimeException(
					"DeweyIDBuffer needs to be instantiated before enabling the CompareMode!");
		}
		// use a copy of the given DeweyID
		compareDivisions = Arrays.copyOf(comparisonDeweyID.getDivisionValues(),
				comparisonDeweyID.getNumberOfDivisions());
		commonPrefix = getCommonPrefixLength(comparisonDeweyID);
		compareMode = true;
		determineCompareValue();
	}

	/**
	 * Disables the comparison mode and releases the needed resources.
	 */
	public void disableCompareMode() {
		if (compareMode) {
			compareDivisions = null;
			commonPrefix = 0;
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
		int upperBound = (length < otherLength) ? length : otherLength;

		int[] otherDivisions = other.getDivisionValues();
		int commonPrefix = 0;
		while (commonPrefix < upperBound) {
			if (this.divisions[commonPrefix] != otherDivisions[commonPrefix]) {
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
	 *            the divisions of the other DeweyID
	 * @return length of common prefix
	 */
	private int getCommonPrefixLength(int[] otherDivisions) {
		int upperBound = (length < otherDivisions.length) ? length
				: otherDivisions.length;

		int commonPrefix = 0;
		while (commonPrefix < upperBound) {
			if (this.divisions[commonPrefix] != otherDivisions[commonPrefix]) {
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
			commonPrefix = getCommonPrefixLength(compareDivisions);
			determineCompareValue();
		}
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#getDivisionValues()
	 */
	@Override
	public int[] getDivisionValues() {
		return divisions;
	}

	/**
	 * @see org.brackit.server.store.page.bracket.SimpleDeweyID#getNumberOfDivisions()
	 */
	@Override
	public int getNumberOfDivisions() {
		return length;
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

		if (other.getNumberOfDivisions() < this.length) {
			return false;
		}

		for (int i = 0; i < this.length; i++) {
			if (this.divisions[i] != otherDivisions[i]) {
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

	/**
	 * Sets this buffer to its parent DeweyID. For attributes the corresponding
	 * element DeweyID is returned.
	 * 
	 * @return false if the current DeweyID is the root DeweyID
	 */
	public boolean setToParent() {

		if (length == 1) {
			// current node is the root node
			return false;
		}

		int newLength = length - 1;

		if (divisions[newLength - 1] == 1 && newLength > 1) {
			// skip attribute root
			newLength--;
		}

		// skip all even divisions
		while (divisions[newLength - 1] % 2 == 0) {
			newLength--;
		}

		removeLastDivisions(length - newLength);

		disableCompareMode();

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
					// remove divisions used for the attribute
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

		if (divisions == null) {
			out.append("Not initialized!");
		} else {

			if (compareMode) {
				out.append("Own ID:     ");
			}

			out.append(this.docID);
			out.append(XTCdeweyID.documentSeparator);

			for (int i = 0; i < this.length; i++) {
				if (i != 0)
					out.append(XTCdeweyID.divisionSeparator);
				out.append(this.divisions[i]);
			} // for i

			if (compareMode) {
				out.append("\nCompare ID: ");
				for (int i = 0; i < this.compareDivisions.length; i++) {
					if (i != 0)
						out.append(XTCdeweyID.divisionSeparator);
					out.append(this.compareDivisions[i]);
				} // for i
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
}
