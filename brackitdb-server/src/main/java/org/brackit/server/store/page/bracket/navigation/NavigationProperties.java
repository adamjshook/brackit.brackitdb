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

/**
 * This class is a container for all properties that has to be specified in
 * order to execute a navigational operation.
 * 
 * @author Martin Hiller
 * 
 */
public class NavigationProperties {
	
	/**
	 * Is the first or last node wanted that satisfies the success condition?
	 */
	public enum NavigationTarget {
		FIRST, LAST
	}
	
	public final NavigationTarget target;
	public final boolean ignoreAttributes;
	public final NavigationCondition successCondition;
	public final NavigationCondition breakCondition;
	
	/**
	 * Creates an instance.
	 * @param target which node is supposed to be returned: the first or the last node that fulfills the success condition?
	 * @param ignoreAttributes can attributes be ignored? (for performance reasons)
	 * @param successCondition the condition that has to be fulfilled to qualify a certain node as result node
	 * @param breakCondition the condition under which the navigational operation can be aborted
	 */
	public NavigationProperties(NavigationTarget target, boolean ignoreAttributes, NavigationCondition successCondition, NavigationCondition breakCondition) {
		this.target = target;
		this.ignoreAttributes = ignoreAttributes;
		this.successCondition = successCondition;
		this.breakCondition = breakCondition;
	}
	
	/**
	 * Creates an instance.
	 * @param target which node is supposed to be returned: the first or the last node that fulfills the success condition?
	 * @param ignoreAttributes can attributes be ignored? (for performance reasons)
	 * @param successCondition the condition that has to be fulfilled to qualify a certain node as result node
	 * @param breakCondition the condition under which the navigational operation can be aborted
	 */
	public NavigationProperties(NavigationTarget target, boolean ignoreAttributes, NavigationCondition successCondition, NavigationCondition breakCondition, int relevantLevelThreshold) {
		this.target = target;
		this.ignoreAttributes = ignoreAttributes;
		this.successCondition = successCondition;
		this.breakCondition = breakCondition;
	}
}
