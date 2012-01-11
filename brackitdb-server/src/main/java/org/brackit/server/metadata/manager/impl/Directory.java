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
package org.brackit.server.metadata.manager.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.brackit.server.node.txnode.TXNode;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.node.parser.FragmentHelper;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Node;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class Directory extends Item<Directory> {
	private final List<Item<?>> children;

	public Directory(String name, Directory parent, TXNode<?> masterDocNode) {
		super(name, parent, masterDocNode);
		children = new ArrayList<Item<?>>();
	}

	public List<Item<?>> getChildren() {
		return Collections.unmodifiableList(children);
	}

	public boolean hasChild(String name) {
		for (Item<?> child : getChildren()) {
			if (child.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public void addChild(Item<?> child) {
		children.add(child);
	}

	public void deleteChild(Item<?> child) {
		children.remove(child);
	}

	@Override
	public void delete() throws DocumentException {
		super.delete();
		for (Item<?> child : children) {
			child.setDeleted(true);
		}
	}

	public void create() throws DocumentException {
		FragmentHelper helper = new FragmentHelper();
		helper.openElement(MetaDataMgrImpl.DIR_TAG);
		helper.attribute(MetaDataMgrImpl.NAME_ATTR, new Str(name));
		helper.closeElement();
		Node<?> toMaterialize = helper.getRoot();

		masterDocNode = parent.getMasterDocNode().append(toMaterialize);
	}

	@Override
	public String toString() {
		return String.format("Directory '%s'", name);
	}
}