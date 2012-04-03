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
package org.brackit.server.session.proxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.brackit.server.session.SessionID;
import org.brackit.server.session.SessionMgr;

/**
 * @author Sebastian Baechle
 * 
 */
public final class SessionInterceptor extends Interceptor {

	private final SessionMgr sessionMgr;

	public SessionInterceptor(Object impl, SessionMgr sessionMgr) {
		super(impl);
		this.sessionMgr = sessionMgr;
	}

	@Override
	public Object interceptCall(Method method, Object[] args) throws Throwable {
		SessionID connectionID = null;

		if ((args != null) && (args.length > 1)
				&& (args[0] instanceof SessionID)) {
			connectionID = (SessionID) args[0];
		}

		try {
			if (connectionID != null) {
				sessionMgr.prepare(connectionID);
			}
			Object result = method.invoke(impl, args);
			if (connectionID != null) {
				sessionMgr.cleanup(connectionID, true, false);
			}
			return result;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();

			if (cause == null) {
				throw new RuntimeException(
						"An invocation error with unknown cause occured.");
			}

			cause.printStackTrace();

			if (connectionID != null) {
				sessionMgr.cleanup(connectionID, false, false);
			}
			throw cause;
		}
	}
}
