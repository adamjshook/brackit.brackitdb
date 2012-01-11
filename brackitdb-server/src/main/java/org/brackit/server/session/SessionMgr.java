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
package org.brackit.server.session;

/**
 * @author Sebastian Baechle
 * 
 */
public interface SessionMgr {
	public static final String MAX_CONNECTIONS = "org.brackit.server.connection.maxConnections";

	public static final String CONNECTION_TIMEOUT = "org.brackit.server.connection.connectionTimeout";

	public static final String SHUTDOWN_RETRY_INTERVAL = "org.brackit.server.connection.shutdownRetryInterval";

	public static final String MAX_SHUTDOWN_RETRIES = "org.brackit.server.connection.maxShutdownRetries";

	public static final String DB_PORT = "org.brackit.server.connection.dbPort";

	public static final String SERVER_PORT = "org.brackit.server.connection.serverPort";

	public static final String HTTP_PORT = "org.brackit.server.connection.httpPort";

	public static final String HTTP_MAX_CLIENTS = "org.brackit.server.connection.httpMaxClients";

	public static final String FTP_PORT = "org.brackit.server.connection.ftpPort";

	public static final String FTP_MAX_CLIENTS = "org.brackit.server.connection.ftpMaxClients";
	
	public static final int DEFAULT_MAX_CONNECTIONS = 50;
	
	public static final int DEFAULT_CONNECTION_TIMEOUT = 50;

	public SessionID login() throws SessionException;

	public void logout(SessionID sessionID);

	/**
	 * Sends a new alive sign for a connection.
	 * 
	 * @param sessionID
	 */
	public void ping(SessionID sessionID);

	public void clearStaleConnections();

	public boolean alive(SessionID sessionID);

	public void shutdown();

	public void prepare(SessionID sessionID) throws SessionException;

	public void cleanup(SessionID sessionID, boolean success,
			boolean forceCommit) throws SessionException;

	public Session getSession(SessionID sessionID) throws SessionException;
}
