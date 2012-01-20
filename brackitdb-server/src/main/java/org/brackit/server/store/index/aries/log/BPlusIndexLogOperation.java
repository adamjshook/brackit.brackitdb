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
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.store.index.aries.log;

import java.nio.ByteBuffer;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.tx.log.LogOperation;

public abstract class BPlusIndexLogOperation extends LogOperation {
	public static final byte NEXT_PAGE = 11;

	public static final byte PREV_PAGE = 12;

	public static final byte BEFORE_PAGE = 13;

	public static final byte FORMAT = 14;

	public static final byte USER_INSERT = 15;

	public static final byte USER_DELETE = 16;

	public static final byte USER_UPDATE = 17;

	public static final byte SMO_INSERT = 18;

	public static final byte SMO_DELETE = 19;

	public static final byte SMO_UPDATE = 20;

	protected static final int BASE_SIZE = 2 * PageID.getSize();

	protected PageID rootPageID;

	protected PageID pageID;

	protected BPlusIndexLogOperation(byte type, PageID pageID, PageID rootPageID) {
		super(type);
		this.pageID = pageID;
		this.rootPageID = rootPageID;
	}

	@Override
	public void toBytes(ByteBuffer bb) {
		bb.put(pageID.getBytes());
		bb.put(rootPageID.getBytes());
	}
}
