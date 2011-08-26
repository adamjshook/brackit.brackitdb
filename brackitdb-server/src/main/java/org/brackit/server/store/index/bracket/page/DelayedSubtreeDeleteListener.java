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
package org.brackit.server.store.index.bracket.page;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.SubtreeDeleteListener;
import org.brackit.server.store.page.bracket.KeyValueTuple;

/**
 * This subtree delete listener buffers all notifications and passes them to
 * another listener, when the {@link #flush()} method is invoked.
 * 
 * @author Martin Hiller
 * 
 */
public class DelayedSubtreeDeleteListener implements SubtreeDeleteListener {

	private final SubtreeDeleteListener deleteListener;
	private final List<KeyValueTuple> nodes;
	private boolean subtreeEnd = false;

	public DelayedSubtreeDeleteListener(SubtreeDeleteListener deleteListener) {
		this.deleteListener = deleteListener;
		this.nodes = new ArrayList<KeyValueTuple>();
	}

	@Override
	public void deleteNode(XTCdeweyID deweyID, byte[] value, int level) {
		nodes.add(new KeyValueTuple(deweyID, value, level));
	}

	public void flush() throws IndexOperationException {
		for (KeyValueTuple node : nodes) {
			deleteListener.deleteNode(node.key, node.value, node.level);
		}
		if (subtreeEnd) {
			deleteListener.subtreeEnd();
		}
	}

	@Override
	public void subtreeEnd() throws IndexOperationException {
		subtreeEnd = true;
	}
	
	public void reset() {
		nodes.clear();
		subtreeEnd = false;
	}
}