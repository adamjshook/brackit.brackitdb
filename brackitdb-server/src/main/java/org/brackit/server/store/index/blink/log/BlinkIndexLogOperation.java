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
package org.brackit.server.store.index.blink.log;

import java.nio.ByteBuffer;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.tx.log.LogOperation;

public abstract class BlinkIndexLogOperation extends LogOperation {
	public static final byte NEXT_PAGE = 51;

	public static final byte PREV_PAGE = 52;

	public static final byte BEFORE_PAGE = 53;

	public static final byte FORMAT = 54;

	public static final byte USER_INSERT = 55;

	public static final byte USER_DELETE = 56;

	public static final byte USER_UPDATE = 57;

	public static final byte SMO_INSERT = 58;

	public static final byte SMO_DELETE = 59;

	public static final byte SMO_UPDATE = 60;

	protected static final int BASE_SIZE = 2 * PageID.getSize();

	protected PageID rootPageID;

	protected PageID pageID;

	protected BlinkIndexLogOperation(byte type, PageID pageID, PageID rootPageID) {
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
