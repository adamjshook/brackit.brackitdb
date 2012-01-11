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
import org.brackit.server.store.page.bracket.BracketKey.Type;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.navigation.NavigationProperties.NavigationTarget;

/**
 * This class provides a set of the most common navigation properties.
 * 
 * @author Martin Hiller
 * 
 */
public class NavigationProfiles {

	private static final NavigationCondition TRUE_CONDITION = new NavigationCondition() {
		@Override
		public boolean checkCondition(int levelDiff, DeweyIDBuffer deweyID,
				Type type, int keyOffset) {
			return true;
		}
	};

	private static final NavigationCondition FALSE_CONDITION = new NavigationCondition() {
		@Override
		public boolean checkCondition(int levelDiff, DeweyIDBuffer deweyID,
				Type type, int keyOffset) {
			return false;
		}
	};

	public static final NavigationProperties FIRST_CHILD = new NavigationProperties(
			NavigationTarget.FIRST, true, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return levelDiff == 1;
				}
			}, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return levelDiff < 1;
				}
			});

	public static final NavigationProperties LAST_CHILD = new NavigationProperties(
			NavigationTarget.LAST, true, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return levelDiff == 1;
				}
			}, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return levelDiff < 1;
				}
			});

	public static final NavigationProperties NEXT_SIBLING = new NavigationProperties(
			NavigationTarget.FIRST, true, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return levelDiff == 0;
				}
			}, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return levelDiff < 0;
				}
			});

	public static final NavigationProperties BY_DEWEYID = new NavigationProperties(
			NavigationTarget.FIRST, false, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return deweyID.compare() == 0;
				}
			}, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return deweyID.compare() > 0;
				}
			});
	
	public static final NavigationProperties GREATER_OR_EQUAL = new NavigationProperties(
			NavigationTarget.FIRST, false, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return deweyID.compare() >= 0;
				}
			}, FALSE_CONDITION);

	public static final NavigationProperties NEXT_ATTRIBUTE = new NavigationProperties(
			NavigationTarget.FIRST, false, TRUE_CONDITION,
			new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return type != BracketKey.Type.ATTRIBUTE;
				}
			});

	public static final NavigationProperties TO_INSERT_POS = new NavigationProperties(
			NavigationTarget.LAST, false, TRUE_CONDITION,
			new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return deweyID.compare() > 0;
				}
			});

	public static final NavigationProperties NEXT_NODE = new NavigationProperties(
			NavigationTarget.FIRST, false, TRUE_CONDITION, FALSE_CONDITION);

	public static final NavigationProperties LAST_NODE = new NavigationProperties(
			NavigationTarget.LAST, false, TRUE_CONDITION, FALSE_CONDITION);

	public static final NavigationProperties PARENT_OR_SIBLING = new NavigationProperties(
			NavigationTarget.LAST, true, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return (levelDiff == -1 || levelDiff == 0);
				}
			}, new NavigationCondition() {
				@Override
				public boolean checkCondition(int levelDiff,
						DeweyIDBuffer deweyID, Type type, int keyOffset) {
					return deweyID.compare() >= 0;
				}
			});

	public static NavigationProperties getPreviousByKeyOffset(
			final int keyOffset) {
		return new NavigationProperties(NavigationTarget.LAST, false,
				TRUE_CONDITION, new NavigationCondition() {
					@Override
					public boolean checkCondition(int levelDiff,
							DeweyIDBuffer deweyID, Type type, int offset) {
						return offset == keyOffset;
					}
				});
	}

	public static NavigationProperties getParentOrSibling(final int keyOffset) {
		return new NavigationProperties(NavigationTarget.LAST, true,
				new NavigationCondition() {
					@Override
					public boolean checkCondition(int levelDiff,
							DeweyIDBuffer deweyID, Type type, int offset) {
						return (levelDiff == -1 || levelDiff == 0);
					}
				}, new NavigationCondition() {
					@Override
					public boolean checkCondition(int levelDiff,
							DeweyIDBuffer deweyID, Type type, int offset) {
						return offset >= keyOffset;
					}
				});
	}
}
