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
package org.brackit.server.store.page.keyvalue;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.page.BufferedPage;
import org.brackit.server.store.page.RecordFlag;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public interface KeyValuePage extends BufferedPage {
	public static final byte EXTERNALIZED_FLAG = 16;

	public int getReservedOffset();

	public void setFlag(int pos, RecordFlag flag, boolean value);

	public boolean checkFlag(int pos, RecordFlag flag);

	public boolean insert(int pos, byte[] key, byte[] value, boolean compressed);

	/**
	 * Sets the BasePageID, initializes free space info management, and resets
	 * entry counter.
	 * 
	 * @param basePageID
	 */
	public void format(PageID basePageID);

	public byte[] getKey(int pos);

	public byte[] getValue(int pos);

	public void delete(int pos);

	public boolean setValue(int pos, byte[] value);

	public boolean setKey(int pos, byte[] key);

	public boolean update(int pos, byte[] key, byte[] value);

	public int requiredSpaceForInsert(int pos, byte[] insertKey,
			byte[] insertValue, boolean compressed);

	public int requiredSpaceForUpdate(int pos, byte[] key, byte[] value,
			boolean compressed);

	public int getUsedSpace(int pos);

	public void clear();

	public int calcMaxInlineValueSize(int minNoOfEntries, int maxKeySize);
}