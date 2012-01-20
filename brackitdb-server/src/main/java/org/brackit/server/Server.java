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
package org.brackit.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.brackit.server.api.TCPConnector;
import org.brackit.server.session.SessionMgr;
import org.brackit.shell.Shell;
import org.brackit.xquery.util.Cfg;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public final class Server {

	public static final int STOP_BYTE = 123;
	public static final int PING_BYTE = 012;
	public static final int PONG_BYTE = 210;
	public static final int DEFAULT_DB_PORT = 24201;
	
	private class ShutdownThread extends Thread {
		final Server server;
		
		public ShutdownThread(Server server) {
			this.server = server;
		}

		@Override
		public void run() {
			try {
				server.stopSingletonThread();
			} catch (ServerException e) {
				// ignore
			}
		}
	}

	private static class StartOption {
		boolean install;
		boolean start;
		boolean clp;
		boolean stop;

		public StartOption(boolean install, boolean start, boolean clp,
				boolean stop) {
			super();
			this.install = install;
			this.start = start;
			this.clp = clp;
			this.stop = stop;
		}

		public boolean isInstall() {
			return install;
		}

		public boolean isStart() {
			return start;
		}

		public boolean isClp() {
			return clp;
		}

		public boolean isStop() {
			return stop;
		}
	}

	private class SingletonThread extends Thread {
		private final int port;
		private final ServerSocket serverSocket;

		SingletonThread(int port) throws Exception {
			this.port = port;
			this.serverSocket = new ServerSocket(port);
			setDaemon(false);
		}

		@Override
		public void run() {			
			int read = 0;
			while (read != STOP_BYTE) {
				try {
					Socket socket = serverSocket.accept();
					InputStream in = socket.getInputStream();
					OutputStream out = socket.getOutputStream();
					read = in.read();
					if (read == STOP_BYTE) {
						shutdown();
					} else if (read == PING_BYTE) {
						out.write(PONG_BYTE);
					}
					socket.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private BrackitDB db;

	private TCPConnector con;

	public Server(StartOption startOption) throws ServerException {
		if ((startOption.isInstall()) || (startOption.isStart())) {
			startSingletonThread();
		}
		if (startOption.isInstall()) {
			install();
		}
		if (startOption.isStart()) {
			start();
			Runtime.getRuntime().addShutdownHook(new ShutdownThread(this));
		}
		if (startOption.isClp()) {
			try {
				new Shell("localhost", 11011).run();
			} catch (Exception e) {
				System.out.print("Error starting shell: ");
				System.out.println(e.getMessage());
			}
		}
		if (startOption.isStop()) {			
			stopSingletonThread();
		}
	}

	public static void main(String args[]) {
		try {
			StartOption startOption = checkArgs(args);
			new Server(startOption);
		} catch (ServerException e) {
			e.printStackTrace();
		}
	}

	private static StartOption checkArgs(String[] args) {
		String startOption = (args.length == 1) ? args[0].toUpperCase() : null;
		if ("INSTALL".equals(startOption)) {
			return new StartOption(true, false, false, true);
		}
		if ("START".equals(startOption)) {
			return new StartOption(false, true, false, false);
		}
		if ("STARTCLP".equals(startOption)) {
			return new StartOption(false, true, true, false);
		}
		if ("INSTALLSTART".equals(startOption)) {
			return new StartOption(true, true, false, false);
		}
		if ("INSTALLSTARTCLP".equals(startOption)) {
			return new StartOption(true, true, true, false);
		}
		if ("STOP".equals(startOption)) {
			return new StartOption(false, false, false, true);
		}
		if ("PING".equals(startOption)) {
			System.exit(check());
			return null;
		} else {
			System.out.println("Invalid arguments:");
			System.out
					.println("install\t- Install a new instance (deletes existing data!)");
			System.out.println("start\t- Start an instance");
			System.out.println("startclp\t- Start an instance with a shell");
			System.out.println("installstart\t- Install and start an instance");
			System.out
					.println("installstartclp\t- Install and start an instance with a shell");
			System.out.println("ping\t- Checks if the server is running");
			System.out.println("stop\t- Stops a running server instance");
			System.exit(-1);
			return null;
		}
	}

	private synchronized void install() throws ServerException {
		System.out.print("Install server ... ");
		try {
			db = new BrackitDB(true);
			db.shutdown();
		} catch (ServerException e) {
			System.out.print("failed: ");
			System.out.println(e.getMessage());
			throw e;
		}
		System.out.println("done.");
	}

	private synchronized void start() throws ServerException {
		System.out.print("Start server ... ");
		try {
			db = new BrackitDB(false);
			con = new TCPConnector(db.getSessionMgr(), db.getMetadataMgr(),
					11011);
			new Thread(con).start();
		} catch (ServerException e) {
			System.out.print("failed: ");
			System.out.println(e.getMessage());
			throw e;
		}
		System.out.println("done.");
	}

	private void stopSingletonThread() throws ServerException {
		int dbPort = Cfg.asInt(SessionMgr.DB_PORT, DEFAULT_DB_PORT);			

		try {
			Socket socket = new Socket("localhost", dbPort);
			OutputStream out = socket.getOutputStream();
			out.write(STOP_BYTE);
			out.flush();
			socket.close();

			for (int i = 0; i < 60; i++) {
				try {
					Thread.sleep(1000);
					socket = new Socket("localhost", dbPort);
					System.out.println("could still connect");
				} catch (InterruptedException e) {
					// ignore
				} catch (Exception e) {
					socket.close();
					return;
				}
			}
			throw new ServerException("Server seems to ignore shutdown signal");
		} catch (Exception e) {
			// ignore
		}
	}

	private void startSingletonThread() throws ServerException {
		try {
			int dbPort = Cfg.asInt(SessionMgr.DB_PORT, DEFAULT_DB_PORT);
			SingletonThread singletonThread = new SingletonThread(dbPort);
			singletonThread.start();
		} catch (Exception e) {
			throw new ServerException(e, "Could not start server");
		}
	}

	private synchronized void shutdown() {
		System.out.print("Shutdown server ... ");
		try {
			if (con != null) {
				con.shutdown();
			}
		} catch (Exception e) {
			// ignore
		}
		try {
			db.shutdown();
		} catch (ServerException e) {
			System.out.print("failed: ");
			System.out.println(e.getMessage());
		}		
		System.out.println("done.");
	}

	private static int check() {
		Socket socket;

		int dbPort = Cfg.asInt(SessionMgr.DB_PORT, DEFAULT_DB_PORT);
		try {
			socket = new Socket("localhost", dbPort);
			socket.setSoTimeout(1000);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			out.write(PING_BYTE);
			int read = in.read();
			socket.close();
			return (read == PONG_BYTE) ? 0 : -1;
		} catch (Exception e) {
			return -1;
		}
	}
}
