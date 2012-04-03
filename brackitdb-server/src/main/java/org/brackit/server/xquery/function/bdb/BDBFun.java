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
package org.brackit.server.xquery.function.bdb;

import static org.brackit.server.xquery.function.bdb.CreateCASIndex.CREATE_CAS_INDEX;
import static org.brackit.server.xquery.function.bdb.CreateNameIndex.CREATE_NAME_INDEX;
import static org.brackit.server.xquery.function.bdb.CreatePathIndex.CREATE_PATH_INDEX;

import org.brackit.server.xquery.function.bdb.buffer.ClearBuffers;
import org.brackit.server.xquery.function.bdb.buffer.StartBuffer;
import org.brackit.server.xquery.function.bdb.buffer.StopBuffer;
import org.brackit.server.xquery.function.bdb.statistics.ListBuffer;
import org.brackit.server.xquery.function.bdb.statistics.ListBuffers;
import org.brackit.server.xquery.function.bdb.statistics.ListConnections;
import org.brackit.server.xquery.function.bdb.statistics.ListContainers;
import org.brackit.server.xquery.function.bdb.statistics.ListLocks;
import org.brackit.server.xquery.function.bdb.statistics.ListVocabulary;
import org.brackit.server.xquery.function.bdb.util.DotIndex;
import org.brackit.server.xquery.function.bdb.util.DumpIndex;
import org.brackit.server.xquery.function.bdb.workload.DocumentScan;
import org.brackit.server.xquery.function.bdb.workload.SaxScan;
import org.brackit.server.xquery.function.bdb.workload.Traverse;
import org.brackit.xquery.atomic.QNm;
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
public class BDBFun {
	public static final String BDB_PREFIX = "bdb";

	public static final String BDB_NSURI = "http://brackit.org/ns/bdb";

	public static final QNm ERR_INVALID_ARGUMENT = new QNm(BDB_NSURI,
			BDB_PREFIX, "BDBF0001");

	public static void register() {
		// dummy function to cause static block
		// to be executed exactly once
	}

	static {
		Namespaces.predefine(BDBFun.BDB_PREFIX, BDBFun.BDB_NSURI);
		Functions.predefine(new DumpIndex());
		Functions.predefine(new DotIndex());
		Functions.predefine(new Traverse());
		Functions.predefine(new SaxScan());
		Functions.predefine(new ClearBuffers());
		Functions.predefine(new ListBuffers());
		Functions.predefine(new ListBuffer());
		Functions.predefine(new StartBuffer());
		Functions.predefine(new StopBuffer());
		Functions.predefine(new ListConnections());
		Functions.predefine(new ListContainers());
		Functions.predefine(new ListLocks());
		Functions.predefine(new ListVocabulary());
		Functions.predefine(new DocumentScan());
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
}
