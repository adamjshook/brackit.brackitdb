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
package org.brackit.server.io.buffer.log;

import org.brackit.server.io.buffer.Buffer;
import org.brackit.server.io.buffer.BufferException;
import org.brackit.server.tx.Tx;
import org.brackit.server.tx.log.LogException;
import org.brackit.xquery.util.log.Logger;

/**
 * 
 * @author Martin Hiller
 *
 */
public class CreateUnitLogOperation extends UnitLogOperation {
	
	private final static Logger log = Logger
			.getLogger(CreateUnitLogOperation.class.getName());

	public CreateUnitLogOperation(int containerID, int unitID) {
		super(PageLogOperation.CREATE_UNIT, containerID, unitID);
	}

	@Override
	public void redo(Tx tx, long LSN) throws LogException {
		
		Buffer buffer = null;

		try {
			buffer = tx.getBufferManager().getBuffer(containerID);
		} catch (BufferException e) {
			throw new LogException(e);
		}
		
		if (log.isDebugEnabled()) {
			log.debug(String.format("Redo creation of unit %s.", unitID));
		}
		
		try {
			buffer.createUnit(tx, unitID, false, -1, true);
		} catch (BufferException e) {
			throw new LogException(e);
		}
	}

	@Override
	public void undo(Tx tx, long LSN, long undoNextLSN) throws LogException {
		
		Buffer buffer = null;

		try {
			buffer = tx.getBufferManager().getBuffer(containerID);
		} catch (BufferException e) {
			throw new LogException(e);
		}
		
		if (log.isDebugEnabled()) {
			log.debug(String.format("Undo creation of unit %s.", unitID));
		}
		
		try {
			buffer.dropUnit(tx, unitID, true, undoNextLSN, false);
		} catch (BufferException e) {
			throw new LogException(e);
		}
	}
}
