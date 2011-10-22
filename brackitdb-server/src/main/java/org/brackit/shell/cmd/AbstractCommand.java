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

import jline.ArgumentCompletor;
import jline.Completor;
import jline.NullCompletor;

import org.brackit.shell.completor.CaseInsensitiveSimpleCompletor;

/**
 * Abstract base for commands
 * 
 * @author Sebastian Baechle
 * 
 */
public abstract class AbstractCommand implements Command {
	private final String names[];
	private final String usage;
	private final String info;
	private final String help;

	public AbstractCommand(String[] names, final String usage,
			final String info, final String help) {
		super();
		this.names = names;
		this.usage = usage;
		this.info = info;
		this.help = help;
	}

	public String getInfo() {
		return info;
	}

	public String[] getNames() {
		return names;
	}

	public String getUsage() {
		return usage;
	}

	public String getHelp() {
		return help;
	}

	public boolean respondsTo(String command) {
		if (command == null) {
			return false;
		}
		for (String name : names) {
			if (name.equalsIgnoreCase(command)) {
				return true;
			}
		}
		return false;
	}

	public Completor getCompletor() {
		Completor comp[] = new Completor[2];
		comp[0] = new CaseInsensitiveSimpleCompletor(names);
		comp[1] = new NullCompletor();
		return new ArgumentCompletor(comp);
	}

}