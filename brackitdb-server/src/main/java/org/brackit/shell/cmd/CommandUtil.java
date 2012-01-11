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
package org.brackit.shell.cmd;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import org.brackit.driver.BrackitConnection;
import org.brackit.driver.BrackitException;

/**
 * Utility class for loading {@link Command Commands} and for checking and
 * converting their parameters.
 * 
 * @author Sebastian Baechle
 * 
 */
public class CommandUtil {
	private java.util.List<String> allCommandNames;

	private java.util.List<Command> commands;

	private BrackitConnection connection;

	public void addCommand(Command cmd) throws Exception {
		for (String cmdName : cmd.getNames()) {
			if (allCommandNames.contains(cmdName)) {
				throw new Exception("Duplicate command name while adding: "
						+ cmd.getNames());
			}
			allCommandNames.add(cmdName);
		}
		commands.add(cmd);
	}

	public void removeCommand(String name) throws Exception {
		Command cmd = getCommand(name);
		if (cmd == null) {
			throw new Exception("Unknown command: " + name);
		}
		for (String cmdName : cmd.getNames()) {
			allCommandNames.remove(cmdName);
		}
		commands.remove(cmd);
	}

	public CommandUtil(BrackitConnection connection) throws Exception {
		this.connection = connection;
		this.commands = new ArrayList<Command>();
		this.allCommandNames = new ArrayList<String>();

		addCommand(new Begin());
		addCommand(new Commit());
	}

	public Command getCommand(String name) {
		for (Command command : commands) {
			if (command.respondsTo(name)) {
				return command;
			}
		}
		return null;
	}

	public Collection<Command> getCommands() {
		return commands;
	}

	public boolean provides(String commandName) {
		return (getCommand(commandName) != null);
	}

	public boolean execute(String line, String command, PrintStream out,
			String... params) {
		Command cmd = getCommand(command);

		try {
			if (cmd != null) {
				cmd.call(connection, out, params);
			} else {
				connection.query(line, out);
			}
			return true;
		} catch (BrackitException e) {
			out.println();
			out.print("Error: ");
			out.println(e.getMessage());
			return false;
		}
	}
}