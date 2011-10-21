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
package org.brackit.server.tx.locking.protocol;

import java.util.Arrays;
import java.util.Collection;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class URIX implements
		TreeLockProtocol<org.brackit.server.tx.locking.protocol.URIX.Mode> {
	public enum Mode implements TreeLockMode<Mode> {
		IR(0) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return R;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return null;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}
		},

		IX(1) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return X;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return null;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}
		},

		R(2) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return R;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return R;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}

			@Override
			public boolean implies(Mode targetMode, int distanceToTargetLevel) {
				return (targetMode == R);
			}
		},

		RIX(3) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return X;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return R;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}

			@Override
			public boolean implies(Mode targetMode, int distanceToTargetLevel) {
				return (targetMode == R);
			}
		},

		U(4) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return U;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return U;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}

			@Override
			public boolean implies(Mode targetMode, int distanceToTargetLevel) {
				return ((targetMode == R) || (targetMode == U));
			}
		},

		X(5) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return X;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return X;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}

			@Override
			public boolean implies(Mode targetMode, int distanceToTargetLevel) {
				return true;
			}
		};

		private static final boolean YE = true;

		private static final boolean NO = false;

		public static final boolean[][] compatibility = {
		/* IR IX R RIX U X */
		/* IR */{ YE, YE, YE, YE, NO, NO },
		/* IX */{ YE, YE, NO, NO, NO, NO },
		/* R */{ YE, NO, YE, NO, NO, NO },
		/* RIX */{ YE, NO, NO, NO, NO, NO },
		/* U */{ YE, NO, YE, NO, NO, NO },
		/* X */{ NO, NO, NO, NO, NO, NO } };

		public static final Mode[][] conversions = {
		/* IR IX R RIX U X */
		/* IR */{ IR, IX, R, RIX, U, X },
		/* IX */{ IX, IX, RIX, RIX, X, X },
		/* R */{ R, RIX, R, RIX, R, X },
		/* RIX */{ RIX, RIX, RIX, RIX, X, X },
		/* U */{ U, X, U, X, U, X },
		/* X */{ X, X, X, X, X, X } };

		private final int id;

		private Mode(int id) {
			this.id = id;
		}

		public boolean implies(Mode targetMode, int distanceToTargetLevel) {
			return false;
		}

		@Override
		public abstract Mode escalate(int distanceToTargetLevel);

		@Override
		public abstract Mode implicitlyGrantedChildMode();

		@Override
		public abstract Mode requiredParentMode();

		public final Mode convert(Mode requestedMode) {
			return (conversions[requestedMode.id][id]);
		}

		public final boolean isCompatible(Mode requestedMode) {
			return (compatibility[requestedMode.id][id]);
		}

		public Mode requiredAncestorMode(int distance) {
			Mode ancestorMode = this;

			for (int i = 0; i < distance; i++) {
				ancestorMode = ancestorMode.requiredParentMode();
			}

			return ancestorMode;
		}

		public Mode implicitMode(int distance) {
			Mode descendantMode = this;

			for (int i = 0; i < distance; i++) {
				descendantMode = descendantMode.implicitlyGrantedChildMode();
			}

			return descendantMode;
		}
	}

	public Collection<Mode> modes() {
		return Arrays.asList(Mode.values());
	}

	public Mode getLevelSharedMode() {
		return null;
	}

	public Mode getNodeExclusiveMode() {
		return Mode.X;
	}

	public Mode getNodeSharedMode() {
		return Mode.IR;
	}

	public Mode getNodeUpdateMode() {
		return Mode.U;
	}

	public Mode getTreeExclusiveMode() {
		return Mode.X;
	}

	public Mode getTreeSharedMode() {
		return Mode.R;
	}

	public Mode getTreeUpdateMode() {
		return Mode.U;
	}

	public final static void main(String[] args) {
		System.out.println(LockProtocolUtil.dump(new URIX()));
		LockProtocolUtil.testConversionMatrix(new URIX());
	}
}