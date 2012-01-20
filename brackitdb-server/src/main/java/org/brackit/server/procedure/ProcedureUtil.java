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
package org.brackit.server.procedure;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.brackit.xquery.util.log.Logger;
import org.brackit.server.metadata.TXQueryContext;
import org.brackit.server.procedure.buffer.ClearBuffers;
import org.brackit.server.procedure.buffer.StartBuffer;
import org.brackit.server.procedure.buffer.StopBuffer;
import org.brackit.server.procedure.statistics.ListBuffer;
import org.brackit.server.procedure.statistics.ListBuffers;
import org.brackit.server.procedure.statistics.ListConnections;
import org.brackit.server.procedure.statistics.ListContainers;
import org.brackit.server.procedure.statistics.ListLocks;
import org.brackit.server.procedure.statistics.ListVocabulary;
import org.brackit.server.procedure.util.DotIndex;
import org.brackit.server.procedure.util.DumpIndex;
import org.brackit.server.procedure.util.List;
import org.brackit.server.procedure.workload.DocumentScan;
import org.brackit.server.procedure.workload.InsertSubtrees;
import org.brackit.server.procedure.workload.SaxScan;
import org.brackit.server.procedure.workload.Traverse;
import org.brackit.server.procedure.xmark.XMark;
import org.brackit.server.procedure.xmark.XMarkQuery1;
import org.brackit.xquery.ErrorCode;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.node.SubtreePrinter;
import org.brackit.xquery.xdm.Item;
import org.brackit.xquery.xdm.Iter;
import org.brackit.xquery.xdm.Kind;
import org.brackit.xquery.xdm.Node;
import org.brackit.xquery.xdm.Sequence;

/**
 * 
 * @author Sebastian Baechle
 * 
 */
public class ProcedureUtil {
	private static final Logger log = Logger.getLogger(ProcedureUtil.class);

	private static Map<String, Class<? extends Procedure>> procedures;

	private static void addProcedure(Class<? extends Procedure> procedureClass) {
		procedures.put(procedureClass.getSimpleName().toLowerCase(),
				procedureClass);
	}

	static {
		procedures = new HashMap<String, Class<? extends Procedure>>();
		addProcedure(InsertSubtrees.class);
		addProcedure(List.class);
		addProcedure(DumpIndex.class);
		addProcedure(DotIndex.class);
		addProcedure(Traverse.class);
		addProcedure(SaxScan.class);
		addProcedure(ClearBuffers.class);
		addProcedure(XMark.class);
		addProcedure(ListBuffers.class);
		addProcedure(ListBuffer.class);
		addProcedure(StartBuffer.class);
		addProcedure(StopBuffer.class);
		addProcedure(ListConnections.class);
		addProcedure(ListContainers.class);
		addProcedure(ListLocks.class);
		addProcedure(ListVocabulary.class);
		addProcedure(DocumentScan.class);
		addProcedure(XMarkQuery1.class);
	}

	public static void execute(TXQueryContext ctx, OutputStream os,
			String name, String[] params) throws QueryException {
		Procedure procedure = getProcedure(name);

		if (procedure == null) {
			throw new QueryException(ErrorCode.BIT_DYN_INT_ERROR,
					"Unknown procedure: %s", name);
		}

		Sequence result = procedure.execute(ctx, params);

		if (result == null) {
			return;
		}

		boolean first = true;
		PrintWriter out = new PrintWriter(os);
		SubtreePrinter printer = new SubtreePrinter(out);
		printer.setPrettyPrint(true);
		printer.setAutoFlush(false);
		Item item;
		Iter it = result.iterate();
		try {
			while ((item = it.next()) != null) {
				if (item instanceof Node<?>) {
					Node<?> node = (Node<?>) item;
					Kind kind = node.getKind();

					if (kind == Kind.ATTRIBUTE) {
						throw new QueryException(
								ErrorCode.ERR_SERIALIZE_ATTRIBUTE_OR_NAMESPACE_NODE);
					}
					if (kind == Kind.DOCUMENT) {
						node = node.getFirstChild();
						while (node.getKind() != Kind.ELEMENT) {
							node = node.getNextSibling();
						}
					}

					printer.print(node);
					first = true;
				} else {
					if (!first) {
						out.write(" ");
					}
					out.write(item.toString());
					first = false;
				}
			}
		} finally {
			printer.flush();
			out.flush();
			it.close();
		}
	}

