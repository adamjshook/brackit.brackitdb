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
package org.brackit.server.tx.locking.protocol;

import java.util.Arrays;
import java.util.Collection;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class TaDOM3Plus
		implements
		TreeLockProtocol<org.brackit.server.tx.locking.protocol.TaDOM3Plus.Mode> {
	public enum Mode implements TreeLockMode<Mode> {
		IR(0) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SR;
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
		NR(1) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return (distanceToTargetLevel == 1) ? LR : SR;
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
		LR(2) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SR;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return NR;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}
		},
		SR(3) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SR;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return SR;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}
		},
		IX(4) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
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
		NRIX(5) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return NR;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}
		},
		LRIX(6) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return NR;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}
		},
		SRIX(7) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return SR;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}
		},
		CX(8) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
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
		NRCX(9) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
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
		LRCX(10) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return NR;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}
		},
		SRCX(11) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return SR;
			}

			@Override
			public Mode requiredParentMode() {
				return IX;
			}
		},
		NU(12) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SU;
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
		LRNU(13) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SU;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return NR;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}
		},
		SRNU(14) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SU;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return SR;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}
		},
		NX(15) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return null;
			}

			@Override
			public Mode requiredParentMode() {
				return CX;
			}
		},
		LRNX(16) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return NR;
			}

			@Override
			public Mode requiredParentMode() {
				return CX;
			}
		},
		SRNX(17) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return SR;
			}

			@Override
			public Mode requiredParentMode() {
				return CX;
			}
		},
		SU(18) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SU;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return SU;
			}

			@Override
			public Mode requiredParentMode() {
				return IR;
			}
		},
		SX(19) {
			@Override
			public Mode escalate(int distanceToTargetLevel) {
				return SX;
			}

			@Override
			public Mode implicitlyGrantedChildMode() {
				return SX;
			}

			@Override
			public Mode requiredParentMode() {
				return CX;
			}
		};

		private static final boolean YE = true;

		private static final boolean NO = false;

		public static final boolean[][] compatibility = {
				/*
				 * IR NR LR SR IX NRIX LRIX SRIX CX NRCX LRCX SRCX NU LRNU SRNU
				 * NX LRNX SRNX SU SX
				 */
				/* IR */{ YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, YE,
						YE, YE, YE, YE, YE, NO, NO },
				/* NR */{ YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* LR */{ YE, YE, YE, YE, YE, YE, YE, YE, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* SR */{ YE, YE, YE, YE, NO, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* IX */{ YE, YE, YE, NO, YE, YE, YE, NO, YE, YE, YE, NO, YE,
						YE, NO, YE, YE, NO, NO, NO },
				/* NRIX */{ YE, YE, YE, NO, YE, YE, YE, NO, YE, YE, YE, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* LRIX */{ YE, YE, YE, NO, YE, YE, YE, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* SRIX */{ YE, YE, YE, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* CX */{ YE, YE, NO, NO, YE, YE, NO, NO, YE, YE, NO, NO, YE,
						NO, NO, YE, NO, NO, NO, NO },
				/* NRCX */{ YE, YE, NO, NO, YE, YE, NO, NO, YE, YE, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* LRCX */{ YE, YE, NO, NO, YE, YE, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* SRCX */{ YE, YE, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* NU */{ YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, YE, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* LRNU */{ YE, YE, YE, YE, YE, YE, YE, YE, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* SRNU */{ YE, YE, YE, YE, NO, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* NX */{ YE, NO, NO, NO, YE, NO, NO, NO, YE, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* LRNX */{ YE, NO, NO, NO, YE, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* SRNX */{ YE, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* SU */{ YE, YE, YE, YE, NO, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO },
				/* SX */{ NO, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO, NO,
						NO, NO, NO, NO, NO, NO, NO } };

		public static final Mode[][] conversions = {
				/*
				 * IR NR LR SR IX NRIX LRIX SRIX CX NRCX LRCX SRCX NU LRNU SRNU
				 * NX LRNX SRNX SU SX
				 */
				/* IR */{ IR, NR, LR, SR, IX, NRIX, LRIX, SRIX, CX, NRCX, LRCX,
						SRCX, NU, LRNU, SRNU, NX, LRNX, SRNX, SU, SX },
				/* NR */{ NR, NR, LR, SR, NRIX, NRIX, LRIX, SRIX, NRCX, NRCX,
						LRCX, SRCX, NR, LR, SR, NX, LRNX, SRNX, SU, SX },
				/* LR */{ LR, LR, LR, SR, LRIX, LRIX, LRIX, SRIX, LRCX, LRCX,
						LRCX, SRCX, LRNU, LRNU, SRNU, LRNX, LRNX, SRNX, SU, SX },
				/* SR */{ SR, SR, SR, SR, SRIX, SRIX, SRIX, SRIX, SRCX, SRCX,
						SRCX, SRCX, SRNU, SRNU, SRNU, SRNX, SRNX, SRNX, SR, SX },
				/* IX */{ IX, NRIX, LRIX, SRIX, IX, NRIX, LRIX, SRIX, CX, NRCX,
						LRCX, SRCX, NX, LRNX, SRNX, NX, LRNX, SRNX, SX, SX },
				/* NRIX */{ NRIX, NRIX, LRIX, SRIX, NRIX, NRIX, LRIX, SRIX,
						NRCX, NRCX, LRCX, SRCX, NX, LRNX, SRNX, NX, LRNX, SRNX,
						SX, SX },
				/* LRIX */{ LRIX, LRIX, LRIX, SRIX, LRIX, LRIX, LRIX, SRIX,
						LRCX, LRCX, LRCX, SRCX, LRNX, LRNX, SRNX, LRNX, LRNX,
						SRNX, SX, SX },
				/* SRIX */{ SRIX, SRIX, SRIX, SRIX, SRIX, SRIX, SRIX, SRIX,
						SRCX, SRCX, SRCX, SRCX, SRNX, SRNX, SRNX, SRNX, SRNX,
						SRNX, SX, SX },
				/* CX */{ CX, NRCX, LRCX, SRCX, CX, NRCX, LRCX, SRCX, CX, NRCX,
						LRCX, SRCX, NX, LRNX, SRNX, NX, LRNX, SRNX, SX, SX },
				/* NRCX */{ NRCX, NRCX, LRCX, SRCX, NRCX, NRCX, LRCX, SRCX,
						NRCX, NRCX, LRCX, SRCX, NX, LRNX, SRNX, NX, LRNX, SRNX,
						SX, SX },
				/* LRCX */{ LRCX, LRCX, LRCX, SRCX, LRCX, LRCX, LRCX, SRCX,
						LRCX, LRCX, LRCX, SRCX, LRNX, LRNX, SRNX, LRNX, LRNX,
						SRNX, SX, SX },
				/* SRCX */{ SRCX, SRCX, SRCX, SRCX, SRCX, SRCX, SRCX, SRCX,
						SRCX, SRCX, SRCX, SRCX, SRNX, SRNX, SRNX, SRNX, SRNX,
						SRNX, SX, SX },
				/* NU */{ NU, NU, LRNU, SRNU, NX, NX, LRNX, SRNX, NX, NX, LRNX,
						SRNX, NU, LRNU, SRNU, NX, LRNX, SRNX, SU, SX },
				/* LRNU */{ LRNU, LRNU, LRNU, SRNU, LRNX, LRNX, LRNX, SRNX,
						LRNX, LRNX, LRNX, SRNX, LRNU, LRNU, SRNU, LRNX, LRNX,
						SRNX, SU, SX },
				/* SRNU */{ SRNU, SRNU, SRNU, SRNU, SRNX, SRNX, SRNX, SRNX,
						SRNX, SRNX, SRNX, SRNX, SRNU, SRNU, SRNU, SRNX, SRNX,
						SRNX, SU, SX },
				/* NX */{ NX, NX, LRNX, SRNX, NX, NX, LRNX, SRNX, NX, NX, LRNX,
						SRNX, NX, LRNX, SRNX, NX, LRNX, SRNX, SX, SX },
				/* LRNX */{ LRNX, LRNX, LRNX, SRNX, LRNX, LRNX, LRNX, SRNX,
						LRNX, LRNX, LRNX, SRNX, LRNX, LRNX, SRNX, LRNX, LRNX,
						SRNX, SX, SX },
				/* SRNX */{ SRNX, SRNX, SRNX, SRNX, SRNX, SRNX, SRNX, SRNX,
						SRNX, SRNX, SRNX, SRNX, SRNX, SRNX, SRNX, SRNX, SRNX,
						SRNX, SX, SX },
				/* SU */{ SU, SU, SU, SU, SX, SX, SX, SX, SX, SX, SX, SX, SU,
						SU, SU, SX, SX, SX, SU, SX },
				/* SX */{ SX, SX, SX, SX, SX, SX, SX, SX, SX, SX, SX, SX, SX,
						SX, SX, SX, SX, SX, SX, SX } };

		private final static int A = Integer.MAX_VALUE;
		public static final int[][] implies = {
				/*
				 * IR NR LR SR IX NRIX LRIX SRIX CX RCX LRCX SRCX NU LRNU SRNU
				 * NX LRNX SRNX SU SX
				 */
				/* IR */{ 0, 0, 0, A, 0, 0, 0, A, 0, 0, 0, A, 0, 0, A, 0, 0, A,
						A, A },
				/* NR */{ 0, 0, 1, A, 0, 0, 1, A, 0, 0, 1, A, 0, 1, A, 0, 1, A,
						A, A },
				/* LR */{ 0, 0, 0, A, 0, 0, 0, A, 0, 0, 0, A, 0, 0, A, 0, 0, A,
						A, A },
				/* SR */{ 0, 0, 0, A, 0, 0, 0, A, 0, 0, 0, A, 0, 0, A, 0, 0, A,
						A, A },
				/* IX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, A,
						0, A },
				/* NRIX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* LRIX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* SRIX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* CX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, A },
				/* NRCX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* LRCX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* SRCX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* NU */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						A, A },
				/* LRNU */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, A, A },
				/* SRNU */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, A, A },
				/* NX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, A },
				/* LRNX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* SRNX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, A },
				/* SU */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						A, A },
				/* SX */{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, A } };

		private final int id;

		private Mode(int id) {
			this.id = id;
		}

		@Override
		public abstract Mode escalate(int distanceToTargetLevel);

		@Override
		public abstract Mode implicitlyGrantedChildMode();

		@Override
		public abstract Mode requiredParentMode();

		public final boolean implies(Mode targetMode, int distanceToTargetLevel) {
			return (implies[targetMode.id][id] >= distanceToTargetLevel);
		}

		public final Mode convert(Mode requestedMode) {
			return conversions[requestedMode.id][id];
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

	public final Mode getLevelSharedMode() {
		return Mode.LR;
	}

	public final Mode getNodeExclusiveMode() {
		return Mode.NX;
	}

	public final Mode getNodeSharedMode() {
		return Mode.NR;
	}

	public final Mode getNodeUpdateMode() {
		return Mode.NU;
	}

	public final Mode getTreeExclusiveMode() {
		return Mode.SX;
	}

	public final Mode getTreeSharedMode() {
		return Mode.SR;
	}

	public final Mode getTreeUpdateMode() {
		return Mode.SU;
	}

	public final static void main(String[] args) {
		System.out.println(LockProtocolUtil.dump(new TaDOM3Plus()));
		LockProtocolUtil.testConversionMatrix(new TaDOM3Plus());
	}
}