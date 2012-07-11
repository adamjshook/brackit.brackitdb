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
package org.brackit.server.tx.locking.util;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.tx.locking.table.TreeLockNameFactory;
import org.brackit.xquery.util.Cfg;

/**
 * Silly-named utility to allow for easy switching between hashed and explicit
 * lock names for debugging.
 * 
 * @author Sebastian Baechle
 * 
 */
public final class DeweyIDLockNameFactoryFactory {
	public static final String DEWEYID_LOCKS = "org.brackit.server.tx.taMgr.locking.util.deweyIDLocks";

	private final boolean hashed;

	public DeweyIDLockNameFactoryFactory() {
		String useType = Cfg.check(DeweyIDLockNameFactoryFactory.DEWEYID_LOCKS);
		hashed = ((useType == null) || (!"explicit".equals(useType)));
	}

	public final TreeLockNameFactory create(XTCdeweyID deweyID, int tail) {
		return (hashed) ? new HashLockNameFactory(deweyID, tail)
				: new DeweyIDLockNameFactory(deweyID, tail);
	}
}
