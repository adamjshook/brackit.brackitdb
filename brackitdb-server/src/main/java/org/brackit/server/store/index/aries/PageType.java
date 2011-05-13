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
package org.brackit.server.store.index.aries;

import org.brackit.server.store.page.slot.SlottedPage;

/**
 * Page types supported by {@link Buffer}.
 * 
 * @author Sebastian Baechle
 * 
 */
public final class PageType {
	/**
	 * Inner (tree) page of an {@link org.brackit.server.io.file.index.Index}.
	 * <ul>
	 * <li>byte 0-3 (int) - root page no
	 * <li>byte 4 (byte)- key type</li>
	 * <li>byte 5 (byte) - value type</li>
	 * <li>byte 6 (byte) - unique flag (mask 128 = 1000 0000), deleted flag
	 * (mask 64 = 0100 0000) and safe flag (mask 32 = 0010 000)</li>
	 * <li>byte 7 (byte) - page Type
	 * <li>byte 12-15 (int) - before page pointer</li>
	 * <li>byte 16-19 (int) - free</li>
	 * </ul>
	 */
	public final static short INDEX_TREE = 2;

	/**
	 * Leaf page of an {@link org.brackit.server.io.file.index.Index}.
	 * <ul>
	 * <li>byte 0-3 (int) - root page no
	 * <li>byte 4 (byte)- key type</li>
	 * <li>byte 5 (byte) - value type</li>
	 * <li>byte 6 (byte) - unique flag (mask 128 = 1000 0000), deleted flag
	 * (mask 64 = 0100 0000) and save flag (mask 32 = 0010 000)</li>
	 * <li>byte 7 (byte) - page type
	 * <li>byte 12-15 (int) - previous page pointer</li>
	 * <li>byte 16-19 (int) - next page pointer</li>
	 * </ul>
	 */
	public final static short INDEX_LEAF = 3;

	/**
	 * {@link SlottedPage}.
	 */
	public final static int SLOTTED_PAGE = 8;

	/**
	 * Returns the additional header size of this {@PageType} in
	 * bytes
	 * 
	 * @return the additional header size of this {@PageType} in
	 *         bytes
	 */
	public final static short getTypeHeaderSize(int pageType) {
		switch (pageType) {
		case INDEX_LEAF:
			return 16;
		case INDEX_TREE:
			return 16;
		case SLOTTED_PAGE:
			return 8;
		default:
			return 0;
		}
	}
}
