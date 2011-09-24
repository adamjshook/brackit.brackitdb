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

import org.brackit.server.node.el.ElRecordAccess;

/**
 * @author Martin Hiller
 *
 */
public class RecordInterpreter {
	
	private final byte[] buf;
	private final int offset;
	private final int len;
	
	private int pcr = -1;
	private byte type = -1;
	private String value = null;
	
	public RecordInterpreter(byte[] buf, int offset, int len) {
		this.buf = buf;
		this.offset = offset;
		this.len = len;
	}
	
	public RecordInterpreter(byte[] value) {
		this.buf = value;
		this.offset = 0;
		this.len = value.length;
	}
	
	public int getPCR() {
		if (pcr == -1) {
			pcr = ElRecordAccess.getPCR(buf, offset, len);
		}
		return pcr;
	}
	
	public byte getType() {
		if (type == -1) {
			type = ElRecordAccess.getType(buf, offset, len);
		}
		return type;
	}
	
	public String getValue() {
		if (value == null) {
			value = ElRecordAccess.getValue(buf, offset, len);
		}
		return value;
	}
}
