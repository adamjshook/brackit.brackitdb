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
package org.brackit.server.store.index.bracket;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.ServerException;
import org.brackit.server.io.buffer.PageID;
import org.brackit.server.store.blob.BlobStore;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.tx.PostCommitHook;
import org.brackit.server.tx.Tx;

/**
 * Deletes a list of externalized value given by their PageID.
 * 
 * @author Martin Hiller
 * 
 */
public class DeleteExternalizedHook implements PostCommitHook {

	private final BlobStore blobStore;
	private final List<PageID> externalPageIDs;

	public DeleteExternalizedHook(BlobStore blobStore,
			List<PageID> externalPageIDs) {
		this.blobStore = blobStore;
		this.externalPageIDs = externalPageIDs;
	}

	/**
	 * @see org.brackit.server.tx.PostCommitHook#execute(org.brackit.server.tx.Tx)
	 */
	@Override
	public void execute(Tx tx) throws ServerException {

		List<PageID> exceptionPageIDs = null;
		for (PageID pageID : externalPageIDs) {
			try {
				blobStore.drop(tx, pageID);
			} catch (BlobStoreAccessException e) {
				if (exceptionPageIDs == null) {
					exceptionPageIDs = new ArrayList<PageID>();
				}
				exceptionPageIDs.add(pageID);
			}
		}
		if (exceptionPageIDs != null) {
			throw new ServerException(String.format(
					"Error deleting overflow values %s.", exceptionPageIDs));
		}
	}

}
