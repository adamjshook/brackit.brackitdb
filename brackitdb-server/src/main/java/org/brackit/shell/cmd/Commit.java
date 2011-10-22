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
package org.brackit.shell.cmd;

import java.io.PrintStream;

import org.brackit.driver.BrackitConnection;
import org.brackit.driver.BrackitException;

/**
 * @author Sebastian Baechle
 * 
 */
public class Commit extends AbstractCommand {
	public final static String NAMES[] = { "COMMIT" };

	private final static String USAGE = "COMMIT [<transaction name>]";

	private final static String INFO = "Commits the currently running transaction.";

	private final static String HELP = "The currently running transaction is committed and all modifications are written\n"
			+ "into the database. The resources acquired by the transaction (memory buffers or locks)\n"
			+ "are released.\n";

	public Commit() {
		super(NAMES, USAGE, INFO, HELP);
	}

	public void call(BrackitConnection connection, PrintStream out,
			String... params) throws BrackitException {
		if (params.length != 0) {
			throw new BrackitException("Invalid number of parameters.");
		} else {
			connection.commit();
			out.println("Commit");
		}
	}
}