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
package org.brackit.server.xquery;

import static org.brackit.server.xquery.function.bdb.CreateCASIndex.CREATE_CAS_INDEX;
import static org.brackit.server.xquery.function.bdb.CreateNameIndex.CREATE_NAME_INDEX;
import static org.brackit.server.xquery.function.bdb.CreatePathIndex.CREATE_PATH_INDEX;

import java.util.Map;

import org.brackit.server.metadata.manager.MetaDataMgr;
import org.brackit.server.tx.Tx;
import org.brackit.server.xquery.compiler.optimizer.DBOptimizer;
import org.brackit.server.xquery.compiler.translator.DBTranslator;
import org.brackit.server.xquery.function.bdb.CreateCASIndex;
import org.brackit.server.xquery.function.bdb.CreateNameIndex;
import org.brackit.server.xquery.function.bdb.CreatePathIndex;
import org.brackit.server.xquery.function.bdb.SetIsolation;
import org.brackit.server.xquery.function.bdb.SetLockdepth;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.compiler.CompileChain;
import org.brackit.xquery.compiler.optimizer.Optimizer;
import org.brackit.xquery.compiler.translator.Translator;
import org.brackit.xquery.module.Functions;
import org.brackit.xquery.module.Namespaces;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;

/**
 * @author Sebastian Baechle
 * 
 */
public class DBCompileChain extends CompileChain {

	public static final String BDB_PREFIX = "bdb";

	public static final String BDB_NSURI = "http://brackit.org/ns/bdb";

	static {
		Namespaces.predefine(BDB_PREFIX, BDB_NSURI);
		Functions.predefine(new SetIsolation());
		Functions.predefine(new SetLockdepth());

		Functions.predefine(new CreatePathIndex(CREATE_PATH_INDEX,
				new Signature(SequenceType.NODE, new SequenceType(
						AtomicType.STR, Cardinality.One), new SequenceType(
						AtomicType.STR, Cardinality.ZeroOrMany))));
		Functions.predefine(new CreatePathIndex(CREATE_PATH_INDEX,
				new Signature(SequenceType.NODE, new SequenceType(
						AtomicType.STR, Cardinality.One))));

		Functions.predefine(new CreateNameIndex(CREATE_NAME_INDEX,
				new Signature(SequenceType.NODE, new SequenceType(
						AtomicType.STR, Cardinality.One), new SequenceType(
						AtomicType.QNM, Cardinality.ZeroOrMany))));
		Functions.predefine(new CreateNameIndex(CREATE_NAME_INDEX,
				new Signature(SequenceType.NODE, new SequenceType(
						AtomicType.STR, Cardinality.One))));

		Functions.predefine(new CreateCASIndex(CREATE_CAS_INDEX, new Signature(
				SequenceType.NODE, new SequenceType(AtomicType.STR,
						Cardinality.One), new SequenceType(AtomicType.STR,
						Cardinality.ZeroOrOne), new SequenceType(
						AtomicType.STR, Cardinality.ZeroOrMany))));
		Functions.predefine(new CreateCASIndex(CREATE_CAS_INDEX, new Signature(
				SequenceType.NODE, new SequenceType(AtomicType.STR,
						Cardinality.One), new SequenceType(AtomicType.STR,
						Cardinality.ZeroOrOne))));
		Functions.predefine(new CreateCASIndex(CREATE_CAS_INDEX, new Signature(
				SequenceType.NODE, new SequenceType(AtomicType.STR,
						Cardinality.One))));

	}

	private final MetaDataMgr mdm;

	private final Tx tx;

	public DBCompileChain(MetaDataMgr mdm, Tx tx) {
		this.mdm = mdm;
		this.tx = tx;
	}

	@Override
	protected Translator getTranslator(Map<QNm, Str> options) {
		return new DBTranslator(options);
	}

	@Override
	protected Optimizer getOptimizer(Map<QNm, Str> options) {
		return new DBOptimizer(options, mdm, tx);
	}
}
