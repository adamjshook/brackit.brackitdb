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
package org.brackit.server.metadata.materialize;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.brackit.server.metadata.masterDocument.Indexes;
import org.brackit.server.node.index.definition.IndexDef;
import org.brackit.server.node.index.external.IndexStatistics;
import org.brackit.xquery.xdm.DocumentException;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public final class MaterializableFactory {
	private Map<String, Class<? extends Materializable>> mapping 
		= new ConcurrentHashMap<String, Class<? extends Materializable>>();

	private static MaterializableFactory instance = new MaterializableFactory();

	static {
		instance.register(Indexes.INDEXES_TAG.stringValue(), Indexes.class);
		instance.register(IndexDef.INDEX_TAG.stringValue(), IndexDef.class);
		instance.register(IndexStatistics.STATISTICS_TAG.stringValue(), 
				IndexStatistics.class);
	}

	private MaterializableFactory() {
	}

	public static MaterializableFactory getInstance() {
		return instance;
	}

	public Materializable create(String name) throws DocumentException {
		Class<? extends Materializable> type = mapping.get(name);

		if (type == null) {
			throw new DocumentException("Unknown materializable name: '%s'",
					name);
		}

		try {
			return type.newInstance();
		} catch (Exception e) {
			throw new DocumentException(e, "Could not instantiate %s", type);
		}
	}

	public void register(String name, Class<? extends Materializable> type) {
		mapping.put(name, type);
	}

	public void deregister(String name) {
		mapping.remove(name);
	}
}