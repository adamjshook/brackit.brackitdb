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
package org.brackit.server.metadata.manager;

import java.io.InputStream;

import org.brackit.server.ServerException;
import org.brackit.server.metadata.BlobHandle;
import org.brackit.server.metadata.DBItem;
import org.brackit.server.metadata.manager.impl.ItemNotFoundException;
import org.brackit.server.node.DocID;
import org.brackit.server.tx.Tx;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.xdm.Collection;
import org.brackit.xquery.xdm.DocumentException;

/**
 * @author Karsten Schmidt
 * @author Sebastian Baechle
 */
public interface MetaDataMgr {
	public Collection<?> lookup(Tx tx, String name) throws DocumentException;

	public Collection<?> create(Tx tx, String name) throws DocumentException;

	public Collection<?> create(Tx tx, String name, SubtreeParser parser)
			throws DocumentException;

	public void drop(Tx tx, String name) throws DocumentException;

	public Collection<?> lookup(Tx tx, DocID docID)
			throws ItemNotFoundException, DocumentException;

	public void mv(Tx tx, String path, String newPath) throws DocumentException;

	public boolean isDirectory(Tx tx, String path) throws DocumentException;

	public void mkdir(Tx tx, String path) throws DocumentException;

	public BlobHandle putBlob(Tx tx, InputStream in, String path,
			int containerNo) throws DocumentException;

	public BlobHandle getBlob(Tx tx, String path) throws ItemNotFoundException,
			DocumentException;

	public BlobHandle getBlob(Tx tx, DocID id) throws ItemNotFoundException,
			DocumentException;

	public void start(Tx tx, boolean install) throws ServerException;

	public void shutdown() throws ServerException;

	public DBItem<?> getItem(Tx tx, String path) throws ItemNotFoundException,
			DocumentException;
}
