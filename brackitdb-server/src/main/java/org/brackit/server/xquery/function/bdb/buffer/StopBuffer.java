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
package org.brackit.server.xquery.function.bdb.buffer;

import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.xquery.function.FunUtil;
import org.brackit.server.xquery.function.bdb.BDBFun;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.util.annotation.FunctionAnnotation;
import org.brackit.xquery.util.log.Logger;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * @author Sebastian Baechle
 * 
 */
@FunctionAnnotation(description = "Stops the given buffer.", parameters = "$bufferNo")
public class StopBuffer extends AbstractFunction {

	private static final Logger log = Logger.getLogger(StopBuffer.class);

	public static final QNm DEFAULT_NAME = new QNm(BDBFun.BDB_NSURI,
			BDBFun.BDB_PREFIX, "stop-buffer");

	public StopBuffer() {
		super(DEFAULT_NAME, new Signature(new SequenceType(AtomicType.STR,
				Cardinality.One), new SequenceType(AtomicType.INR,
				Cardinality.One)), true);
	}

	public Sequence execute(StaticContext sctx, QueryContext ctx,
			Sequence[] args) throws QueryException {
		int containerID = FunUtil.getInt(args, 0, "ContainerID", -1,
				null, true);
		boolean force = FunUtil
				.getBoolean(args, 1, "Force", false, false);
		TXQueryContext txctx = (TXQueryContext) ctx;
		BufferMgr bufferMgr = txctx.getTX().getBufferManager();

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