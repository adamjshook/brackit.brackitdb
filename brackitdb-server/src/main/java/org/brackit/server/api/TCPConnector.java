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
package org.brackit.server.api;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.log4j.Logger;
import org.brackit.server.ServerException;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.metadata.manager.MetaDataMgr;
import org.brackit.server.session.Session;
import org.brackit.server.session.SessionID;
import org.brackit.server.session.SessionMgr;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.XQuery;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class TCPConnector implements Runnable {

	private static final Logger log = Logger.getLogger(TCPConnector.class);

	final SessionMgr cm;

	final MetaDataMgr store;

	private ServerSocket server;

	public TCPConnector(SessionMgr cm, MetaDataMgr mdm, int port) {
		this.cm = cm;
		this.store = mdm;
	}

	public void shutdown() {
		try {
			if (server != null) {
				server.close();
			}
		} catch (IOException e) {
			log.error(e);
		}
	}

	public void run() {
		try {
			server = new ServerSocket(11011);
			server.setSoTimeout(600000);
			Socket socket;
			while ((socket = server.accept()) != null) {
				new ClientThread(socket).start();
			}
		} catch (SocketTimeoutException e) {
			// ignore
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				server.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static class DeferredOutputStream extends OutputStream {
		OutputStream out;

		public DeferredOutputStream(OutputStream out) {
			this.out = out;
		}

		@Override
		public void write(int b) throws IOException {
			out.write(b);
		}

		@Override
		public void flush() {
			// intercept
		}

		void deferedFlush() throws IOException {
			out.flush();
		}
	}

	private class ClientThread extends Thread {
		private final Socket socket;

		private Session session;

		public ClientThread(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				InputStream from = new BufferedInputStream(socket
						.getInputStream());
				DeferredOutputStream to = new DeferredOutputStream(
						new BufferedOutputStream(socket.getOutputStream()));

				// TODO authentication
				SessionID cid = cm.login();
				session = cm.getSession(cid);

				int r;
				while ((r = from.read()) > 0) {
					if (log.isTraceEnabled()) {
						log.trace("Cmd: " + (char) r);
					}

					try {
						switch (r) {
						case 'q':
							query(from, to);
							to.write(0); // end of response
							break;
						case 'b':
							session.begin(false);
							writeString(to, "begin");
							break;
						case 'c':
							session.commit();
							writeString(to, "commit");
							break;
						case 'r':
							session.rollback();
							writeString(to, "rollback");
							break;
						}
						to.write('s');
					} catch (SocketTimeoutException e) {
						throw new Exception("Connection timed out", e);
					} catch (IOException e) {
						// directly abort if communication failed
						throw new Exception("Connection error", e);
					} catch (Throwable e) {
						if (log.isDebugEnabled()) {
							if (!(e instanceof QueryException)) {
								e.printStackTrace();
								log.error(e);
							}
						}
						// a non-io-related app error occured
						to.write(0); // write 0 to indicate end of response
						to.write('e');
						writeString(to, e.getMessage());
					} finally {
						to.deferedFlush();
					}
				}
				cm.logout(session.getSessionID());

				if (log.isTraceEnabled()) {
					log.trace("Close TCP connection");
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				cm.logout(session.getSessionID());
				try {
					socket.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		private void query(InputStream from, OutputStream to) throws Throwable {
			Tx tx = session.getTX();

			try {
				String query = readString(from);

				if (log.isTraceEnabled()) {
					log.trace("Query: " + query);
				}

				XQuery xq = new XQuery(query);
				xq
						.serialize(new TXQueryContext(tx, store),
								new PrintStream(to));

				if (tx == null) {
					session.commit();
				}
			} catch (Throwable e) {
				log.error(e);
				try {
					if (tx == null) {
						session.rollback();
					}
				} catch (ServerException e1) {
					log.error(e1);
				}
				throw e;
			}
		}

		private void writeString(OutputStream out, String s) throws IOException {
			int r;
			ByteArrayInputStream response = new ByteArrayInputStream(s
					.getBytes("UTF-8"));
			while ((r = response.read()) > 0) {
				out.write(r);
			}
			out.write(0);
		}

		private String readString(InputStream in) throws IOException {
			int r;
			ByteArrayOutputStream payload = new ByteArrayOutputStream();
			while ((r = in.read()) > 0) {
				payload.write(r);
			}
			String string = payload.toString("UTF-8");
			return string;
		}
	}
}