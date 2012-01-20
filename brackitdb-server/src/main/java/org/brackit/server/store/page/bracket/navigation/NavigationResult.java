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
package org.brackit.server.store.page.bracket.navigation;

import org.brackit.server.store.page.bracket.BracketKey;
import org.brackit.server.store.page.bracket.BracketPage;

/**
 * Result of a navigation operation in the bracket page.
 * 
 * @author Martin Hiller
 * 
 */
public class NavigationResult {

	/**
	 * Navigation status.
	 */
	public NavigationStatus status;

	/**
	 * If the node was found or probably found, this value describes the offset
	 * of the found node.
	 */
	public int keyOffset;

	/**
	 * If the node was found or probably found, this value describes the bracket
	 * key type of the found node.
	 */
	public BracketKey.Type keyType;

	/**
	 * If the node was found or probably found, this value describes the
	 * (logical) level difference between the reference node and the found node.
	 */
	public int levelDiff;

	/**
	 * Indicates whether the break condition of the navigation was reached.
	 */
	public boolean breakConditionFulfilled;
	
	/**
	 * Creates a NavigationResult.
	 */
	public NavigationResult() {
		reset();
	}
	
	/**
	 * Resets this object to its default values.
	 */
	public void reset() {
		this.status = NavigationStatus.NOT_EXISTENT;
		this.keyOffset = BracketPage.BEFORE_LOW_KEY_OFFSET;
		this.keyType = null;
		this.levelDiff = 0;
		this.breakConditionFulfilled = true;
	}
}
