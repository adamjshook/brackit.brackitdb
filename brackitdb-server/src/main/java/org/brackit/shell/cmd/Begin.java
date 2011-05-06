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
public class Begin extends AbstractCommand {
	public final static String NAMES[] = { "BEGIN" };

	private final static String USAGE = "BEGIN [<transaction name>]";

	private final static String INFO = "Starts a new transaction.";

	private final static String HELP = "The BEGIN command starts a new transaction in order to execute several commands\n"
			+ "within one transaction. An optional transaction name can be additionally specified to\n"
			+ "identify the transaction. This name appears also in the list of all currently running\n"
			+ "transactions (command LIST TRANSACTIONS).\n"
			+ "The transaction is running with the currently set isolation level (command ISOLATION)\n"
			+ "and the currently set lock depth (command LOCKDEPTH).\n"
			+ "The started transaction is running until the COMMIT or ROLLBACK commands are invoked or\n"
			+ "until the command line processor is stopped (command QUIT).\n";

	public Begin() {
		super(NAMES, USAGE, INFO, HELP);
	}

	public void call(BrackitConnection connection, PrintStream out,
			String... params) throws BrackitException {
		String transactionName = null;

		if (params.length > 1) {
			throw new BrackitException("Invalid number of parameters.\n\n"
					+ getUsage());
		} else {
			if ((params.length == 1) && (params[0] == null)) {
				transactionName = params[0];
			}

			connection.begin();
			out.println("Begin");
		}
	}
}
