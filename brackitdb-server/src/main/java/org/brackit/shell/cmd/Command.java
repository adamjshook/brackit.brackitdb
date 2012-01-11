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

import jline.Completor;

import org.brackit.driver.BrackitConnection;
import org.brackit.driver.BrackitException;

/**
 * Command that can be called by the client
 * 
 * @author Sebastian Baechle
 * 
 */
public interface Command {
	/**
	 * Returns all names of the command
	 * 
	 * @return names of the command
	 */
	public String[] getNames();

	/**
	 * Returns the help text for this command
	 * 
	 * @return help text for this command
	 */
	public String getUsage();

	/**
	 * Returns some information about this command
	 * 
	 * @return information about this command
	 */
	public String getInfo();

	/**
	 * Returns the help for this command
	 * 
	 * @return help for this command
	 */
	public String getHelp();

	/**
	 * Executes the command
	 * 
	 * @param connection
	 *            connection for server access
	 * @param out
	 *            TODO
	 * @param params
	 *            parameters for the command
	 * @throws BrackitException
	 *             TODO
	 */
	public void call(BrackitConnection connection, PrintStream out,
			String... params) throws BrackitException;

	/**
	 * Checks whether this command responds the given <code>command</code>
	 * string
	 * 
	 * @param command
	 *            name of the command
	 * @return <code>true</code> iff this command responds to the given
	 *         <code>command</code> string
	 */
	public boolean respondsTo(String command);

	public Completor getCompletor();

}
