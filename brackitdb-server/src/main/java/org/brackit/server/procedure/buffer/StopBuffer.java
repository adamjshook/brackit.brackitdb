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
package org.brackit.server.procedure.buffer;

import org.apache.log4j.Logger;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.procedure.Procedure;
import org.brackit.server.procedure.ProcedureUtil;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.xdm.Sequence;

/**
 * @author Sebastian Baechle
 * 
 */
public class StopBuffer implements Procedure {
	private static final Logger log = Logger.getLogger(StopBuffer.class);
	private static final String[] PARAMETER = new String[] {
			"CONTAINERID - ID of the buffer", "[FORCE - Force shutdown]" };

	private final static String INFO = "Stops a buffer";

	public String getInfo() {
		return INFO;
	}

	public String getName() {
		return getClass().getSimpleName();
	}

	public String[] getParameter() {
		return PARAMETER;
	}

	public Sequence execute(TXQueryContext context, String... parameter)
			throws QueryException {
		ProcedureUtil.checkParameterCount(parameter, 1, 2, PARAMETER);
		int containerID = ProcedureUtil.getInt(parameter, 0, "ContainerID", -1,
				null, true);
		boolean force = ProcedureUtil.getBoolean(parameter, 1, "Force", false,
				false);
		BufferMgr bufferMgr = context.getTX().getBufferManager();

		try {
			bufferMgr.stopBuffer(containerID, force);
			return new Str(String.format("Buffer %s succcessfully closed",
					containerID));
		} catch (BufferException e) {
			log.error(e);
			return new Str(String.format("Error closing buffer %s: %s",
					containerID, e.getMessage()));
		}
	}
}