	public static Procedure getProcedure(String name) {
		Procedure procedure = null;
		Class<? extends Procedure> clazz = procedures.get(name.toLowerCase());

		if (clazz != null) {
			try {
				procedure = clazz.newInstance();
			} catch (Exception e) {
				log.error("procedure not found: " + name);
			}
		}

		return procedure;
	}

	public static void register(Class<? extends Procedure> clazz,
			InfoContributor ic) {
		String name = clazz.getSimpleName().toLowerCase();
		DynamicProcedure proc = (DynamicProcedure) getProcedure(name);
		proc.addInfoContributor(ic);
	}

	public static void deregister(Class<? extends Procedure> clazz,
			InfoContributor ic) {
		String name = clazz.getSimpleName().toLowerCase();
		DynamicProcedure proc = (DynamicProcedure) getProcedure(name);
		proc.removeInfoContributor(ic);
	}

	public static void register(String name, InfoContributor ic) {
		DynamicProcedure proc = (DynamicProcedure) getProcedure(name);
		proc.addInfoContributor(ic);
	}

	public static Collection<Procedure> getPlans() {
		ArrayList<Procedure> list = new ArrayList<Procedure>();

		for (String name : procedures.keySet())
			list.add(getProcedure(name));

		return list;
	}

	public static void checkParameterCount(String[] params, int minCount,
			int maxCount, String[] paramDesc) throws ProcedureException {
		if ((params.length < minCount) || (params.length > maxCount)) {
			System.err.println(Arrays.toString(params));
			throw new ProcedureException(
					"Invalid Number of parameters.\nUsage: %s", Arrays
							.toString(paramDesc));
		}
	}

	public static int getInt(String[] params, int pos, String parameterName,
			int defaultValue, int[] allowedValues, boolean required)
			throws ProcedureException {
		if (pos >= params.length) {
			if (required) {
				throw new ProcedureException("Invalid number of parameters.");
			}

			return defaultValue;
		}

		try {
			int value = Integer.parseInt(params[pos]);

			if (allowedValues == null) {
				return value;
			}

			for (int allowedValue : allowedValues) {
				if (value == allowedValue) {
					return value;
				}
			}

			throw new ProcedureException("Invalid parameter %s. Expected %s",
					parameterName, Arrays.toString(allowedValues));
		} catch (NumberFormatException e) {
			throw new ProcedureException(
					"Parameter %s is not a valid integer: %s", parameterName,
					params[pos]);
		}
	}

	public static boolean getBoolean(String[] params, int pos,
			String parameterName, boolean defaultValue, boolean required)
			throws ProcedureException {
		if (pos >= params.length) {
			if (required) {
				throw new ProcedureException("Invalid number of parameters.");
			}

			return defaultValue;
		}

		return Boolean.parseBoolean(params[pos]);
	}

	public static String getString(String[] params, int pos,
			String parameterName, String defaultValue, String[] allowedValues,
			boolean required) throws ProcedureException {
		if (pos >= params.length) {
			if (required) {
				throw new ProcedureException("Invalid number of parameters.");
			}

			return defaultValue;
		}

		String value = params[pos];

		if (allowedValues == null) {
			return value;
		}

		for (String allowedValue : allowedValues) {
			if (value.equals(allowedValue)) {
				return value;
			}
		}

		throw new ProcedureException("Invalid parameter %s. Expected %s",
				parameterName, Arrays.toString(allowedValues));
	}
}