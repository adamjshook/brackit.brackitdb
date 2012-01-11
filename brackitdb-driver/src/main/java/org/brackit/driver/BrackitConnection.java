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
package org.brackit.driver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class BrackitConnection {
	private final Socket bit;

	private final OutputStream to;

	private final InputStream from;

	public BrackitConnection(String host, int port) throws BrackitException {
		try {
			bit = new Socket(host, port);
			from = new BufferedInputStream(bit.getInputStream());
			to = new BufferedOutputStream(bit.getOutputStream());
		} catch (Exception e) {
			e.printStackTrace();
			throw new BrackitException("Connection failed", e);
		}
	}

	public void begin() throws BrackitException {
		send('b');
	}

	public void commit() throws BrackitException {
		send('c');
	}

	public void rollback() throws BrackitException {
		send('r');
	}

	public String query(String query) throws BrackitException {
		return send('q', query);
	}

	public void query(String query, OutputStream out) throws BrackitException {
		send('q', query, out);
	}

	private String send(char cmd) throws BrackitException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			send(cmd, (InputStream) null, out);
			return out.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new BrackitException(e);
		}
	}

	private String send(char cmd, String query) throws BrackitException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			send(cmd, new ByteArrayInputStream(query.getBytes("UTF-8")), out);
			return out.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new BrackitException(e);
		}
	}

	private void send(char cmd, String query, OutputStream out)
			throws BrackitException {
		try {
			send(cmd, new ByteArrayInputStream(query.getBytes("UTF-8")), out);
		} catch (UnsupportedEncodingException e) {
			throw new BrackitException(e);
		}
	}

	private void send(char cmd, InputStream in, OutputStream out)
			throws BrackitException {
		try {
			to.write(cmd);
			int r;

			if (in != null) {
				while ((r = in.read()) > 0) {
					to.write(r);
				}
				to.write(0);
			}
			to.flush();

			while ((r = from.read()) > 0) {
				out.write(r);
			}
			out.flush();

			// check for success
			if ((from.read() != 's')) {
				ByteArrayOutputStream err = new ByteArrayOutputStream();
				while ((r = from.read()) > 0) {
					err.write(r);
				}
				err.flush();
				throw new BrackitException(err.toString("UTF-8"));
			}
		} catch (IOException e) {
			throw new BrackitException(e);
		}
	}

	public void close() {
		try {
			to.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			from.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			bit.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class Client implements Runnable {
		public void run() {
			try {
				BrackitConnection c = new BrackitConnection("localhost", 11011);
				c.send('q', new ByteArrayInputStream("1+1".getBytes("UTF-8")),
						System.out);
				c.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		// Server s = new Server(11011);
		// new Thread(s).start();
		Client c = new Client();
		new Thread(c).start();
	}
}
