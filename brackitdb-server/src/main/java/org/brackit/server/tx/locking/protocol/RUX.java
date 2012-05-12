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
package org.brackit.server.tx.locking.protocol;

import java.util.Arrays;
import java.util.Collection;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class RUX implements
		LockProtocol<org.brackit.server.tx.locking.protocol.RUX.Mode> {
	public enum Mode implements LockMode<Mode> {
		R(0), U(1), X(2);

		private static final boolean YE = true;

		private static final boolean NO = false;

		public static boolean[][] compatibility = {
		/* R U X */
		/* R */{ YE, NO, NO },
		/* U */{ YE, NO, NO },
		/* X */{ NO, NO, NO } };

		public static final Mode[][] conversions = {
		/* R U X */
		/* R */{ R, R, X },
		/* U */{ U, U, X },
		/* X */{ X, X, X } };

		private final int id;

		private Mode(int id) {
			this.id = id;
		}

		public final Mode convert(Mode requestedMode) {
			return conversions[requestedMode.id][id];
		}

		public final boolean isCompatible(Mode requestedMode) {
			return (compatibility[requestedMode.id][id]);
		}
	}

	public Collection<Mode> modes() {
		return Arrays.asList(Mode.values());
	}

	public final static void main(String[] args) {
		System.out.println(LockProtocolUtil.dump(new RUX()));
		LockProtocolUtil.testConversionMatrix(new RUX());
	}
}
