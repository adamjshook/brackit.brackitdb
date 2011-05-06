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
package org.brackit.shell;

import java.io.File;
import java.io.PrintStream;

import jline.ConsoleReader;

import org.brackit.driver.BrackitConnection;
import org.brackit.shell.cmd.Command;
import org.brackit.shell.cmd.CommandUtil;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class Shell {
	private static final File HISTORY_FILE = new File(System
			.getProperty("user.home")
			+ System.getProperty("file.separator") + ".brackit_history");

	private final BrackitConnection connection;

	private final CommandUtil cmdUtil;

	private final ConsoleReader reader;

	private final PrintStream out = System.out;

	public Shell(String host, int port) throws Exception {
		connection = new BrackitConnection(host, port);
		cmdUtil = new CommandUtil(connection);
		reader = new ConsoleReader();
		reader.setBellEnabled(false);
		reader.getHistory().setHistoryFile(HISTORY_FILE);
		for (Command cmd : cmdUtil.getCommands()) {
			reader.addCompletor(cmd.getCompletor());
		}
	}

	private void prompt(long time) {
		out.println();
		out.print(String.format("[%5s]$ ", time));
	}

	public void run() {
		prompt(0);
		String input;
		;

		try {
			while ((input = reader.readLine()) != null) {
				input = input.trim();

				if (input.isEmpty()) {
					continue;
				}

				int indexOf = input.indexOf(' ');
				String cmd = input;
				String[] params = new String[0];

				if (indexOf >= 0) {
					cmd = input.substring(0, indexOf);
					params = input.substring(indexOf, input.length()).trim()
							.split(" ");
				}

				long start = System.currentTimeMillis();
				cmdUtil.execute(input, cmd, out, params);
				long end = System.currentTimeMillis();
				prompt(end - start);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try {
			Shell shell = new Shell("localhost", 11011);
			shell.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
