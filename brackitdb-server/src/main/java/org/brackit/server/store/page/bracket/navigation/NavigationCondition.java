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
package org.brackit.server.store.page.bracket.navigation;

import org.brackit.server.store.page.bracket.BracketKey;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;

/**
 * This interface represents a condition (success or break condition) that is
 * used for navigating over the bracket storage.
 * 
 * <p>
 * There are four variables that can be used for the check of the condition:
 * </p>
 * <ul>
 * <li>the variable "levelDiff" (describing the logical level difference since
 * the beginning of the navigation)</li>
 * <li>the compare value of the current DeweyIDBuffer</li>
 * <li>the current bracket key type</li>
 * <li>the current offset</li>
 * </ul>
 * 
 * @author Martin Hiller
 * 
 */
public interface NavigationCondition {

	/**
	 * Evaluates the condition this instance stands for.
	 * @param levelDiff the current level difference (since the start of the navigation)
	 * @param deweyID the current DeweyIDBuffer (to invoke the "compare" method)
	 * @param type the type of the current BracketKey
	 * @param keyOffset the current offset in the key area
	 * @return true if this condition is fulfilled, false otherwise
	 */
	public boolean checkCondition(int levelDiff, DeweyIDBuffer deweyID, BracketKey.Type type, int keyOffset);
	
}
